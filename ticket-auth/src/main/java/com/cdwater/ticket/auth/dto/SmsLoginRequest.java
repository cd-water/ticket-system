package com.cdwater.ticket.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SmsLoginRequest {

    @NotBlank
    private String phone;

    @NotBlank
    private String code;
}
