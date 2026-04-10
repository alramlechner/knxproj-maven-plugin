# Copilot Instructions

## What this project is

A Maven plugin (`io.github.alramlechner:knxproj-maven-plugin`) that reads an ETS `.knxproj` file and generates a Java source file containing strongly-typed Calimero `GroupAddress` constants, a `KnxDatapoint` record, and a `BY_ADDRESS` lookup map — one nested class per KNX Hauptgruppe.

The plugin runs in the `generate-sources` phase (goal: `knxproj:generate`) and registers its output directory as a compile source root via `MavenProject.addCompileSourceRoot(...)`.

## Build & test commands

```bash
# Compile and package
mvn package

# Install to local repo (needed to test in a consumer project)
mvn install

# Compile only
mvn compile

# Release (signs + publishes to Maven Central via OSSRH)
mvn deploy -P release
```

There are no automated tests in the repository. Manual testing is done by consuming the installed plugin in a separate project with a real `.knxproj` file.

## Architecture

The plugin consists of exactly four classes in `name.lechners.knx.knxproj`:

| Class | Role |
|---|---|
| `GenerateGroupAddressesMojo` | Maven `@Mojo`; orchestrates parse → generate → write → register |
| `KnxProjectParser` | Unzips the `.knxproj` (ZIP), locates `P-XXXX/0.xml`, parses XML DOM to extract `<GroupAddress>` elements |
| `GroupAddressEntry` | Plain data holder: raw address integer, name, Hauptgruppe, Mittelgruppe, DPT string, description |
| `JavaSourceGenerator` | Produces the `.java` source as a `String`; contains the full `DPT_CONSTANT_MAP` static initialiser |

**Data flow:**

```
.knxproj (ZIP)
  └─ P-XXXX/0.xml (XML DOM)
       └─ <GroupAddress> elements
            └─ List<GroupAddressEntry>  (sorted by raw address)
                 └─ JavaSourceGenerator.generate(...)
                      └─ packageName/ClassName.java  (written to outputDirectory)
                           └─ MavenProject.addCompileSourceRoot(outputDirectory)
```

## Key conventions

### Mojo parameters
All `@Parameter` properties use the `knxproj.*` CLI namespace (e.g. `-Dknxproj.file=...`). The `dptIdClass` is **derived** from `groupAddressClass` inside `execute()` by replacing `.GroupAddress` with `.dptxlator.DPT` — there is no separate plugin parameter for it.

### .knxproj format
`.knxproj` files are ZIP archives. The project XML lives at the entry matching `P-[^/]+/0\.xml`. The ZIP is opened with `ISO-8859-1` charset for entry names (avoids failures with CP437/Windows-1252 ETS filenames). The XML parser has XXE disabled (`disallow-doctype-decl`).

### KNX address structure
Raw address integers use the KNX 3-level encoding:
- bits 15–11 → main (Hauptgruppe)
- bits 10–8  → middle (Mittelgruppe)
- bits 7–0   → sub (Endgruppe)

Decoded via `GroupAddressEntry.getFormattedAddress()`.

### DPT mapping (`JavaSourceGenerator`)
`DPT_CONSTANT_MAP` maps normalised DPT IDs (e.g. `"1.001"`) to `{ translatorSimpleClassName, constantName }` pairs. ETS uses DPST-prefixed strings (e.g. `DPST-9-1`); `normalizeDptId()` converts them to the canonical form. Unknown DPTs fall back to `new DPT("X.XXX", "", "", "")`. Only Calimero 2.6 (`tuwien.auto.calimero.*`) constants are in the map; the default `groupAddressClass` targets this version.

### Code generation style
- Imports are minimised: only DPTXlator classes actually referenced in the project are emitted (two-pass: collect imports first, then emit source).
- Identifier collision is handled by appending `_<rawAddress>` to the constant name.
- Nested class names come from `toJavaClassName(hauptgruppe)` (PascalCase); constant names from `toJavaConstantName(name)` (SCREAMING_SNAKE_CASE).
- Java strings in generated code are escaped via `escapeJavaString()`; Javadoc strings via `escapeHtml()`.

### Logging
Uses Maven's `getLog()` (from `AbstractMojo`), not SLF4J or java.util.logging.

### Compiler target
The plugin itself compiles to Java 11 (`<maven.compiler.source>11</maven.compiler.source>`). The generated code uses Java 16+ `record` syntax — consuming projects must use Java 17+.

### Release
The `release` Maven profile activates GPG signing (`maven-gpg-plugin`) and publishing to Maven Central via `central-publishing-maven-plugin` with `autoPublish=false` (manual promotion required).
