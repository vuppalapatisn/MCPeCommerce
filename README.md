# India E-commerce MCP Server (Spring AI)

[![Build and Publish Docker image](https://github.com/vuppalapatisn/MCPeCommerce/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/vuppalapatisn/MCPeCommerce/actions/workflows/docker-publish.yml)
[![Docker Hub](https://img.shields.io/docker/v/sudhavuppalapati/mcp-ecommerce-server?label=docker%20hub&logo=docker&sort=semver)](https://hub.docker.com/r/sudhavuppalapati/mcp-ecommerce-server)

A remote MCP server, built with Spring AI + Spring Boot, that exposes tools for:
- Searching a product across Amazon.in, Flipkart, Myntra, and Meesho
- Reading current price / rating / rating count for a specific product page
- Building and reading price history over time (self-tracked, since no public
  India price-history API exists)

> **Requires Spring AI 1.1+ (pinned in `pom.xml`).** The Streamable-HTTP MCP
> server transport does not exist in Spring AI 1.0.0 — on 1.0.0 the
> `protocol: STREAMABLE` setting is silently ignored, the server falls back to
> legacy SSE, and the `/mcp` endpoint returns 404. Do not downgrade.

For full build/run/publish/connect instructions see [SETUP.md](SETUP.md).

## ⚠️ Read this before deploying

1. **Scraping these sites likely violates their Terms of Service**, and all
   four use anti-bot protection (CAPTCHAs, IP rate-limiting, JS-rendered pages).
   This project uses plain HTTP requests (Jsoup) which:
   - Will work reasonably well for **Amazon.in** and often **Flipkart**, since
     their search/product pages are largely server-rendered.
   - May return **empty results for Myntra/Meesho** in production, since both
     render search results client-side via JavaScript. If that happens, swap
     the `SiteScraper` implementation for a headless-browser approach
     (Playwright or Selenium) — the interface is designed so nothing else
     needs to change. See "Swapping in a headless browser" below.
   - **Will get your server's IP rate-limited or blocked** if you scrape
     aggressively. Keep the scheduled refresh interval conservative
     (`ecom.scraper.tracking-interval-cron`, default every 6 hours) and don't
     lower `maxResultsPerSite` limits to run frequent bulk crawls.

2. **There is no historical backfill.** Price history only starts
   accumulating from the moment a product is first searched/tracked. This
   mirrors how a real price-tracking service like BuyHatke works — they also
   only know what they've observed themselves.

3. **CSS selectors will break** when these sites redesign their markup
   (Flipkart and Myntra rotate obfuscated class names especially often). Each
   scraper falls back to regex-based extraction on card text so it degrades
   gracefully, but you'll want to periodically check the logs for parsing
   failures and adjust selectors.

If sustained reliability matters more than build cost, consider instead:
   - **Flipkart Affiliate API** (free, requires approval) — official, stable data.
   - **Amazon Product Advertising API** (free with an Amazon Associates account)
     — official, but requires you to have qualifying affiliate sales.
   These won't cover Myntra/Meesho, but they remove the fragility for the two
   biggest sites.

## Project structure

```
src/main/java/com/example/ecomserver/
  config/McpToolConfig.java        # registers @Tool methods with the MCP server
  model/                           # Product DTO, TrackedProduct + PricePoint JPA entities
  repository/                      # Spring Data repositories
  scraper/
    SiteScraper.java               # common interface - swap implementations here
    ScraperHttpClient.java         # Jsoup wrapper with browser-like headers
    ScraperTextUtils.java          # regex fallback for price/rating extraction
    AmazonInScraper.java
    FlipkartScraper.java
    MyntraScraper.java
    MeeshoScraper.java
    ScraperRegistry.java           # maps site key -> scraper bean
  service/
    ProductSearchService.java      # fans a query out across sites concurrently
    PriceHistoryService.java       # tracking + history read/write
  scheduler/PriceTrackerScheduler.java  # periodic re-scrape of tracked products
  tools/EcommerceTools.java        # the actual MCP tools Claude calls
```

## Tools exposed

| Tool | Purpose |
|---|---|
| `search_indian_ecommerce` | Search a product across sites, returns price/rating sorted by price. Auto-starts tracking. |
| `get_product_details` | Fresh price/rating for one product URL. Also records a history point. |
| `get_price_history` | Recorded price history for a tracked product (site + productId). |
| `list_tracked_products` | Everything currently being tracked. |

## Running locally

```bash
mvn spring-boot:run
```

The MCP endpoint will be available at `http://localhost:8080/mcp` (Streamable HTTP).
Test it with the MCP Inspector:

```bash
npx @modelcontextprotocol/inspector
```

### REST API + Swagger UI

The same capabilities are also exposed as a plain REST API for browser/HTTP clients:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

Endpoints (all under `/api`): `GET /api/search`, `GET /api/product`,
`GET /api/price-history/{site}/{productId}`, `GET /api/tracked`, `GET /api/sites`.

When `ECOM_SECURITY_API_KEY` is set, the `/api/**` endpoints require the key — click
**Authorize** in Swagger UI and paste it. (The Swagger UI and `/v3/api-docs` pages
themselves stay reachable so the docs can render.)

> **Behind a corporate TLS-inspection proxy?** Live scrapes may fail with
> `PKIX path building failed`. The correct fix is to import your corporate root CA into
> the JVM truststore. For a quick local demo on a trusted network only, you can set
> `ECOM_SCRAPER_INSECURE_TLS=true` to disable certificate validation for scraping —
> **never enable this in production.**

## Running tests

```bash
mvn test
```

Covers: regex price/rating/rating-count extraction edge cases, Amazon.in card
parsing against static HTML fixtures (no real network calls - `ScraperHttpClient`
is mocked), multi-site search fan-out/sorting/error-isolation, price-history
tracking idempotency, and the MCP tool layer's input clamping and auto-tracking
behavior. Flipkart/Myntra/Meesho scrapers share the same tested regex helpers
via `ScraperTextUtilsTest`; add fixture-based tests for them the same way as
`AmazonInScraperTest` if you customize their selectors.

## Running with Docker

Build and run the app alone (uses the embedded H2 file DB):

```bash
docker build -t mcp-ecommerce-server .
docker run -p 8080:8080 -v ecom-data:/app/data mcp-ecommerce-server
```

Or run the full stack with Postgres via Compose:

```bash
docker compose up --build
```

The MCP endpoint is then at `http://localhost:8080/mcp`. For claude.ai to reach
it, this container needs to run on a host with a public HTTPS address (see
"Deploying so claude.ai can reach it" below) - `docker compose up` alone only
gets you a local/internal endpoint.

## Deploying so claude.ai can reach it

Custom connectors in Claude connect from **Anthropic's cloud infrastructure**, not
from your machine, so:

1. Deploy this app somewhere **publicly reachable over HTTPS** (Render, Fly.io,
   Railway, a VPS behind a reverse proxy with TLS, etc.). A bare HTTP endpoint
   on localhost will not work.
2. Switch the datasource from H2 to Postgres for anything beyond local testing.
   Both drivers are already in `pom.xml`, so no dependency changes are needed —
   just override the datasource via environment variables (the `docker-compose.yml`
   already wires exactly these):

   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/ecom_mcp
   SPRING_DATASOURCE_USERNAME=ecom
   SPRING_DATASOURCE_PASSWORD=<password>
   SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
   SPRING_JPA_HIBERNATE_DDL_AUTO=update
   ```

   These override the H2 defaults in `application.yml` without editing it.
3. In Claude: **Customize > Connectors > "+" > Add custom connector**, and enter
   `https://your-deployed-host/mcp` as the URL. No OAuth is configured by
   default in this project — add Spring Security + OAuth2 resource server
   config if you need to restrict access.

## Swapping in a headless browser

If Myntra/Meesho (or Amazon/Flipkart, if they start blocking you) need real
JS rendering:

1. Add a Playwright dependency (`com.microsoft.playwright:playwright`).
2. Create e.g. `PlaywrightMyntraScraper implements SiteScraper` that launches
   a headless Chromium context, navigates to the URL, waits for the product
   grid selector, then extracts the rendered HTML for `ScraperTextUtils` /
   Jsoup-on-a-string to parse the same way.
3. Replace the `@Component` on `MyntraScraper` with your new class (only one
   bean per `siteKey()` should be active) — the rest of the app (tools,
   services, scheduler) needs no changes since they depend only on
   `SiteScraper`.

## Extending

- **Add a site**: implement `SiteScraper`, annotate with `@Component`, done —
  `ScraperRegistry` and `ProductSearchService` pick it up automatically.
- **Rate limiting per site**: wrap `ScraperHttpClient.get()` calls with a
  `Bucket4j` rate limiter keyed by site if you scale up tracked-product volume.
