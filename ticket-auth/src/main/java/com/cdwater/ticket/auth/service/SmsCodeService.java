package com.cdwater.ticket.auth.service;

import com.cdwater.ticket.common.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${ticket.sms.echo-code}")
    private boolean echoCode;

    @Value("${ticket.sms.ttl}")
    private Duration ttl;

    /** 返回验证码本身，由调用方决定是否回传给前端（仅开发环境） */
    public String send(String phone) {
        String code = String.valueOf(RANDOM.nextInt(100_000, 1_000_000));
        redis.opsForValue().set(RedisKey.smsCode(phone), code, ttl);
        if (!echoCode) {
            // 无短信服务商时，日志就是「发送」这一步：不回传响应体，
            // 但开发者必须拿得到码，否则 echo-code=false（默认）下短信登录无法使用。
            // ponytail: 码会进日志且含手机号；接入真实短信服务商时删掉这行。
            log.info("[mock 短信] phone={} code={}", phone, code);
        }
        return code;
    }

    public boolean verify(String phone, String code) {
        String key = RedisKey.smsCode(phone);
        if (!code.equals(redis.opsForValue().get(key))) {
            return false;
        }
        // 一次性：校验通过即销毁
        redis.delete(key);
        return true;
    }

    public boolean isEchoEnabled() {
        return echoCode;
    }
}
