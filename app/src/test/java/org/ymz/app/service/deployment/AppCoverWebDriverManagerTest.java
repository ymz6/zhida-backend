package org.ymz.app.service.deployment;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.ymz.app.config.AppDeploymentProperties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AppCoverWebDriverManagerTest {

    @Test
    void getDriverReusesSingleDriver() {
        WebDriver driver = mock(WebDriver.class);
        TestAppCoverWebDriverManager manager = new TestAppCoverWebDriverManager(driver);

        manager.getDriver();
        manager.getDriver();

        manager.verifyCreateCount(1);
        verify(driver, never()).quit();
    }

    @Test
    void discardDriverQuitsCurrentDriverAndAllowsRecreate() {
        WebDriver firstDriver = mock(WebDriver.class);
        WebDriver secondDriver = mock(WebDriver.class);
        TestAppCoverWebDriverManager manager = new TestAppCoverWebDriverManager(firstDriver, secondDriver);

        manager.getDriver();
        manager.discardDriver();
        manager.getDriver();

        manager.verifyCreateCount(2);
        verify(firstDriver).quit();
        verify(secondDriver, never()).quit();
    }

    @Test
    void closeQuitsManagedDriver() {
        WebDriver driver = mock(WebDriver.class);
        TestAppCoverWebDriverManager manager = new TestAppCoverWebDriverManager(driver);

        manager.getDriver();
        manager.close();
        manager.close();

        verify(driver, times(1)).quit();
    }

    private static class TestAppCoverWebDriverManager extends AppCoverWebDriverManager {

        private final WebDriver[] drivers;
        private int createCount;

        TestAppCoverWebDriverManager(WebDriver... drivers) {
            super(new AppDeploymentProperties());
            this.drivers = drivers;
        }

        @Override
        WebDriver createWebDriver() {
            return drivers[createCount++];
        }

        void verifyCreateCount(int expected) {
            org.junit.jupiter.api.Assertions.assertEquals(expected, createCount);
        }
    }
}
