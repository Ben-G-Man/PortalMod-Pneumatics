/* Owns and reconciles the pure vent graph without any Minecraft world-space knowledge. */
package io.github.bengman.pneumaticdiversityvents.server.network;

import io.github.bengman.pneumaticdiversityvents.shared.VentImpellerControlMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VentNetworkManager {
    private final Map<UUID, VentEdge> edgesById = new HashMap<>();
    private final Map<UUID, VentConnection> connectionsById = new HashMap<>();
    private final Map<UUID, VentNetwork> networksById = new HashMap<>();

    public VentEdge getEdge(UUID id) {
        return edgesById.get(id);
    }

    public VentConnection getConnection(UUID id) {
        return connectionsById.get(id);
    }

    public Collection<VentEdge> getEdges() {
        return Collections.unmodifiableCollection(edgesById.values());
    }

    public Collection<VentNetwork> getNetworks() {
        return Collections.unmodifiableCollection(networksById.values());
    }

    public void addEdge(VentEdge edge) {
        if (edgesById.containsKey(edge.getId())) throw new IllegalStateException("Vent edge UUID already registered: " + edge.getId());
        for (VentConnection connection : edge.getConnections()) {
            if (connectionsById.containsKey(connection.getId())) throw new IllegalStateException("Vent connection UUID already registered: " + connection.getId());
        }
        edgesById.put(edge.getId(), edge);
        for (VentConnection connection : edge.getConnections()) connectionsById.put(connection.getId(), connection);
    }

    public Set<VentEdge> removeEdge(UUID id) {
        VentEdge edge = edgesById.remove(id);
        if (edge == null) return Collections.emptySet();
        Set<VentEdge> affected = new HashSet<>();
        for (VentConnection connection : edge.getConnections()) {
            if (connection.getPeer() != null) {
                VentConnection peer = connection.getPeer();
                affected.add(peer.getParent());
                peer.setPeer(null);
                connection.setPeer(null);
            }
            connectionsById.remove(connection.getId());
        }
        edge.setNetwork(null);
        pruneNetworks();
        return affected;
    }

    public void connect(UUID aId, UUID bId) {
        VentConnection a = requireConnection(aId), b = requireConnection(bId);
        if (a == b || a.getParent() == b.getParent()) throw new IllegalArgumentException("Cannot connect a vent edge to itself.");
        if (a.getPeer() == b && b.getPeer() == a) return;
        if (a.getPeer() != null || b.getPeer() != null) throw new IllegalStateException("Vent connection is already connected.");
        a.setPeer(b);
        b.setPeer(a);
    }

    public void rebuildAll() {
        rebuildFrom(new HashSet<>(edgesById.values()));
    }

    public void rebuildFrom(Collection<VentEdge> seeds) {
        Set<VentEdge> liveSeeds = seeds.stream().filter(Objects::nonNull).filter(edge -> edgesById.get(edge.getId()) == edge).collect(Collectors.toSet());
        if (liveSeeds.isEmpty()) {
            pruneNetworks();
            return;
        }

        List<Set<VentEdge>> components = new ArrayList<>();
        Set<VentEdge> seen = new HashSet<>();
        for (VentEdge seed : liveSeeds) {
            if (seen.contains(seed)) continue;
            Set<VentEdge> component = collectComponent(seed);
            seen.addAll(component);
            components.add(component);
        }
        components.sort(Comparator.comparing(this::componentKey));
        for (Set<VentEdge> component : components) orientComponent(component);
        assignNetworks(components);
        recalculateForComponents(components);
        pruneNetworks();
    }

    private VentConnection requireConnection(UUID id) {
        VentConnection connection = connectionsById.get(id);
        if (connection == null) throw new IllegalArgumentException("Unknown vent connection: " + id);
        return connection;
    }

    private Set<VentEdge> collectComponent(VentEdge start) {
        Set<VentEdge> result = new HashSet<>();
        Deque<VentEdge> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            VentEdge edge = queue.removeFirst();
            if (!result.add(edge)) continue;
            if (edge.getA().getPeer() != null) queue.add(edge.getA().getPeer().getParent());
            if (edge.getB().getPeer() != null) queue.add(edge.getB().getPeer().getParent());
        }
        return result;
    }

    private void orientComponent(Set<VentEdge> component) {
        VentConnection start = component.stream().flatMap(edge -> java.util.Arrays.stream(edge.getConnections()))
                .filter(connection -> connection.getPeer() == null)
                .min(Comparator.comparing((VentConnection c) -> c.getParent().getId()).thenComparing(VentConnection::getId)).orElse(null);
        if (start == null) start = component.stream().min(Comparator.comparing(VentEdge::getId)).orElseThrow(IllegalStateException::new).getA();

        VentConnection entry = start;
        Set<VentEdge> visited = new HashSet<>();
        while (entry != null && component.contains(entry.getParent()) && visited.add(entry.getParent())) {
            VentEdge edge = entry.getParent();
            VentConnection exit = edge.getOther(entry);
            edge.setAToB(entry == edge.getA());
            entry = exit.getPeer();
        }
    }

    private UUID componentKey(Set<VentEdge> component) {
        return component.stream().map(VentEdge::getId).min(Comparator.naturalOrder()).orElseThrow(IllegalStateException::new);
    }

    private void assignNetworks(List<Set<VentEdge>> components) {
        Map<VentNetwork, List<Set<VentEdge>>> owners = new HashMap<>();
        for (Set<VentEdge> component : components) {
            for (VentNetwork network : component.stream().map(VentEdge::getNetwork).filter(Objects::nonNull).collect(Collectors.toSet())) {
                owners.computeIfAbsent(network, ignored -> new ArrayList<>()).add(component);
            }
        }

        Set<VentNetwork> retained = new HashSet<>();
        for (Set<VentEdge> component : components) {
            List<VentNetwork> candidates = component.stream().map(VentEdge::getNetwork).filter(Objects::nonNull).distinct()
                    .sorted(Comparator.comparing(VentNetwork::getId)).collect(Collectors.toList());
            VentNetwork network = null;
            for (VentNetwork candidate : candidates) {
                Set<VentEdge> firstOwner = owners.get(candidate).stream().min(Comparator.comparing(this::componentKey)).orElse(null);
                if (firstOwner == component && retained.add(candidate)) {
                    network = candidate;
                    break;
                }
            }

            if (network == null) {
                network = new VentNetwork();
                networksById.put(network.getId(), network);
            }
            for (VentEdge edge : component) edge.setNetwork(network);
        }
    }

    public void invertForceSource(VentEdge edge) {
        VentForceSource source = requireForceSource(edge);
        int contribution = activeContribution(edge);
        source.invert();
        if (edge.getNetwork() != null && contribution != 0) edge.getNetwork().setRawNetForce(edge.getNetwork().getRawNetForce() - 2 * contribution);
    }

    public boolean updateForceSourceControl(VentEdge edge, boolean controlled, boolean powered) {
        VentForceSource source = requireForceSource(edge);
        boolean modeReset = !controlled && source.getControlMode() != VentImpellerControlMode.ON_OFF;
        boolean changed = source.setAntlineState(controlled, powered);
        if (modeReset) {
            source.setControlMode(VentImpellerControlMode.ON_OFF);
            changed = true;
        }
        if (changed) recalculateForce(edge.getNetwork());
        return changed;
    }

    public boolean cycleForceSourceControlMode(VentEdge edge) {
        VentForceSource source = requireForceSource(edge);
        if (!source.isAntlineControlled()) return false;
        source.setControlMode(source.getControlMode().next());
        recalculateForce(edge.getNetwork());
        return true;
    }

    public void recalculateForce(VentNetwork network) {
        if (network == null) return;
        int force = 0, active = 0;
        for (VentEdge edge : edgesById.values()) {
            if (edge.getNetwork() != network) continue;
            int contribution = activeContribution(edge);
            if (contribution == 0) continue;
            active++;
            force += contribution;
        }
        network.setRawNetForce(force);
        network.setActiveForceSources(active);
    }

    private VentForceSource requireForceSource(VentEdge edge) {
        if (edge == null || edgesById.get(edge.getId()) != edge || edge.getForceSource() == null)
            throw new IllegalArgumentException("Vent edge does not contain a registered force source.");
        return edge.getForceSource();
    }

    private int activeContribution(VentEdge edge) {
        VentForceSource source = edge.getForceSource();
        return source == null || !source.isEnabled() || (!edge.getA().isConnected() && !edge.getB().isConnected()) ? 0 : signedContribution(edge);
    }

    private int signedContribution(VentEdge edge) {
        VentForceSource source = edge.getForceSource();
        return source.isAToB() == edge.isAToB() ? source.getUnits() : -source.getUnits();
    }

    public void recalculateAllForces() {
        for (VentNetwork network : networksById.values()) recalculateForce(network);
    }

    private void recalculateForComponents(List<Set<VentEdge>> components) {
        Set<VentNetwork> touched = new HashSet<>();
        for (Set<VentEdge> component : components) {
            for (VentEdge edge : component) if (edge.getNetwork() != null) touched.add(edge.getNetwork());
        }
        for (VentNetwork network : touched) recalculateForce(network);
    }

    private void pruneNetworks() {
        Set<VentNetwork> live = edgesById.values().stream().map(VentEdge::getNetwork).filter(Objects::nonNull).collect(Collectors.toSet());
        networksById.values().removeIf(network -> !live.contains(network));
    }
}
