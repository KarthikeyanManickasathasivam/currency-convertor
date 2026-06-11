package com.exchange.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalUsers;
    private long activeUsers;
    private long totalTransactions;
    private long pendingApprovals;
    private List<String> topFromCurrencies;
}
