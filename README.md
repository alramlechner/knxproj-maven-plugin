# knxproj-maven-plugin

A Maven plugin that generates Java source code from an ETS `.knxproj` file.

> **Calimero version compatibility**
> | Plugin version | Calimero version |
> |----------------|-----------------|
> | 1.x            | 2.x (`tuwien.auto.calimero`) |
> | 2.x            | 3.x (`io.calimero`) |

The plugin generates three Java files:
- **`KnxDatapoint.java`** — A class bundling `GroupAddress`, ETS name, Calimero `DPT` constant, and the `DPTXlator` class reference. Includes `newXlatorInstance()` methods that create new DPTXlator instances via reflection, optionally setting ASDU data.
- **`TranslatorTypes.java`** — An enum of all DPTXlator types used in the project, with factory methods for creating translator instances.
- **`{ClassName}.java`** (e.g., `KNXGroupAddresses.java`) — For each Hauptgruppe, contains a nested class with:
  - Strongly-typed `KnxDatapoint` constants (one per group address)
  - A `BY_ADDRESS` map (`Map<GroupAddress, KnxDatapoint>`) for efficient runtime lookup by received group address

## Requirements

- Java 17+
- Maven 3.8+
- [Calimero Core](https://github.com/calimero-project/calimero-core) 3.0-M2 or later as a runtime dependency in the consuming project

## Usage

Add the plugin to your `pom.xml`:

```xml
<plugin>
  <groupId>io.github.alramlechner</groupId>
  <artifactId>knxproj-maven-plugin</artifactId>
  <version>2.1.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
      <configuration>
        <!-- Required -->
        <knxprojFile>${project.basedir}/src/main/knx/MyProject.knxproj</knxprojFile>
        <packageName>com.example.knx</packageName>
        <!-- Optional -->
        <!-- <className>KNXGroupAddresses</className> -->
        <!-- <outputDirectory>${project.build.directory}/generated-sources/knx</outputDirectory> -->
        <!-- <groupAddressClass>io.calimero.GroupAddress</groupAddressClass> -->
        <!-- <skip>false</skip> -->
      </configuration>
    </execution>
  </executions>
</plugin>
```

Also add the Calimero Core runtime dependency:

```xml
<dependency>
  <groupId>io.calimero</groupId>
  <artifactId>calimero-core</artifactId>
  <version>3.0-M2</version>
</dependency>
```

> **Tip:** Version the `.knxproj` file alongside your project sources to ensure build reproducibility.

## Parameters

| Parameter          | CLI property                     | Default                                                   | Required |
|--------------------|----------------------------------|-----------------------------------------------------------|----------|
| `knxprojFile`      | `-Dknxproj.file=...`             | –                                                         | **yes**  |
| `packageName`      | `-Dknxproj.packageName=...`      | –                                                         | **yes**  |
| `outputDirectory`  | `-Dknxproj.outputDirectory=...`  | `${project.build.directory}/generated-sources/knx`        | no       |
| `className`        | `-Dknxproj.className=...`        | `KNXGroupAddresses`                                       | no       |
| `groupAddressClass`| `-Dknxproj.groupAddressClass=...`| `io.calimero.GroupAddress`                       | no       |
| `skip`             | `-Dknxproj.skip=true`            | `false`                                                   | no       |
| `warnOnUnmappedDpt`| `-Dknxproj.warnOnUnmappedDpt=false`| `true`                                                  | no       |

## Generated code example

Given a group address `1/0/1` named *"Living room light"* with DPT `1.001 (switch)`, the plugin generates:

### KnxDatapoint.java
```java
/**
 * Type-safe datapoint wrapper with generics for DPTXlator types.
 * T is the specific DPTXlator subtype, enabling type-safe access without casting.
 */
public final class KnxDatapoint<T extends DPTXlator> {

    private final GroupAddress address;
    private final String name;
    private final DPT dpt;
    private final Class<T> dptXlatorClass;  // Generically typed

    public KnxDatapoint(GroupAddress address, String name, DPT dpt, Class<T> dptXlatorClass) {
        this.address = address;
        this.name = name;
        this.dpt = dpt;
        this.dptXlatorClass = dptXlatorClass;
    }

    public GroupAddress address() { return address; }
    public String name() { return name; }
    public DPT dpt() { return dpt; }
    public Class<T> dptXlatorClass() { return dptXlatorClass; }

    /**
     * Creates a new instance of the translator.
     * Returns the specific type T - no casting required!
     * Only throws unchecked exceptions (no checked exception handling needed).
     */
    public T newXlatorInstance() {
        // Reflection-based instantiation, throws only RuntimeException if needed
    }
    
    /**
     * Creates a translator instance and initializes with ASDU data.
     * Only throws unchecked exceptions (no checked exception handling needed).
     */
    public T newXlatorInstance(byte[] asdu) {
        // Reflection-based instantiation + setData
    }
}
```

### KNXGroupAddresses.java
```java
public final class KNXGroupAddresses {

    /**
     * Global map of all group addresses across all groups.
     * Useful for runtime lookup when only the raw address is known.
     */
    public static final Map<GroupAddress, KnxDatapoint<?>> BY_ADDRESS;

    public static final class Licht {  // Hauptgruppe

        /** Living room light · 1/0/1 · DPST-1-1 */
        public static final KnxDatapoint<DPTXlatorBoolean> WOHNZIMMER_LICHT = 
            new KnxDatapoint<>(
                new GroupAddress(1, 0, 1),
                "Living room light",
                DPTXlatorBoolean.DPT_SWITCH,
                DPTXlatorBoolean.class
            );

        /** Group-specific BY_ADDRESS map */
        public static final Map<GroupAddress, KnxDatapoint<?>> BY_ADDRESS;
        static {
            Map<GroupAddress, KnxDatapoint<?>> m = new LinkedHashMap<>();
            m.put(WOHNZIMMER_LICHT.address(), WOHNZIMMER_LICHT);
            BY_ADDRESS = Collections.unmodifiableMap(m);
        }
    }

    // Global BY_ADDRESS map combining all groups
    static {
        Map<GroupAddress, KnxDatapoint<?>> globalMap = new LinkedHashMap<>();
        globalMap.put(Licht.WOHNZIMMER_LICHT.address(), Licht.WOHNZIMMER_LICHT);
        // ... other groups
        BY_ADDRESS = Collections.unmodifiableMap(globalMap);
    }
}
```

### TranslatorTypes.java
```java
public enum TranslatorTypes {
    /** {@link DPTXlatorBoolean} */
    DPT_BOOLEAN(DPTXlatorBoolean.class),
    /** {@link DPTXlator2ByteFloat} */
    DPT_2BYTE_FLOAT(DPTXlator2ByteFloat.class),
    // ... other translator types
    ;

    private final Class<?> xlatorClass;

    TranslatorTypes(Class<?> xlatorClass) {
        this.xlatorClass = xlatorClass;
    }

    public DPTXlator createInstance(DPT dpt, byte[] asdu) throws KNXFormatException { ... }

    public static DPTXlator createTranslator(DPT dpt, byte[] asdu) throws KNXFormatException { ... }
}
```

### Usage examples

**Using KnxDatapoint with type-safe generics:**
```java
var dp = KNXGroupAddresses.Licht.WOHNZIMMER_LICHT;

// Access datapoint information
GroupAddress address = dp.address();      // 1/0/1
String name = dp.name();                  // "Living room light"
DPT dpt = dp.dpt();                       // DPTXlatorBoolean.DPT_SWITCH

// Create a new translator instance - returns DPTXlatorBoolean directly, NO CAST!
// No try-catch needed - only unchecked exceptions
DPTXlatorBoolean translator = dp.newXlatorInstance();
translator.setValue(true);
byte[] data = translator.getData();

// Or with ASDU data:
byte[] receivedData = /* ... */;
DPTXlatorBoolean translator = dp.newXlatorInstance(receivedData);
boolean value = translator.getBoolean();  // Type-safe, no casting

// Failures throw RuntimeException (e.g., reflection errors)
// These are programmer errors, not recoverable runtime issues

// Runtime lookup from received group address using global BY_ADDRESS map:
GroupAddress receivedAddress = /* ... */;
KnxDatapoint<?> dp = KNXGroupAddresses.BY_ADDRESS.get(receivedAddress);
if (dp != null) {
    var xlator = dp.newXlatorInstance(receivedData);
    Object value = xlator.getNumericValue();
}

// Or use group-specific lookup:
KnxDatapoint<?> dp = KNXGroupAddresses.Licht.BY_ADDRESS.get(receivedAddress);
```

**Using TranslatorTypes:**
```java
DPT dpt = KNXGroupAddresses.Licht.WOHNZIMMER_LICHT.dpt();
byte[] asdu = /* received KNX telegram data */;

DPTXlator xlator = TranslatorTypes.createTranslator(dpt, asdu);
Object value = xlator.getNumericValue();  // Interpret the KNX data
```

## Supported DPT main types

Direct Calimero constant mapping is supported for main types:
`1`, `3`, `5`, `6`, `7`, `8`, `9`, `10`, `11`, `13`, `14`, `16`, `17`, `19`, `20`, `232`.

For unknown or unmapped DPT IDs the generator falls back to `new DPT("X.XXX", "", "", "")`.

**Important:** Group addresses without a datapoint type are skipped and not included in the generated code. 
A warning is logged for each skipped address. This is by design, since `KnxDatapoint` requires a DPT type for 
translator instantiation.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
