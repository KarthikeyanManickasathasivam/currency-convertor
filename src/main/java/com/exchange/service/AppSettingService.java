package com.exchange.service;

import com.exchange.model.AppSetting;
import com.exchange.model.User;
import com.exchange.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingService {

    public static final String APPROVAL_THRESHOLD_KEY = "approval_threshold";

    private final AppSettingRepository appSettingRepository;

    @Value("${transaction.approval.threshold:100}")
    private BigDecimal defaultThreshold;

    // DB row takes precedence; falls back to the JVM property value if no row has been persisted yet
    public BigDecimal getApprovalThreshold() {
        return appSettingRepository.findById(APPROVAL_THRESHOLD_KEY)
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(defaultThreshold);
    }

    @Transactional
    public BigDecimal updateApprovalThreshold(BigDecimal threshold, User admin) {
        AppSetting setting = appSettingRepository.findById(APPROVAL_THRESHOLD_KEY)
                .orElse(AppSetting.builder().key(APPROVAL_THRESHOLD_KEY).build());
        setting.setValue(threshold.toPlainString());
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(admin);
        appSettingRepository.save(setting);
        log.info("Approval threshold updated to {} by admin {}", threshold, admin.getUserId());
        return threshold;
    }
}
