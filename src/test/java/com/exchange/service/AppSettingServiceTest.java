package com.exchange.service;

import com.exchange.model.AppSetting;
import com.exchange.model.User;
import com.exchange.model.enums.Role;
import com.exchange.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    @Mock private AppSettingRepository appSettingRepository;

    @InjectMocks
    private AppSettingService appSettingService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(appSettingService, "defaultThreshold", new BigDecimal("100"));

        adminUser = User.builder()
                .userId(UUID.randomUUID())
                .username("admin")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
    }

    @Test
    void getApprovalThreshold_settingExists_returnsStoredValue() {
        AppSetting setting = AppSetting.builder()
                .key(AppSettingService.APPROVAL_THRESHOLD_KEY)
                .value("250.00")
                .build();
        when(appSettingRepository.findById(AppSettingService.APPROVAL_THRESHOLD_KEY))
                .thenReturn(Optional.of(setting));

        BigDecimal result = appSettingService.getApprovalThreshold();

        assertThat(result).isEqualByComparingTo("250.00");
    }

    @Test
    void getApprovalThreshold_settingMissing_returnsDefault() {
        when(appSettingRepository.findById(AppSettingService.APPROVAL_THRESHOLD_KEY))
                .thenReturn(Optional.empty());

        BigDecimal result = appSettingService.getApprovalThreshold();

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void updateApprovalThreshold_existingSetting_updatesValue() {
        AppSetting existing = AppSetting.builder()
                .key(AppSettingService.APPROVAL_THRESHOLD_KEY)
                .value("100")
                .build();
        when(appSettingRepository.findById(AppSettingService.APPROVAL_THRESHOLD_KEY))
                .thenReturn(Optional.of(existing));
        when(appSettingRepository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        BigDecimal result = appSettingService.updateApprovalThreshold(new BigDecimal("500.00"), adminUser);

        assertThat(result).isEqualByComparingTo("500.00");

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(appSettingRepository).save(captor.capture());
        AppSetting saved = captor.getValue();
        assertThat(saved.getValue()).isEqualTo("500.00");
        assertThat(saved.getUpdatedBy()).isEqualTo(adminUser);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateApprovalThreshold_noExistingSetting_createsNewRow() {
        when(appSettingRepository.findById(AppSettingService.APPROVAL_THRESHOLD_KEY))
                .thenReturn(Optional.empty());
        when(appSettingRepository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        appSettingService.updateApprovalThreshold(new BigDecimal("200.00"), adminUser);

        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(appSettingRepository).save(captor.capture());
        AppSetting saved = captor.getValue();
        assertThat(saved.getKey()).isEqualTo(AppSettingService.APPROVAL_THRESHOLD_KEY);
        assertThat(saved.getValue()).isEqualTo("200.00");
        assertThat(saved.getUpdatedBy()).isEqualTo(adminUser);
    }
}
