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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p>和稍后读抓取共用一套防 SSRF 的 DNS 解析，只连公网地址。只看响应码和响应头不读正文，拿到响应头就中止连接。
 * 判定偏保守，算打不开的只有：域名不存在、连接被拒绝、404 / 410、Cloudflare 报源站故障（521~530，
 * 读超时要放到 20 秒以上才等得到 522）、证书过期或与域名不符（浏览器会拦下）。
 * 超时、连接被重置、其他 5xx、证书链不全可能只是临时故障或网络限制（境外站点从服务器上常常访问不到），
 * 记为无法确定，不改书签状态，由前端再用用户自己的浏览器复查。内网地址服务器不去访问，也记为无法确定。</p>
 */
@Slf4j
@Component
public class LinkChecker implements DisposableBean {

    /** 单个网址从发起到结束的上限 */
    static final long DEADLINE_SECONDS = 30;

    /** 读响应的超时：Cloudflare 连不上源站时约 19 秒后才回 522，要比它长 */
    static final int RESPONSE_TIMEOUT_SECONDS = 25;

    /** Cloudflare 报源站故障的状态码：源站宕机、连接超时、不可达、加密握手失败、证书无效、源站域名解析失败 */
    private static final Map<Integer, String> CLOUDFLARE_ORIGIN_ERRORS = Map.of(
            521, "源站已宕机",
            522, "源站连接超时",
            523, "源站不可达",
            525, "源站加密握手失败",
            526, "源站证书无效",
            530, "源站域名解析失败");

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
                                .setSocketTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                                .build())
                        .setMaxConnTotal(PARALLELISM * 2)
                        .setMaxConnPerRoute(4)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
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
            Header server = response.getFirstHeader("Server");
            return judge(response.getCode(), server == null ? null : server.getValue());
        } catch (ArticleFetcher.NonPublicAddressException e) {
            return Outcome.unknown("内网地址，服务器不检查");
        } catch (UnknownHostException e) {
            return Outcome.dead("域名无法解析");
        } catch (ConnectException e) {
            return Outcome.dead("连接被拒绝");
        } catch (SSLException e) {
            return judgeTls(e);
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
     *
     * @param server 响应头 Server，用来认出 Cloudflare 的源站故障页（CSDN 等站点的防爬 WAF 也回 521，但不是 Cloudflare）
     */
    static Outcome judge(int code, String server) {
        String cloudflare = server != null && "cloudflare".equalsIgnoreCase(server.trim()) ? CLOUDFLARE_ORIGIN_ERRORS.get(code) : null;
        if (cloudflare != null) {
            return Outcome.dead(cloudflare + "（Cloudflare " + code + "）");
        }
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

    /**
     * 加密连接失败：证书过期、证书与域名不符时浏览器也会拦下，算打不开；
     * 证书链不全等可能只是服务器的根证书库比浏览器少，记为无法确定
     */
    static Outcome judgeTls(SSLException e) {
        int guard = 0;
        for (Throwable cur = e; cur != null && guard++ < 10; cur = cur.getCause()) {
            if (cur instanceof CertificateExpiredException
                    || (cur instanceof CertPathValidatorException cpv && cpv.getReason() == CertPathValidatorException.BasicReason.EXPIRED)) {
                return Outcome.dead("证书已过期");
            }
            if (isHostnameMismatch(cur)) {
                return Outcome.dead("证书与域名不符");
            }
        }
        return Outcome.unknown("安全连接失败（证书或加密协议有问题）");
    }

    /**
     * 主机名校验失败有两处来源：JDK 握手时的「No subject alternative DNS name matching x found」
     * （IP 为「No subject alternative names matching IP address」，没有 SAN 时为「No name matching」），
     * 以及 HttpClient 自己的「Certificate for &lt;x&gt; doesn't match …」
     */
    private static boolean isHostnameMismatch(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return false;
        }
        if (t instanceof CertificateException) {
            return message.startsWith("No subject alternative") || message.startsWith("No name matching");
        }
        return t instanceof SSLPeerUnverifiedException && message.contains("doesn't match");
    }

    @Override
    public void destroy() throws IOException {
        pool.shutdownNow();
        client.close();
    }
}
