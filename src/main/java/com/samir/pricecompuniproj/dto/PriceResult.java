package com.samir.pricecompuniproj.dto;

import java.math.BigDecimal;

public record PriceResult(
    String url,
    String title,
    BigDecimal price,
    String store
) {
}