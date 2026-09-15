# Pneumatic Diversity Vents Architecture

Fast-start context for developers and AI agents working on the Minecraft 1.16.5 Forge mod.

## Core rule

**`server/network` never knows about Minecraft world space.** It owns abstract non-branching topology and cached signed impeller force only. `BlockPos`, blockstates, endpoint coordinates, persistence, transport, external physics, networking and rendering live outside that package.

Each `VentEdge` has exactly two `VentConnection`s; each connection has at most one peer. Networks are therefore paths or cycles, never junction graphs.

## Placement / mutation flow

1. `VentItem.place()` delegates to `VentPlacementUtil`.
2. `ShortVentBuilder`, `LongVentBuilder` and `BendVentBuilder` plan physical blocks plus two world endpoints.
3. `VentAddition`, `VentExtension`, `VentReroute` and `VentRemoval` are queued in `VentUpdateQueue`.
4. Updates execute removal-before-addition deterministically at server tick end.
5. `WorldVentManager` applies blocks and registers/unregisters edges in `VentSpatialRegistry`.
6. `VentSpatialRegistry` matches physically coincident endpoints and tells `VentNetworkManager` which abstract connections meet.
7. `VentNetworkManager` rebuilds affected components, orients them, retains network identities where possible, and recomputes force as a topology consistency backstop.

## Physical endpoints

`WorldVentConnection` stores integer half-block coordinates so `.5` positions remain exact, but the current v5 convention places every endpoint at the **true center of its 2x2 opening face**.

Examples in doubled coordinates:

- X endpoint cross-section center: `(xBoundary, y + 2, z + 2)`
- Y endpoint cross-section center: `(x + 2, yBoundary, z + 2)`
- Z endpoint cross-section center: `(x + 2, y + 2, zBoundary)`

This same position is used for endpoint matching, transport openings, external-force origins and future endpoint effects. v2-v4 saves are normalized to this convention on load; the convention was introduced in persistence v5 and remains current.

## Impeller / network force

`VentForceSource` is pure edge metadata:

- `units` — currently `2` per impeller; registry v8 migrates older persisted impellers to this value.
- `baseAToB` — the wrench-controlled physical A->B polarity.
- `controlMode` — persisted Portal-style powered behavior (`ON_OFF` or `REVERSE_WHEN_POWERED`).
- `antlineControlled` / `powered` — derived runtime state from nearby PortalMod `TestElementActivator`s and intentionally not persisted.

Without a nearby activator the impeller is always enabled. With one or more activators it uses PortalMod semantics: **all present activators must be active** for the impeller to be considered powered. In `ON_OFF`, unpowered contributes zero force and powered contributes normally. In `REVERSE_WHEN_POWERED`, it contributes in both states but the powered state XOR-inverts the base wrench polarity. Removing every activator resets the mode to `ON_OFF` and immediately restores always-on behavior.

`VentNetwork.rawNetForce` is cached and signed relative to the manager's current canonical traversal. `getNetForce()` exposes the **effective** force: it returns the raw value normally and `0` while the network is blocked. Blockage is derived from current world geometry every server tick and is never persisted or represented as topology. Physical/raw force remains stable even if a split/join causes canonical traversal to reverse because local edge orientation is projected together with the network sign.

Examples:

- `+2 +2 -2 = +2`
- `+2 -2 = 0`
- 10 downstream and 8 upstream = net force `2`, not `18`.

Wrench inversion is O(1): flip the base source polarity then adjust cached **raw** network force by twice its previous signed contribution. Antline power/mode changes recalculate only the containing network. Topology rebuilds perform a full affected-network force recomputation.

`VentSpatialRegistry` checks the complete four-block impeller footprint once per server tick. Every cell in the one-block shell around the full 2x2x1 footprint is a valid controller location, including face-adjacent, above/below and corner-offset positions. Any PortalMod `TestElementActivator` there counts, not just a hard-coded indicator block class. This mirrors PortalMod's test-element contract and keeps compatibility with other activator types.

An enabled impeller only counts as active when its edge connects to at least one other edge. `VentSpatialRegistry.getLocalForce(pos)` exposes the network result in the containing edge's physical A->B convention.

## Deterministic in-vent transport

Internal movement is intentionally **not acceleration physics**. `VentTransportManager` captures an entity onto `(edgeId, distanceAlongPath)` and advances the authoritative path distance every server-world tick.

`VentPath` uses the real physical centerline:

- straight edges: exact opening-center line;
- bends: radius-1 quarter-circle through the 2x2x2 elbow;
- open boundaries: short virtual centerline continuations for clean entry/exit.

Transport speed depends only on absolute net force and is identical for everything currently transported in the same network:

- force 1: `0.40` blocks/tick;
- linear increase through force 12;
- force 12+: `2.00` blocks/tick cap.

Non-player entities require `netForce != 0`. Players require three aligned impellers: `MINIMUM_IMPELLERS_TO_TRANSPORT_PLAYER = 3`, deriving `MINIMUM_FORCE_TO_TRANSPORT_PLAYER = 6` from `VentImpellerBlock.FORCE_UNITS = 2`. Creative players who are actively flying are explicitly excluded from deterministic capture/continued transport and from external vent acceleration. At zero net force, transport releases immediately back to normal Minecraft physics.

While captured, normal collision/gravity and mob AI are temporarily suppressed; projectiles have impact events canceled. Players use velocity-synchronized movement for smooth rendering while the path distance remains authoritative. Open-end exits clear the shell before release and receive current transport velocity plus a small exit bonus.

The deterministic mouth handoff extends `0.85` blocks outside the opening with a `0.95` center radius. This intentionally reaches a standing player's center before shell collision stops them; external suction brings them into that handoff and deterministic transport then takes over.

## Player transport presentation

Moving players use vanilla fall-flying state so their body can point along the travel tangent, including vertical/downward travel, while camera yaw/pitch remain under player control. Survival/Creative crouch braking exits fall-flying and uses ordinary crouching; Adventure cannot brake.

Original collision/gravity/AI/pose/fall-flying/swimming state is restored on release.

## External endpoint force system

External suction/blowing is ordinary additive Minecraft motion, separate from deterministic in-vent transport. Standard profiles are intentionally strong enough to overcome normal player gravity/friction near the mouth.

### Field lifecycle

`VentSpatialRegistry.getOpenForceEndpoints()` returns immutable snapshots for powered open endpoints. Each snapshot contains:

- connection / edge / network IDs;
- true mouth center;
- outward face direction;
- intake vs exhaust role derived from physical flow direction;
- signed network force;
- deterministic endpoint block used to select the section's field profile;
- owned edge blocks ignored by airflow occlusion rays.

`VentExternalFieldManager` converts snapshots into runtime fields.

- Invalid/occupied endpoints are removed immediately on the next server-world tick. Structurally valid blocked endpoints remain represented with effective force `0`, so removing an obstruction restores an already-established field without waiting through another rebuild debounce.
- Newly appearing structural fields are debounced for **40 ticks** so rapid building does not repeatedly create endpoint effects.
- Force magnitude/polarity changes on an already-existing endpoint update without rebuilding unrelated topology.
- Runtime field state is per `ServerWorld` and discarded on world unload.

### Field geometry

A field is an analytic **square frustum**, not a stored block grid.

At outward distance `d`:

`halfWidth(d) = 1 + d * tan(spread)`

Therefore spread `0°` is exactly a 2x2 rectangular prism, while positive spread expands from the complete 2x2 mouth.

Standard profiles live in `VentExternalFieldProfiles`:

- intake: range `5.5`, spread `25°`, base acceleration `0.115`, radial steering `0.58`;
- exhaust: range `5.0`, spread `15°`, base acceleration `0.100`, radial steering `0.30`.

Focused terminal profiles remain deliberately stronger/narrower: intake `10 / 8° / 0.185`, exhaust `8 / 6° / 0.165`.

Force strength scales from 1x at one aligned impeller (2 force units) to 5x at six aligned impellers (12 force units), matching the internal transport progression/cap. Smooth distance and lateral falloff make force strongest near the mouth/center and zero at the field limits.

Intake direction is predominantly `-outward` plus steering toward visible mouth apertures. Exhaust direction is predominantly `+outward` with weaker radial divergence.

### Multiple fields

Fields never mutate entities while being iterated. Every applicable field first contributes a `Vector3d` into a per-entity accumulator; the final summed vector is added to velocity once.

This makes overlapping fields order-independent:

- equal opposing fields cancel;
- perpendicular fields combine diagonally;
- any number of overlapping fields can contribute.

External physics leaves gravity, collision, mob AI and normal player controls enabled. Players retain normal controls in the external field, although the standard powered field is deliberately strong near the mouth. Just before an intake mouth, deterministic transport captures eligible entities before shell collision can stop them; players immediately receive the forced tube-ride pose/no-collision state. A field below player threshold cannot move a player directly **or indirectly through a vehicle carrying that player**.

### Broadphase / caching cadence

Expensive work intentionally runs slower than force application:

- desired endpoint reconciliation: every server-world tick;
- entity AABB broadphase: every **2 ticks** per field;
- four-aperture occlusion refresh: every **4 ticks** per entity/field pair;
- cached vector evaluation/application: every tick.

Server-authored external motion marks `Entity.hasImpulse`; local players additionally receive `SEntityVelocityPacket` so suction remains responsive while player movement is client-driven.

## Occlusion / airflow transparency

Each 2x2 mouth is represented by four aperture sample points, one per 1x1 quadrant. For every entity/field pair, visibility is a four-bit mask.

Each visible aperture contributes one quarter of full field strength and also influences steering. This means blocking three mouth quadrants naturally leaves approximately quarter-strength airflow directed through the surviving quadrant.

`VentOcclusionTester` performs a bounded voxel DDA from entity center to each aperture and tests actual collision `VoxelShape.clip(...)` intersections. Exact multi-axis boundary ties advance simultaneously so a block touched only at a mathematical corner cannot falsely occlude airflow.

Blocks in `pneumaticdiversityvents:force_transparent` are skipped by airflow rays but keep their normal collision. The default datapack tag contains:

- `minecraft:iron_bars`
- `#minecraft:fences`

Modpacks can extend this tag without Java changes.

## Endpoint-specific profiles / focusing terminal

`VentBlock` implements `VentExternalFieldProfileProvider`. `VentExternalFieldManager` asks the physical endpoint section for its intake/exhaust profile; it does not switch on concrete block classes.

The **Diversity Concentration Terminal** returns the focused `TERMINAL_INTAKE` / `TERMINAL_EXHAUST` profiles. Its rear endpoint is the only connectable graph endpoint; its outward endpoint is permanently open/non-connectable and therefore always remains a possible network field end.

The terminal still occupies a 2x2x2 placement footprint while the collision shell is exactly **1.5 blocks long**. `VentTerminalBlock.OUTWARD_POSITIVE` identifies which of the two physical layers is the open tip; that layer intersects the shared specialized long-shell corner collision with only its inward half-block.

## Particle visualization

Particles are server-authored from the same **desired `VentExternalField` objects** used by physics. There is no client field cache or field-descriptor packet anymore.

- ordinary fields emit on every second tick, averaging **0.375 particles/tick** before portal density scaling; portal fields are sparser again at 65% of that density;
- samples are biased toward the mouth and toward the cone interior, with occasional boundary samples so the frustum remains readable;
- particle velocity scales with the network transport-speed curve plus a 1.8x visual multiplier, so external dust more closely communicates tube speed;
- force dust uses a deliberately minimal client particle with the vanilla campfire-smoke sprite set; its only custom behaviour is removing itself before stepping into any `VentBlock`, so intake smoke terminates at the tube entry plane instead of visibly travelling down the tube;
- particle state is ephemeral and never persisted.

## PortalMod integration

`PortalVentBridge` is the only PortalMod-specific adapter. The rest of the vent graph/physics code remains PortalMod-agnostic.

For every ordinary desired vent field, the bridge finds open PortalMod portals intersecting the field and samples the actual vent acceleration at the source portal. Airflow occlusion is honored, except for the portal's own supporting blocks. Only the force component perpendicular to the portal plane is transmitted.

PortalMod's own `PortalEntity.teleportVector(...)` rotates that normal force through the portal pair. The paired portal then receives a derived square-frustum field with:

- **30 degree** spread;
- range from **2.5 to 6 blocks**, scaled by how much of the source field remains at the source portal;
- mouth acceleration derived from the sampled source acceleration;
- polarity determined by the transformed vector (intake stays suction where appropriate; exhaust emerges as exhaust);
- its backing/supporting blocks ignored for destination airflow occlusion;
- no recursive portal-to-portal derivation, preventing feedback loops.

Portal entities themselves are excluded from vent acceleration; PortalMod handles entity teleportation/velocity transformation. Other entities, including PortalMod props such as cubes/turrets, remain ordinary external-force candidates.

## Networking

`VentNetworkChannel` carries ambient-sound snapshots plus a tiny wrench-inspection snapshot. Force particles and force physics remain entirely server-authored, so the obsolete field-descriptor packet/client particle cache are gone. Inspection packets are sent only while a player is actually holding PortalMod's wrench.

## Threading

The live `VentNetworkManager` graph remains server-thread-only. Rendering uses synchronized immutable `VentSpatialRegistry` snapshots and never live graph collections.

Old-save force/blockstate migration runs only through `prepareForceState(ServerWorld)` on the server thread. External runtime field state and force-particle spawning are server-thread-owned; only ambient-sound and wrench-inspection snapshots cross the mod network channel.

## Persistence

`VentSpatialRegistry` remains `WorldSavedData` named `diversity_vent_segments` and writes **version 7**.

Persisted per edge:

- edge UUID;
- connection UUIDs and true-center world coordinates;
- physical blocks;
- traversal orientation;
- optional impeller `VentForceSource` (`Units`, base physical `AToB`, `ControlMode`).

Network membership, cached net force, transport state and external fields are derived runtime state and are never persisted as authoritative values.

## Important APIs

- `VentSpatialRegistry.get(world)` — persistent per-dimension world/topology bridge.
- `getOpenForceEndpoints()` — immutable powered-open-end snapshots for external physics.
- `getEdge(pos/id)`, `getBlocks(edgeId)`, `getWorldConnection(id)` — ownership/physical metadata.
- `getNetworkForce(pos)` / `getLocalForce(pos)` — canonical/local signed force.
- `invertImpeller(world, pos)` — O(1) base-polarity update plus synchronized physical blockstate refresh.
- `isImpellerAntlineControlled(pos)` / `getImpellerControlMode(pos)` / `cycleImpellerControlMode(world, pos)` — Portal-style test-element configuration.
- `VentTransportManager.getTransportSpeed(netForce)` — shared internal speed curve.
- `VentTransportManager.MINIMUM_IMPELLERS_TO_TRANSPORT_PLAYER` — currently `3`; `MINIMUM_FORCE_TO_TRANSPORT_PLAYER` is derived from it and `VentImpellerBlock.FORCE_UNITS` (currently `6` net units).
- `VentExternalFieldManager` — debounced fields, cached visibility and vector accumulation.
- `VentExternalFieldProfileProvider` — endpoint-type customization seam.
- `VentOcclusionTester` — airflow line-of-sight using real collision shapes + transparent tag.

## Force visualization, audio, drops, terminal, and portals (2026-09-10)

- Survival drops are issued once per abstract edge: short=1 segment, long/bend=2 segments, impeller=1 impeller, terminal=1 terminal, encased=1 encased vent, scanner=1 scanner, junction=1 junction. Creative destruction does not drop.
- Tube blocks use glass sounds/dark-glass break particles; impellers and terminals use metal sounds. Pickaxes are preferred/effective, but recovery is not gated on tool correctness.
- Client-owned ambient loops follow server snapshots immediately. Nearest-tube wind alone scales pitch/volume with effective network force; overcharged networks rattle; normal enabled impellers use constant motor pitch/volume; blocked-network impellers deliberately keep running with the same motor sample at lower volume and much higher pitch to communicate strain. Portal-derived cones emit their own constant-character subtle whoosh with distance fade. Network wind is the only ambience whose pitch/volume scales continuously with force.
- Impellers expose five texture profiles without duplicating geometry: `always_on`, powered ON/OFF blue/orange variants, and powered reverse blue/orange variants. Texture-only child models inherit the canonical normal/mirrored geometry.
- `VentImpellerBlock.DIRECTION` stores only the configured/base polarity. Powered reverse never rotates or mirrors the physical model; `VISUAL` swaps the light/blade textures, and `impeller_blade_reverse` reverses the animation frame order for acting reverse polarity.
- `VentImpellerBlock.ACTIVE` is derived from effective source state; `VISUAL` is derived from control mode + powered state.
- Creative pick-block maps every ordinary short/long/bend corner to the generic segment item and specialized blocks to their own items.
- `TerminalVentBuilder` stores one rear connectable endpoint and one permanently open outward endpoint. Its focused field profile is selected through `VentExternalFieldProfileProvider`.
- `PortalVentBridge` derives non-recursive portal force cones from the actual sampled force at an intersecting source portal and uses PortalMod's own vector transform for orientation/polarity.
- Portal-derived fields retain the directional ~30° / maximum-6-block far frustum. Intake portals additionally union a compact suction-only near-field capture halo around the portal plane/rim, allowing nearby cubes/turrets beside a floor or wall portal to be drawn into it without making the distant field omnidirectional.
- Shift-click rerouting accepts segment, impeller, scanner, terminal and junction items as the new continuation when bending/rerouting an existing 2-long vent. The decorative encased vent is intentionally excluded. The terminal uses `TerminalVentBuilder`; the junction uses `JunctionVentBuilder`; other one-long components use `ShortVentBuilder`. Only the ordinary extendable glass segment participates in short-to-long extension.
- `EncasedVentBlock` is deliberately only a one-long decorative vent edge with the same graph/force behavior as glass tubing and a full 2x2 shell. It has no mirrored state, no long/bend model family, cannot extend, and cannot be used as a shift-reroute continuation.

## Derived network blockage

Blockage is a transient airflow property, not a graph mutation. `VentSpatialRegistry.prepareForceState` checks each exposed network endpoint once per server tick. For a normal mouth, it inspects the four world cells immediately outside the 2x2 opening; the endpoint is blocked only when all four contain non-empty collision shapes. If those four cells belong to one `VentJunctionBlock`, the junction is treated semantically instead: an active/open junction port does not obstruct airflow, while an inactive/closed port does. If either exposed end of a network is blocked, `VentNetwork.isBlocked()` becomes true and effective force is zero.

Raw impeller force remains untouched while blocked. Consequently polarity/configuration changes still update correctly and removing the obstruction restores the same raw flow immediately, without rebuilding network topology. Transport, endpoint physics, portal propagation, particles, network wind/rattle, and terminal capability indicators consume effective force. Impeller motors are the intentional exception: enabled impellers continue running and receive the strained sound profile.

## Terminal indication modes

The focusing terminal stores its wrench-selected presentation directly in the `VISUAL` blockstate; no additional graph persistence is required. The modes are:

- `NORMAL` — unlit regardless of airflow/activity;
- `FORCE` — lit while the containing network has non-zero net force;
- `PLAYER` — lit while the network is capable of carrying players: it has an active field and `VentTransportManager.canTransportPlayer(netForce)`. This is a capability indicator, not an occupancy detector.

`VentTerminalStateManager` resolves the runtime lit/unlit member once per server tick and updates all physical blocks belonging to the terminal edge. The focused force profile itself is unchanged by the indication mode.

## Vent scanner

`VentScannerBlock` is a one-long ordinary graph edge with two wrench-selectable filters: all PortalMod cubes (`Cube`, default) or players. `VentScannerManager` detects both physical occupancy and deterministic edge traversal, so entities moving more than one block per tick cannot tunnel through the sensor without being observed.

A new detection creates/extends a 20-tick pulse, swaps the scanner to its active texture, and drives adjacent PortalMod `AntlineBlock`s through `PortalAntlineBridge`. The bridge tracks scanner source IDs so one scanner ending its pulse cannot deactivate an antline still driven by another scanner. Scanner runtime pulses are not persisted; stale active blockstates are cleared after load.

## Tube movement advancements

`VentAdvancements.grantMaxSpeedRide` awards `pneumaticdiversityvents:too_fast_too_factory` while a player is actually traveling at the capped network speed (`abs(netForce) >= MAX_SPEED_FORCE`). Merely entering the handoff while braking does not award it.

A genuine nonzero open-end launch arms the `pneumaticdiversityvents:dont_tell_osha` fall condition. It is awarded only if the player subsequently dies from `DamageSource.FALL`; touching ground, starting elytra flight, creative flight, mounting/riding an entity, leaving the world, or dying from another source clears the armed state first.

## Survival completeness

Every public vent component has a crafting recipe, creative pick-block mapping, tooltip, creative-tab registration, and edge-aware survival drop. Destruction recovers one specialized item for impeller/terminal/encased/scanner/junction edges, one segment for a one-long ordinary edge, and two segments for an ordinary 2-long/bend. Impeller, terminal, scanner and junction items can be used to bend/reroute an existing 2-long vent; the decorative encased vent cannot.

## Wrench airflow inspector

The old developer force/connection renderer has been removed. There are no force action-bar printouts, world-space connection arrows, or debug connection points in normal gameplay.

`VentInspectionManager` provides the replacement gameplay-facing diagnostic. While a player is holding PortalMod's wrench, the server ray-traces the selected vent edge and, when that edge has non-zero airflow, sends a `VentInspectionPacket` containing:

- local airflow tangent at the selected point;
- deterministic transport speed converted to blocks/second;
- whether the network meets the player transport threshold.

`VentInspectionClient` renders a compact HUD beneath the crosshair: a direction arrow, `b/s` speed text, and a player-capability icon. A selected blocked network instead renders bold red `BLOCKED`, even though its effective force is zero. No inspection packets are sent when the wrench is not held.

## PortalMod antline integration

`VentAntlineConnections` is the single shared definition of valid logical controller/output positions for specialized vent components. It builds the one-block shell around the complete multi-block footprint, so scanner outputs and impeller/junction inputs accept the same placements, including above/offset positions.

For PortalMod-native visual connection behavior, each physical corner block exposes an `AntlineConnector` mounting plane through `getHorsedOn(...)` / `antlineConnectsInDirection(...)`. The scanner additionally implements `AntlineActivator`, allowing directly adjacent antlines to render and query it like a native PortalMod test element. The broader shell remains the logical power-discovery/output area because PortalMod's connector API exposes only one mounting plane per physical block.

## Junction vent

The **Diversity Vent Junction** is physically a 2-long T-piece with three openings but deliberately does **not** introduce branching graph topology. Its rendered T-shell is assembled from the canonical long-segment and inner-bend models; junction-only rendering is limited to a rotation-only quarter-gate over the two conditional exits. At any instant it is represented by one ordinary two-ended `VentEdge`:

- one straight opening is permanently fixed/open;
- the second active endpoint is either the opposite straight opening or the configured 90-degree branch;
- the unused physical opening is closed by the junction's internal doorway state.

`VentJunctionManager` applies a route change as a naive topology replacement: unregister the old edge and register a replacement edge over the same physical blocks with the newly active endpoint. Junction power synchronization runs before force/transport consumers each world tick, so consumers see one coherent topology for that tick. No split/merge traversal algorithm exists.

Configuration is stored in ordinary blockstate properties:

- `BRANCH` — `TOP/RIGHT/BOTTOM/LEFT` relative to the junction axis;
- `FIXED_POSITIVE` — which straight-through side can never close;
- `VISUAL` — active route plus powered/unpowered state.

Normal wrench-click reverses the fixed straight side. Crouch-wrench toggles whether straight-through or 90-degree diversion is the unpowered default. Antline power toggles away from that default. Shift-placement on an unused side face can reorient the alternate branch without adding real branching.

Junction assets are generated by `tools/generate_junction_assets.py`. Gate UV `(0,0)` / the canonical SOUTH-facing top-left corner is defined as the shared 2x2 opening centre. Each visual state has only `model.json` and `model_mirrored.json`; ordinary blockstate X/Y rotations place those two authored quarters on every face and complete the remaining 180-degree roll. This gives all four blocks a common centre point while keeping the asset scheme equivalent in complexity to the long segment. The gate slab is identical from either side.  Canonical shared `specialized_vent_long` / `specialized_vent_long_mirrored` body geometry is inherited rather than copied. The generator derives only two body-wrap textures (unpowered/blue accent and powered/orange accent) from the canonical long frame plus three shared conditional-port profiles: closed/unlit, open/blue, and open/orange. All three port profiles inherit one canonical `junction_port` geometry model; blockstate rotations place that single geometry on all six faces. Therefore the unpowered active conditional port is always blue, the powered active conditional port is always orange, and every inactive conditional port is unlit/closed, regardless of whether straight or turn is the configured default. Route changes use PortalMod's cube-dropper opening/closing sounds.

## Specialized outer shells

The regular transparent glass vent keeps the original thin shell. All specialized sections use the encased/full 2x2 outer envelope while preserving the same inner passage circumference: impeller, terminal, scanner, encased vent, and junction. The terminal still truncates its outward layer so its physical shell extends only 1.5 blocks.

## PortalMod props and tube transport

PortalMod cubes are ordinary scanner/force candidates. External suction does **not** intentionally topple turrets: PortalMod normally changes a grounded turret to `FALLING` when horizontal motion is detected, so `VentExternalFieldManager` narrowly restores suction-moved standing turrets unless deterministic transport has captured them. When a turret actually begins tube transport, Pneumatic Diversity Vents changes it directly to PortalMod's `TurretState.DEAD`, resets the animation state/dimensions, and carries the resulting inert body through the tube.

## Item information

`VentItem` mirrors PortalMod's hold-modifier tooltip convention and honors PortalMod's global tooltip setting. On macOS the prompt says `Command`; elsewhere it says `Ctrl`. Each public vent item keeps its descriptive text in localization resources so copy can change without Java changes.
