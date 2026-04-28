package org.ymz.app.service.impl;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.ymz.app.config.AppDeploymentProperties;
import org.ymz.app.model.entity.App;
import org.ymz.app.oss.BucketType;
import org.ymz.app.oss.RustFSClient;
import org.ymz.app.service.AppCoverCaptureService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.deployment.AppCoverWebDriverManager;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static org.ymz.app.model.entity.table.AppTableDef.APP;

/**
 * 部署成功后异步生成应用封面图。
 *
 * @author ymz
 */
@Slf4j
@Service
public class AppCoverCaptureServiceImpl implements AppCoverCaptureService {

    private static final String COVER_CONTENT_TYPE = "image/jpeg";

    private final AppService appService;
    private final RustFSClient rustFSClient;
    private final AppDeploymentProperties appDeploymentProperties;
    private final AppCoverWebDriverManager appCoverWebDriverManager;
    private final Executor appCoverExecutor;
    private final ReentrantLock captureLock = new ReentrantLock();

    public AppCoverCaptureServiceImpl(
            AppService appService,
            RustFSClient rustFSClient,
            AppDeploymentProperties appDeploymentProperties,
            AppCoverWebDriverManager appCoverWebDriverManager,
            @Qualifier("appCoverExecutor") Executor appCoverExecutor
    ) {
        this.appService = appService;
        this.rustFSClient = rustFSClient;
        this.appDeploymentProperties = appDeploymentProperties;
        this.appCoverWebDriverManager = appCoverWebDriverManager;
        this.appCoverExecutor = appCoverExecutor;
    }

    @Override
    public void captureCoverAsync(Long appId, String deployUrl, LocalDateTime deployedAt) {
        if (!appDeploymentProperties.getCover().isEnabled()) {
            return;
        }
        appCoverExecutor.execute(() -> {
            captureLock.lock();
            try {
                captureCover(appId, deployUrl, deployedAt);
            } finally {
                captureLock.unlock();
            }
        });
    }

    void captureCover(Long appId, String deployUrl, LocalDateTime deployedAt) {
        if (!isSameDeployment(appId, deployUrl, deployedAt)) {
            log.info("跳过应用封面生成，部署快照已过期: appId={}", appId);
            return;
        }

        String coverKey = null;
        try {
            AppDeploymentProperties.Cover cover = appDeploymentProperties.getCover();
            byte[] coverBytes = captureCoverBytes(deployUrl, cover);
            coverKey = buildCoverKey(appId, cover.getOssPrefix());
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(coverBytes)) {
                rustFSClient.uploadObject(BucketType.PUBLIC, inputStream, coverKey, COVER_CONTENT_TYPE, coverBytes.length);
            }
            String coverUrl = rustFSClient.getPublicObjectUrl(coverKey);
            updateCoverUrl(appId, deployUrl, deployedAt, coverUrl, coverKey);
        } catch (Exception e) {
            appCoverWebDriverManager.discardDriver();
            log.warn("应用封面生成失败: appId={}, deployUrl={}", appId, deployUrl, e);
        }
    }

    private byte[] captureCoverBytes(String deployUrl, AppDeploymentProperties.Cover cover) throws Exception {
        Path rawScreenshotPath = null;
        Path compressedCoverPath = null;
        try {
            WebDriver driver = appCoverWebDriverManager.getDriver();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(cover.getPageLoadTimeoutSeconds()));
            loadCleanPage(driver, deployUrl, cover);

            if (!(driver instanceof TakesScreenshot takesScreenshot)) {
                throw new IllegalStateException("当前浏览器不支持截图");
            }
            byte[] screenshotBytes = takesScreenshot.getScreenshotAs(OutputType.BYTES);
            rawScreenshotPath = Files.createTempFile("app-cover-", ".png");
            compressedCoverPath = Files.createTempFile("app-cover-", ".jpg");
            Files.write(rawScreenshotPath, screenshotBytes);
            ImgUtil.compress(rawScreenshotPath.toFile(), compressedCoverPath.toFile(), cover.getQuality());
            return Files.readAllBytes(compressedCoverPath);
        } finally {
            deleteTempFile(rawScreenshotPath);
            deleteTempFile(compressedCoverPath);
        }
    }

    private void loadCleanPage(WebDriver driver, String deployUrl, AppDeploymentProperties.Cover cover) {
        driver.manage().deleteAllCookies();
        driver.get(deployUrl);
        clearBrowserStorage(driver);
        driver.navigate().refresh();
        waitPageReady(driver, cover);
        ((JavascriptExecutor) driver).executeScript("""
                window.scrollTo(0, 0);
                document.documentElement.style.overflow = 'hidden';
                document.body.style.overflow = 'hidden';
                """);
        sleepForRendering(cover.getSettleDelayMillis());
    }

    private void clearBrowserStorage(WebDriver driver) {
        ((JavascriptExecutor) driver).executeScript("""
                window.localStorage.clear();
                window.sessionStorage.clear();
                """);
    }

    private void waitPageReady(WebDriver driver, AppDeploymentProperties.Cover cover) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(cover.getPageLoadTimeoutSeconds()));
        wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState")));
    }

    private void sleepForRendering(long settleDelayMillis) {
        if (settleDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(settleDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("应用封面截图等待被中断", e);
        }
    }

    private boolean isSameDeployment(Long appId, String deployUrl, LocalDateTime deployedAt) {
        App app = appService.getById(appId);
        return app != null
                && StrUtil.equals(app.getDeployUrl(), deployUrl)
                && deployedAt != null
                && deployedAt.equals(app.getDeployedAt());
    }

    private void updateCoverUrl(Long appId, String deployUrl, LocalDateTime deployedAt, String coverUrl, String coverKey) {
        boolean updated = appService.updateChain()
                .set(APP.COVER_URL, coverUrl)
                .where(APP.ID.eq(appId))
                .and(APP.DEPLOY_URL.eq(deployUrl))
                .and(APP.DEPLOYED_AT.eq(deployedAt))
                .update();
        if (!updated) {
            try {
                rustFSClient.deleteObject(BucketType.PUBLIC, coverKey);
            } catch (Exception e) {
                log.warn("部署快照过期后删除封面文件失败: key={}", coverKey, e);
            }
            log.info("跳过应用封面写回，部署快照已过期: appId={}", appId);
        }
    }

    private String buildCoverKey(Long appId, String ossPrefix) {
        String prefix = StrUtil.blankToDefault(ossPrefix, "app-covers/");
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + appId + "/" + IdUtil.fastSimpleUUID() + ".jpg";
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除封面截图临时文件失败: path={}", path, e);
        }
    }
}
