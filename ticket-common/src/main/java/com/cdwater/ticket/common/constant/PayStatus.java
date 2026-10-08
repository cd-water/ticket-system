package com.cdwater.ticket.common.constant;

/** 支付单状态 */
public final class PayStatus {

    private PayStatus() {
    }

    public static final int UNPAID = 0;

    public static final int SUCCESS = 1;

    public static final int FAILED = 2;

    public static final int CLOSED = 3;
}
