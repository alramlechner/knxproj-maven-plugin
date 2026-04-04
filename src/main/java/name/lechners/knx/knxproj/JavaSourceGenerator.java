package name.lechners.knx.knxproj;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Generates a Java source file containing {@code KnxDatapoint} constants
 * (wrapping a Calimero {@code GroupAddress} and a {@code DPT}) from a list
 * of parsed KNX group address entries.
 *
 * <p>For every group address whose ETS datapoint type maps to a known Calimero
 * constant (e.g. {@code DPTXlatorBoolean.DPT_SWITCH}), the generated code
 * references that constant directly.  Unknown–but–defined datapoint types fall
 * back to {@code new DPT("X.XXX", "", "", "")}.  Addresses without any
 * datapoint type get {@code null}.
 *
 * <pre>{@code
 *   KnxDatapoint dp = KNXGroupAddresses.Licht.WOHNZIMMER_EIN_AUS;
 *   process.send(dp.address(), new DPT1Value(true));
 *   DPT dpt = dp.dpt();                    // DPTXlatorBoolean.DPT_SWITCH
 *   String unit = dpt.getUnit();           // ""
 *   String desc = dpt.getDescription();    // "Switch"
 * }</pre>
 */
public class JavaSourceGenerator {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Built-in DPT constant mapping ─────────────────────────────────────────
    //
    // Key:   normalised DPT ID, e.g. "1.001"  (see normalizeDptId())
    // Value: { simpleTranslatorClassName, publicConstantName }
    //
    // Only includes constants that carry DPT_ prefix (stable public API).
    // Entries are intentionally conservative – if a constant is uncertain it is
    // omitted so the fallback constructor is used instead of a compile error.

    private static final Map<String, String[]> DPT_CONSTANT_MAP;
    static {
        Map<String, String[]> m = new HashMap<>();

        // ── Main type 1 – Boolean (DPTXlatorBoolean) ──────────────────────────
        m.put("1.001", new String[]{"DPTXlatorBoolean", "DPT_SWITCH"});
        m.put("1.002", new String[]{"DPTXlatorBoolean", "DPT_BOOL"});
        m.put("1.003", new String[]{"DPTXlatorBoolean", "DPT_ENABLE"});
        m.put("1.004", new String[]{"DPTXlatorBoolean", "DPT_RAMP"});
        m.put("1.005", new String[]{"DPTXlatorBoolean", "DPT_ALARM"});
        m.put("1.006", new String[]{"DPTXlatorBoolean", "DPT_BINARYVALUE"});
        m.put("1.007", new String[]{"DPTXlatorBoolean", "DPT_STEP"});
        m.put("1.008", new String[]{"DPTXlatorBoolean", "DPT_UPDOWN"});
        m.put("1.009", new String[]{"DPTXlatorBoolean", "DPT_OPENCLOSE"});
        m.put("1.010", new String[]{"DPTXlatorBoolean", "DPT_START"});
        m.put("1.011", new String[]{"DPTXlatorBoolean", "DPT_STATE"});
        m.put("1.012", new String[]{"DPTXlatorBoolean", "DPT_INVERT"});
        m.put("1.013", new String[]{"DPTXlatorBoolean", "DPT_DIMSENDSTYLE"});
        m.put("1.014", new String[]{"DPTXlatorBoolean", "DPT_INPUTSOURCE"});
        m.put("1.015", new String[]{"DPTXlatorBoolean", "DPT_RESET"});
        m.put("1.016", new String[]{"DPTXlatorBoolean", "DPT_ACK"});
        m.put("1.017", new String[]{"DPTXlatorBoolean", "DPT_TRIGGER"});
        m.put("1.018", new String[]{"DPTXlatorBoolean", "DPT_OCCUPANCY"});
        m.put("1.019", new String[]{"DPTXlatorBoolean", "DPT_WINDOW_DOOR"});
        m.put("1.021", new String[]{"DPTXlatorBoolean", "DPT_LOGICAL_FUNCTION"});
        m.put("1.022", new String[]{"DPTXlatorBoolean", "DPT_SCENE_AB"});
        m.put("1.023", new String[]{"DPTXlatorBoolean", "DPT_SHUTTER_BLINDS_MODE"});
        m.put("1.100", new String[]{"DPTXlatorBoolean", "DPT_HEAT_COOL"});

        // ── Main type 5 – 8-bit unsigned (DPTXlator8BitUnsigned) ──────────────
        m.put("5.001", new String[]{"DPTXlator8BitUnsigned", "DPT_SCALING"});
        m.put("5.003", new String[]{"DPTXlator8BitUnsigned", "DPT_ANGLE"});
        m.put("5.004", new String[]{"DPTXlator8BitUnsigned", "DPT_PERCENT_U8"});
        m.put("5.005", new String[]{"DPTXlator8BitUnsigned", "DPT_DECIMALFACTOR"});
        m.put("5.006", new String[]{"DPTXlator8BitUnsigned", "DPT_TARIFF"});
        m.put("5.010", new String[]{"DPTXlator8BitUnsigned", "DPT_VALUE_1_UCOUNT"});

        // ── Main type 6 – 8-bit signed (DPTXlator8BitSigned) ──────────────────
        m.put("6.001", new String[]{"DPTXlator8BitSigned", "DPT_PERCENT_V8"});
        m.put("6.010", new String[]{"DPTXlator8BitSigned", "DPT_VALUE_1_COUNT"});
        m.put("6.020", new String[]{"DPTXlator8BitSigned", "DPT_STATUS_MODE3"});

        // ── Main type 7 – 2-byte unsigned (DPTXlator2ByteUnsigned) ───────────
        m.put("7.001", new String[]{"DPTXlator2ByteUnsigned", "DPT_VALUE_2_UCOUNT"});
        m.put("7.002", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD"});
        m.put("7.003", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD_10"});
        m.put("7.004", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD_100"});
        m.put("7.005", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD_SEC"});
        m.put("7.006", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD_MIN"});
        m.put("7.007", new String[]{"DPTXlator2ByteUnsigned", "DPT_TIMEPERIOD_HOURS"});
        m.put("7.010", new String[]{"DPTXlator2ByteUnsigned", "DPT_PROP_DATATYPE"});
        m.put("7.011", new String[]{"DPTXlator2ByteUnsigned", "DPT_LENGTH_MM"});
        m.put("7.012", new String[]{"DPTXlator2ByteUnsigned", "DPT_ELECTRICAL_CURRENT"});
        m.put("7.013", new String[]{"DPTXlator2ByteUnsigned", "DPT_BRIGHTNESS"});
        m.put("7.600", new String[]{"DPTXlator2ByteUnsigned", "DPT_ABSOLUTE_COLOR_TEMPERATURE"});

        // ── Main type 8 – 2-byte signed (DPTXlator2ByteSigned) ───────────────
        m.put("8.001", new String[]{"DPTXlator2ByteSigned", "DPT_VALUE_2_COUNT"});
        m.put("8.002", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIMEMS"});
        m.put("8.003", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIME10MS"});
        m.put("8.004", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIME100MS"});
        m.put("8.005", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIMESEC"});
        m.put("8.006", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIMEMIN"});
        m.put("8.007", new String[]{"DPTXlator2ByteSigned", "DPT_DELTA_TIMEHRS"});
        m.put("8.010", new String[]{"DPTXlator2ByteSigned", "DPT_PERCENT_V16"});
        m.put("8.011", new String[]{"DPTXlator2ByteSigned", "DPT_ROTATION_ANGLE"});

        // ── Main type 9 – 2-byte float (DPTXlator2ByteFloat) ─────────────────
        m.put("9.001", new String[]{"DPTXlator2ByteFloat", "DPT_TEMPERATURE"});
        m.put("9.002", new String[]{"DPTXlator2ByteFloat", "DPT_TEMPERATURE_DIFFERENCE"});
        m.put("9.003", new String[]{"DPTXlator2ByteFloat", "DPT_TEMPERATURE_GRADIENT"});
        m.put("9.004", new String[]{"DPTXlator2ByteFloat", "DPT_INTENSITY_OF_LIGHT"});
        m.put("9.005", new String[]{"DPTXlator2ByteFloat", "DPT_WIND_SPEED"});
        m.put("9.006", new String[]{"DPTXlator2ByteFloat", "DPT_AIR_PRESSURE"});
        m.put("9.007", new String[]{"DPTXlator2ByteFloat", "DPT_HUMIDITY"});
        m.put("9.008", new String[]{"DPTXlator2ByteFloat", "DPT_AIRQUALITY"});
        m.put("9.009", new String[]{"DPTXlator2ByteFloat", "DPT_AIR_FLOW"});
        m.put("9.010", new String[]{"DPTXlator2ByteFloat", "DPT_TIME_DIFFERENCE1"});
        m.put("9.011", new String[]{"DPTXlator2ByteFloat", "DPT_TIME_DIFFERENCE2"});
        m.put("9.020", new String[]{"DPTXlator2ByteFloat", "DPT_VOLTAGE"});
        m.put("9.021", new String[]{"DPTXlator2ByteFloat", "DPT_ELECTRICAL_CURRENT"});
        m.put("9.022", new String[]{"DPTXlator2ByteFloat", "DPT_POWERDENSITY"});
        m.put("9.023", new String[]{"DPTXlator2ByteFloat", "DPT_KELVIN_PER_PERCENT"});
        m.put("9.024", new String[]{"DPTXlator2ByteFloat", "DPT_POWER"});
        m.put("9.025", new String[]{"DPTXlator2ByteFloat", "DPT_VOLUME_FLOW"});
        m.put("9.026", new String[]{"DPTXlator2ByteFloat", "DPT_RAIN_AMOUNT"});
        m.put("9.027", new String[]{"DPTXlator2ByteFloat", "DPT_TEMP_F"});
        m.put("9.028", new String[]{"DPTXlator2ByteFloat", "DPT_WIND_SPEED_KMH"});

        // ── Main type 10 – Time (DPTXlatorTime) ──────────────────────────────
        m.put("10.001", new String[]{"DPTXlatorTime", "DPT_TIMEOFDAY"});

        // ── Main type 11 – Date (DPTXlatorDate) ──────────────────────────────
        m.put("11.001", new String[]{"DPTXlatorDate", "DPT_DATE"});

        // ── Main type 16 – String (DPTXlatorString) ──────────────────────────
        m.put("16.000", new String[]{"DPTXlatorString", "DPT_STRING_ASCII"});
        m.put("16.001", new String[]{"DPTXlatorString", "DPT_STRING_8859_1"});

        // ── Main type 232 – RGB (DPTXlatorRGB) ───────────────────────────────
        m.put("232.600", new String[]{"DPTXlatorRGB", "DPT_RGB"});

        DPT_CONSTANT_MAP = Collections.unmodifiableMap(m);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates the complete Java source file content.
     *
     * @param entries           parsed group address entries
     * @param packageName       Java package of the generated class
     * @param className         simple class name of the generated class
     * @param groupAddressClass fully-qualified class name of the GroupAddress type to use
     * @param dptClass          fully-qualified class name of the DPT type to use
     * @param sourceFile        original .knxproj filename (used in Javadoc)
     * @param generatedAt       timestamp for the generated-at comment
     * @return Java source code as a string
     */
    public String generate(List<GroupAddressEntry> entries,
                           String packageName,
                           String className,
                           String groupAddressClass,
                           String dptClass,
                           String sourceFile,
                           LocalDateTime generatedAt) {

        String simpleGaClass  = simpleClassName(groupAddressClass);
        String simpleDptClass = simpleClassName(dptClass);

        // Package prefix for translator classes, e.g. "io.calimero.dptxlator."
        String xlatorPackage = dptClass.substring(0, dptClass.lastIndexOf('.') + 1);

        // Group entries by Hauptgruppe, preserving insertion order
        Map<String, List<GroupAddressEntry>> byHauptgruppe = new LinkedHashMap<>();
        for (GroupAddressEntry e : entries) {
            byHauptgruppe.computeIfAbsent(e.getHauptgruppe(), k -> new ArrayList<>()).add(e);
        }

        // ── Pass 1: collect translator classes actually needed ─────────────────
        TreeSet<String> xlatorImports = new TreeSet<>();
        for (GroupAddressEntry e : entries) {
            dptExpression(e.getDatapointType(), simpleDptClass, xlatorPackage, xlatorImports);
        }

        // ── Pass 2: generate source ────────────────────────────────────────────
        StringBuilder sb = new StringBuilder(64 * 1024);

        // Package & imports
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import ").append(groupAddressClass).append(";\n");
        sb.append("import ").append(dptClass).append(";\n");
        for (String xlatorImport : xlatorImports) {
            sb.append("import ").append(xlatorImport).append(";\n");
        }
        sb.append("import java.util.Collections;\n");
        sb.append("import java.util.LinkedHashMap;\n");
        sb.append("import java.util.Map;\n\n\n");

        // Class Javadoc
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

        // KnxDatapoint record – generated once per class
        sb.append("    /**\n");
        sb.append("     * Combines a KNX group address with its name and Calimero datapoint type.\n");
        sb.append("     *\n");
        sb.append("     * @param address the KNX group address\n");
        sb.append("     * @param name    the group address name as defined in ETS\n");
        sb.append("     * @param dpt     the Calimero {@link DPT} constant, or {@code null} if\n");
        sb.append("     *                no datapoint type was defined in ETS\n");
        sb.append("     */\n");
        sb.append("    public record KnxDatapoint(")
          .append(simpleGaClass).append(" address, ")
          .append("String name, ")
          .append(simpleDptClass).append(" dpt) {}\n");

        // One nested class per Hauptgruppe
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

            Map<String, Integer> usedNames    = new HashMap<>();
            List<String>         identifiers  = new ArrayList<>();
            String currentMittelgruppe = null;

            for (GroupAddressEntry ga : groupAddresses) {
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
                identifiers.add(identifier);

                // Javadoc: name · address · DPT (if defined)
                sb.append("\n        /** ").append(escapeHtml(ga.getName()))
                  .append(" · ").append(ga.getFormattedAddress());
                if (!ga.getDatapointType().isEmpty()) {
                    sb.append(" · ").append(escapeHtml(ga.getDatapointType()));
                }
                sb.append(" */\n");

                int addr   = ga.getRawAddress();
                int main   = (addr >> 11) & 0x1F;
                int middle = (addr >>  8) & 0x07;
                int sub    =  addr        & 0xFF;

                String dptArg = dptExpression(
                        ga.getDatapointType(), simpleDptClass, xlatorPackage, new TreeSet<>());

                sb.append("        public static final KnxDatapoint ")
                  .append(identifier)
                  .append(" = new KnxDatapoint(new ").append(simpleGaClass)
                  .append("(").append(main).append(", ").append(middle).append(", ").append(sub).append(")")
                  .append(", \"").append(escapeJavaString(ga.getName())).append("\"")
                  .append(", ").append(dptArg).append(");\n");
            }

            // BY_ADDRESS map: GroupAddress → KnxDatapoint, for runtime lookup
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
            for (String id : identifiers) {
                sb.append("            m.put(").append(id).append(".address(), ").append(id).append(");\n");
            }
            sb.append("            BY_ADDRESS = Collections.unmodifiableMap(m);\n");
            sb.append("        }\n");

            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── DPT resolution ────────────────────────────────────────────────────────

    /**
     * Returns the Java expression to use for a datapoint type argument in the
     * generated code.
     *
     * <ul>
     *   <li>If {@code etsDpt} maps to a known Calimero constant → {@code DPTXlatorFoo.DPT_BAR}
     *   <li>If {@code etsDpt} is parseable but unknown → {@code new DPT("X.XXX", "", "", "")}
     *   <li>If {@code etsDpt} is empty/null → {@code null}
     * </ul>
     *
     * @param etsDpt          raw ETS datapoint type string (e.g. "DPST-9-1" or "DPT-1")
     * @param simpleDptClass  simple name of the DPT class (e.g. "DPT")
     * @param xlatorPackage   package prefix for translator classes
     * @param usedTranslators mutable set that receives the FQN of any translator class used
     * @return Java expression string, never {@code null}
     */
    static String dptExpression(String etsDpt,
                                String simpleDptClass,
                                String xlatorPackage,
                                TreeSet<String> usedTranslators) {
        String normalizedId = normalizeDptId(etsDpt);
        if (normalizedId == null) return "null";

        String[] mapping = DPT_CONSTANT_MAP.get(normalizedId);
        if (mapping != null) {
            usedTranslators.add(xlatorPackage + mapping[0]);
            return mapping[0] + "." + mapping[1];
        }

        // Fallback: construct a minimal DPT with just the type ID
        return "new " + simpleDptClass + "(\"" + normalizedId + "\", \"\", \"\", \"\")";
    }

    /**
     * Converts an ETS datapoint type string to the normalised Calimero ID format
     * ({@code "main.sub"} with three-digit zero-padded sub-number).
     *
     * <ul>
     *   <li>{@code "DPST-9-1"}  → {@code "9.001"}
     *   <li>{@code "DPST-1-1"}  → {@code "1.001"}
     *   <li>{@code "DPT-9"}     → {@code "9.000"}
     * </ul>
     *
     * @param etsDpt ETS datapoint type string, may be empty or null
     * @return normalised ID, or {@code null} if not parseable
     */
    static String normalizeDptId(String etsDpt) {
        if (etsDpt == null || etsDpt.isEmpty()) return null;
        try {
            if (etsDpt.startsWith("DPST-")) {
                String[] parts = etsDpt.substring(5).split("-", 2);
                if (parts.length == 2) {
                    int main = Integer.parseInt(parts[0]);
                    int sub  = Integer.parseInt(parts[1]);
                    return main + "." + String.format("%03d", sub);
                }
            } else if (etsDpt.startsWith("DPT-")) {
                int main = Integer.parseInt(etsDpt.substring(4));
                return main + ".000";
            }
        } catch (NumberFormatException ignored) { }
        return null;
    }

    // ── Name conversion utilities ─────────────────────────────────────────────

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

    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
