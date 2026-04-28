package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.update.UpdateChain;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.model.entity.App;
import org.ymz.app.oss.BucketType;
import org.ymz.app.oss.RustFSClient;
import org.ymz.app.service.AppService;
import org.ymz.app.service.deployment.AppCoverWebDriverManager;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.ymz.app.model.entity.table.AppTableDef.APP;

class AppCoverCaptureServiceImplTest {

    @Test
    void captureCoverUploadsCompressedJpegAndUpdatesCoverUrl() throws Exception {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/demo/", deployedAt);
        AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
        doAnswer(invocation -> {
            InputStream inputStream = invocation.getArgument(1);
            uploadedBytes.set(inputStream.readAllBytes());
            return null;
        }).when(fixture.rustFSClient).uploadObject(
                eq(BucketType.PUBLIC),
                any(InputStream.class),
                anyString(),
                eq("image/jpeg"),
                anyLong()
        );

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.rustFSClient).uploadObject(
                eq(BucketType.PUBLIC),
                any(InputStream.class),
                keyCaptor.capture(),
                eq("image/jpeg"),
                anyLong()
        );
        String coverKey = keyCaptor.getValue();
        assertTrue(coverKey.startsWith("app-covers/1/"));
        assertTrue(coverKey.endsWith(".jpg"));
        assertTrue(uploadedBytes.get().length > 0);
        assertEquals((byte) 0xFF, uploadedBytes.get()[0]);
        assertEquals((byte) 0xD8, uploadedBytes.get()[1]);

        String coverUrl = "http://localhost:9000/zhida-public/" + coverKey;
        verify(fixture.updateChain).set(eq(APP.COVER_URL), eq(coverUrl));
        verify(fixture.updateChain).update();
        verify(fixture.driver, never()).quit();
        verify(fixture.webDriverManager, never()).discardDriver();
    }

    @Test
    void captureCoverReusesManagedDriverAcrossTasks() {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/demo/", deployedAt);

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);
        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);

        verify(fixture.webDriverManager, times(2)).getDriver();
        verify(fixture.driver, never()).quit();
        verify(fixture.webDriverManager, never()).discardDriver();
        verify(fixture.rustFSClient, times(2)).uploadObject(
                eq(BucketType.PUBLIC),
                any(InputStream.class),
                anyString(),
                eq("image/jpeg"),
                anyLong()
        );
    }

    @Test
    void captureCoverHidesScrollbarsBeforeScreenshot() {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/demo/", deployedAt);

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);

        verify((JavascriptExecutor) fixture.driver).executeScript("""
                window.scrollTo(0, 0);
                document.documentElement.style.overflow = 'hidden';
                document.body.style.overflow = 'hidden';
                """);
    }

    @Test
    void captureCoverSkipsExpiredDeploymentSnapshot() {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/current/", deployedAt.plusSeconds(1));

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/old/", deployedAt);

        verify(fixture.webDriverManager, never()).getDriver();
        verify(fixture.rustFSClient, never()).uploadObject(
                any(BucketType.class),
                any(InputStream.class),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(fixture.appService, never()).updateChain();
    }

    @Test
    void captureCoverFailureDoesNotUpdateCoverUrl() throws Exception {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/demo/", deployedAt);
        doThrow(new RuntimeException("upload failed")).when(fixture.rustFSClient).uploadObject(
                any(BucketType.class),
                any(InputStream.class),
                anyString(),
                anyString(),
                anyLong()
        );

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);

        verify(fixture.updateChain, never()).update();
        verify(fixture.driver, never()).quit();
        verify(fixture.webDriverManager).discardDriver();
    }

    @Test
    void captureCoverDeletesUploadedObjectWhenConditionalUpdateFails() {
        LocalDateTime deployedAt = LocalDateTime.of(2026, 4, 27, 20, 30);
        Fixture fixture = fixture("http://localhost/apps/demo/", deployedAt);
        when(fixture.updateChain.update()).thenReturn(false);

        fixture.service.captureCoverAsync(1L, "http://localhost/apps/demo/", deployedAt);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.rustFSClient).deleteObject(eq(BucketType.PUBLIC), keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith("app-covers/1/"));
    }

    private Fixture fixture(String currentDeployUrl, LocalDateTime currentDeployedAt) {
        AppService appService = mock(AppService.class);
        RustFSClient rustFSClient = mock(RustFSClient.class);
        AppCoverWebDriverManager webDriverManager = mock(AppCoverWebDriverManager.class);
        @SuppressWarnings("unchecked")
        UpdateChain<App> updateChain = mock(UpdateChain.class, RETURNS_SELF);
        WebDriver driver = mock(WebDriver.class, withSettings()
                .extraInterfaces(TakesScreenshot.class, JavascriptExecutor.class));
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Timeouts timeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);

        AppDeploymentProperties properties = new AppDeploymentProperties();
        properties.getCover().setSettleDelayMillis(0);
        properties.getCover().setPageLoadTimeoutSeconds(1);

        when(appService.getById(1L)).thenReturn(App.builder()
                .id(1L)
                .deployUrl(currentDeployUrl)
                .deployedAt(currentDeployedAt)
                .build());
        when(appService.updateChain()).thenReturn(updateChain);
        when(updateChain.set(any(QueryColumn.class), any())).thenReturn(updateChain);
        when(updateChain.update()).thenReturn(true);
        when(webDriverManager.getDriver()).thenReturn(driver);
        when(driver.manage()).thenReturn(options);
        when(options.timeouts()).thenReturn(timeouts);
        when(timeouts.pageLoadTimeout(any(Duration.class))).thenReturn(timeouts);
        when(driver.navigate()).thenReturn(navigation);
        when(((JavascriptExecutor) driver).executeScript("return document.readyState")).thenReturn("complete");
        when(((JavascriptExecutor) driver).executeScript(anyString())).thenReturn("complete");
        when(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)).thenReturn(testPngBytes());
        when(rustFSClient.getPublicObjectUrl(anyString()))
                .thenAnswer(invocation -> "http://localhost:9000/zhida-public/" + invocation.getArgument(0));

        AppCoverCaptureServiceImpl service = new AppCoverCaptureServiceImpl(
                appService,
                rustFSClient,
                properties,
                webDriverManager,
                Runnable::run
        );
        return new Fixture(service, appService, rustFSClient, webDriverManager, updateChain, driver);
    }

    private byte[] testPngBytes() {
        try {
            BufferedImage image = new BufferedImage(40, 24, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLUE);
            graphics.fillRect(4, 4, 20, 12);
            graphics.dispose();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(
            AppCoverCaptureServiceImpl service,
            AppService appService,
            RustFSClient rustFSClient,
            AppCoverWebDriverManager webDriverManager,
            UpdateChain<App> updateChain,
            WebDriver driver
    ) {
    }
}
