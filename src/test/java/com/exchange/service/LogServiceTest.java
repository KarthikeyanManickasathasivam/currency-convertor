package com.exchange.service;

import com.exchange.model.Log;
import com.exchange.repository.LogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock LogRepository logRepository;

    @InjectMocks LogService logService;

    @Test
    void log_savesEntryWithCorrectFields() {
        UUID userId = UUID.randomUUID();

        // @Async is not processed in unit tests — method runs synchronously
        logService.log("USER_REGISTERED", "AUTH", userId, "127.0.0.1",
                Map.of("email", "user@example.com"));

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository).save(captor.capture());

        Log saved = captor.getValue();
        assertThat(saved.getEvent()).isEqualTo("USER_REGISTERED");
        assertThat(saved.getEventType()).isEqualTo("AUTH");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getDetails()).containsEntry("email", "user@example.com");
    }

    @Test
    void log_nullUserIdAndIp_savesWithoutError() {
        logService.log("ANONYMOUS_EVENT", "AUTH", null, null, Map.of());

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository).save(captor.capture());

        Log saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getIpAddress()).isNull();
    }
}
