package hu.eszterhazy.yyyp;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String VOCAB = "http://example.org/yyyp/vocab#";
    private static final String RES = "http://example.org/yyyp/resource/";

    private static final Pattern WEAR_PATTERN = Pattern.compile(
            "\\((Factory New|Minimal Wear|Field-Tested|Well-Worn|Battle-Scarred)\\)\\s*$"
    );

    private static final DateTimeFormatter DB_TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private final Property marketHashName;
    private final Property chineseName;
    private final Property localItemId;
    private final Property tier;
    private final Property enabled;
    private final Property weaponType;
    private final Property skinName;
    private final Property wearCondition;
    private final Property statTrak;
    private final Property souvenir;
    private final Property hasPlatformIdentifier;
    private final Property identifierPlatform;
    private final Property identifierValue;
    private final Property hasPriceSnapshot;
    private final Property observedItem;
    private final Property observedOnPlatform;
    private final Property observedAt;
    private final Property sellPriceCny;
    private final Property sellCount;
    private final Property bidPriceCny;
    private final Property bidCount;
    private final Property hasValidSellPrice;
    private final Property sourceFile;

    private Main(Model model) {
        this.marketHashName = model.createProperty(VOCAB, "marketHashName");
        this.chineseName = model.createProperty(VOCAB, "chineseName");
        this.localItemId = model.createProperty(VOCAB, "localItemId");
        this.tier = model.createProperty(VOCAB, "tier");
        this.enabled = model.createProperty(VOCAB, "enabled");
        this.weaponType = model.createProperty(VOCAB, "weaponType");
        this.skinName = model.createProperty(VOCAB, "skinName");
        this.wearCondition = model.createProperty(VOCAB, "wearCondition");
        this.statTrak = model.createProperty(VOCAB, "statTrak");
        this.souvenir = model.createProperty(VOCAB, "souvenir");
        this.hasPlatformIdentifier = model.createProperty(VOCAB, "hasPlatformIdentifier");
        this.identifierPlatform = model.createProperty(VOCAB, "identifierPlatform");
        this.identifierValue = model.createProperty(VOCAB, "identifierValue");
        this.hasPriceSnapshot = model.createProperty(VOCAB, "hasPriceSnapshot");
        this.observedItem = model.createProperty(VOCAB, "observedItem");
        this.observedOnPlatform = model.createProperty(VOCAB, "observedOnPlatform");
        this.observedAt = model.createProperty(VOCAB, "observedAt");
        this.sellPriceCny = model.createProperty(VOCAB, "sellPriceCny");
        this.sellCount = model.createProperty(VOCAB, "sellCount");
        this.bidPriceCny = model.createProperty(VOCAB, "bidPriceCny");
        this.bidCount = model.createProperty(VOCAB, "bidCount");
        this.hasValidSellPrice = model.createProperty(VOCAB, "hasValidSellPrice");
        this.sourceFile = model.createProperty(VOCAB, "sourceFile");
    }

    public static void main(String[] args) throws Exception {
        Path projectDir = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path datasetsDir = projectDir.resolve("datasets");
        Path outputDir = projectDir.resolve("output");
        Path queriesDir = projectDir.resolve("queries");
        Path turtleFile = outputDir.resolve("yyyp-market.ttl");

        Files.createDirectories(outputDir);

        IntegratedData data = loadData(datasetsDir);
        Model model = ModelFactory.createDefaultModel();
        Main app = new Main(model);
        app.buildGraph(model, data);

        try (OutputStream out = Files.newOutputStream(turtleFile)) {
            RDFDataMgr.write(out, model, Lang.TURTLE);
        }

        System.out.printf(
                "Loaded %d items, %d platforms, %d platform identifiers, %d price observations.%n",
                data.items.size(), data.platforms.size(), data.identifiers.size(), data.prices.size()
        );
        System.out.printf("Generated RDF graph: %s (%d triples)%n", turtleFile.toAbsolutePath(), model.size());

        if (Files.isDirectory(queriesDir)) {
            runQueries(model, queriesDir, outputDir.resolve("query-results"));
        }
    }

    private static IntegratedData loadData(Path datasetsDir) throws IOException {
        List<ItemRecord> items = new ArrayList<>();
        for (CSVRecord row : parseCsv(datasetsDir.resolve("items.csv"))) {
            items.add(new ItemRecord(
                    row.get("local_item_id"),
                    row.get("market_hash_name"),
                    row.get("chinese_name"),
                    parseInt(row.get("tier")).orElse(0),
                    "1".equals(row.get("enabled")) || "true".equalsIgnoreCase(row.get("enabled"))
            ));
        }

        List<PlatformRecord> platforms = new ArrayList<>();
        for (CSVRecord row : parseCsv(datasetsDir.resolve("platforms.csv"))) {
            platforms.add(new PlatformRecord(row.get("platform"), row.get("display_name")));
        }

        List<IdentifierRecord> identifiers = new ArrayList<>();
        for (CSVRecord row : parseCsv(datasetsDir.resolve("platform_mappings.csv"))) {
            identifiers.add(new IdentifierRecord(
                    row.get("local_item_id"),
                    row.get("platform"),
                    row.get("platform_item_id")
            ));
        }

        List<PriceRecord> prices = new ArrayList<>();
        Path pricesDir = datasetsDir.resolve("prices");
        try (Stream<Path> files = Files.list(pricesDir)) {
            List<Path> priceFiles = files
                    .filter(path -> path.getFileName().toString().endsWith("_prices.csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
            for (Path file : priceFiles) {
                String platform = file.getFileName().toString()
                        .replace("_prices.csv", "")
                        .toUpperCase(Locale.ROOT);
                for (CSVRecord row : parseCsv(file)) {
                    prices.add(new PriceRecord(
                            platform,
                            blankToNull(row.get("platform_item_id")),
                            row.get("market_hash_name"),
                            row.get("observed_at"),
                            parseDecimal(row.get("sell_price_cny")).orElse(BigDecimal.ZERO),
                            parseInt(row.get("sell_count")).orElse(0),
                            parseDecimal(row.get("bid_price_cny")).orElse(BigDecimal.ZERO),
                            parseInt(row.get("bid_count")).orElse(0),
                            file.getFileName().toString()
                    ));
                }
            }
        }

        return new IntegratedData(items, platforms, identifiers, prices);
    }

    private void buildGraph(Model model, IntegratedData data) {
        model.setNsPrefix("yyyp", VOCAB);
        model.setNsPrefix("res", RES);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("rdfs", RDFS.uri);
        model.setNsPrefix("xsd", XSDDatatype.XSD + "#");

        Resource itemClass = vocabResource(model, "Item", "CS2 market item");
        Resource platformClass = vocabResource(model, "MarketPlatform", "Market platform");
        Resource identifierClass = vocabResource(model, "PlatformIdentifier", "Platform-specific item identifier");
        Resource snapshotClass = vocabResource(model, "PriceSnapshot", "Latest price observation");
        Resource weaponClass = vocabResource(model, "WeaponType", "Weapon type");
        Resource wearClass = vocabResource(model, "WearCondition", "Wear condition");

        Map<String, Resource> itemsByLocalId = new HashMap<>();
        Map<String, Resource> itemsByMarketHash = new HashMap<>();
        Map<String, Resource> platformsByCode = new HashMap<>();
        Map<String, Resource> itemsByPlatformIdentifier = new HashMap<>();

        for (PlatformRecord platform : data.platforms) {
            Resource platformResource = resource(model, "platform/" + safe(platform.code));
            platformResource.addProperty(RDF.type, platformClass);
            platformResource.addProperty(RDFS.label, platform.displayName);
            platformResource.addLiteral(model.createProperty(VOCAB, "platformCode"), platform.code);
            platformsByCode.put(platform.code, platformResource);
        }

        for (ItemRecord item : data.items) {
            Resource itemResource = resource(model, "item/" + item.localItemId);
            itemResource.addProperty(RDF.type, itemClass);
            itemResource.addProperty(RDFS.label, item.marketHashName);
            itemResource.addLiteral(localItemId, integerLiteral(model, item.localItemId));
            itemResource.addLiteral(marketHashName, item.marketHashName);
            if (item.chineseName != null && !item.chineseName.isBlank()) {
                itemResource.addLiteral(chineseName, item.chineseName);
            }
            itemResource.addLiteral(tier, item.tier);
            itemResource.addLiteral(enabled, item.enabled);
            itemResource.addLiteral(statTrak, item.marketHashName.contains("StatTrak"));
            itemResource.addLiteral(souvenir, item.marketHashName.contains("Souvenir"));

            String weapon = parseWeaponType(item.marketHashName);
            if (!weapon.isBlank()) {
                Resource weaponResource = resource(model, "weapon/" + safe(weapon));
                weaponResource.addProperty(RDF.type, weaponClass);
                weaponResource.addProperty(RDFS.label, weapon);
                itemResource.addProperty(weaponType, weaponResource);
            }

            parseSkinName(item.marketHashName).ifPresent(value -> itemResource.addLiteral(skinName, value));

            parseWearCondition(item.marketHashName).ifPresent(value -> {
                Resource wearResource = resource(model, "wear/" + safe(value));
                wearResource.addProperty(RDF.type, wearClass);
                wearResource.addProperty(RDFS.label, value);
                itemResource.addProperty(wearCondition, wearResource);
            });

            itemsByLocalId.put(item.localItemId, itemResource);
            itemsByMarketHash.put(item.marketHashName, itemResource);
        }

        for (IdentifierRecord identifier : data.identifiers) {
            Resource item = itemsByLocalId.get(identifier.localItemId);
            Resource platform = platformsByCode.get(identifier.platform);
            if (item == null || platform == null || identifier.platformItemId == null || identifier.platformItemId.isBlank()) {
                continue;
            }
            Resource identifierResource = resource(
                    model,
                    "identifier/" + safe(identifier.platform) + "/" + safe(identifier.platformItemId)
            );
            identifierResource.addProperty(RDF.type, identifierClass);
            identifierResource.addProperty(identifierPlatform, platform);
            identifierResource.addLiteral(identifierValue, identifier.platformItemId);
            identifierResource.addProperty(model.createProperty(VOCAB, "identifiesItem"), item);
            item.addProperty(hasPlatformIdentifier, identifierResource);
            itemsByPlatformIdentifier.put(identifierKey(identifier.platform, identifier.platformItemId), item);
        }

        for (PriceRecord price : data.prices) {
            Resource platform = platformsByCode.get(price.platform);
            Resource item = resolveItem(price, itemsByPlatformIdentifier, itemsByMarketHash);
            if (platform == null || item == null) {
                continue;
            }

            String itemId = item.getProperty(localItemId).getObject().asLiteral().getString();
            Resource snapshot = resource(model, "snapshot/" + safe(price.platform) + "/" + safe(itemId));
            snapshot.addProperty(RDF.type, snapshotClass);
            snapshot.addProperty(observedItem, item);
            snapshot.addProperty(observedOnPlatform, platform);
            snapshot.addLiteral(observedAt, typedDateTime(model, normalizeDateTime(price.observedAt)));
            snapshot.addLiteral(sellPriceCny, decimalLiteral(model, price.sellPriceCny));
            snapshot.addLiteral(sellCount, price.sellCount);
            snapshot.addLiteral(hasValidSellPrice, price.sellPriceCny.compareTo(BigDecimal.ZERO) > 0);
            snapshot.addLiteral(sourceFile, price.sourceFile);
            if (price.bidPriceCny.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.addLiteral(bidPriceCny, decimalLiteral(model, price.bidPriceCny));
            }
            if (price.bidCount > 0) {
                snapshot.addLiteral(bidCount, price.bidCount);
            }

            item.addProperty(hasPriceSnapshot, snapshot);
            platform.addProperty(model.createProperty(VOCAB, "hasObservation"), snapshot);
        }
    }

    private static void runQueries(Model model, Path queriesDir, Path resultsDir) throws IOException {
        Files.createDirectories(resultsDir);
        List<Path> queryFiles;
        try (Stream<Path> files = Files.list(queriesDir)) {
            queryFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".rq"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        for (Path queryFile : queryFiles) {
            Query query = QueryFactory.read(queryFile.toString());
            Path resultFile = resultsDir.resolve(queryFile.getFileName().toString().replace(".rq", ".csv"));
            try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model);
                 OutputStream out = Files.newOutputStream(resultFile)) {
                if (query.isSelectType()) {
                    ResultSetFormatter.outputAsCSV(out, queryExecution.execSelect());
                } else if (query.isAskType()) {
                    out.write(Boolean.toString(queryExecution.execAsk()).getBytes(StandardCharsets.UTF_8));
                } else if (query.isConstructType()) {
                    RDFDataMgr.write(out, queryExecution.execConstruct(), Lang.TURTLE);
                } else if (query.isDescribeType()) {
                    RDFDataMgr.write(out, queryExecution.execDescribe(), Lang.TURTLE);
                }
            }
            System.out.printf("Executed query: %s -> %s%n", queryFile.getFileName(), resultFile.toAbsolutePath());
        }
    }

    private static List<CSVRecord> parseCsv(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            return parser.getRecords();
        }
    }

    private static Resource resolveItem(
            PriceRecord price,
            Map<String, Resource> itemsByPlatformIdentifier,
            Map<String, Resource> itemsByMarketHash
    ) {
        if (price.platformItemId != null && !price.platformItemId.isBlank()) {
            Resource byIdentifier = itemsByPlatformIdentifier.get(identifierKey(price.platform, price.platformItemId));
            if (byIdentifier != null) {
                return byIdentifier;
            }
        }
        return itemsByMarketHash.get(price.marketHashName);
    }

    private Resource vocabResource(Model model, String localName, String label) {
        Resource resource = model.createResource(VOCAB + localName);
        resource.addProperty(RDF.type, RDFS.Class);
        resource.addProperty(RDFS.label, label);
        return resource;
    }

    private Resource resource(Model model, String path) {
        return model.createResource(RES + path);
    }

    private static String identifierKey(String platform, String platformItemId) {
        return platform + "::" + platformItemId;
    }

    private static String safe(String value) {
        String cleaned = value == null ? "unknown" : value
                .replace("™", "TM")
                .replace("★", "star")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    private static String parseWeaponType(String marketHashName) {
        String weapon = marketHashName.split("\\|", 2)[0].trim();
        weapon = weapon.replace("★", "")
                .replace("StatTrak™", "")
                .replace("Souvenir", "")
                .trim();
        return weapon;
    }

    private static Optional<String> parseSkinName(String marketHashName) {
        String[] parts = marketHashName.split("\\|", 2);
        if (parts.length < 2) {
            return Optional.empty();
        }
        String value = WEAR_PATTERN.matcher(parts[1]).replaceFirst("").trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<String> parseWearCondition(String marketHashName) {
        Matcher matcher = WEAR_PATTERN.matcher(marketHashName);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String normalizeDateTime(String value) {
        LocalDateTime timestamp = LocalDateTime.parse(value, DB_TIMESTAMP_FORMAT);
        return timestamp.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static Literal typedDateTime(Model model, String value) {
        return model.createTypedLiteral(value, XSDDatatype.XSDdateTime);
    }

    private static Literal decimalLiteral(Model model, BigDecimal value) {
        return model.createTypedLiteral(value);
    }

    private static Literal integerLiteral(Model model, String value) {
        return model.createTypedLiteral(Integer.parseInt(value));
    }

    private static Optional<Integer> parseInt(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return Optional.empty();
        }
        try {
            return Optional.of((int) Double.parseDouble(cleaned));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> parseDecimal(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record IntegratedData(
            List<ItemRecord> items,
            List<PlatformRecord> platforms,
            List<IdentifierRecord> identifiers,
            List<PriceRecord> prices
    ) {
    }

    private record ItemRecord(
            String localItemId,
            String marketHashName,
            String chineseName,
            int tier,
            boolean enabled
    ) {
    }

    private record PlatformRecord(String code, String displayName) {
    }

    private record IdentifierRecord(String localItemId, String platform, String platformItemId) {
    }

    private record PriceRecord(
            String platform,
            String platformItemId,
            String marketHashName,
            String observedAt,
            BigDecimal sellPriceCny,
            int sellCount,
            BigDecimal bidPriceCny,
            int bidCount,
            String sourceFile
    ) {
    }
}
