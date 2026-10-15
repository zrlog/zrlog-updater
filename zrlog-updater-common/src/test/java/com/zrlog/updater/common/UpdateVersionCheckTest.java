package com.zrlog.updater.common;

import com.zrlog.common.updater.UpdateChannel;
import com.zrlog.common.updater.UpdateVersionCheckResult;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.updater.spi.UpdateHttp;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class UpdateVersionCheckTest {
    private final DefaultUpdateCatalog catalog = new DefaultUpdateCatalog();

    @Test public void releaseOnlyReadsItsOwnChannel() throws Exception {
        Metadata http = new Metadata(Map.of("release", json("4.0.0", "2026-10-14 10:00:00Z")));
        UpdateVersionCheckResult result = catalog.checkVersion(context(http), false, version -> true);
        assertEquals(UpdateChannel.RELEASE, result.getChannel());
        assertTrue(result.isUpgradeAvailable());
        assertEquals(List.of("release"), http.reads);
    }
    @Test public void newerPreviewDoesNotNeedAnOptionalRelease() throws Exception {
        Metadata http = new Metadata(Map.of("preview", json("4.0.0-SNAPSHOT", "2026-10-14 10:00:00Z")));
        UpdateVersionCheckResult result = catalog.checkVersion(context(http), true, version -> true);
        assertEquals(UpdateChannel.PREVIEW, result.getChannel());
        assertTrue(result.isUpgradeAvailable());
        assertEquals(List.of("preview"), http.reads);
    }
    @Test public void checkedPreviewSurvivesAnUnpublishedRelease() throws Exception {
        Metadata http = new Metadata(Map.of("preview", json("4.0.0-SNAPSHOT", "2026-10-14 10:00:00Z")));
        UpdateVersionCheckResult result = catalog.checkVersion(context(http), true, version -> false);
        assertEquals(UpdateChannel.PREVIEW, result.getChannel());
        assertFalse(result.isUpgradeAvailable());
        assertEquals(List.of("preview", "release"), http.reads);
    }
    @Test public void missingSelectedChannelFailsTheCheck() {
        for (boolean preview : new boolean[]{false, true}) {
            assertThrows(FileNotFoundException.class,
                    () -> catalog.checkVersion(context(new Metadata(Map.of())), preview, version -> false));
        }
    }
    @Test public void brokenOrUnreachableOptionalReleaseIsNotHidden() {
        for (Object release : new Object[]{new IOException("HTTP 403"), new IOException("HTTP 503"),
                new java.net.SocketTimeoutException("timeout"), "{broken"}) {
            Metadata http = new Metadata(Map.of("preview", json("4.0.0-SNAPSHOT", "2026-10-14 10:00:00Z"),
                    "release", release));
            assertThrows(Exception.class, () -> catalog.checkVersion(context(http), true, version -> false));
        }
    }
    @Test public void discoversANewerReleaseAfterAnAlreadyInstalledPreview() throws Exception {
        Metadata http = new Metadata(Map.of("preview", json("4.0.0-SNAPSHOT", "2026-10-14 10:00:00Z"),
                "release", json("4.0.1", "2026-10-14 11:00:00Z")));
        UpdateVersionCheckResult result = catalog.checkVersion(context(http), true,
                version -> version.getVersion().equals("4.0.1"));
        assertEquals(UpdateChannel.RELEASE, result.getChannel());
        assertTrue(result.isUpgradeAvailable());
    }
    @Test public void prefersTheMostRecentAlreadyInstalledChannel() throws Exception {
        for (boolean previewNewer : new boolean[]{false, true}) {
            Metadata http = new Metadata(Map.of("preview", json("4.0.0-SNAPSHOT",
                    previewNewer ? "2026-10-14 12:00:00Z" : "2026-10-14 10:00:00Z"),
                    "release", json("4.0.0", "2026-10-14 11:00:00Z")));
            UpdateVersionCheckResult result = catalog.checkVersion(context(http), true, version -> false);
            assertEquals(previewNewer ? UpdateChannel.PREVIEW : UpdateChannel.RELEASE, result.getChannel());
            assertFalse(result.isUpgradeAvailable());
        }
    }
    private static UpdateContext context(UpdateHttp http) {
        UpdateContext.Builder builder = new UpdateContext.Builder();
        builder.http = http; builder.metadataOrigin = "https://dl.example"; builder.manifestFilename = "update.json";
        return builder.build();
    }
    private static String json(String version, String date) {
        return "{\"version\":\"" + version + "\",\"buildId\":\"1234567\",\"releaseDate\":\"" + date + "\"}";
    }
    private static final class Metadata implements UpdateHttp {
        private final Map<String, ?> responses;
        private final List<String> reads = new ArrayList<>();
        Metadata(Map<String, ?> responses) { this.responses = responses; }
        public String read(URI uri) throws Exception {
            String channel = uri.getPath().split("/")[1]; reads.add(channel);
            Object response = responses.get(channel);
            if (response instanceof Exception) throw (Exception) response;
            if (response == null) throw new FileNotFoundException("HTTP 404");
            return (String) response;
        }
        public int download(URI uri, File file) { throw new AssertionError("Version check must not download a package"); }
        public HttpResponse<String> send(HttpRequest request) { throw new AssertionError("Unexpected request"); }
    }
}
