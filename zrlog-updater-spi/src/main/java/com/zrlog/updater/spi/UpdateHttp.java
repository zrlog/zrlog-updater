package com.zrlog.updater.spi;

import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Uses the application's existing transport and UA; no response-size policy belongs here. */
public interface UpdateHttp {
    String read(URI uri) throws Exception;
    int download(URI uri, File target) throws Exception;
    HttpResponse<String> send(HttpRequest request) throws Exception;
}
