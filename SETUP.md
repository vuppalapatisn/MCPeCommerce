# Setup Guide

End-to-end steps to build, run, publish, and connect this MCP server to Claude.

> **Requires Spring AI 1.1+.** Streamable-HTTP MCP server support does not exist
> in Spring AI 1.0.0 — on 1.0.0 the `protocol: STREAMABLE` setting is silently
> ignored, the server falls back to legacy SSE, and `/mcp` returns 404. The
> `pom.xml` is pinned to `1.1.0`; do not downgrade it.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | or use the bundled build in the Dockerfile |
| Docker Desktop | latest | only needed for the container / compose path |
| Git | any | for pushing to GitHub |
| GitHub CLI (`gh`) | optional | convenient for creating the repo |

## 1. Build and test locally

```bash
mvn clean package
```

Expected: `BUILD SUCCESS`, `Tests run: 43, Failures: 0, Errors: 0`.
The runnable jar is written to `target/mcp-ecommerce-server-1.0.0.jar`.

## 2. Run locally

```bash
mvn spring-boot:run
# or
java -jar target/mcp-ecommerce-server-1.0.0.jar
```

- Health: <http://localhost:8080/actuator/health> → `{"status":"UP"}`
- MCP endpoint (Streamable HTTP): `http://localhost:8080/mcp`

### Verify the MCP endpoint by hand

`initialize` returns HTTP 200 with an `Mcp-Session-Id` header, and `tools/list`
returns the four tools:

```bash
curl -i -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1.0"}}}'
```

Or use the MCP Inspector: `npx @modelcontextprotocol/inspector` and point it at
`http://localhost:8080/mcp`.

## 3. Run with Docker

App alone (embedded H2 file DB):

```bash
docker build -t mcp-ecommerce-server .
docker run -p 8080:8080 -v ecom-data:/app/data mcp-ecommerce-server
```

Full stack with Postgres:

```bash
docker compose up --build
```

`docker compose` only gives you a local/internal endpoint — see step 5 for
making it reachable from claude.ai.

## 3b. Publish the image to Docker Hub

Image name: `sudhavuppalapati/mcp-ecommerce-server`.

### Automated (recommended) — GitHub Actions

`.github/workflows/docker-publish.yml` builds, tests, and pushes the image on
every push to `main` (and on `v*` tags). It just needs two repository secrets —
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `DOCKERHUB_USERNAME` | `sudhavuppalapati` |
| `DOCKERHUB_TOKEN` | a Docker Hub **access token** (hub.docker.com → Account Settings → Personal access tokens), *not* your password |

Tags produced: `latest` (on `main`), the release tag on `v*` pushes, and a
short-SHA tag on every build.

### Manual (from a machine with Docker)

```bash
docker login -u sudhavuppalapati            # paste the access token as the password
docker build -t sudhavuppalapati/mcp-ecommerce-server:latest .
docker push sudhavuppalapati/mcp-ecommerce-server:latest
```

Pull and run it anywhere:

```bash
docker run -p 8080:8080 -v ecom-data:/app/data sudhavuppalapati/mcp-ecommerce-server:latest
```

## 4. Publish to GitHub

If your working changes live on a feature branch (e.g. a Claude Code worktree
branch), merge them into `main` first:

```bash
git checkout main
git merge <your-feature-branch>
```

Create the **empty** repo `MCPeCommerce` (no README/license so history stays
clean) — via the website (New repository) or the CLI:

```bash
gh repo create vuppalapatisn/MCPeCommerce --private --source . --remote origin
```

Then add the remote (skip if `gh` already did) and push:

```bash
git remote add origin https://github.com/vuppalapatisn/MCPeCommerce.git
git branch -M main
git push -u origin main
```

## 5. Connect to Claude

This is a **remote HTTP** server (not stdio), so it must already be running.

### Claude Code (local)

```bash
claude mcp add --transport http ecommerce http://localhost:8080/mcp
```

Then run `/mcp` inside a session to confirm it connects and lists the tools.
Equivalent `.mcp.json`:

```json
{
  "mcpServers": {
    "ecommerce": { "type": "http", "url": "http://localhost:8080/mcp" }
  }
}
```

### claude.ai (custom connector)

Custom connectors connect from Anthropic's cloud, **not** your machine, so
`localhost` will not work. Deploy the app to a **public HTTPS** URL first
(Render, Fly.io, Railway, or a VPS behind a TLS reverse proxy). Then:

**Settings → Connectors → Add custom connector** → URL `https://your-host/mcp`.

Before exposing it publicly:

- **Switch H2 → Postgres.** Set `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and
  `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver` (the `docker-compose.yml`
  already wires these). Both drivers are already in `pom.xml`.
- **Add authentication.** No auth is configured by default — add Spring Security
  + an OAuth2 resource server if the endpoint is internet-facing.

## Tools exposed

| Tool | Purpose |
|---|---|
| `search_indian_ecommerce` | Search a product across sites; returns price/rating sorted by price. Auto-starts tracking. |
| `get_product_details` | Fresh price/rating for one product URL. Records a history point. |
| `get_price_history` | Recorded price history for a tracked product (site + productId). |
| `list_tracked_products` | Everything currently being tracked. |

## Caveats

Scraping these sites likely violates their Terms of Service and all four use
anti-bot protection. Plain HTTP (Jsoup) works reasonably for Amazon.in and often
Flipkart, but **Myntra and Meesho render results client-side and will often
return empty** without a headless browser (Playwright/Selenium) — see
"Swapping in a headless browser" in [README.md](README.md). Keep the scheduled
refresh interval conservative to avoid IP blocks. Price history has no backfill;
it accumulates only from when a product is first tracked.
