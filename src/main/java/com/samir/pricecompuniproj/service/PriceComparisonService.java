package com.samir.pricecompuniproj.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samir.pricecompuniproj.dto.PriceResult;
import com.samir.pricecompuniproj.dto.ProductSearchRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PriceComparisonService {

  @Value("${serper.api.key}")
  private String apiKey;

  @Value("${serper.api.url}")
  private String apiUrl;

  private final OkHttpClient client;
  private final ObjectMapper objectMapper;

  public PriceComparisonService() {
    this.client = new OkHttpClient.Builder().build();
    this.objectMapper = new ObjectMapper();
  }

  public List<PriceResult> execute(ProductSearchRequest request) {
    log.info("🔍 Searching for: {}", request.productName());

    try {
      String responseBody = callSerperApi(request.productName());

      List<PriceResult> results = parseShoppingResults(responseBody);

      if (results.isEmpty()) {
        log.info("📦 No shopping results, parsing organic results");
        results = parseOrganicResults(responseBody);
      }

      List<PriceResult> cheapest = results.stream()
          .sorted(Comparator.comparing(PriceResult::price))
          .limit(5)
          .collect(Collectors.toList());

      log.info("✅ Found {} total results, returning {} cheapest", results.size(), cheapest.size());

      return cheapest;

    } catch (IOException e) {
      log.error("❌ Failed to call Serper API", e);
      throw new RuntimeException("Failed to fetch search results", e);
    }
  }

  private String callSerperApi(String productName) throws IOException {
    String searchQuery = productName + " qiymət satış al";

    String jsonBody = String.format("""
        {
            "q": "%s",
            "gl": "az",
            "hl": "az",
            "num": 50
        }
        """, searchQuery);

    log.debug("📤 Query: {}", searchQuery);

    MediaType mediaType = MediaType.parse("application/json");
    RequestBody body = RequestBody.create(mediaType, jsonBody);

    Request httpRequest = new Request.Builder()
        .url(apiUrl)
        .method("POST", body)
        .addHeader("X-API-KEY", apiKey)
        .addHeader("Content-Type", "application/json")
        .build();

    try (Response response = client.newCall(httpRequest).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Serper API error: " + response.code());
      }
      return response.body().string();
    }
  }

  private List<PriceResult> parseShoppingResults(String jsonResponse) {
    List<PriceResult> results = new ArrayList<>();

    try {
      JsonNode root = objectMapper.readTree(jsonResponse);
      JsonNode shopping = root.get("shopping");

      if (shopping != null && shopping.isArray()) {
        log.info("🛒 Found {} shopping results", shopping.size());

        for (JsonNode item : shopping) {
          String title = item.get("title").asText();
          String link = item.get("link").asText();
          String priceStr = item.has("price") ? item.get("price").asText() : null;

          if (priceStr != null) {
            BigDecimal price = extractPrice(priceStr);
            if (price != null) {
              results.add(new PriceResult(
                  link,
                  title,
                  price,
                  extractStoreName(link)
              ));
              log.debug("💰 Shopping: {} - {} ₼ from {}", title, price, extractStoreName(link));
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("⚠️ Failed to parse shopping results: {}", e.getMessage());
    }

    return results;
  }

  private List<PriceResult> parseOrganicResults(String jsonResponse) {
    List<PriceResult> results = new ArrayList<>();

    try {
      JsonNode root = objectMapper.readTree(jsonResponse);
      JsonNode organic = root.get("organic");

      if (organic == null || !organic.isArray()) {
        log.warn("⚠️ No organic results found");
        return results;
      }

      log.info("📄 Parsing {} organic results", organic.size());

      for (JsonNode result : organic) {
        String title = result.get("title").asText();
        String link = result.get("link").asText();
        String snippet = result.has("snippet") ? result.get("snippet").asText() : "";

        String text = title + " " + snippet;
        BigDecimal price = extractPrice(text);

        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
          results.add(new PriceResult(
              link,
              title,
              price,
              extractStoreName(link)
          ));
          log.debug("💰 Organic: {} - {} ₼ from {}", title, price, extractStoreName(link));
        }
      }
    } catch (Exception e) {
      log.error("❌ Failed to parse organic results", e);
    }

    return results;
  }

  private BigDecimal extractPrice(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }

    // ONLY match prices with AZN currency indicators (₼, AZN, manat)
    List<Pattern> patterns = List.of(
        Pattern.compile("(\\d{1,6}[.,]?\\d{0,3}[.,]\\d{2})\\s?₼"),
        Pattern.compile("₼\\s?(\\d{1,6}[.,]?\\d{0,3}[.,]\\d{2})"),
        Pattern.compile("(\\d{1,6}[.,]?\\d{0,3}[.,]?\\d{0,2})\\s?(?:AZN|azn)"),
        Pattern.compile("(?:AZN|azn)\\s?(\\d{1,6}[.,]?\\d{0,3}[.,]?\\d{0,2})"),
        Pattern.compile("(\\d{1,6}[.,]?\\d{0,3}[.,]?\\d{0,2})\\s?(?:manat|Manat)"),
        Pattern.compile("(\\d{2,6})\\s?₼"),
        Pattern.compile("₼\\s?(\\d{2,6})\\b")
    );

    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(text);
      while (matcher.find()) {
        String priceStr = matcher.group(1);
        try {
          BigDecimal price = normalizePrice(priceStr);
          if (price.compareTo(BigDecimal.ONE) >= 0 &&
              price.compareTo(new BigDecimal("100000")) <= 0) {
            return price;
          }
        } catch (Exception e) {
          log.debug("Failed to parse price: {}", priceStr);
        }
      }
    }

    return null;
  }

  private BigDecimal normalizePrice(String priceStr) {
    priceStr = priceStr.trim();

    long dotCount = priceStr.chars().filter(ch -> ch == '.').count();
    long commaCount = priceStr.chars().filter(ch -> ch == ',').count();

    int lastComma = priceStr.lastIndexOf(',');
    int lastDot = priceStr.lastIndexOf('.');

    if (lastComma > lastDot) {
      priceStr = priceStr.replace(".", "").replace(",", ".");
    } else if (lastDot > lastComma) {
      priceStr = priceStr.replace(",", "");
    }

    return new BigDecimal(priceStr);
  }

  private String extractStoreName(String url) {
    try {
      String host = new URL(url).getHost();
      String domain = host.replace("www.", "");
      return domain.split("\\.")[0];
    } catch (Exception e) {
      return "Unknown";
    }
  }
}