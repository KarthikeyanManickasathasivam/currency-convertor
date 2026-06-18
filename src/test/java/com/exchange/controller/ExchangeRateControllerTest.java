package com.exchange.controller;

import com.exchange.dto.request.RateRequest;
import com.exchange.dto.request.RateUpdateRequest;
import com.exchange.dto.response.RateResponse;
import com.exchange.exception.DuplicateResourceException;
import com.exchange.exception.ResourceNotFoundException;
import com.exchange.filter.JwtAuthFilter;
import com.exchange.filter.RateLimitFilter;
import com.exchange.service.ExchangeRateService;
import com.exchange.service.JwtService;
import com.exchange.service.LogService;
import com.exchange.service.TokenBlacklistService;
import com.exchange.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ExchangeRateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExchangeRateService rateService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LogService logService;
    @MockBean UserRepository userRepository;
    @MockBean JwtAuthFilter jwtAuthFilter;
    @MockBean RateLimitFilter rateLimitFilter;

    private RateResponse buildRate(String from, String to, String rate) {
        return RateResponse.builder()
                .id(1L)
                .fromCurrency(from)
                .toCurrency(to)
                .rate(new BigDecimal(rate))
                .lastUpdated(LocalDateTime.now())
                .source("API")
                .isActive(true)
                .build();
    }

    @Test
    void getRate_validPair_returns200() throws Exception {
        when(rateService.getRateResponse("USD", "EUR")).thenReturn(buildRate("USD", "EUR", "0.92"));

        mockMvc.perform(get("/api/rates/USD/EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.92));
    }

    @Test
    void getAllRates_returns200WithList() throws Exception {
        when(rateService.getAllActiveRates()).thenReturn(
                List.of(buildRate("USD", "EUR", "0.92"), buildRate("USD", "GBP", "0.79")));

        mockMvc.perform(get("/api/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createRate_validRequest_returns201() throws Exception {
        RateRequest req = new RateRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("JPY");
        req.setRate(new BigDecimal("148.50000000"));

        when(rateService.createRate(any())).thenReturn(buildRate("USD", "JPY", "148.50000000"));

        mockMvc.perform(post("/api/rates/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("JPY"));
    }

    @Test
    void createRate_missingFromCurrency_returns400() throws Exception {
        RateRequest req = new RateRequest();
        req.setToCurrency("EUR");
        req.setRate(new BigDecimal("0.92"));

        mockMvc.perform(post("/api/rates/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRate_duplicatePair_returns409() throws Exception {
        RateRequest req = new RateRequest();
        req.setFromCurrency("USD");
        req.setToCurrency("EUR");
        req.setRate(new BigDecimal("0.92"));

        when(rateService.createRate(any())).thenThrow(new DuplicateResourceException("Rate already exists for pair: USD/EUR"));

        mockMvc.perform(post("/api/rates/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRate_validRequest_returns200() throws Exception {
        RateUpdateRequest req = new RateUpdateRequest();
        req.setRate(new BigDecimal("0.95000000"));

        when(rateService.updateRate(eq(1L), any())).thenReturn(buildRate("USD", "EUR", "0.95000000"));

        mockMvc.perform(put("/api/rates/admin/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.95000000));
    }

    @Test
    void updateRate_nonExistent_returns404() throws Exception {
        RateUpdateRequest req = new RateUpdateRequest();
        req.setRate(new BigDecimal("0.95000000"));

        when(rateService.updateRate(eq(99L), any())).thenThrow(new ResourceNotFoundException("Exchange rate not found: 99"));

        mockMvc.perform(put("/api/rates/admin/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRate_existingRate_returns204() throws Exception {
        doNothing().when(rateService).deleteRate(1L);

        mockMvc.perform(delete("/api/rates/admin/1"))
                .andExpect(status().isNoContent());

        verify(rateService).deleteRate(1L);
    }

    @Test
    void deleteRate_nonExistent_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Exchange rate not found: 99"))
                .when(rateService).deleteRate(99L);

        mockMvc.perform(delete("/api/rates/admin/99"))
                .andExpect(status().isNotFound());
    }
}
