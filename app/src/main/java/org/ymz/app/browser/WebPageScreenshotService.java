package org.ymz.app.browser;

import cn.hutool.core.img.ImgUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 通用网页截图服务。
 * 当前固定使用 ChromeDriver 操作本机 Chrome 浏览器；如果运行环境未安装 Chrome，本服务可能无法正常工作。
 * 
 * @author ymz
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPageScreenshotService {

    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 30;
    private static final long SETTLE_DELAY_MILLIS = 2000;
    private static final float JPEG_QUALITY = 0.8f;

    private final BrowserWebDriverManager browserWebDriverManager;

    public byte[] captureJpeg(String url) throws Exception {
        Path rawScreenshotPath = null;
        Path compressedScreenshotPath = null;
        try {
            // 复用单例浏览器打开目标页面，并限制页面加载最长等待时间。
            WebDriver driver = browserWebDriverManager.getDriver();
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));

            // 先清理 Cookie，再访问页面，避免登录态或历史状态影响截图。
            driver.manage().deleteAllCookies();
            if (driver instanceof ChromiumDriver chromiumDriver) {
                // 固定 prefers-color-scheme，避免系统深色模式影响封面截图背景色。
                chromiumDriver.executeCdpCommand("Emulation.setEmulatedMedia", Map.of(
                        "features", List.of(Map.of(
                                "name", "prefers-color-scheme",
                                "value", "light"))));
            }
            driver.get(url);
            // 清理浏览器本地状态，避免截图结果受上一次页面访问影响。
            ((JavascriptExecutor) driver).executeScript("""
                    window.localStorage.clear();
                    window.sessionStorage.clear();
                    """);
            // localStorage/sessionStorage 只能在页面域名下清理，清理后刷新一次让页面以干净状态重新渲染。
            driver.navigate().refresh();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
            // 等待文档加载完成，再执行滚动和样式调整。
            wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")));
            // 固定截图起点并隐藏滚动条，让同一页面多次截图的视觉结果更稳定。
            ((JavascriptExecutor) driver).executeScript("""
                    window.scrollTo(0, 0);
                    document.documentElement.style.overflow = 'hidden';
                    document.body.style.overflow = 'hidden';
                    """);
            try {
                // 给前端动画、字体和图片一点收尾时间，避免刚 ready 就截图导致内容未完全稳定。
                Thread.sleep(SETTLE_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("网页截图等待被中断", e);
            }

            // Selenium 原始截图是 PNG 字节，先落到临时文件后再压缩为 JPEG。
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            rawScreenshotPath = Files.createTempFile("webpage-screenshot-", ".png");
            compressedScreenshotPath = Files.createTempFile("webpage-screenshot-", ".jpg");
            Files.write(rawScreenshotPath, screenshotBytes);
            ImgUtil.compress(rawScreenshotPath.toFile(), compressedScreenshotPath.toFile(), JPEG_QUALITY);
            return Files.readAllBytes(compressedScreenshotPath);
        } catch (Exception e) {
            // 截图失败后关闭当前浏览器实例，下次截图时重新创建，避免复用异常状态。
            browserWebDriverManager.close();
            throw e;
        } finally {
            // 无论截图是否成功，都清理临时文件，避免长期运行时堆积。
            try {
                if (rawScreenshotPath != null) {
                    Files.deleteIfExists(rawScreenshotPath);
                }
                if (compressedScreenshotPath != null) {
                    Files.deleteIfExists(compressedScreenshotPath);
                }
            } catch (Exception e) {
                log.warn("删除网页截图临时文件失败", e);
            }
        }
    }
}
