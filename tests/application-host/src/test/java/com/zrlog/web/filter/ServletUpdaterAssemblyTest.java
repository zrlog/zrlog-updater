package com.zrlog.web.filter;

import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;

/** Host assembly checks are maintained by updater and run against an application's test classpath. */
public class ServletUpdaterAssemblyTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldResolveRootWarFileFromServletContext() throws Exception {
        File webapps = temporaryFolder.newFolder("webapps");
        ZrLogSwsServletFilter filter = filter("/", new File(webapps, "ROOT"));
        assertEquals(new File(webapps, "ROOT.war").getAbsolutePath(), filter.getWarFile());
    }

    @Test
    public void shouldResolveContextWarFileFromServletContext() throws Exception {
        File webapps = temporaryFolder.newFolder("webapps");
        ZrLogSwsServletFilter filter = filter("/blog", new File(webapps, "blog"));
        assertEquals(new File(webapps, "blog.war").getAbsolutePath(), filter.getWarFile());
    }

    @Test
    public void shouldUseWarUpdaterOutsideDevMode() throws Exception {
        File webapps = temporaryFolder.newFolder("webapps");
        ZrLogSwsServletFilter filter = filter("/blog", new File(webapps, "blog"));
        String previous = System.getProperty("sws.run.mode");
        try {
            System.clearProperty("sws.run.mode");
            Updater updater = filter.getUpdater();
            assertNotNull(updater);
            assertEquals(UpdaterTypeEnum.WAR, updater.getType());
            assertEquals(new File(webapps, "blog.war"), updater.execFile());
        } finally {
            restoreProperty("sws.run.mode", previous);
        }
    }

    @Test
    public void shouldUseZipUpdaterForPackagedDevLaunch() throws Exception {
        File webapps = temporaryFolder.newFolder("webapps");
        ZrLogSwsServletFilter filter = filter("/blog", new File(webapps, "blog"));
        String previousRunMode = System.getProperty("sws.run.mode");
        String previousClassPath = System.getProperty("java.class.path");
        File launcher = temporaryFolder.newFile("zrlog-starter.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.zrlog.web.Application");
        try (JarOutputStream ignored = new JarOutputStream(new FileOutputStream(launcher), manifest)) { }
        try {
            System.setProperty("java.class.path", launcher.getAbsolutePath());
            System.setProperty("sws.run.mode", "dev");
            Updater updater = filter.getUpdater();
            assertNotNull(updater);
            assertEquals(UpdaterTypeEnum.ZIP, updater.getType());
            assertEquals(launcher.getCanonicalFile(), updater.execFile());
        } finally {
            restoreProperty("java.class.path", previousClassPath);
            restoreProperty("sws.run.mode", previousRunMode);
        }
    }

    private ZrLogSwsServletFilter filter(String contextPath, File root) throws Exception {
        ServletContext servletContext = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{ServletContext.class}, (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) return contextPath;
                    if ("getRealPath".equals(method.getName())) return root.getAbsolutePath();
                    if ("toString".equals(method.getName())) return "ServletContextProxy";
                    return null;
                });
        FilterConfig config = (FilterConfig) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{FilterConfig.class}, (proxy, method, args) -> {
                    if ("getServletContext".equals(method.getName())) return servletContext;
                    if ("getFilterName".equals(method.getName())) return "zrlog";
                    if ("toString".equals(method.getName())) return "FilterConfigProxy";
                    return null;
                });
        ZrLogSwsServletFilter filter = new ZrLogSwsServletFilter();
        Field field = GenericFilter.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(filter, config);
        return filter;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
