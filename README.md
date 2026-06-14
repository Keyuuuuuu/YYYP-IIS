# YYYP-IIS

Java-based RDF integration prototype for CS2 item market data collected by the YYYP monitoring system from the SteamDT multi-platform market API.

## Run

```powershell
mvn clean package
java -Xmx2g -jar target/YYYP-IIS-1.0.0.jar
```

On Windows without Maven in `PATH`, run:

```powershell
.\run.ps1
```

The application reads `datasets/`, builds an Apache Jena RDF model, writes `output/yyyp-market.ttl`, and executes all SPARQL queries from `queries/` into `output/query-results/`.

The submitted queries are intentionally analytical rather than only structural. They cover identifier reconciliation, one-item integrated views, non-Steam liquidity-aware price spreads, platform liquidity, cheapest-platform share, platform-pair disagreement, and Steam premium/outlier detection.

Current dataset size:

- 10,000 CS2 items
- 5 market platforms with valid prices: YOUPIN, C5, BUFF, HALOSKINS, STEAM
- 50,000 price observations
- 870,495 generated RDF triples

## Project Structure

```text
datasets/                 CSV source datasets
datasets/prices/          One price file per market platform
src/main/java/            Java + Apache Jena implementation
queries/                  5 basic and 5 aggregation SPARQL queries
output/                   Generated Turtle and query results
docs/documentation.md     Short assignment documentation
```
