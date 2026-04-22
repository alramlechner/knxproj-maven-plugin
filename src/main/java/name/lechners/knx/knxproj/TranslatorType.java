package name.lechners.knx.knxproj;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapping zwischen DPTXlator-Klassen und deren Klassennamen.
 * Wird verwendet, um den Enum-Namen aus dem Klassennamen zu ermitteln.
 */
public final class TranslatorType {

    private static final Map<String, String> CLASS_NAME_TO_ENUM_NAME = new HashMap<>();

    static {
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorBoolean", "DPT_BOOLEAN");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator3BitControlled", "DPT_3BIT_CONTROLLED");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator8BitUnsigned", "DPT_8BIT_UNSIGNED");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator8BitSigned", "DPT_8BIT_SIGNED");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator2ByteUnsigned", "DPT_2BYTE_UNSIGNED");
        CLASS_NAME_TO_ENUM_NAME.put("DptXlator2ByteSigned", "DPT_2BYTE_SIGNED");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator2ByteFloat", "DPT_2BYTE_FLOAT");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorTime", "DPT_TIME");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorDate", "DPT_DATE");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator4ByteSigned", "DPT_4BYTE_SIGNED");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator4ByteFloat", "DPT_4BYTE_FLOAT");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorString", "DPT_STRING");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorSceneNumber", "DPT_SCENE_NUMBER");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorDateTime", "DPT_DATE_TIME");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlator8BitEnum", "DPT_8BIT_ENUM");
        CLASS_NAME_TO_ENUM_NAME.put("DPTXlatorRGB", "DPT_RGB");
    }

    /**
     * Konvertiert einen DPTXlator-Klassennamen zu einem Enum-Namen.
     * Beispiel: "DPTXlatorBoolean" → "DPT_BOOLEAN"
     *
     * @param className der einfache Klassenname
     * @return der entsprechende Enum-Name, oder null wenn nicht mappiert
     */
    public static String getEnumName(String className) {
        return CLASS_NAME_TO_ENUM_NAME.get(className);
    }

    private TranslatorType() {}
}
