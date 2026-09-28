package com.nebula.space.reading;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleFetcherTest {

    private static boolean isPublic(String ip) throws UnknownHostException {
        return ArticleFetcher.isPublic(InetAddress.getByName(ip));
    }

    @Test
    void internalAddressesAreNotPublic() throws UnknownHostException {
        for (String ip : new String[]{
                "127.0.0.1", "10.1.2.3", "172.16.5.4", "192.168.1.1", "169.254.169.254", "100.64.0.1",
                "0.0.0.0", "198.18.0.1", "192.0.0.8", "224.0.0.1", "255.255.255.255",
                "::1", "::", "fe80::1", "fd00::1", "::ffff:10.0.0.1",
                // 内嵌 IPv4：NAT64 指向云元数据、6to4 指向内网
                "64:ff9b::a9fe:a9fe", "2002:0a00:0001::"}) {
            assertFalse(isPublic(ip), ip);
        }
    }

    @Test
    void publicAddressesPass() throws UnknownHostException {
        for (String ip : new String[]{"8.8.8.8", "93.184.216.34", "2606:4700:4700::1111", "2002:5db8:d822::"}) {
            assertTrue(isPublic(ip), ip);
        }
    }

    @Test
    void resolverRefusesLoopback() {
        ArticleFetcher.PublicOnlyDnsResolver resolver = new ArticleFetcher.PublicOnlyDnsResolver();
        assertThrows(UnknownHostException.class, () -> resolver.resolve("127.0.0.1"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("localhost"));
    }

    @Test
    void fetchingInternalUrlGivesNothing() throws Exception {
        ArticleFetcher fetcher = new ArticleFetcher();
        try {
            assertNull(fetcher.fetch("http://127.0.0.1:9/admin"));
            assertNull(fetcher.fetch("http://localhost/"));
            assertNull(fetcher.fetch("not a url"));
        } finally {
            fetcher.destroy();
        }
    }
}
