package com.minishop.project.minishop.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossErrorResponse {
    private String code;
    private String message;
}
