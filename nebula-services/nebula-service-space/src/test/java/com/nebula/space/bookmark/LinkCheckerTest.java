package com.nebula.space.bookmark;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkCheckerTest {

    @Test
    void onlyNotFoundAndGoneCountAsDead() {
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(404, "nginx").verdict());
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(410, null).verdict());
        for (int code : new int[]{200, 204, 301, 302, 401, 403, 405, 406, 429}) {
            LinkChecker.Outcome outcome = LinkChecker.judge(code, "cloudflare");
            assertEquals(LinkChecker.Verdict.ALIVE, outcome.verdict(), String.valueOf(code));
            assertNull(outcome.reason());
        }
        for (int code : new int[]{400, 418, 451, 500, 502, 503}) {
            assertEquals(LinkChecker.Verdict.UNKNOWN, LinkChecker.judge(code, null).verdict(), String.valueOf(code));
        }
    }

    @Test
    void cloudflareOriginErrorsCountAsDead() {
        assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "源站连接超时（Cloudflare 522）"), LinkChecker.judge(522, "cloudflare"));
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(521, " Cloudflare ").verdict());
        assertEquals(LinkChecker.Verdict.DEAD, LinkChecker.judge(526, "cloudflare").verdict());
        // CSDN 的防爬 WAF 也回 521，不是 Cloudflare 就不算
        assertEquals(LinkChecker.Verdict.UNKNOWN, LinkChecker.judge(521, "WAF").verdict());
        // 520 是源站回了看不懂的东西，不一定挂了
        assertEquals(LinkChecker.Verdict.UNKNOWN, LinkChecker.judge(520, "cloudflare").verdict());
    }

    @Test
    void expiredOrMismatchedCertificateCountsAsDead() {
        SSLHandshakeException expired = new SSLHandshakeException("PKIX path validation failed");
        expired.initCause(new CertPathValidatorException("validity check failed", new CertificateExpiredException("NotAfter"),
                null, -1, CertPathValidatorException.BasicReason.EXPIRED));
        assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "证书已过期"), LinkChecker.judgeTls(expired));

        SSLPeerUnverifiedException mismatch = new SSLPeerUnverifiedException(
                "Certificate for <old.example.com> doesn't match any of the subject alternative names: [parking.example.net]");
        assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "证书与域名不符"), LinkChecker.judgeTls(mismatch));
        // JDK 握手时的主机名校验（HttpClient 5 默认交给 JDK 做）
        SSLHandshakeException jdkMismatch = new SSLHandshakeException("(certificate_unknown) No subject alternative DNS name matching wrong.host.badssl.com found.");
        jdkMismatch.initCause(new CertificateException("No subject alternative DNS name matching wrong.host.badssl.com found."));
        assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.DEAD, "证书与域名不符"), LinkChecker.judgeTls(jdkMismatch));

        // 证书链不全：可能只是服务器的根证书库比浏览器少
        SSLHandshakeException pkix = new SSLHandshakeException("PKIX path building failed");
        pkix.initCause(new CertPathValidatorException("unable to find valid certification path"));
        assertEquals(LinkChecker.Verdict.UNKNOWN, LinkChecker.judgeTls(pkix).verdict());
    }

    @Test
    void internalAndNonWebAddressesAreNotJudged() throws Exception {
        LinkChecker checker = new LinkChecker();
        try {
            List<LinkChecker.Outcome> outcomes = checker.checkAll(List.of(
                    "http://127.0.0.1:9/admin", "http://localhost/", "javascript:void(0)", "chrome://settings", "not a url", "http:///x"));
            assertEquals(6, outcomes.size());
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "内网地址，服务器不检查"), outcomes.get(0));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "内网地址，服务器不检查"), outcomes.get(1));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "不是网页地址"), outcomes.get(2));
            assertEquals(new LinkChecker.Outcome(LinkChecker.Verdict.UNKNOWN, "不是网页地址"), outcomes.get(3));
            assertEquals(LinkChecker.Verdict.UNKNOWN, outcomes.get(4).verdict());
            assertEquals(LinkChecker.Verdict.UNKNOWN, outcomes.get(5).verdict());
        } finally {
            checker.destroy();
        }
    }
}
