package com.zrlog.updater.docker;
import com.zrlog.updater.spi.UpdateHttp;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
public class TestHttp implements UpdateHttp {
    private final HttpClient client = HttpClient.newHttpClient();
    public HttpResponse<String> send(HttpRequest request) throws Exception { return client.send(request, HttpResponse.BodyHandlers.ofString()); }
    public String read(URI uri) throws Exception { return send(HttpRequest.newBuilder(uri).build()).body(); }
    public int download(URI uri, File file) throws Exception {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(file.toPath(), response.body()); return response.statusCode();
    }
}
