package com.zrlog.updater.docker;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.docker.DockerUpdaterProtocol;
import com.zrlog.updater.common.DefaultUpdateCatalog;
import com.zrlog.updater.common.HostedUpdater;
import com.zrlog.updater.spi.DockerConnection;
import com.zrlog.updater.spi.DockerControl;
import com.zrlog.updater.spi.UpdateContext;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Backend-only protocol client; credentials and address are supplied by the deployment. */
public final class DockerImageUpdater extends HostedUpdater implements DockerControl {
    private static final Gson GSON = new Gson();
    private final URI base;
    private final DockerConnection connection;
    private final DefaultUpdateCatalog catalog = new DefaultUpdateCatalog();
    public DockerImageUpdater(UpdateContext context) {
        super(context, UpdaterTypeEnum.DOCKER);
        connection = Objects.requireNonNull(context.docker, "Docker updater is not configured");
        if (connection.url == null || connection.token == null || !connection.token.matches("[A-Za-z0-9_-]{32,256}"))
            throw new IllegalStateException("Docker updater is not configured");
        base = URI.create(connection.url.endsWith("/") ? connection.url : connection.url + "/");
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null || !"/".equals(base.getPath()))
            throw new IllegalArgumentException("Invalid ZRLOG_UPDATER_URL");
        catalog.metadataUri(connection.metadataOrigin, connection.manifestFilename, "release");
        Objects.requireNonNull(context.http, "Missing host HTTP capability");
    }
    public DockerUpdaterProtocol.Capabilities capabilities() {
        DockerUpdaterProtocol.Capabilities value = call("GET", "v1/capabilities", null, DockerUpdaterProtocol.Capabilities.class);
        if (value.protocolVersion != 1 || !value.ready || !Objects.equals(
                catalog.metadataUri(connection.metadataOrigin, connection.manifestFilename, "release"),
                catalog.metadataUri(value.metadataBaseUrl, value.manifestFilename, "release")))
            throw new RequestException(0, "updater_configuration_mismatch");
        return value;
    }
    public DockerUpdaterProtocol.SettingsResponse settings(String channel) {
        channel(channel); capabilities();
        return call("GET", "v1/settings" + ("preview".equals(channel) ? "/preview" : ""), null, DockerUpdaterProtocol.SettingsResponse.class);
    }
    public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
        channel(channel); capabilities();
        DockerUpdaterProtocol.Settings settings = new DockerUpdaterProtocol.Settings();
        settings.repository = Objects.requireNonNull(repository); settings.channel = channel;
        return call("PUT", "v1/settings", settings, DockerUpdaterProtocol.Settings.class);
    }
    public DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request) {
        DockerControl.requestId(request.requestId); channel(request.channel); capabilities();
        return call("POST", "v1/upgrades", request, DockerUpdaterProtocol.Job.class);
    }
    public DockerUpdaterProtocol.Job status(String requestId) {
        DockerControl.requestId(requestId);
        return call("GET", "v1/upgrades/" + requestId, null, DockerUpdaterProtocol.Job.class);
    }
    private static void channel(String channel) {
        if (!"release".equals(channel) && !"preview".equals(channel)) throw new IllegalArgumentException("Invalid update channel");
    }
    private <T> T call(String method, String path, Object body, Class<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + connection.token).header("Accept", "application/json");
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
            HttpResponse<String> response = context.http.send(builder.build());
            if (response.statusCode() != 200 && response.statusCode() != 202) throw readError(response);
            T value = GSON.fromJson(response.body(), type);
            if (value == null) throw new RequestException(0, "updater_invalid_response");
            return value;
        } catch (RequestException e) { throw e; }
        catch (JsonParseException e) { throw new RequestException(0, "updater_invalid_response"); }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RequestException(0, "updater_unavailable");
        }
    }
    private RequestException readError(HttpResponse<String> response) {
        try {
            DockerUpdaterProtocol.ErrorResponse error = GSON.fromJson(response.body(), DockerUpdaterProtocol.ErrorResponse.class);
            if (error != null && ("metadata_unavailable".equals(error.code) || "metadata_not_found".equals(error.code)))
                return new RequestException(response.statusCode(), error.code);
        } catch (JsonParseException ignored) { }
        int status = response.statusCode();
        return new RequestException(status, status == 401 || status == 403 ? "updater_unauthorized" : "updater_request_failed");
    }
}
