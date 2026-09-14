/* Keeps vent-travelling players in an Elytra-style body pose aligned to transport while leaving camera look untouched. */
package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;

import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VentPlayerPoseHandler {
    private static final double DIRECTION_EPSILON = 1.0E-5D;
    private static final Map<UUID, Vector3d> LAST_DIRECTIONS = new HashMap<>();
    private static final Map<UUID, RotationSnapshot> RENDER_ROTATIONS = new HashMap<>();

    private VentPlayerPoseHandler() {
    }

    private static boolean isMovingInVent(PlayerEntity player) {
        return player.isNoGravity() && player.getPose() == Pose.FALL_FLYING && player.isFallFlying() && !player.isSwimming();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.level.isClientSide) return;
        PlayerEntity player = event.player;
        if (!isMovingInVent(player)) {
            LAST_DIRECTIONS.remove(player.getUUID());
            return;
        }
        Vector3d motion = player.getDeltaMovement();
        if (motion.lengthSqr() > DIRECTION_EPSILON * DIRECTION_EPSILON) LAST_DIRECTIONS.put(player.getUUID(), motion.normalize());
        player.animationPosition = 0.0F;
        player.animationSpeed = 0.0F;
        player.animationSpeedOld = 0.0F;
        player.attackAnim = 0.0F;
        player.oAttackAnim = 0.0F;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        PlayerEntity player = event.getPlayer();
        if (!isMovingInVent(player)) return;
        Vector3d direction = getDirection(player);
        if (direction == null) return;

        // Vanilla Elytra rendering already turns the body horizontally toward motion. Temporarily substitute only
        // the render pitch with the tube tangent pitch; restore it immediately in Post so the camera remains free.
        RENDER_ROTATIONS.put(player.getUUID(), new RotationSnapshot(player.xRot, player.xRotO));
        float horizontal = MathHelper.sqrt((float) (direction.x * direction.x + direction.z * direction.z));
        float travelPitch = (float) Math.toDegrees(Math.atan2(-direction.y, horizontal));
        player.xRot = travelPitch;
        player.xRotO = travelPitch;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        PlayerEntity player = event.getPlayer();
        RotationSnapshot snapshot = RENDER_ROTATIONS.remove(player.getUUID());
        if (snapshot == null) return;
        player.xRot = snapshot.xRot;
        player.xRotO = snapshot.xRotO;
    }

    private static Vector3d getDirection(PlayerEntity player) {
        Vector3d motion = player.getDeltaMovement();
        if (motion.lengthSqr() > DIRECTION_EPSILON * DIRECTION_EPSILON) {
            Vector3d direction = motion.normalize();
            LAST_DIRECTIONS.put(player.getUUID(), direction);
            return direction;
        }
        return LAST_DIRECTIONS.get(player.getUUID());
    }

    private static final class RotationSnapshot {
        private final float xRot, xRotO;
        private RotationSnapshot(float xRot, float xRotO) {
            this.xRot = xRot;
            this.xRotO = xRotO;
        }
    }
}
