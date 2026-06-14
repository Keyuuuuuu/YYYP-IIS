# YYYP-IIS: SteamDT RDF Integration Prototype

## Domain

The domain of this prototype is CS2 item market monitoring. The goal is to integrate market observations for the same CS2 items across multiple trading platforms into one RDF graph. The source scenario is based on the YYYP project, which monitors item prices, listing counts, bid prices, and bid counts.

## Datasets

The sample datasets were exported from the local YYYP SQLite database. The original market observations were collected from the SteamDT CS2 market API. The prototype uses 10,000 CS2 items and the five platforms that have valid positive sell-price observations in the local collected data:

- YOUPIN
- C5
- BUFF
- HALOSKINS
- STEAM

The submitted CSV datasets are:

- `datasets/items.csv`: local item catalog with item id, Steam market hash name, Chinese display name, tier, and enabled flag.
- `datasets/platforms.csv`: platform code and display name.
- `datasets/platform_mappings.csv`: available platform-specific item identifiers.
- `datasets/prices/*.csv`: one file per platform, containing the latest valid observed sell price, sell count, bid price, bid count, and timestamp.

## Integration Problems

The data contains several realistic integration issues:

- The same CS2 item can have a local YYYP id, a Steam `market_hash_name`, and different platform-specific item ids.
- Some platforms have known platform item ids, while others must be matched through the Steam market hash name.
- The SteamDT documentation mentions more supported platforms than this prototype uses. Platforms whose latest local observations were zero or unavailable were excluded from the submitted dataset, because they would not add meaningful market evidence to the integration graph.
- Item names contain embedded semantics such as weapon type and wear condition, for example `AK-47 | Asiimov (Factory New)`.
- Bid price and bid count are missing or zero for some platform observations.

## RDF Model

The Java application constructs the RDF graph programmatically with Apache Jena. The main classes are:

- `yyyp:Item`: a CS2 market item.
- `yyyp:MarketPlatform`: a trading platform.
- `yyyp:PlatformIdentifier`: an item id used by a specific platform.
- `yyyp:PriceSnapshot`: a latest price observation from one platform.
- `yyyp:WeaponType`: weapon category parsed from the market hash name.
- `yyyp:WearCondition`: wear condition parsed from the market hash name.

The main properties are:

- `yyyp:marketHashName`
- `yyyp:chineseName`
- `yyyp:weaponType`
- `yyyp:wearCondition`
- `yyyp:hasPlatformIdentifier`
- `yyyp:hasPriceSnapshot`
- `yyyp:observedOnPlatform`
- `yyyp:sellPriceCny`
- `yyyp:sellCount`
- `yyyp:bidPriceCny`
- `yyyp:bidCount`
- `yyyp:hasValidSellPrice`
- `yyyp:observedAt`

## SPARQL Queries

The project contains 10 SPARQL queries.

Basic queries:

- `basic-01-items.rq`: lists items with names, weapon type, wear condition, and tier.
- `basic-02-youpin-valid-prices.rq`: lists valid YOUPIN prices.
- `basic-03-one-item-all-platforms.rq`: shows one item across all platforms.
- `basic-04-items-on-buff-youpin-steam.rq`: retrieves items that have valid prices on BUFF, YOUPIN, and STEAM.
- `basic-05-cross-platform-price-gap.rq`: demonstrates integration value by comparing BUFF, YOUPIN, and STEAM prices for the same item and finding large differences.

Aggregation queries:

- `aggregation-01-observations-by-platform.rq`: counts observations per platform.
- `aggregation-02-valid-price-coverage.rq`: calculates valid price coverage per platform.
- `aggregation-03-average-valid-price-by-platform.rq`: calculates average valid sell price by platform.
- `aggregation-04-average-price-by-wear.rq`: calculates average valid sell price by wear condition.
- `aggregation-05-price-spread-by-item.rq`: calculates min price, max price, and spread percentage for each item across platforms.

## Normalization Decisions

The Java program resolves items first by platform-specific item id when available, then falls back to the Steam market hash name. Prices are stored in CNY as decimal literals. The exported price datasets contain only positive sell prices, and the Java program still records `yyyp:hasValidSellPrice` for each observation so analytical queries can filter valid market prices explicitly. Timestamps from the local database are normalized to `xsd:dateTime`.
