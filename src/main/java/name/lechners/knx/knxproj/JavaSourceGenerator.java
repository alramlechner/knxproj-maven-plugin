package name.lechners.knx.knxproj;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a Java source file containing Calimero {@code GroupAddress} constants
 * from a list of parsed KNX group address entries.
 *
 * <p>The generated class uses one nested {@code static final} inner class per
 * Hauptgruppe. This allows concise usage like:
 * <pre>{@code
 *   process.send(KNXGroupAddresses.Licht.WOHNZIMMER_EIN_AUS, new DPT1Value(true));
 * }</pre>
 * and selective imports via:
 * <pre>{@code
 *   import static com.example.KNXGroupAddresses.Licht.*;
 * }</pre>
 */
public class JavaSourceGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Generates the complete Java source file content.
     *
     * @param entries           parsed group address entries
     * @param packageName       Java package of the generated class
     * @param className         simple class name of the generated class
     * @param groupAddressClass fully-qualified class name of the GroupAddress type to use
     * @param sourceFile        original .knxproj filename (used in Javadoc)
     * @param generatedAt       timestamp for the generated-at comment
     * @return Java source code as a string
     */
    public String generate(List<GroupAddressEntry> entries,
                           String packageName,
                           String className,
                           String groupAddressClass,
                           String sourceFile,
                           LocalDateTime generatedAt) {

        String simpleGaClass = simpleClassName(groupAddressClass);

        // Group entries by Hauptgruppe, preserving insertion order
        Map<String, List<GroupAddressEntry>> byHauptgruppe = new LinkedHashMap<>();
        for (GroupAddressEntry e : entries) {
            byHauptgruppe.computeIfAbsent(e.getHauptgruppe(), k -> new ArrayList<>()).add(e);
        }

        StringBuilder sb = new StringBuilder(64 * 1024);

        // ── Package & imports ──────────────────────────────────────────────────
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import ").append(groupAddressClass).append(";\n");
        sb.append("import java.util.Collections;\n");
        sb.append("import java.util.LinkedHashMap;\n");
        sb.append("import java.util.Map;\n\n\n");

        // ── Class Javadoc ──────────────────────────────────────────────────────
        sb.append("/**\n");
        sb.append(" * KNX Group Addresses.\n");
        sb.append(" *\n");
        sb.append(" * <p>Generated from {@code ").append(escapeHtml(sourceFile)).append("}\n");
        sb.append(" * on ").append(generatedAt.format(TIMESTAMP_FMT)).append(".\n");
        sb.append(" *\n");
        sb.append(" * <p><b>Do not modify</b> – re-generate via the knxproj-maven-plugin.\n");
        sb.append(" *\n");
        sb.append(" * <p>Total group addresses: ").append(entries.size()).append("\n");
        sb.append(" */\n");
        sb.append("@SuppressWarnings(\"unused\")\n");
        sb.append("public final class ").append(className).append(" {\n\n");
        sb.append("    private ").append(className).append("() {}\n");

        // ── One nested class per Hauptgruppe ───────────────────────────────────
        for (Map.Entry<String, List<GroupAddressEntry>> groupEntry : byHauptgruppe.entrySet()) {
            String hauptgruppe      = groupEntry.getKey();
            List<GroupAddressEntry> groupAddresses = groupEntry.getValue();

            String nestedClassName = toJavaClassName(hauptgruppe.isEmpty() ? "Default" : hauptgruppe);

            sb.append("\n");
            sb.append("    // ").append("=".repeat(73)).append("\n");
            sb.append("    // ").append(hauptgruppe).append("\n");
            sb.append("    // ").append("=".repeat(73)).append("\n\n");
            sb.append("    /** Group: ").append(escapeHtml(hauptgruppe)).append(" */\n");
            sb.append("    public static final class ").append(nestedClassName).append(" {\n\n");
            sb.append("        private ").append(nestedClassName).append("() {}\n");

            // Track used identifiers within this class to detect collisions
            Map<String, Integer> usedNames = new HashMap<>();
            String currentMittelgruppe = null;

            // Ordered map of identifier → DPT for the DATAPOINT_TYPES map at the end
            Map<String, String> dptEntries = new LinkedHashMap<>();

            for (GroupAddressEntry ga : groupAddresses) {
                // Mittelgruppe separator comment
                String mg = ga.getMittelgruppe();
                if (!mg.equals(currentMittelgruppe)) {
                    currentMittelgruppe = mg;
                    if (!mg.isEmpty()) {
                        sb.append("\n        // --- ").append(mg).append(" ")
                          .append("-".repeat(Math.max(0, 60 - mg.length()))).append("\n");
                    }
                }

                String identifier = toJavaConstantName(ga.getName());
                // Resolve collision: append raw address suffix
                if (usedNames.containsKey(identifier)) {
                    identifier = identifier + "_" + ga.getRawAddress();
                }
                usedNames.put(identifier, ga.getRawAddress());

                // Javadoc: name · address · datapoint type (no free-text description fields)
                sb.append("\n        /** ").append(escapeHtml(ga.getName()))
                  .append(" · ").append(ga.getFormattedAddress());
                if (!ga.getDatapointType().isEmpty()) {
                    sb.append(" · ").append(escapeHtml(ga.getDatapointType()));
                }
                sb.append(" */\n");

                // Constant declaration – 3-level constructor: new GroupAddress(main, middle, sub)
                int addr   = ga.getRawAddress();
                int main   = (addr >> 11) & 0x1F;
                int middle = (addr >>  8) & 0x07;
                int sub    =  addr        & 0xFF;

                sb.append("        public static final ")
                  .append(simpleGaClass).append(" ")
                  .append(identifier)
                  .append(" = new ").append(simpleGaClass)
                  .append("(").append(main).append(", ")
                  .append(middle).append(", ")
                  .append(sub).append(");\n");

                // Companion DPT constant (only when a datapoint type is defined in ETS)
                String dpt = ga.getDatapointType();
                if (!dpt.isEmpty()) {
                    sb.append("        /** Datapoint type for {@link #").append(identifier).append("} */\n");
                    sb.append("        public static final String ").append(identifier)
                      .append("_DPT = \"").append(dpt).append("\";\n");
                    dptEntries.put(identifier, dpt);
                }
            }

            // Static DATAPOINT_TYPES map for runtime lookup (address → DPT string)
            sb.append("\n        /**\n");
            sb.append("         * Maps every group address constant in this group to its ETS datapoint type string.\n");
            sb.append("         * Contains only entries for which a datapoint type was defined in the ETS project.\n");
            sb.append("         * Example lookup: {@code String dpt = ").append(nestedClassName)
              .append(".DATAPOINT_TYPES.get(").append(nestedClassName).append(".SOME_ADDRESS);}\n");
            sb.append("         */\n");
            sb.append("        public static final Map<").append(simpleGaClass)
              .append(", String> DATAPOINT_TYPES;\n");
            sb.append("        static {\n");
            sb.append("            Map<").append(simpleGaClass)
              .append(", String> m = new LinkedHashMap<>();\n");
            for (Map.Entry<String, String> e : dptEntries.entrySet()) {
                sb.append("            m.put(").append(e.getKey())
                  .append(", \"").append(e.getValue()).append("\");\n");
            }
            sb.append("            DATAPOINT_TYPES = Collections.unmodifiableMap(m);\n");
            sb.append("        }\n");

            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── Name conversion utilities ──────────────────────────────────────────────

    /**
     * Converts a human-readable KNX name to a SCREAMING_SNAKE_CASE Java constant identifier.
     * German umlauts are transliterated (ä→AE, ö→OE, ü→UE, ß→SS).
     */
    static String toJavaConstantName(String name) {
        String result = normalize(name).toUpperCase();
        return result.isEmpty() ? "_UNNAMED" : result;
    }

    /**
     * Converts a human-readable KNX group name to a PascalCase Java class identifier.
     */
    static String toJavaClassName(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) return "Default";
        // PascalCase: capitalize first letter of each word segment
        String[] parts = normalized.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        String result = sb.toString();
        if (Character.isDigit(result.charAt(0))) result = "G" + result;
        return result;
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) return "";

        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case 'ä': sb.append("ae"); break;
                case 'Ä': sb.append("Ae"); break;
                case 'ö': sb.append("oe"); break;
                case 'Ö': sb.append("Oe"); break;
                case 'ü': sb.append("ue"); break;
                case 'Ü': sb.append("Ue"); break;
                case 'ß': sb.append("ss"); break;
                default:
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    } else {
                        // Replace any non-alphanumeric character with underscore separator
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                            sb.append('_');
                        }
                    }
            }
        }

        // Strip leading/trailing underscores
        String result = sb.toString().replaceAll("_+", "_");
        while (result.startsWith("_")) result = result.substring(1);
        while (result.endsWith("_"))   result = result.substring(0, result.length() - 1);

        // Ensure the identifier doesn't start with a digit
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }
        return result;
    }

    private static String simpleClassName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
