package name.lechners.knx.knxproj;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a Java source file containing {@code KnxDatapoint} constants
 * (wrapping a Calimero {@code GroupAddress} and a {@code DptId}) from a list
 * of parsed KNX group address entries.
 *
 * <p>The generated class uses one nested {@code static final} inner class per
 * Hauptgruppe. Each constant is of the generated {@code KnxDatapoint} record
 * type, which combines the group address with the ETS datapoint type.
 *
 * <pre>{@code
 *   // Access address and DPT in one object:
 *   KnxDatapoint dp = KNXGroupAddresses.Licht.WOHNZIMMER_EIN_AUS;
 *   process.send(dp.address(), new DPT1Value(true));
 *   DptId dpt = dp.dpt();  // e.g. DptId[mainNumber=1, subNumber=1]
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
     * @param dptIdClass        fully-qualified class name of the DptId type to use
     * @param sourceFile        original .knxproj filename (used in Javadoc)
     * @param generatedAt       timestamp for the generated-at comment
     * @return Java source code as a string
     */
    public String generate(List<GroupAddressEntry> entries,
                           String packageName,
                           String className,
                           String groupAddressClass,
                           String dptIdClass,
                           String sourceFile,
                           LocalDateTime generatedAt) {

        String simpleGaClass    = simpleClassName(groupAddressClass);
        String simpleDptIdClass = simpleClassName(dptIdClass);

        // Group entries by Hauptgruppe, preserving insertion order
        Map<String, List<GroupAddressEntry>> byHauptgruppe = new LinkedHashMap<>();
        for (GroupAddressEntry e : entries) {
            byHauptgruppe.computeIfAbsent(e.getHauptgruppe(), k -> new ArrayList<>()).add(e);
        }

        StringBuilder sb = new StringBuilder(64 * 1024);

        // ── Package & imports ──────────────────────────────────────────────────
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import ").append(groupAddressClass).append(";\n");
        sb.append("import ").append(dptIdClass).append(";\n");
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
        sb.append("    private ").append(className).append("() {}\n\n");

        // ── KnxDatapoint record (generated once at the top of the class) ───────
        sb.append("    /**\n");
        sb.append("     * Combines a KNX group address with its ETS datapoint type.\n");
        sb.append("     *\n");
        sb.append("     * @param address the KNX group address\n");
        sb.append("     * @param dpt     the ETS datapoint type, or {@code null} if not defined\n");
        sb.append("     */\n");
        sb.append("    public record KnxDatapoint(")
          .append(simpleGaClass).append(" address, ")
          .append(simpleDptIdClass).append(" dpt) {}\n");

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

            Map<String, Integer> usedNames     = new HashMap<>();
            List<String>         allIdentifiers = new ArrayList<>(); // ordered, for BY_ADDRESS map
            String currentMittelgruppe = null;

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
                if (usedNames.containsKey(identifier)) {
                    identifier = identifier + "_" + ga.getRawAddress();
                }
                usedNames.put(identifier, ga.getRawAddress());
                allIdentifiers.add(identifier);

                // Javadoc: name · address · datapoint type
                sb.append("\n        /** ").append(escapeHtml(ga.getName()))
                  .append(" · ").append(ga.getFormattedAddress());
                if (!ga.getDatapointType().isEmpty()) {
                    sb.append(" · ").append(escapeHtml(ga.getDatapointType()));
                }
                sb.append(" */\n");

                // Address bit decomposition
                int addr   = ga.getRawAddress();
                int main   = (addr >> 11) & 0x1F;
                int middle = (addr >>  8) & 0x07;
                int sub    =  addr        & 0xFF;

                // DptId argument – new DptId(main, sub) or null
                int[] dptParts = parseDptId(ga.getDatapointType());
                String dptArg = dptParts != null
                        ? "new " + simpleDptIdClass + "(" + dptParts[0] + ", " + dptParts[1] + ")"
                        : "null";

                sb.append("        public static final KnxDatapoint ")
                  .append(identifier)
                  .append(" = new KnxDatapoint(new ").append(simpleGaClass)
                  .append("(").append(main).append(", ").append(middle).append(", ").append(sub).append(")")
                  .append(", ").append(dptArg).append(");\n");
            }

            // ── BY_ADDRESS: GroupAddress → KnxDatapoint, for runtime lookup ───
            sb.append("\n        /**\n");
            sb.append("         * Maps every group address in this group to its {@link KnxDatapoint}.\n");
            sb.append("         * Useful for runtime lookup when only the raw address is known.\n");
            sb.append("         * <pre>KnxDatapoint dp = ")
              .append(nestedClassName).append(".BY_ADDRESS.get(receivedAddress);</pre>\n");
            sb.append("         */\n");
            sb.append("        public static final Map<").append(simpleGaClass)
              .append(", KnxDatapoint> BY_ADDRESS;\n");
            sb.append("        static {\n");
            sb.append("            Map<").append(simpleGaClass)
              .append(", KnxDatapoint> m = new LinkedHashMap<>();\n");
            for (String id : allIdentifiers) {
                sb.append("            m.put(").append(id).append(".address(), ").append(id).append(");\n");
            }
            sb.append("            BY_ADDRESS = Collections.unmodifiableMap(m);\n");
            sb.append("        }\n");

            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Parses an ETS datapoint type string into {@code [mainNumber, subNumber]}.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code DPT-1}    → {@code [1, 0]}
     *   <li>{@code DPST-1-1} → {@code [1, 1]}
     * </ul>
     *
     * @param dpt ETS datapoint type string, may be empty or null
     * @return two-element array {@code [main, sub]}, or {@code null} if not parseable
     */
    static int[] parseDptId(String dpt) {
        if (dpt == null || dpt.isEmpty()) return null;
        try {
            if (dpt.startsWith("DPST-")) {
                String[] parts = dpt.substring(5).split("-", 2);
                if (parts.length == 2) {
                    return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
                }
            } else if (dpt.startsWith("DPT-")) {
                return new int[]{ Integer.parseInt(dpt.substring(4)), 0 };
            }
        } catch (NumberFormatException ignored) { }
        return null;
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
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                            sb.append('_');
                        }
                    }
            }
        }

        String result = sb.toString().replaceAll("_+", "_");
        while (result.startsWith("_")) result = result.substring(1);
        while (result.endsWith("_"))   result = result.substring(0, result.length() - 1);

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
