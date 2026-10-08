package com.cdwater.ticket.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PwdLoginRequest {

    @NotBlank
    private String phone;

    @NotBlank
    private String password;
}
