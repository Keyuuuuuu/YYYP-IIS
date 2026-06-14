import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class FullGraphExporter {
    public static void main(String[] args) throws Exception {
        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("output", "yyyp-market.ttl");
        Path output = args.length > 1 ? Path.of(args[1]) : Path.of("output", "full-rdf-graph.gexf");

        Model model = RDFDataMgr.loadModel(input.toAbsolutePath().toUri().toString(), Lang.TURTLE);
        Path outputParent = output.toAbsolutePath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        Map<String, String> nodeIds = new LinkedHashMap<>();
        Map<String, String> nodeLabels = new LinkedHashMap<>();

        long edgeCount = 0;
        try (OutputStream out = Files.newOutputStream(output)) {
            XMLStreamWriter xml = XMLOutputFactory.newFactory()
                    .createXMLStreamWriter(out, StandardCharsets.UTF_8.name());

            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            xml.writeStartElement("gexf");
            xml.writeDefaultNamespace("http://www.gexf.net/1.2draft");
            xml.writeAttribute("version", "1.2");

            xml.writeStartElement("graph");
            xml.writeAttribute("mode", "static");
            xml.writeAttribute("defaultedgetype", "directed");

            Iterator<Statement> nodeScan = model.listStatements();
            while (nodeScan.hasNext()) {
                Statement statement = nodeScan.next();
                registerNode(model, nodeIds, nodeLabels, statement.getSubject());
                registerNode(model, nodeIds, nodeLabels, statement.getObject());
            }

            xml.writeStartElement("nodes");
            for (Map.Entry<String, String> entry : nodeIds.entrySet()) {
                xml.writeEmptyElement("node");
                xml.writeAttribute("id", entry.getValue());
                xml.writeAttribute("label", nodeLabels.get(entry.getKey()));
            }
            xml.writeEndElement();

            xml.writeStartElement("edges");
            Iterator<Statement> edgeScan = model.listStatements();
            while (edgeScan.hasNext()) {
                Statement statement = edgeScan.next();
                xml.writeEmptyElement("edge");
                xml.writeAttribute("id", "e" + edgeCount);
                xml.writeAttribute("source", nodeIds.get(termKey(statement.getSubject())));
                xml.writeAttribute("target", nodeIds.get(termKey(statement.getObject())));
                xml.writeAttribute("label", predicateLabel(model, statement));
                edgeCount++;
            }
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        }

        System.out.printf(
                "Exported full RDF graph to %s (%d RDF triples as edges, %d RDF terms as nodes)%n",
                output.toAbsolutePath(), edgeCount, nodeIds.size()
        );
    }

    private static void registerNode(
            Model model,
            Map<String, String> nodeIds,
            Map<String, String> nodeLabels,
            RDFNode node
    ) {
        String key = termKey(node);
        if (nodeIds.containsKey(key)) {
            return;
        }

        String id = "n" + nodeIds.size();
        nodeIds.put(key, id);
        nodeLabels.put(key, displayLabel(labelFor(model, node), 140));
    }

    private static String termKey(RDFNode node) {
        if (node.isURIResource()) {
            return "U:" + node.asResource().getURI();
        }
        if (node.isAnon()) {
            return "B:" + node.asResource().getId().getLabelString();
        }

        Literal literal = node.asLiteral();
        String datatype = literal.getDatatypeURI() == null ? "" : literal.getDatatypeURI();
        return "L:" + literal.getLexicalForm() + "^^" + datatype + "@" + literal.getLanguage();
    }

    private static String labelFor(Model model, RDFNode node) {
        if (node.isLiteral()) {
            return node.asLiteral().getLexicalForm();
        }

        Resource resource = node.asResource();
        if (resource.isURIResource()) {
            String qname = model.qnameFor(resource.getURI());
            if (qname != null) {
                return qname;
            }

            String localName = resource.getLocalName();
            if (localName != null && !localName.isBlank()) {
                return localName;
            }
            return resource.getURI();
        }

        return "_:" + resource.getId().getLabelString();
    }

    private static String predicateLabel(Model model, Statement statement) {
        String qname = model.qnameFor(statement.getPredicate().getURI());
        return displayLabel(qname != null ? qname : statement.getPredicate().getURI(), 140);
    }

    private static String displayLabel(String value, int maxCodePoints) {
        if (value == null) {
            return "";
        }

        int totalCodePoints = value.codePointCount(0, value.length());
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints()
                .filter(FullGraphExporter::isXmlCharacter)
                .limit(maxCodePoints)
                .forEach(cleaned::appendCodePoint);

        if (totalCodePoints > maxCodePoints) {
            cleaned.append("...");
        }
        return cleaned.toString();
    }

    private static boolean isXmlCharacter(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }
}
