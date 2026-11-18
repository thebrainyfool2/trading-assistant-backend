package com.tradingassistant.broker.angelone.dto;

public record LoginRequest(String clientcode, String password, String totp, String state) {
}
