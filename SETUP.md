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

### Authentication (optional but recommended for public deploys)

The `/mcp` endpoint is unauthenticated unless you set a shared secret. Set env
`ECOM_SECURITY_API_KEY` (the Render blueprint auto-generates one) and the server
then requires that key on every `/mcp` request. `/actuator/health` stays open so
health checks keep working. When the env var is unset, auth is disabled (handy
for local dev and tests).

Clients pass the key as either header:

```
Authorization: Bearer <key>
X-API-Key: <key>
```

### Claude Code (local)

Without auth:

```bash
claude mcp add --transport http ecommerce http://localhost:8080/mcp
```

With auth (add the header):

```bash
claude mcp add --transport http ecommerce https://<your-host>/mcp \
  --header "Authorization: Bearer <your-key>"
```

Then run `/mcp` inside a session to confirm it connects and lists the tools.
Equivalent `.mcp.json`:

```json
{
  "mcpServers": {
    "ecommerce": {
      "type": "http",
      "url": "https://<your-host>/mcp",
      "headers": { "Authorization": "Bearer <your-key>" }
    }
  }
}
```

### Deploy to a public URL (Render) — browser only, no local tools

A GitHub repo is just source; it does not run a server. To use this from
claude.ai (or from Claude Code without running it locally), deploy it to a host
that gives a public HTTPS URL. This repo ships a `render.yaml` Blueprint:

1. Sign in at <https://dashboard.render.com>.
2. **New + → Blueprint →** select the `vuppalapatisn/MCPeCommerce` repo. Render
   reads `render.yaml`, builds the `Dockerfile`, and deploys it.
3. When it's live you get a URL like `https://mcp-ecommerce-server-xxxx.onrender.com`.
   Your MCP endpoint is that URL **+ `/mcp`**.
4. `autoDeploy` is on, so every push to `main` redeploys automatically.

Notes:
- The **free** instance sleeps after ~15 min idle; the first request after that
  cold-starts (~30–60 s), so a first connection from Claude may need a retry.
- The free filesystem is ephemeral (H2 data resets on restart) — see the
  commented Postgres block in `render.yaml` for durable history.
- The endpoint is **public and unauthenticated** by default — anyone with the
  URL can call the tools. Add auth before relying on it (see below).

### claude.ai (custom connector)

Custom connectors connect from Anthropic's cloud, **not** your machine, so
`localhost` will not work. Deploy to a **public HTTPS** URL first (Render above,
or Fly.io / Railway / a VPS behind TLS). Then:

**Settings → Connectors → Add custom connector** → URL `https://your-host/mcp`.

> **Note on auth from claude.ai:** the browser custom-connector flow is built
> around OAuth, so it may not let you attach a static `Authorization` header. The
> shared-key auth here works cleanly with **Claude Code** (via `--header`). To
> test from **claude.ai**, either deploy without the key set, or add a full
> OAuth2 resource server. The Claude Code path is recommended for a quick,
> secured setup.

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
