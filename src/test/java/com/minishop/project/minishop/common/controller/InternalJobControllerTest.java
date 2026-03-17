package com.minishop.project.minishop.common.controller;

import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import com.minishop.project.minishop.common.dto.InternalJobResult;
import com.minishop.project.minishop.order.service.OrderExpirationJobService;
import com.minishop.project.minishop.outbox.service.RetryTaskJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InternalJobControllerTest {

    private static final String INTERNAL_JOB_KEY = "test-internal-job-key";

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private OrderExpirationJobService orderExpirationJobService;

    @MockitoBean
    private RetryTaskJobService retryTaskJobService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void expireOrders_헤더없음_401() throws Exception {
        mockMvc.perform(post("/internal/jobs/orders/expire"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C003"));
    }

    @Test
    void expireOrders_유효한헤더_실행성공() throws Exception {
        given(orderExpirationJobService.expireDueOrders(25))
                .willReturn(new InternalJobResult("order-expiration", 25, 3, 3, 0));

        mockMvc.perform(post("/internal/jobs/orders/expire")
                        .header("X-Internal-Job-Key", INTERNAL_JOB_KEY)
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobName").value("order-expiration"))
                .andExpect(jsonPath("$.data.requestedLimit").value(25))
                .andExpect(jsonPath("$.data.selectedCount").value(3))
                .andExpect(jsonPath("$.data.successCount").value(3))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    @Test
    void processRetryTasks_유효한헤더_기본배치크기적용() throws Exception {
        given(retryTaskJobService.processDueTasks(100))
                .willReturn(new InternalJobResult("retry-task-batch", 100, 2, 2, 0));

        mockMvc.perform(post("/internal/jobs/retry-tasks/process")
                        .header("X-Internal-Job-Key", INTERNAL_JOB_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobName").value("retry-task-batch"))
                .andExpect(jsonPath("$.data.requestedLimit").value(100));
    }
}
