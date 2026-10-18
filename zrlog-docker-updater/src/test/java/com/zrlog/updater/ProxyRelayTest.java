package com.zrlog.updater;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProxyRelayTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String TOKEN = "test-token-" + "a".repeat(40);
    private final HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    @Test(timeout = 20000)
    public void authenticatedMetadataOperationsUseTheirForwardedProxyWithoutLeakingControlHeaders() throws Exception {
        Fixtures.FakeDeployment docker = new Fixtures.FakeDeployment();
        Properties properties = new Properties();
        properties.setProperty("mirrorWebSite", "https://metadata.external.test/");
        try (StateStore store = new StateStore(temporary.newFolder().toPath());
             UpgradeService service = new UpgradeService(store, Metadata.fromProperties(properties), docker);
             ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), TOKEN, service, Map.of())) {
            server.start();
            for (String operation : List.of("GET /v1/settings", "GET /v1/settings/preview", "PUT /v1/settings", "POST /v1/upgrades")) {
                try (RejectingProxy proxy = new RejectingProxy()) {
                    String[] route = operation.split(" ");
                    String body = "PUT".equals(route[0]) ? "{\"repository\":\"\",\"channel\":\"release\"}"
                            : "POST".equals(route[0]) ? Json.GSON.toJson(Fixtures.request("proxy-upgrade-001")) : null;
                    HttpRequest request = request(server, route[0], route[1], body)
                            .header("Authorization", "Bearer " + TOKEN).header("X-ZrLog-HTTPS-Proxy", proxy.url())
                            .header("X-ZrLog-No-Proxy", ".internal.example").build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    assertEquals(operation, 502, response.statusCode());
                    assertEquals("metadata_unavailable", Json.tree(response.body()).getAsJsonObject().get("code").getAsString());
                    String traffic = proxy.request.get(3, TimeUnit.SECONDS);
                    assertTrue(traffic, traffic.startsWith("CONNECT metadata.external.test:443 HTTP/1.1\n"));
                    assertFalse(traffic.contains(TOKEN));
                    assertFalse(traffic.toLowerCase().contains("x-zrlog-"));
                    assertFalse(response.body().contains(proxy.url()));
                }
            }
            assertEquals(0, docker.replacements);
            assertNull(docker.pulled);
            HttpResponse<String> unauthorized = client.send(request(server, "GET", "/v1/settings", null)
                    .header("X-ZrLog-HTTPS-Proxy", "http://user:secret@proxy").build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthorized.statusCode());
            HttpResponse<String> invalid = client.send(request(server, "GET", "/v1/settings", null)
                    .header("Authorization", "Bearer " + TOKEN).header("X-ZrLog-HTTPS-Proxy", "http://user:secret@proxy")
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertFalse(invalid.body().contains("secret"));
        }
    }

    @Test(timeout = 10000)
    public void updaterEnvironmentTakesPriorityOverTheApplicationProxy() throws Exception {
        try (RejectingProxy proxy = new RejectingProxy();
             StateStore store = new StateStore(temporary.newFolder().toPath());
             UpgradeService service = new UpgradeService(store, Metadata.fromProperties(new Properties()), new Fixtures.FakeDeployment());
             ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), TOKEN, service,
                     Map.of("HTTPS_PROXY", "http://unused.invalid:9", "https_proxy", proxy.url()))) {
            server.start();
            HttpResponse<String> response = client.send(request(server, "GET", "/v1/settings", null)
                    .header("Authorization", "Bearer " + TOKEN).header("X-ZrLog-HTTPS-Proxy", "http://also-unused.invalid:9")
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(502, response.statusCode());
            assertTrue(proxy.request.get(3, TimeUnit.SECONDS).startsWith("CONNECT dl.zrlog.com:443 HTTP/1.1\n"));
        }
    }

    private HttpRequest.Builder request(ApiServer server, String method, String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json").method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    }

    private static final class RejectingProxy implements AutoCloseable {
        private final ServerSocket server;
        final CompletableFuture<String> request;

        RejectingProxy() throws Exception {
            server = new ServerSocket();
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(4000);
            request = CompletableFuture.supplyAsync(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    StringBuilder received = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) received.append(line).append('\n');
                    socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    return received.toString();
                } catch (Exception error) { throw new RuntimeException(error); }
            });
        }

        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); }
    }
}
