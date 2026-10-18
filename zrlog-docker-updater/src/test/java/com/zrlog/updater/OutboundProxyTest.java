package com.zrlog.updater;

import com.sun.net.httpserver.Headers;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Map;

import static org.junit.Assert.*;

public class OutboundProxyTest {
    @Test public void updaterEnvironmentOverridesForwardedValuesPerFieldIncludingExplicitDisable() {
        Headers headers = headers("http://forwarded.example:3128", "http://forwarded.example:3128", ".forwarded.example");
        OutboundProxy proxy = OutboundProxy.from(headers, Map.of("HTTP_PROXY", "http://upper.example:80",
                "http_proxy", "http://lower.example:8080", "HTTPS_PROXY", "", "NO_PROXY", ".upper.example",
                "no_proxy", ".local.example"));
        assertEquals("http://lower.example:8080", proxy.http());
        assertEquals("", proxy.https());
        assertEquals(".local.example", proxy.noProxy());
        assertEquals("lower.example", ((InetSocketAddress) proxy.selector().select(URI.create("http://outside.test/")).getFirst().address()).getHostString());
        assertEquals(Proxy.Type.DIRECT, proxy.selector().select(URI.create("https://outside.test/")).getFirst().type());
        assertEquals(Proxy.Type.DIRECT, proxy.selector().select(URI.create("http://db.local.example/")).getFirst().type());
        assertEquals(Proxy.Type.HTTP, proxy.selector().select(URI.create("http://db.forwarded.example/")).getFirst().type());
    }

    @Test public void forwardedPolicyKeepsItsBypassRulesWithoutChangingGlobalRouting() {
        ProxySelector previous = ProxySelector.getDefault();
        OutboundProxy proxy = OutboundProxy.from(headers("http://plain.example:3128", "http://secure.example:8080",
                ".internal.example,*.private,10.20.30.40,::1,2001:db8::2"), Map.of());
        for (String host : new String[]{"localhost", "127.9.1.2", "internal.example", "db.internal.example", "db.private",
                "10.20.30.40", "[::1]", "[2001:db8::2]"}) {
            assertEquals(host, Proxy.Type.DIRECT, proxy.selector().select(URI.create("https://" + host + "/")).getFirst().type());
        }
        assertEquals("plain.example", ((InetSocketAddress) proxy.selector().select(URI.create("http://external.test/")).getFirst().address()).getHostString());
        assertEquals("secure.example", ((InetSocketAddress) proxy.selector().select(URI.create("https://external.test/")).getFirst().address()).getHostString());
        assertSame(previous, ProxySelector.getDefault());
        assertEquals(Proxy.Type.DIRECT, OutboundProxy.from(headers("http://proxy:3128", "http://proxy:3128", "*"), Map.of())
                .selector().select(URI.create("https://external.test/")).getFirst().type());
    }

    @Test public void absentConfigurationRetainsJvmPolicyWhileEmptyValuesDisableIt() {
        ProxySelector previous = ProxySelector.getDefault();
        try {
            ProxySelector inherited = ProxySelector.of(new InetSocketAddress("inherited.example", 8080));
            ProxySelector.setDefault(inherited);
            URI uri = URI.create("https://external.test/");
            assertEquals(inherited.select(uri), OutboundProxy.from(new Headers(), Map.of()).selector().select(uri));
            assertEquals(Proxy.Type.DIRECT, OutboundProxy.from(headers("", "", ""), Map.of()).selector().select(uri).getFirst().type());
        } finally { ProxySelector.setDefault(previous); }
    }

    @Test public void invalidProxyAndBypassValuesAreRejectedWithoutEchoingConfiguration() {
        for (String invalid : new String[]{"http://user:secret@proxy", "https://proxy", "http://proxy/path",
                "http://proxy?secret=value", "http://proxy#secret", "http://proxy:0", "http://proxy:65536", "bad value"}) {
            ApiException error = assertThrows(ApiException.class, () -> OutboundProxy.from(headers(invalid, "", ""), Map.of()));
            assertEquals(400, error.status);
            assertEquals("Invalid outbound proxy configuration", error.getMessage());
        }
        for (String invalid : new String[]{"10.0.0.0/8", "updater:8081", ".", "a..b", "[not:ipv6]"}) {
            assertThrows(ApiException.class, () -> OutboundProxy.from(headers("", "", invalid), Map.of()));
        }
    }

    private static Headers headers(String http, String https, String noProxy) {
        Headers headers = new Headers();
        headers.set("X-ZrLog-HTTP-Proxy", http);
        headers.set("X-ZrLog-HTTPS-Proxy", https);
        headers.set("X-ZrLog-No-Proxy", noProxy);
        return headers;
    }
}
