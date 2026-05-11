package org.ymz.app.browser;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * 管理网页自动化使用的单例浏览器实例。
 *
 * @author ymz
 */
@Slf4j
@Component
public class BrowserWebDriverManager {

    private static final int VIEWPORT_WIDTH = 1440;
    private static final int VIEWPORT_HEIGHT = 900;

    private WebDriver webDriver;

    public synchronized WebDriver getDriver() {
        if (webDriver == null) {
            // 首次截图时初始化浏览器，后续截图复用同一个实例，减少重复启动开销。
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            // 无头模式适合后端服务运行，不需要真实桌面窗口。
            options.addArguments("--headless=new");
            // Docker 或部分 Linux 环境中常需要关闭沙箱，否则 Chrome 可能无法启动。
            options.addArguments("--no-sandbox");
            // 避免 Chrome 使用空间较小的 /dev/shm，减少页面加载或截图时崩溃。
            options.addArguments("--disable-dev-shm-usage");
            // 无头截图不依赖 GPU 加速，关闭后减少运行环境要求。
            options.addArguments("--disable-gpu");
            // 隐藏滚动条并固定视口，保证封面截图尺寸和视觉结果稳定。
            options.addArguments("--hide-scrollbars");
            options.addArguments("--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT);
            webDriver = new ChromeDriver(options);
        }
        return webDriver;
    }

    @PreDestroy
    public synchronized void close() {
        if (webDriver == null) {
            return;
        }
        try {
            webDriver.quit();
        } catch (Exception e) {
            log.warn("关闭浏览器实例失败", e);
        } finally {
            webDriver = null;
        }
    }
}
