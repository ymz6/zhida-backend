package org.ymz.app.deployment;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppDevConfig;

/**
 * 管理应用封面截图使用的单例浏览器实例。
 *
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppCoverWebDriverManager {

    private final AppDevConfig appDevConfig;

    private WebDriver webDriver;

    public synchronized WebDriver getDriver() {
        if (webDriver == null) {
            webDriver = createWebDriver();
        }
        return webDriver;
    }

    public synchronized void discardDriver() {
        quitDriver();
        webDriver = null;
    }

    @PreDestroy
    public synchronized void close() {
        discardDriver();
    }

    WebDriver createWebDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--hide-scrollbars");
        options.addArguments("--window-size=" + appDevConfig.getCoverViewportWidth() + "," + appDevConfig.getCoverViewportHeight());
        return new ChromeDriver(options);
    }

    private void quitDriver() {
        if (webDriver == null) {
            return;
        }
        try {
            webDriver.quit();
        } catch (Exception e) {
            log.warn("关闭封面截图浏览器失败", e);
        }
    }
}
