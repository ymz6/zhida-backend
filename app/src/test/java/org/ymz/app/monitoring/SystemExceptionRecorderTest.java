package org.ymz.app.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.ymz.app.model.entity.SystemExceptionLog;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.service.SystemExceptionLogService;
import org.ymz.app.web.response.ResultCode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemExceptionRecorderTest {

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void recordsExceptionDetailAndMetric() {
        SystemExceptionLogService logService = mock(SystemExceptionLogService.class);
        SystemExceptionMetrics metrics = mock(SystemExceptionMetrics.class);
        SystemExceptionRecorder recorder = new SystemExceptionRecorder(logService, metrics);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apps/1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/apps/{appId}");
        AuthContextHolder.set(AuthContext.builder().userId(10L).build());

        recorder.record(new IllegalStateException("boom"), ResultCode.SYSTEM_ERROR, "boom", request);

        verify(metrics).record("IllegalStateException", ResultCode.SYSTEM_ERROR.getCode(), "/apps/{appId}");
        ArgumentCaptor<SystemExceptionLog> captor = ArgumentCaptor.forClass(SystemExceptionLog.class);
        verify(logService).save(captor.capture());
        SystemExceptionLog log = captor.getValue();
        assertEquals(IllegalStateException.class.getName(), log.getExceptionType());
        assertEquals(ResultCode.SYSTEM_ERROR.getCode(), log.getResultCode());
        assertEquals("GET", log.getRequestMethod());
        assertEquals("/apps/{appId}", log.getRequestPath());
        assertEquals("boom", log.getErrorMessage());
        assertEquals(10L, log.getUserId());
        assertNotNull(log.getStackTrace());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void swallowsPersistenceFailure() {
        SystemExceptionLogService logService = mock(SystemExceptionLogService.class);
        doThrow(new RuntimeException("db down")).when(logService).save(any(SystemExceptionLog.class));
        SystemExceptionRecorder recorder = new SystemExceptionRecorder(logService, mock(SystemExceptionMetrics.class));

        assertDoesNotThrow(() -> recorder.record(
                new IllegalStateException("boom"),
                ResultCode.SYSTEM_ERROR,
                "boom",
                new MockHttpServletRequest("GET", "/demo")
        ));
    }
}
