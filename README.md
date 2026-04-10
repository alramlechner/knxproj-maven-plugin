# knxproj-maven-plugin

A Maven plugin that generates Java source code from an ETS `.knxproj` file.

> **Calimero version compatibility**
> | Plugin version | Calimero version |
> |----------------|-----------------|
> | 1.x            | 2.x (`tuwien.auto.calimero`) |
> | 2.x            | 3.x (`io.calimero`) |

For each Hauptgruppe the plugin produces a nested class containing:
- Strongly-typed `KnxDatapoint` constants (one per group address), each bundling `GroupAddress`, ETS name, and Calimero `DPT` constant
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
  <version>2.0.0</version>
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

```java
public final class KNXGroupAddresses {

    public record KnxDatapoint(GroupAddress address, String name, DPT dpt) {}

    public static final class Licht {  // Hauptgruppe

        /** Living room light · 1/0/1 · DPST-1-1 */
        public static final KnxDatapoint WOHNZIMMER_LICHT = new KnxDatapoint(
            new GroupAddress(1, 0, 1),
            "Living room light",
            DPTXlatorBoolean.DPT_SWITCH
        );

        public static final Map<GroupAddress, KnxDatapoint> BY_ADDRESS;
        static {
            Map<GroupAddress, KnxDatapoint> m = new LinkedHashMap<>();
            m.put(WOHNZIMMER_LICHT.address(), WOHNZIMMER_LICHT);
            BY_ADDRESS = Collections.unmodifiableMap(m);
        }
    }
}
```

## Supported DPT main types

Direct Calimero constant mapping is supported for main types:
`1`, `3`, `5`, `6`, `7`, `8`, `9`, `10`, `11`, `13`, `14`, `16`, `17`, `19`, `20`, `232`.

For unknown or unmapped DPT IDs the generator falls back to `new DPT("X.XXX", "", "", "")`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
