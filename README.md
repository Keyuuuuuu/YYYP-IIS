# YYYP-IIS

Java-based RDF integration prototype for CS2 item market data collected by the YYYP monitoring system from the SteamDT multi-platform market API.

## Run

```powershell
mvn clean package
java -jar target/YYYP-IIS-1.0.0.jar
```

On Windows without Maven in `PATH`, run:

```powershell
.\run.ps1
```

The application reads `datasets/`, builds an Apache Jena RDF model, writes `output/yyyp-market.ttl`, and executes all SPARQL queries from `queries/` into `output/query-results/`.

## Project Structure

```text
datasets/                 CSV source datasets
datasets/prices/          One price file per market platform
src/main/java/            Java + Apache Jena implementation
queries/                  5 basic and 5 aggregation SPARQL queries
output/                   Generated Turtle and query results
docs/documentation.md     Short assignment documentation
```
