/* Manual advancement hooks for vent-specific movement conditions that vanilla criteria cannot express cleanly. */
package io.github.bengman.pneumaticdiversityvents.server;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class VentAdvancements {
    private static final ResourceLocation MAX_SPEED_RIDE = new ResourceLocation(PneumaticDiversityVents.MOD_ID, "too_fast_too_factory");
    private static final ResourceLocation FATAL_EXIT_FALL = new ResourceLocation(PneumaticDiversityVents.MOD_ID, "dont_tell_osha");
    private static final Set<UUID> EXIT_FALL_CANDIDATES = new HashSet<>();

    private VentAdvancements() {
    }

    public static void grantMaxSpeedRide(ServerPlayerEntity player) {
        award(player, MAX_SPEED_RIDE, "max_speed_ride");
    }

    /* Armed only by a real open-end launch; ordinary braking/network teardown uses zero release velocity and never reaches this. */
    public static void armExitFall(ServerPlayerEntity player) {
        EXIT_FALL_CANDIDATES.add(player.getUUID());
    }

    /* Any normal interruption of the launch trajectory means a later fall was no longer caused directly by the tube exit. */
    public static void tick(ServerWorld world) {
        for (ServerPlayerEntity player : world.players()) {
            if (!EXIT_FALL_CANDIDATES.contains(player.getUUID())) continue;
            if (player.isOnGround() || player.isFallFlying() || player.isPassenger() || player.abilities.flying)
                EXIT_FALL_CANDIDATES.remove(player.getUUID());
        }
    }

    public static void onDeath(ServerPlayerEntity player, DamageSource source) {
        boolean armed = EXIT_FALL_CANDIDATES.remove(player.getUUID());
        if (armed && source == DamageSource.FALL) award(player, FATAL_EXIT_FALL, "fatal_exit_fall");
    }

    public static void disarmExitFall(ServerPlayerEntity player) {
        EXIT_FALL_CANDIDATES.remove(player.getUUID());
    }

    public static void onPlayerLeaving(ServerPlayerEntity player) {
        disarmExitFall(player);
    }

    private static void award(ServerPlayerEntity player, ResourceLocation id, String criterion) {
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(id);
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }
}
