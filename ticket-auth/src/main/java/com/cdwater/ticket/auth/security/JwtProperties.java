package com.cdwater.ticket.auth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties("ticket.jwt")
public class JwtProperties {

    /** HS256 密钥，长度必须 ≥ 32 字节 */
    private String secret;

    private Duration accessTtl;

    private Duration refreshTtl;
}
