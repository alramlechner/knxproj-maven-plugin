package name.lechners.knx.knxproj;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses a .knxproj file (ZIP archive) and extracts all KNX group addresses.
 *
 * <p>The .knxproj format is a ZIP file containing an ETS project XML at
 * {@code P-XXXX/0.xml}. Group addresses are found as {@code <GroupAddress>}
 * elements nested inside {@code <GroupRange>} elements (up to two levels deep).
 */
public class KnxProjectParser {

    /**
     * Parses the given .knxproj file and returns all group addresses.
     *
     * @param knxprojFile path to the .knxproj file
     * @return list of group address entries, sorted by raw address
     * @throws Exception if the file cannot be read or parsed
     */
    public List<GroupAddressEntry> parse(File knxprojFile) throws Exception {
        // ISO_8859_1 accepts all byte values (0x00–0xFF) without throwing,
        // which avoids failures when ETS uses CP437/Windows-1252 for entry names.
        Charset entryNameCharset = Charset.forName("ISO-8859-1");
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(knxprojFile)), entryNameCharset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.matches("P-[^/]+/0\\.xml")) {
                    // Read the full entry into memory before the stream is
                    // closed/advanced, then parse from a ByteArrayInputStream.
                    byte[] bytes = zis.readAllBytes();
                    return parseXml(new ByteArrayInputStream(bytes));
                }
            }
        }
        throw new IOException("No project XML (P-XXXX/0.xml) found in " + knxprojFile.getName());
    }

    private List<GroupAddressEntry> parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity processing (security hardening)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(is);

        List<GroupAddressEntry> result = new ArrayList<>();
        NodeList gaNodes = doc.getElementsByTagNameNS("*", "GroupAddress");

        for (int i = 0; i < gaNodes.getLength(); i++) {
            Element ga = (Element) gaNodes.item(i);

            int    rawAddress    = Integer.parseInt(ga.getAttribute("Address"));
            String name          = ga.getAttribute("Name");
            String datapointType = ga.getAttribute("DatapointType");
            String comment       = ga.getAttribute("Comment");

            String hauptgruppe  = "";
            String mittelgruppe = "";

            Node parent      = ga.getParentNode();
            Node grandParent = (parent != null) ? parent.getParentNode() : null;

            if (isGroupRange(parent)) {
                Element pe = (Element) parent;
                if (isGroupRange(grandParent)) {
                    // 3-level: grandParent = Hauptgruppe, parent = Mittelgruppe
                    hauptgruppe  = ((Element) grandParent).getAttribute("Name");
                    mittelgruppe = pe.getAttribute("Name");
                } else {
                    // 2-level: parent = Hauptgruppe, no Mittelgruppe
                    hauptgruppe = pe.getAttribute("Name");
                }
            }

            result.add(new GroupAddressEntry(
                    rawAddress, name, hauptgruppe, mittelgruppe, datapointType, comment));
        }

        result.sort((a, b) -> Integer.compare(a.getRawAddress(), b.getRawAddress()));
        return result;
    }

    private static boolean isGroupRange(Node node) {
        return node instanceof Element && "GroupRange".equals(node.getLocalName());
    }
}
