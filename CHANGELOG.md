# Changelog

All notable changes to this project will be documented in this file.

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
