package com.nebula.space.bookmark;

import com.nebula.space.reading.ArticleFetcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 书签链接检查：服务端替用户访问网址，看还打不打得开
 *
 * <p>和稍后读抓取共用一套防 SSRF 的 DNS 解析，只连公网地址。只看响应码不读正文，拿到响应头就中止连接。
 * 判定偏保守：只有「域名不存在」「连接被拒绝」「404 / 410」算打不开；超时、连接被重置、5xx、证书错误
 * 可能只是临时故障或网络限制（境外站点从服务器上常常访问不到），记为无法确定，不改书签状态。
 * 内网地址服务器不去访问，也记为无法确定。</p>
 */
@Slf4j
@Component
public class LinkChecker implements DisposableBean {

    /** 单个网址从发起到结束的上限 */
    static final long DEADLINE_SECONDS = 15;

    /** 同时检查的网址数（所有用户共用） */
    static final int PARALLELISM = 16;

    public enum Verdict {
        /** 能打开 */
        ALIVE,
        /** 打不开 */
        DEAD,
        /** 无法确定 */
        UNKNOWN
    }

    /**
     * 检查结论
     *
     * @param verdict 能否打开
     * @param reason  打不开或无法确定的原因；能打开时为 null
     */
    public record Outcome(Verdict verdict, String reason) {

        static Outcome alive() {
            return new Outcome(Verdict.ALIVE, null);
        }

        static Outcome dead(String reason) {
            return new Outcome(Verdict.DEAD, reason);
        }

        static Outcome unknown(String reason) {
            return new Outcome(Verdict.UNKNOWN, reason);
        }
    }

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "link-check-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final CloseableHttpClient client;
    private final ExecutorService pool;

    public LinkChecker() {
        client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new ArticleFetcher.PublicOnlyDnsResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(6))
                                .setSocketTimeout(Timeout.ofSeconds(10))
                                .build())
                        .setMaxConnTotal(PARALLELISM * 2)
                        .setMaxConnPerRoute(4)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .setMaxRedirects(8)
                        .build())
                // 有的站点不像浏览器就直接 403，用浏览器的 UA 少些误判；403 本身也按「能打开」算
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0 Safari/537.36 NebulaLinkCheck/1.0")
                .disableAutomaticRetries()
                .build();
        AtomicInteger seq = new AtomicInteger();
        pool = Executors.newFixedThreadPool(PARALLELISM, r -> {
            Thread t = new Thread(r, "link-check-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 并发检查一批网址，结果与入参一一对应
     */
    public List<Outcome> checkAll(List<String> urls) {
        List<CompletableFuture<Outcome>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> check(url), pool))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /**
     * 检查一个网址；不抛异常
     */
    public Outcome check(String url) {
        URI uri;
        try {
            uri = URI.create(URI.create(url == null ? "" : url.trim()).toASCIIString());
        } catch (IllegalArgumentException e) {
            return Outcome.unknown("网址格式不对");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Outcome.unknown("不是网页地址");
        }
        if (uri.getHost() == null) {
            return Outcome.unknown("网址格式不对");
        }
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
        get.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        // 每次检查单独一份 Cookie：有的站点先种 Cookie 再跳转，不带就一直跳
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(new BasicCookieStore());
        ScheduledFuture<?> timeout = WATCHDOG.schedule(get::cancel, DEADLINE_SECONDS, TimeUnit.SECONDS);
        ClassicHttpResponse response = null;
        try {
            response = client.executeOpen(RoutingSupport.determineHost(get), get, context);
            return judge(response.getCode());
        } catch (ArticleFetcher.NonPublicAddressException e) {
            return Outcome.unknown("内网地址，服务器不检查");
        } catch (UnknownHostException e) {
            return Outcome.dead("域名无法解析");
        } catch (ConnectException e) {
            return Outcome.dead("连接被拒绝");
        } catch (SSLException e) {
            return Outcome.unknown("安全连接失败（证书或加密协议有问题）");
        } catch (InterruptedIOException e) {
            // 连接超时、读超时、到点被中止都在这里
            return Outcome.unknown("访问超时");
        } catch (ClientProtocolException e) {
            return Outcome.unknown("跳转过多或响应不规范");
        } catch (IOException e) {
            return Outcome.unknown("连接中断");
        } catch (Exception e) {
            log.info("书签链接检查 {} 出错：{}", url, e.toString());
            return Outcome.unknown("检查出错");
        } finally {
            timeout.cancel(false);
            // 不读正文：直接中止，不把连接留给复用
            get.cancel();
            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignored) {
                    // 连接已中止
                }
            }
        }
    }

    /**
     * 按最终（跟完跳转后）的响应码判定
     */
    static Outcome judge(int code) {
        if (code == 404) {
            return Outcome.dead("页面不存在（404）");
        }
        if (code == 410) {
            return Outcome.dead("页面已被删除（410）");
        }
        // 2xx、3xx（跳转没跟到底），以及站点在线但拒绝程序访问的 401 / 403 / 405 / 406 / 429
        if (code < 400 || code == 401 || code == 403 || code == 405 || code == 406 || code == 429) {
            return Outcome.alive();
        }
        if (code >= 500) {
            return Outcome.unknown("服务器出错（" + code + "）");
        }
        return Outcome.unknown("访问被拒绝（" + code + "）");
    }

    @Override
    public void destroy() throws IOException {
        pool.shutdownNow();
        client.close();
    }
}
