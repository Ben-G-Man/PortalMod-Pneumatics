# Pneumatic Diversity Vents

Pneumatic Diversity Vents is a Minecraft Forge 1.16.5 mod that adds modular pneumatic transport tubes inspired by Portal-style Diversity Vents. Vent networks can move players and supported entities, generate airflow with impellers, focus intake/exhaust with terminals, detect traffic with scanners, and route traffic through powered junctions.

## Requirements

- Minecraft 1.16.5
- Forge 36.2.34
- Java 8
- PortalMod 1.3.1

The development PortalMod dependency is provided in `libs/portalmod-1.3.1.jar`.

## Items and configuration

Most configurable vent components use PortalMod's wrench. Normal wrench-click and crouch-wrench-click perform different actions where noted below. Vent pieces automatically align to compatible open vent endpoints when placed against an existing network.

### Diversity Vent Segment

The basic transparent tube piece and the main building block of a vent network.

- Normal placement creates a one-block-long, 2x2 vent segment.
- Crouch-place a segment onto the open end of an existing one-long segment to extend it into a two-block-long segment.
- Crouch-place against an appropriate side of a two-long segment to bend or reroute that section while continuing the network.
- The segment itself has no wrench configuration.

### Diversity Encouragement Impeller

Adds airflow to the connected vent network. Each impeller contributes force in its configured direction; multiple impellers combine through the network.

- Wrench-click reverses the impeller's configured polarity/direction.
- By default an impeller runs continuously.
- When connected to PortalMod antline/indicator control, crouch-wrench cycles between:
  - **On/off:** powered = running, unpowered = stopped.
  - **Reverse when powered:** unpowered = normal polarity, powered = reversed polarity.

### Diversity Concentration Terminal

A focused open endpoint. It narrows and strengthens the external intake/exhaust field compared with an ordinary open vent end.

Wrench-click cycles the terminal indicator through three modes:

- **Standard:** no status light.
- **Force:** lights while the connected network has active non-zero airflow.
- **Player:** lights when airflow is strong enough for player transport.

The terminal uses the PortalMod antline-indicator activation/deactivation sounds when its light changes state.

### Encased Diversity Vent

A one-block-long vent with a full outer shell. Internally it behaves like a normal straight vent segment, but it is intended as a visually enclosed section.

- It cannot be extended into a two-long section or rerouted into a bend.
- It has no wrench configuration.

### Diversity Vent Scanner

Detects supported traffic passing through its vent section and emits a short activation pulse to connected PortalMod antlines.

- Wrench-click switches the detection target between **PortalMod cubes** and **players**.
- When a new matching target crosses the scanner, it activates for one second (20 ticks).
- Activation uses `portalmod:block.antline_indicator.activate`.
- Deactivation uses `portalmod:block.antline_indicator.deactivate`.

### Diversity Vent Junction

A powered T-junction that chooses between a straight route and a 90-degree branch. Internally it remains a two-ended vent edge rather than creating a permanent three-way graph split.

- Place it as part of a vent network to establish the straight axis and alternate branch.
- Crouch-place a compatible vent item against an unused side face to reorient the branch toward that side while continuing the route.
- Wrench-click toggles which of the two straight axial faces is the fixed side.
- Crouch-wrench toggles whether the **straight** or **turn** route is the default while unpowered.
- Connected PortalMod indicator/antline power swaps the active route away from the configured unpowered default.

## Block materials and particles

The block families intentionally use different Minecraft material sound profiles:

- **Glass sounds:** vent segments, junctions, scanners.
- **Metal sounds:** terminals, impellers, encased vents.

Breaking particles use dedicated placeholder textures under `textures/block/vent/break/`:

- `glass.png` — segments, junctions, scanners.
- `case.png` — terminals, impellers.
- `frame.png` — encased vents.

These textures can be replaced without changing block code or blockstates.

## Build

```sh
./gradlew build
```

The reobfuscated mod JAR is produced under `build/libs/`.

## Development

The mod ID and resource namespace are `pneumaticdiversityvents`.

`ARCHITECTURE.md` documents the network model, placement/update flow, transport system, external forces, PortalMod integration, and extension points. Junction blockstate/model assets can be regenerated with `tools/generate_junction_assets.py`.

## Project structure

- `src/main/java/.../client` — client rendering, sounds, inspection UI, and smoke particles.
- `src/main/java/.../server` — network topology, world updates, transport, external forces, and integrations.
- `src/main/java/.../shared` — blocks, items, common state, geometry, and shared world types.
- `src/main/resources` — blockstates, models, textures, language, recipes, tags, particles, and advancements.
- `tools` — deterministic resource-generation helpers.

## License

Pneumatic Diversity Vents may be used, modified, and redistributed under the terms in `LICENSE.txt`, provided appropriate credit is given to Benjamin Hume as the original author.
