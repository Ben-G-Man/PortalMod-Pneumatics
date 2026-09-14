package io.github.bengman.pneumaticdiversityvents.shared;

import net.minecraft.util.IStringSerializable;

public enum VentTerminalVisualState implements IStringSerializable {
    NORMAL("normal", Mode.NORMAL, false),
    FORCE_OFF("force_off", Mode.FORCE, false),
    FORCE_ON("force_on", Mode.FORCE, true),
    PLAYER_OFF("player_off", Mode.PLAYER, false),
    PLAYER_ON("player_on", Mode.PLAYER, true);

    public enum Mode { NORMAL, FORCE, PLAYER }

    private final String name;
    private final Mode mode;
    private final boolean lit;

    VentTerminalVisualState(String name, Mode mode, boolean lit) {
        this.name = name;
        this.mode = mode;
        this.lit = lit;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isLit() {
        return lit;
    }

    public VentTerminalVisualState withLit(boolean lit) {
        switch (mode) {
            case FORCE: return lit ? FORCE_ON : FORCE_OFF;
            case PLAYER: return lit ? PLAYER_ON : PLAYER_OFF;
            default: return NORMAL;
        }
    }

    public VentTerminalVisualState nextMode() {
        switch (mode) {
            case NORMAL: return FORCE_OFF;
            case FORCE: return PLAYER_OFF;
            default: return NORMAL;
        }
    }
}
