package com.nebula.space.reading;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 稍后读的网页抓取
 *
 * <p>服务端替用户去访问任意网址，要防 SSRF：DNS 解析交给 {@link PublicOnlyDnsResolver}，
 * 解析出内网、回环、链路本地等地址就拒绝连接。每一跳重定向都走同一个解析器，检查和连接用的是同一批地址，
 * 不怕 DNS 重绑定。响应最多读 3MB（解压后），整个抓取 20 秒内没完成就中止。</p>
 */
@Slf4j
@Component
public class ArticleFetcher implements DisposableBean {

    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final long DEADLINE_SECONDS = 20;

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "reading-fetch-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final CloseableHttpClient client;

    public ArticleFetcher() {
        client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PublicOnlyDnsResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(5))
                                .setSocketTimeout(Timeout.ofSeconds(10))
                                .build())
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(4)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .setMaxRedirects(5)
                        .build())
                .setUserAgent("Mozilla/5.0 (compatible; NebulaReader/1.0)")
                .disableCookieManagement()
                .disableAutomaticRetries()
                .build();
    }

    /**
     * 抓取网页并提取正文
     *
     * @return 提取结果；打不开、是内网地址、不是网页、超时都返回 null（文章照样加入，只能打开原文）
     */
    public ArticleExtractor.Article fetch(String url) {
        HttpGet get;
        try {
            get = new HttpGet(URI.create(URI.create(url).toASCIIString()));
        } catch (IllegalArgumentException e) {
            return null;
        }
        get.setHeader("Accept", "text/html,application/xhtml+xml,text/plain;q=0.8,*/*;q=0.5");
        get.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        ScheduledFuture<?> timeout = WATCHDOG.schedule(get::cancel, DEADLINE_SECONDS, TimeUnit.SECONDS);
        ClassicHttpResponse response = null;
        try {
            response = client.executeOpen(RoutingSupport.determineHost(get), get, null);
            if (response.getCode() / 100 != 2 || response.getEntity() == null) {
                log.info("稍后读抓取 {} 返回 {}", url, response.getCode());
                return null;
            }
            HttpEntity entity = response.getEntity();
            ContentType type = ContentType.parseLenient(entity.getContentType());
            String mime = type == null || type.getMimeType() == null ? "text/html" : type.getMimeType().toLowerCase(Locale.ROOT);
            boolean html = "text/html".equals(mime) || "application/xhtml+xml".equals(mime);
            if (!html && !"text/plain".equals(mime)) {
                return null;
            }
            InputStream in = entity.getContent();
            byte[] body = in == null ? new byte[0] : in.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                // 超长的只用前 3MB，剩下的不读（finally 里中止连接）
                body = Arrays.copyOf(body, MAX_BYTES);
            }
            Charset charset = type == null ? null : type.getCharset();
            if (html) {
                // 响应头没写编码时 jsoup 按 BOM 和 <meta charset> 判断
                return ArticleExtractor.extract(Jsoup.parse(new ByteArrayInputStream(body), charset == null ? null : charset.name(), url));
            }
            return ArticleExtractor.fromPlainText(new String(body, charset == null ? StandardCharsets.UTF_8 : charset));
        } catch (Exception e) {
            log.info("稍后读抓取 {} 失败：{}", url, e.toString());
            return null;
        } finally {
            timeout.cancel(false);
            // 正常关闭会把没读完的响应读完以便复用连接（可能是个大文件），这里直接中止；已经读完的中止不影响
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

    @Override
    public void destroy() throws IOException {
        client.close();
    }

    /**
     * 是不是公网地址：回环、内网、链路本地、组播、运营商 NAT、保留段都不算
     */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int b0 = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            return b0 != 0
                    // 100.64.0.0/10 运营商 NAT
                    && !(b0 == 100 && b1 >= 64 && b1 <= 127)
                    // 192.0.0.0/24 协议分配
                    && !(b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0)
                    // 198.18.0.0/15 基准测试
                    && !(b0 == 198 && (b1 == 18 || b1 == 19))
                    // 240.0.0.0/4 保留、广播
                    && b0 < 240;
        }
        if (address instanceof Inet6Address v6) {
            int b0 = b[0] & 0xff;
            // fc00::/7 唯一本地
            if ((b0 & 0xfe) == 0xfc) {
                return false;
            }
            // 内嵌 IPv4 的几种写法：兼容地址 ::a.b.c.d、NAT64 64:ff9b::/96、6to4 2002::/16
            if (v6.isIPv4CompatibleAddress() || (b0 == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b)) {
                return isPublic(v4(b[12], b[13], b[14], b[15]));
            }
            if (b0 == 0x20 && (b[1] & 0xff) == 0x02) {
                return isPublic(v4(b[2], b[3], b[4], b[5]));
            }
        }
        return true;
    }

    private static InetAddress v4(byte a, byte b, byte c, byte d) {
        try {
            return InetAddress.getByAddress(new byte[]{a, b, c, d});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 只放行公网地址的 DNS 解析：有一个地址不是公网就整体拒绝
     */
    static final class PublicOnlyDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new UnknownHostException("不抓取内网地址：" + host);
                }
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }
}
