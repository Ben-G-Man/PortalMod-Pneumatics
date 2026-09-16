/* User-tunable gameplay values. Forge writes these defaults to config/pneumaticdiversityvents-common.toml. */
package io.github.bengman.pneumaticdiversityvents.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VentCommonConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue MINIMUM_IMPELLERS_TO_TRANSPORT_PLAYER;
    private static final ForgeConfigSpec.IntValue FORCE_UNITS_PER_IMPELLER;
    private static final ForgeConfigSpec.DoubleValue BASE_TRANSPORT_SPEED;
    private static final ForgeConfigSpec.DoubleValue MAXIMUM_TRANSPORT_SPEED;
    private static final ForgeConfigSpec.IntValue FORCE_UNITS_AT_MAXIMUM_SPEED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("transport");
        MINIMUM_IMPELLERS_TO_TRANSPORT_PLAYER = builder
                .comment("Minimum number of aligned, active impellers required before players can be transported by vents or external airflow.")
                .defineInRange("minimum_impellers_to_transport_player", 3, 1, 64);
        FORCE_UNITS_PER_IMPELLER = builder
                .comment("Signed network-force units contributed by each active impeller. Opposing impellers still cancel each other.")
                .defineInRange("force_units_per_impeller", 2, 1, 64);
        BASE_TRANSPORT_SPEED = builder
                .comment("Internal tube speed in blocks/tick at one net force unit. Speed scales linearly toward maximum_transport_speed.")
                .defineInRange("base_transport_speed", 0.40D, 0.01D, 10.0D);
        MAXIMUM_TRANSPORT_SPEED = builder
                .comment("Maximum internal tube speed in blocks/tick.")
                .defineInRange("maximum_transport_speed", 2.00D, 0.01D, 10.0D);
        FORCE_UNITS_AT_MAXIMUM_SPEED = builder
                .comment("Absolute net force at which maximum_transport_speed is reached. Values above this remain capped.")
                .defineInRange("force_units_at_maximum_speed", 12, 2, 1024);
        builder.pop();

        SPEC = builder.build();
    }

    private VentCommonConfig() {
    }

    public static int minimumImpellersToTransportPlayer() {
        return MINIMUM_IMPELLERS_TO_TRANSPORT_PLAYER.get();
    }

    public static int forceUnitsPerImpeller() {
        return FORCE_UNITS_PER_IMPELLER.get();
    }

    public static int minimumForceToTransportPlayer() {
        return minimumImpellersToTransportPlayer() * forceUnitsPerImpeller();
    }

    public static double baseTransportSpeed() {
        return BASE_TRANSPORT_SPEED.get();
    }

    public static double maximumTransportSpeed() {
        return Math.max(baseTransportSpeed(), MAXIMUM_TRANSPORT_SPEED.get());
    }

    public static int forceUnitsAtMaximumSpeed() {
        return FORCE_UNITS_AT_MAXIMUM_SPEED.get();
    }
}
