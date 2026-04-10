package name.lechners.knx.knxproj;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

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

        // ── Main type 3 – 3-bit controlled (DPTXlator3BitControlled) ─────────
        m.put("3.007", new String[]{"DPTXlator3BitControlled", "DPT_CONTROL_DIMMING"});

        // ── Main type 5 – 8-bit unsigned (DPTXlator8BitUnsigned) ──────────────
        m.put("5.001", new String[]{"DPTXlator8BitUnsigned", "DPT_SCALING"});
        m.put("5.003", new String[]{"DPTXlator8BitUnsigned", "DPT_ANGLE"});
        m.put("5.004", new String[]{"DPTXlator8BitUnsigned", "DPT_PERCENT_U8"});
        m.put("5.005", new String[]{"DPTXlator8BitUnsigned", "DPT_DECIMALFACTOR"});
        m.put("5.006", new String[]{"DPTXlator8BitUnsigned", "DPT_TARIFF"});
        m.put("5.010", new String[]{"DPTXlator8BitUnsigned", "DPT_VALUE_1_UCOUNT"});

        // ── Main type 6 – 8-bit signed (DPTXlator8BitSigned) ──────────────────
        m.put("6.001", new String[]{"DPTXlator8BitSigned", "DPT_PERCENT_V8"});
        m.put("6.010", new String[]{"DPTXlator8BitSigned", "DPT_VALUE_1_UCOUNT"});
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
        m.put("7.011", new String[]{"DPTXlator2ByteUnsigned", "DPT_LENGTH"});
        m.put("7.012", new String[]{"DPTXlator2ByteUnsigned", "DPT_ELECTRICAL_CURRENT"});
        m.put("7.013", new String[]{"DPTXlator2ByteUnsigned", "DPT_BRIGHTNESS"});
        m.put("7.600", new String[]{"DPTXlator2ByteUnsigned", "DPT_ABSOLUTE_COLOR_TEMPERATURE"});

        // ── Main type 8 – 2-byte signed (DptXlator2ByteSigned) ───────────────
        m.put("8.001", new String[]{"DptXlator2ByteSigned", "DptValueCount"});
        m.put("8.002", new String[]{"DptXlator2ByteSigned", "DptDeltaTime"});
        m.put("8.003", new String[]{"DptXlator2ByteSigned", "DptDeltaTime10"});
        m.put("8.004", new String[]{"DptXlator2ByteSigned", "DptDeltaTime100"});
        m.put("8.005", new String[]{"DptXlator2ByteSigned", "DptDeltaTimeSec"});
        m.put("8.006", new String[]{"DptXlator2ByteSigned", "DptDeltaTimeMin"});
        m.put("8.007", new String[]{"DptXlator2ByteSigned", "DptDeltaTimeHours"});
        m.put("8.010", new String[]{"DptXlator2ByteSigned", "DptPercent"});
        m.put("8.011", new String[]{"DptXlator2ByteSigned", "DptRotationAngle"});

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

        // ── Main type 13 – 4-byte signed (DPTXlator4ByteSigned) ──────────────
        m.put("13.001", new String[]{"DPTXlator4ByteSigned", "DPT_COUNT"});
        m.put("13.002", new String[]{"DPTXlator4ByteSigned", "DPT_FLOWRATE"});
        m.put("13.010", new String[]{"DPTXlator4ByteSigned", "DPT_ACTIVE_ENERGY"});
        m.put("13.011", new String[]{"DPTXlator4ByteSigned", "DPT_APPARENT_ENERGY"});
        m.put("13.012", new String[]{"DPTXlator4ByteSigned", "DPT_REACTIVE_ENERGY"});
        m.put("13.013", new String[]{"DPTXlator4ByteSigned", "DPT_ACTIVE_ENERGY_KWH"});
        m.put("13.014", new String[]{"DPTXlator4ByteSigned", "DPT_APPARENT_ENERGY_KVAH"});
        m.put("13.015", new String[]{"DPTXlator4ByteSigned", "DPT_REACTIVE_ENERGY_KVARH"});
        m.put("13.016", new String[]{"DPTXlator4ByteSigned", "DptActiveEnergyMWh"});
        m.put("13.100", new String[]{"DPTXlator4ByteSigned", "DPT_DELTA_TIME"});
        m.put("13.1200", new String[]{"DPTXlator4ByteSigned", "DPT_DELTA_VOLUME_LIQUID_LITER"});
        m.put("13.1201", new String[]{"DPTXlator4ByteSigned", "DPT_DELTA_VOLUME_M3"});

        // ── Main type 14 – 4-byte float (DPTXlator4ByteFloat) ────────────────
        m.put("14.000", new String[]{"DPTXlator4ByteFloat", "DPT_ACCELERATION"});
        m.put("14.001", new String[]{"DPTXlator4ByteFloat", "DPT_ACCELERATION_ANGULAR"});
        m.put("14.002", new String[]{"DPTXlator4ByteFloat", "DPT_ACTIVATION_ENERGY"});
        m.put("14.003", new String[]{"DPTXlator4ByteFloat", "DPT_ACTIVITY"});
        m.put("14.004", new String[]{"DPTXlator4ByteFloat", "DPT_MOL"});
        m.put("14.005", new String[]{"DPTXlator4ByteFloat", "DPT_AMPLITUDE"});
        m.put("14.006", new String[]{"DPTXlator4ByteFloat", "DPT_ANGLE_RAD"});
        m.put("14.007", new String[]{"DPTXlator4ByteFloat", "DPT_ANGLE_DEG"});
        m.put("14.008", new String[]{"DPTXlator4ByteFloat", "DPT_ANGULAR_MOMENTUM"});
        m.put("14.009", new String[]{"DPTXlator4ByteFloat", "DPT_ANGULAR_VELOCITY"});
        m.put("14.010", new String[]{"DPTXlator4ByteFloat", "DPT_AREA"});
        m.put("14.011", new String[]{"DPTXlator4ByteFloat", "DPT_CAPACITANCE"});
        m.put("14.012", new String[]{"DPTXlator4ByteFloat", "DPT_CHARGE_DENSITY_SURFACE"});
        m.put("14.013", new String[]{"DPTXlator4ByteFloat", "DPT_CHARGE_DENSITY_VOLUME"});
        m.put("14.014", new String[]{"DPTXlator4ByteFloat", "DPT_COMPRESSIBILITY"});
        m.put("14.015", new String[]{"DPTXlator4ByteFloat", "DPT_CONDUCTANCE"});
        m.put("14.016", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRICAL_CONDUCTIVITY"});
        m.put("14.017", new String[]{"DPTXlator4ByteFloat", "DPT_DENSITY"});
        m.put("14.018", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_CHARGE"});
        m.put("14.019", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_CURRENT"});
        m.put("14.020", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_CURRENTDENSITY"});
        m.put("14.021", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_DIPOLEMOMENT"});
        m.put("14.022", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_DISPLACEMENT"});
        m.put("14.023", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_FIELDSTRENGTH"});
        m.put("14.024", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_FLUX"});
        m.put("14.025", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_FLUX_DENSITY"});
        m.put("14.026", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_POLARIZATION"});
        m.put("14.027", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_POTENTIAL"});
        m.put("14.028", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTRIC_POTENTIAL_DIFFERENCE"});
        m.put("14.029", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTROMAGNETIC_MOMENT"});
        m.put("14.030", new String[]{"DPTXlator4ByteFloat", "DPT_ELECTROMOTIVE_FORCE"});
        m.put("14.031", new String[]{"DPTXlator4ByteFloat", "DPT_ENERGY"});
        m.put("14.032", new String[]{"DPTXlator4ByteFloat", "DPT_FORCE"});
        m.put("14.033", new String[]{"DPTXlator4ByteFloat", "DPT_FREQUENCY"});
        m.put("14.034", new String[]{"DPTXlator4ByteFloat", "DPT_ANGULAR_FREQUENCY"});
        m.put("14.035", new String[]{"DPTXlator4ByteFloat", "DPT_HEAT_CAPACITY"});
        m.put("14.036", new String[]{"DPTXlator4ByteFloat", "DPT_HEAT_FLOWRATE"});
        m.put("14.037", new String[]{"DPTXlator4ByteFloat", "DPT_HEAT_QUANTITY"});
        m.put("14.038", new String[]{"DPTXlator4ByteFloat", "DPT_IMPEDANCE"});
        m.put("14.039", new String[]{"DPTXlator4ByteFloat", "DPT_LENGTH"});
        m.put("14.040", new String[]{"DPTXlator4ByteFloat", "DPT_LIGHT_QUANTITY"});
        m.put("14.041", new String[]{"DPTXlator4ByteFloat", "DPT_LUMINANCE"});
        m.put("14.042", new String[]{"DPTXlator4ByteFloat", "DPT_LUMINOUS_FLUX"});
        m.put("14.043", new String[]{"DPTXlator4ByteFloat", "DPT_LUMINOUS_INTENSITY"});
        m.put("14.044", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIC_FIELDSTRENGTH"});
        m.put("14.045", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIC_FLUX"});
        m.put("14.046", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIC_FLUX_DENSITY"});
        m.put("14.047", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIC_MOMENT"});
        m.put("14.048", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIC_POLARIZATION"});
        m.put("14.049", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETIZATION"});
        m.put("14.050", new String[]{"DPTXlator4ByteFloat", "DPT_MAGNETOMOTIVE_FORCE"});
        m.put("14.051", new String[]{"DPTXlator4ByteFloat", "DPT_MASS"});
        m.put("14.052", new String[]{"DPTXlator4ByteFloat", "DPT_MASS_FLUX"});
        m.put("14.053", new String[]{"DPTXlator4ByteFloat", "DPT_MOMENTUM"});
        m.put("14.054", new String[]{"DPTXlator4ByteFloat", "DPT_PHASE_ANGLE_RAD"});
        m.put("14.055", new String[]{"DPTXlator4ByteFloat", "DPT_PHASE_ANGLE_DEG"});
        m.put("14.056", new String[]{"DPTXlator4ByteFloat", "DPT_POWER"});
        m.put("14.057", new String[]{"DPTXlator4ByteFloat", "DPT_POWER_FACTOR"});
        m.put("14.058", new String[]{"DPTXlator4ByteFloat", "DPT_PRESSURE"});
        m.put("14.059", new String[]{"DPTXlator4ByteFloat", "DPT_REACTANCE"});
        m.put("14.060", new String[]{"DPTXlator4ByteFloat", "DPT_RESISTANCE"});
        m.put("14.061", new String[]{"DPTXlator4ByteFloat", "DPT_RESISTIVITY"});
        m.put("14.062", new String[]{"DPTXlator4ByteFloat", "DPT_SELF_INDUCTANCE"});
        m.put("14.063", new String[]{"DPTXlator4ByteFloat", "DPT_SOLID_ANGLE"});
        m.put("14.064", new String[]{"DPTXlator4ByteFloat", "DPT_SOUND_INTENSITY"});
        m.put("14.065", new String[]{"DPTXlator4ByteFloat", "DPT_SPEED"});
        m.put("14.066", new String[]{"DPTXlator4ByteFloat", "DPT_STRESS"});
        m.put("14.067", new String[]{"DPTXlator4ByteFloat", "DPT_SURFACE_TENSION"});
        m.put("14.068", new String[]{"DPTXlator4ByteFloat", "DPT_COMMON_TEMPERATURE"});
        m.put("14.069", new String[]{"DPTXlator4ByteFloat", "DPT_ABSOLUTE_TEMPERATURE"});
        m.put("14.070", new String[]{"DPTXlator4ByteFloat", "DPT_TEMPERATURE_DIFFERENCE"});
        m.put("14.071", new String[]{"DPTXlator4ByteFloat", "DPT_THERMAL_CAPACITY"});
        m.put("14.072", new String[]{"DPTXlator4ByteFloat", "DPT_THERMAL_CONDUCTIVITY"});
        m.put("14.073", new String[]{"DPTXlator4ByteFloat", "DPT_THERMOELECTRIC_POWER"});
        m.put("14.074", new String[]{"DPTXlator4ByteFloat", "DPT_TIME"});
        m.put("14.075", new String[]{"DPTXlator4ByteFloat", "DPT_TORQUE"});
        m.put("14.076", new String[]{"DPTXlator4ByteFloat", "DPT_VOLUME"});
        m.put("14.077", new String[]{"DPTXlator4ByteFloat", "DPT_VOLUME_FLUX"});
        m.put("14.078", new String[]{"DPTXlator4ByteFloat", "DPT_WEIGHT"});
        m.put("14.079", new String[]{"DPTXlator4ByteFloat", "DPT_WORK"});
        m.put("14.080", new String[]{"DPTXlator4ByteFloat", "DPT_APPARENT_POWER"});
        m.put("14.1200", new String[]{"DPTXlator4ByteFloat", "DPT_VOLUME_FLUX_METER"});
        m.put("14.1201", new String[]{"DPTXlator4ByteFloat", "DPT_VOLUME_FLUX_LS"});

        // ── Main type 16 – String (DPTXlatorString) ──────────────────────────
        m.put("16.000", new String[]{"DPTXlatorString", "DPT_STRING_ASCII"});
        m.put("16.001", new String[]{"DPTXlatorString", "DPT_STRING_8859_1"});

        // ── Main type 17 – Scene number (DPTXlatorSceneNumber) ───────────────
        m.put("17.001", new String[]{"DPTXlatorSceneNumber", "DPT_SCENE_NUMBER"});

        // ── Main type 19 – Date and time (DPTXlatorDateTime) ─────────────────
        m.put("19.001", new String[]{"DPTXlatorDateTime", "DPT_DATE_TIME"});

        // ── Main type 20 – 8-bit enum (DPTXlator8BitEnum) ────────────────────
        m.put("20.102", new String[]{"DPTXlator8BitEnum", "DptHvacMode"});

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
     * @param warnConsumer      receives one warning message per unmapped DPT ID; never {@code null}
     * @return Java source code as a string
     */
    public String generate(List<GroupAddressEntry> entries,
                           String packageName,
                           String className,
                           String groupAddressClass,
                           String dptClass,
                           String sourceFile,
                           LocalDateTime generatedAt,
                           Consumer<String> warnConsumer) {

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
        // DPT ID → list of group address names that use the fallback (sorted for stable output)
        Map<String, List<String>> unmappedDpts = new TreeMap<>();
        for (GroupAddressEntry e : entries) {
            dptExpression(e.getDatapointType(), simpleDptClass, xlatorPackage, xlatorImports);
            String normalizedId = normalizeDptId(e.getDatapointType());
            if (normalizedId != null && !DPT_CONSTANT_MAP.containsKey(normalizedId)) {
                unmappedDpts.computeIfAbsent(normalizedId, k -> new ArrayList<>())
                            .add(e.getName() + " (" + e.getFormattedAddress() + ")");
            }
        }
        // Emit one warning per unmapped DPT ID, listing the affected group addresses
        for (Map.Entry<String, List<String>> unmapped : unmappedDpts.entrySet()) {
            warnConsumer.accept(
                "No Calimero constant for DPT '" + unmapped.getKey() + "' – "
                + "using fallback new DPT(...) for: " + unmapped.getValue());
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
