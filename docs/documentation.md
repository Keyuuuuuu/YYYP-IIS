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
- Steam observations behave differently from the direct trading platforms: they have no platform-specific item id in the local snapshot, no bid side in this dataset, and some Steam sell prices are large outliers compared with BUFF/YOUPIN/C5/HALOSKINS. The RDF graph keeps the reported SteamDT values, while the analytical queries separate Steam premium/outlier detection from non-Steam trading-platform comparisons.

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

- `basic-01-integrated-item-view.rq`: shows one item across all platforms, including platform ids, prices, sell depth, bid information, timestamp, and source file.
- `basic-02-platform-identifier-map.rq`: demonstrates identifier reconciliation by listing the different platform ids for the same integrated item.
- `basic-03-liquid-nonsteam-price-spread.rq`: compares YOUPIN, BUFF, C5, and HALOSKINS for liquid items and reports large non-Steam price spreads.
- `basic-04-steam-premium-red-flags.rq`: lists cases where the Steam value is at least three times both YOUPIN and BUFF, making Steam a data-quality or market-semantics red flag rather than an arbitrage source.
- `basic-05-nonsteam-price-opportunities.rq`: finds potential non-Steam buy/sell price differences with minimum liquidity and a minimum price threshold.

Aggregation queries:

- `aggregation-01-platform-liquidity-and-bids.rq`: compares market depth by platform using total sell listings, average sell listings, bid coverage, and total bid orders.
- `aggregation-02-cheapest-platform-share.rq`: counts how often each non-Steam platform is the cheapest liquid source.
- `aggregation-03-platform-price-index.rq`: compares each platform against the item's non-Steam average price index.
- `aggregation-04-platform-pair-disagreement.rq`: measures average absolute price disagreement for each pair of platforms.
- `aggregation-05-steam-premium-by-price-band.rq`: summarizes Steam premium/outlier behavior by non-Steam price band.

## Normalization Decisions

The Java program resolves items first by platform-specific item id when available, then falls back to the Steam market hash name. Prices are stored in CNY as decimal literals exactly as reported in the local YYYP snapshot. The exported price datasets contain only positive sell prices, and the Java program still records `yyyp:hasValidSellPrice` for each observation so analytical queries can filter valid market prices explicitly. Timestamps from the local database are normalized to `xsd:dateTime`.

For analytical outputs, non-Steam trading opportunity queries use only YOUPIN, BUFF, C5, and HALOSKINS, require minimum sell counts, and ignore prices below 1 CNY to reduce noise from very small minimum-price increments. Steam is still integrated in the RDF graph, but it is mainly used in the submitted queries to show cross-source disagreement and potential source-quality issues.
