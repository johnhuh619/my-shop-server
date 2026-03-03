package com.minishop.project.minishop;

import com.minishop.project.minishop.auth.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MiniShopApplicationTests {

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void contextLoads() {
    }

}
