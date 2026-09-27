package com.cdwater.ticket.common.enums;

import lombok.Getter;

@Getter
public enum ResultCode {

    // 成功
    SUCCESS("A200", "Success"),

    // 客户端错误
    BAD_REQUEST("C400", "Bad Request"),
    UNAUTHORIZED("C401", "Unauthorized"),
    FORBIDDEN("C403", "Forbidden"),
    NOT_FOUND("C404", "Not Found"),
    CONFLICT("C409", "Conflict"),
    TOO_MANY_REQUESTS("C429", "Too Many Requests"),

    // 服务端错误
    INTERNAL_SERVER_ERROR("S500", "Internal Server Error");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
