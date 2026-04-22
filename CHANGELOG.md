# Changelog

All notable changes to this project will be documented in this file.

## [2.1.0] - 2026-04-21

### Changed
- **Group addresses without DPT are now skipped** — Group addresses that have no datapoint type defined in ETS
  are no longer generated as `KnxDatapoint` constants. A warning is logged for each skipped address.
  This is by design since `KnxDatapoint` requires a DPT for translator instantiation.
- **newXlatorInstance() throws only unchecked exceptions** — The `newXlatorInstance()` and 
  `newXlatorInstance(byte[] asdu)` methods no longer declare `throws KNXFormatException`. Instead, any failures
  (reflection errors, invalid DPT) are wrapped in `RuntimeException`. This reduces boilerplate error handling
  since these are typically programmer errors, not recoverable runtime conditions.

### Added

### Added
- **Global BY_ADDRESS map** — A class-level `KnxDatapoint<?>[] BY_ADDRESS` map that combines all group addresses
  from all Hauptgruppen for convenient runtime lookup across groups:
  ```java
  KnxDatapoint<?> dp = KNXGroupAddresses.BY_ADDRESS.get(receivedAddress);
  ```
  In addition, each Hauptgruppe still has its own `BY_ADDRESS` map for group-specific lookups.
- **KnxDatapoint with generics** — `KnxDatapoint<T extends DPTXlator>` is now a generic class that captures the specific
  translator type at compile time. This enables **type-safe access without casting**:
  ```java
  var dp = KNXGroupAddresses.Licht.WOHNZIMMER_LICHT;  // KnxDatapoint<DPTXlatorBoolean>
  DPTXlatorBoolean xlator = dp.newXlatorInstance();   // No cast required!
  ```
- **DPTXlator instantiation methods** — `KnxDatapoint<T>` includes overloaded `newXlatorInstance()` methods with
  return type T:
  - `T newXlatorInstance()` — creates a new translator with just the DPT
  - `T newXlatorInstance(byte[] asdu)` — creates a translator and immediately sets ASDU data
  Both methods use reflection to invoke the translator's DPT-parameter constructor.
- **TranslatorTypes enum** — A new generated enum (`TranslatorTypes.java`) provides factory methods for creating
  DPTXlator instances when the exact type is not known at compile time:
  - `createInstance(DPT dpt, byte[] asdu)` — instance method per enum constant
  - `createTranslator(DPT dpt, byte[] asdu)` — static convenience method that tries all available translators
- **Type-safe BY_ADDRESS map** — The BY_ADDRESS map is now typed as `Map<GroupAddress, KnxDatapoint<?>>`, allowing
  type-safe retrieval with wildcard generics.

## [2.0.0] - 2026-04-10

### Breaking changes
- **Calimero 3.0-M2** — target package changed from `tuwien.auto.calimero` to `io.calimero`.
  Default `groupAddressClass` is now `io.calimero.GroupAddress`; the DPT class default follows
  as `io.calimero.dptxlator.DPT`. Projects still using Calimero 2.x must pin
  `groupAddressClass` explicitly.
- **Generated constant type changed** — each group address is now a single `KnxDatapoint`
  constant (instead of a separate `GroupAddress` constant plus a `KnxDatapoint`). The
  `BY_ADDRESS` map is built with a static initializer instead of `Map.of(...)`.
- **DPT constant renames** to match Calimero 3 public API:
  - `DPTXlator8BitSigned` 6.010: `DPT_VALUE_1_COUNT` → `DPT_VALUE_1_UCOUNT`
  - `DPTXlator2ByteUnsigned` 7.011: `DPT_LENGTH_MM` → `DPT_LENGTH`
  - `DptXlator2ByteSigned` 8.001–8.011: all constants renamed to camelCase
    (`DptValueCount`, `DptDeltaTime`, `DptDeltaTime10`, `DptDeltaTime100`,
    `DptDeltaTimeSec`, `DptDeltaTimeMin`, `DptDeltaTimeHours`, `DptPercent`, `DptRotationAngle`)

### Added
- **`warnOnUnmappedDpt` parameter** — logs a warning for each ETS DPT that has no Calimero
  constant mapping and falls back to `new DPT(...)`. Enabled by default; disable with
  `<warnOnUnmappedDpt>false</warnOnUnmappedDpt>` or `-Dknxproj.warnOnUnmappedDpt=false`.
- **Extended DPT constant coverage** — added direct Calimero mappings for main types:
  - `3` — `DPTXlator3BitControlled` (3.007 Control Dimming)
  - `13` — `DPTXlator4ByteSigned` (energy, flow rate, delta time; 12 subtypes)
  - `14` — `DPTXlator4ByteFloat` (physical quantities 14.000–14.1201; 83 subtypes)
  - `17` — `DPTXlatorSceneNumber` (17.001 Scene Number)
  - `19` — `DPTXlatorDateTime` (19.001 Date and Time)
  - `20` — `DPTXlator8BitEnum` (20.102 HVAC Mode)

## [1.1.1] - 2026-04-04

### Fixed
- Reverted default `groupAddressClass` back to `tuwien.auto.calimero.GroupAddress` (Calimero 2.6).
  The change to `io.calimero` introduced in 1.1.0 incorrectly targeted Calimero 3.x, causing
  `package io.calimero.dptxlator does not exist` compile errors in projects using Calimero 2.6.

## [1.1.0] - 2026-04-04

### Added
- **`KnxDatapoint` record** — generated alongside `GroupAddress` constants; bundles address, ETS group address name, and DPT in one type for better usability and type safety.
- **Calimero DPT constants** — the generator maps ETS DPT IDs (e.g. `DPST-9-1`) directly to the corresponding Calimero constants (e.g. `DPTXlator2ByteFloat.DPT_TEMPERATURE`). Supported main types: 1, 5, 6, 7, 8, 9, 10, 11, 16, 232.
- **`BY_ADDRESS` map** — each generated Hauptgruppe class contains a `Map<GroupAddress, KnxDatapoint> BY_ADDRESS` for efficient runtime lookup by received group address.
- **Group address name** — the ETS name of each group address is included as `String name` in `KnxDatapoint`.

### Changed
- Generated class structure now contains nested `KnxDatapoint` record in addition to `GroupAddress` constants.
- Only translator classes actually referenced in the project are imported (no unused imports).

### Fixed
- Class name casing: `DPTXlator2ByteSigned` → `DptXlator2ByteSigned` (matches actual Calimero 2.6 class name).
- DPT 8.010 constant name: `DPT_PERCENT_V16` → `DptPercent` (matches actual Calimero 2.6 constant name).
- Default `groupAddressClass` updated from `tuwien.auto.calimero.GroupAddress` to `io.calimero.GroupAddress` (Calimero 2.6 package).

## [1.0.0] - 2025-01-01

### Added
- Initial release.
- Generates Java source from ETS `.knxproj` files.
- Produces strongly-typed `GroupAddress` constants organised by Hauptgruppe / Mittelgruppe.
- Maven plugin parameters: `knxprojFile`, `outputDirectory`, `packageName`, `className`, `groupAddressClass`, `filterHauptgruppe`.
