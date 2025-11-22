# CLAUDE.md

## Workflow Requirments
Update Claude.md before every commit

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PriceCompUniProj is a Spring Boot REST API for price comparison in Azerbaijan. It searches for products using the Serper API (Google search API) and extracts price information in AZN (Azerbaijani Manat) from both shopping results and organic search results.

## Build and Run Commands

### Build the project
```bash
./mvnw clean package
```

### Run the application
```bash
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```

### Run a specific test
```bash
./mvnw test -Dtest=PriceCompUniProjApplicationTests
```

## Technology Stack

- **Java 21** (required)
- **Spring Boot 3.5.7** - REST API framework
- **Maven** - Build tool (use `./mvnw` wrapper)
- **OkHttp 4.12.0** - HTTP client for Serper API calls
- **Jackson** - JSON parsing
- **Lombok** - Reduce boilerplate code
- **SpringDoc OpenAPI 2.0.2** - API documentation (accessible when running)

## Application Architecture

### High-Level Flow
1. Client sends POST request to `/api/v1/compare/search` with `ProductSearchRequest`
2. `PriceComparisonController` receives request and delegates to `PriceComparisonService`
3. Service calls Serper API with localized search query (adds "qiymət satış al" to product name)
4. Service parses response in two stages:
   - First attempts to parse shopping results from Google Shopping
   - Falls back to parsing organic search results if no shopping results found
5. Prices are extracted using regex patterns that specifically look for AZN currency indicators (₼, AZN, manat)
6. Results are sorted by price and top 5 cheapest are returned

### Key Components

**PriceComparisonService** (`src/main/java/com/samir/pricecompuniproj/service/PriceComparisonService.java`)
- Main business logic for price comparison
- **Serper API Integration**: Calls Google Search API with Azerbaijan locale (`gl=az`, `hl=az`)
- **Two-stage parsing strategy**:
  - `parseShoppingResults()` - Extracts from structured shopping data
  - `parseOrganicResults()` - Fallback that extracts prices from titles/snippets using regex
- **Price extraction logic** (`extractPrice()` at line 178):
  - Only matches prices with AZN currency indicators (₼, AZN, manat)
  - Uses multiple regex patterns to handle various price formats
  - Validates prices are between 1 and 100,000 AZN
  - `normalizePrice()` handles both European (1.234,56) and US (1,234.56) number formats

**PriceComparisonController** (`src/main/java/com/samir/pricecompuniproj/controller/PriceComparisonController.java`)
- Single endpoint: `POST /api/v1/compare/search`
- Accepts `ProductSearchRequest`, returns `List<PriceResult>`

**DTOs** (`src/main/java/com/samir/pricecompuniproj/dto/`)
- `ProductSearchRequest` - Simple record with `productName` field
- `PriceResult` - Record containing `url`, `title`, `price` (BigDecimal), and `store` (extracted from URL domain)

### Configuration

API credentials are in `src/main/resources/application.yaml`:
- `serper.api.key` - Serper API key for Google search
- `serper.api.url` - Serper API endpoint

**Note**: The API key is currently committed in the repository. Consider moving to environment variables or secure credential storage.

## Development Notes

### When modifying price extraction logic:
- Price patterns are specifically designed for AZN currency (Azerbaijan)
- The service adds Azerbaijani keywords ("qiymət satış al" = "price sale buy") to search queries
- Price normalization handles both comma and dot as decimal separators
- Valid price range is 1-100,000 AZN (see line 200-201 in PriceComparisonService)

### When adding new endpoints:
- Follow the existing pattern: Controller → Service → External API
- Use Lombok annotations (`@RequiredArgsConstructor`, `@Slf4j`) for consistency
- Return `ResponseEntity` from controllers

### Testing the API:
- API documentation available via SpringDoc OpenAPI (check Spring Boot logs for URL when running)
- Example request body: `{"productName": "iPhone 15"}`
- Endpoint: `POST http://localhost:8080/api/v1/compare/search`