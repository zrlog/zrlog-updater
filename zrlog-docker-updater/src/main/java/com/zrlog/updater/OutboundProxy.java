package com.zrlog.updater;

import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Request-scoped outbound policy; it is never stored in updater state or applied to the Docker engine. */
record OutboundProxy(String http, String https, String noProxy) {
    static OutboundProxy from(Headers headers, Map<String, String> environment) {
        String http = value(environment, "HTTP_PROXY", headers.getFirst("X-ZrLog-HTTP-Proxy"));
        String https = value(environment, "HTTPS_PROXY", headers.getFirst("X-ZrLog-HTTPS-Proxy"));
        String noProxy = value(environment, "NO_PROXY", headers.getFirst("X-ZrLog-No-Proxy"));
        try {
            endpoint(http);
            endpoint(https);
            bypass(noProxy);
        } catch (IllegalArgumentException error) {
            throw ApiException.invalid("Invalid outbound proxy configuration");
        }
        return new OutboundProxy(http, https, noProxy);
    }

    private static String value(Map<String, String> environment, String name, String forwarded) {
        String lower = name.toLowerCase(Locale.ROOT);
        String value = environment.containsKey(lower) ? environment.get(lower)
                : environment.containsKey(name) ? environment.get(name) : forwarded;
        return value == null ? null : value.trim();
    }

    private static Proxy endpoint(String value) {
        if (value == null || value.isEmpty()) return Proxy.NO_PROXY;
        URI uri = URI.create(value);
        if (value.length() > 2048 || !"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1) throw new IllegalArgumentException();
        String host = uri.getHost();
        if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, uri.getPort() == -1 ? 80 : uri.getPort()));
    }

    private static List<String> bypass(String value) {
        List<String> hosts = new ArrayList<>(List.of("localhost", "127.*", "[::1]", "[::ffff:127.*"));
        if (value == null) return hosts;
        if (value.length() > 4096) throw new IllegalArgumentException();
        for (String item : value.split("[,|]")) {
            String token = item.trim();
            if (token.isEmpty()) continue;
            if (!token.matches("[A-Za-z0-9.*_:\\[\\]-]+") || token.contains("..") || token.equals("."))
                throw new IllegalArgumentException();
            if (token.contains(":")) {
                if (!(token.startsWith("[") && token.endsWith("]"))) token = "[" + token + "]";
                if (URI.create("http://" + token + "/").getHost() == null) throw new IllegalArgumentException();
            }
            if (token.startsWith(".")) {
                hosts.add(token.substring(1));
                token = "*" + token;
            }
            hosts.add(token);
        }
        return hosts;
    }

    ProxySelector selector() {
        Proxy plain = endpoint(http);
        Proxy secure = endpoint(https);
        List<String> hosts = bypass(noProxy);
        ProxySelector inherited = ProxySelector.getDefault();
        return new ProxySelector() {
            @Override public List<Proxy> select(URI uri) {
                if (uri == null) throw new IllegalArgumentException("URI is required");
                boolean tls = "https".equalsIgnoreCase(uri.getScheme());
                if (!(tls || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                    return List.of(Proxy.NO_PROXY);
                for (String pattern : hosts) {
                    if (matches(uri.getHost(), pattern)) return List.of(Proxy.NO_PROXY);
                }
                if ((tls ? https : http) == null && inherited != null) return inherited.select(uri);
                return List.of(tls ? secure : plain);
            }
            @Override public void connectFailed(URI uri, SocketAddress address, IOException error) {
                // A failed proxy must not cause a direct retry.
            }
        };
    }

    private static boolean matches(String destination, String pattern) {
        String host = destination.toLowerCase(Locale.ROOT);
        String token = pattern.toLowerCase(Locale.ROOT);
        if (token.equals("*")) return true;
        boolean prefix = token.startsWith("*");
        boolean suffix = token.endsWith("*");
        String literal = token.substring(prefix ? 1 : 0, token.length() - (suffix ? 1 : 0));
        if (prefix && suffix) return host.contains(literal);
        if (prefix) return host.endsWith(literal);
        if (suffix) return host.startsWith(literal);
        return host.equals(literal);
    }
}
