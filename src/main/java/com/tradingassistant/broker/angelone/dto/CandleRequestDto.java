package com.tradingassistant.broker.angelone.dto;

public record CandleRequestDto(
        String exchange,
        String symboltoken,
        String interval,
        String fromdate,
        String todate
) {
}
