# CS2 Auto Targets

A JavaFX desktop app for managing CS2 skin **buy targets** and **price alerts**
across DMarket and CSFloat, backed by a local SQLite database pre-seeded with
~8,800 weapon/skin/wear combinations. Built to run continuously on a home PC
("server") -- close the window and background polling keeps going as long as
the process is alive.

## Features

1. **Manage offers** -- create, edit, enable/disable, and delete buy targets
   per platform from one screen (Targets tab).
2. **Auto target price adjustment** -- a background scheduler periodically
   checks the competing public buy-order price for each active target and
   bumps your price just above it (by a configurable cent increment), never
   exceeding your set ceiling.
3. **Float ranges / wear conditions** -- every target and alert can be scoped
   to a wear tier (Factory New ... Battle-Scarred) or a custom float range.
4. **Alerts** -- same shape as Targets, but never places an order; just fires
   a desktop notification when the lowest matching offer crosses your
   threshold.

## Architecture

```
org.example
 ├── App.java                 JavaFX entry point, wires everything together
 ├── AppConfig.java            Paths/constants (DB location, default intervals)
 ├── db/Database.java          SQLite connection + schema bootstrap
 ├── model/                    Plain data classes (Target, Alert, SkinCatalogEntry, ...)
 ├── repository/                Plain-JDBC CRUD per table
 ├── integration/
 │    ├── TradingPlatform.java  Interface every platform client implements
 │    ├── dmarket/              DMarketClient + Ed25519 request signing
 │    └── csfloat/              CSFloatClient
 ├── service/
 │    ├── PriceAggregator       Merges price signals across all configured platforms
 │    ├── TargetService         Auto-adjust logic (the core feature)
 │    ├── AlertService          Notification-only threshold checks
 │    ├── SchedulerService      Two recurring background jobs
 │    ├── SkinDataService       Loads the bundled skin catalog on first run
 │    └── NotificationService   Desktop tray notifications
 └── ui/                        One package per tab (targets, alerts, skinbrowser, pricemonitor, settings)
```

Adding a third trading site later means: implement `TradingPlatform`, add it
to the `PriceAggregator.mapOf(...)` call in `App.java`, add an enum value to
`Platform`. Nothing else needs to change.

### Why plain JDBC instead of Hibernate?

The schema is five small tables with no complex relationships. Hand-rolled
JDBC in the `repository` package is less code than configuring an ORM here,
has zero mapping-annotation footguns, and is trivial to debug since every
query is just a String you can paste into a SQLite browser.

## Skin catalog seed data

`src/main/resources/seed/skin_catalog_seed.json.gz` is a flattened, gzipped
export (8,799 rows, ~275KB) derived from the community-maintained
[ByMykel/CSGO-API](https://github.com/ByMykel/CSGO-API) dataset
(`public/api/en/skins.json`), expanded from one row per skin into one row per
**(skin, wear)** combination with Steam's standard float boundaries clipped to
each skin's actual min/max float range. It's loaded into `skin_catalog`
automatically on first launch if the table is empty -- no network call needed
to get started.

Caveats:
- The dataset's `stattrak`/`souvenir` booleans mean "this skin *can* exist as
  StatTrak/Souvenir", not "this row is one" -- harmless for buy-target
  purposes but don't treat them as per-listing flags.
- `weapon.weapon_id` is used as DefIndex and `paint_index` as PaintIndex --
  this matches CSFloat's schema directly and lines up with the values DMarket
  and CSFloat both expose.
- The catalog won't include brand-new skins released after this snapshot was
  taken. There's no in-app "refresh catalog" button yet; if you want one,
  it's a small addition to `SkinDataService` (re-fetch
  `raw.githubusercontent.com/ByMykel/CSGO-API/main/public/api/en/skins.json`
  and re-run the same flattening logic that produced the seed file).

## Setup

### Prerequisites
- JDK 21 (the build is configured for it via Gradle's toolchain support --
  Gradle will download a matching JDK automatically if you don't have one).
- A real internet connection on first `./gradlew run` to pull dependencies
  from Maven Central (JavaFX, Jackson, Apache HttpClient5, BouncyCastle,
  sqlite-jdbc, SLF4J/Logback).

### Run it

```bash
./gradlew run
```

On Windows: `gradlew.bat run`

First launch will:
1. Create `~/.cs_auto_targets/data.db` (SQLite) and `~/.cs_auto_targets/logs/`.
2. Decompress and load the bundled skin catalog into `skin_catalog` (~8,800 rows, a few seconds).
3. Open the main window with five tabs: Targets, Alerts, Skin Browser, Price Monitor, Settings.

### Configure API credentials

Go to the **Settings** tab:

- **DMarket**: paste your Public Key and Secret Key (from your DMarket
  account's API settings page). Used to sign every authenticated request with
  Ed25519, exactly like DMarket's docs describe.
- **CSFloat**: paste your API key (from csfloat.com/profile -> Developer tab)
  for sell-offer lookups. For **buy orders** specifically, CSFloat's
  documented API surface doesn't cover them -- the app uses the same
  undocumented `/api/v1/buy-orders` endpoint that was reverse-engineered
  during this project's prototyping, authenticated with your logged-in
  session cookie (grab it from your browser's dev tools on csfloat.com).
  This is inherently more fragile than a documented API and may break if
  CSFloat changes how sessions work -- there's no way around that until/unless
  CSFloat documents buy orders officially.

### Create your first target

1. Targets tab -> **New Target**.
2. Type into the skin search box (e.g. "AK-47 Redline") and click a result.
3. Pick a platform, set a max price (or leave at $0 to use the current lowest
   sell offer as your ceiling), set the outbid increment in cents, pick a
   wear condition or custom float range.
4. Save. The background scheduler picks it up on its next cycle (default:
   every 10 minutes, adjustable in Settings), or click **Run Adjust Cycle
   Now** to trigger it immediately.

### Create your first alert

Same flow under the Alerts tab, minus platform order placement -- you'll get
a desktop notification (via system tray) when the price condition is met,
subject to a cooldown so it doesn't re-fire every poll cycle.

## Known limitations / things to verify before relying on this for real money

- **DMarket `floatPartValue` buckets are reverse-engineered, not
  documented.** `FloatUtils` assumes each wear tier splits into 3 equal
  buckets (`FN-0/1/2`, `MW-0/1/2`, etc.) based on values observed in the
  original prototyping. If your float range doesn't fit cleanly into a
  bucket, the code falls back to `"any"` for that wear tier rather than
  guessing wrong. Spot-check this against DMarket's actual UI before trusting
  it for tight float-sniping.
- **CSFloat buy-order visibility is account-scoped.** Unlike DMarket (which
  exposes a public order book by title), CSFloat's API only lets you see your
  *own* buy orders -- there's no way to query "what's the highest competing
  buy order on CSFloat for this skin" the way DMarket allows. The auto-adjust
  logic for CSFloat targets is therefore weaker: it can tell if your own
  order still exists, but can't truly outbid competitors the way it can on
  DMarket.
- This was built and reviewed carefully but **not run against live DMarket /
  CSFloat accounts** in this environment (no network access to Maven Central
  or the trading APIs from here). Test with small amounts first.

## Database location

- Database: `~/.cs_auto_targets/data.db`
- Logs: `~/.cs_auto_targets/logs/app.log` (rotated daily, 14 days retained)

Back up `data.db` if you want to preserve your targets/alerts/price history
across machines.
