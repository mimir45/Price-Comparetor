package com.samir.pricecompuniproj.controller;

import com.samir.pricecompuniproj.dto.PriceResult;
import com.samir.pricecompuniproj.dto.ProductSearchRequest;
import com.samir.pricecompuniproj.service.PriceComparisonService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/compare")
@RequiredArgsConstructor
@Slf4j
public class PriceComparisonController {

  private final PriceComparisonService priceComparisonService;

  @PostMapping("/search")
  public ResponseEntity<List<PriceResult>> search(@RequestBody ProductSearchRequest request) {
    log.info("🚀search end point called for this item {} ",
        request.productName());
    var response = priceComparisonService.execute(request);
    return ResponseEntity.ok(response);
  }
}