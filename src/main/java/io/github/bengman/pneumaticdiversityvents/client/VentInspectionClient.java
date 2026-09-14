package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.PneumaticDiversityVents;
import io.github.bengman.pneumaticdiversityvents.networking.VentInspectionPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.portalmod.common.items.WrenchItem;

import java.util.Locale;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

/** Wrench-only airflow inspector using pixel-art HUD assets. */
@Mod.EventBusSubscriber(modid = PneumaticDiversityVents.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VentInspectionClient {
    private static final ResourceLocation ARROW = new ResourceLocation(PneumaticDiversityVents.MOD_ID, "textures/gui/vent_inspection_arrow.png");
    private static final ResourceLocation PERSON_CAPABLE = new ResourceLocation(PneumaticDiversityVents.MOD_ID, "textures/gui/vent_inspection_person_capable.png");
    private static final ResourceLocation PERSON_INCAPABLE = new ResourceLocation(PneumaticDiversityVents.MOD_ID, "textures/gui/vent_inspection_person_incapable.png");
    private static final int ARROW_W = 24, ARROW_H = 11;
    private static final int PERSON_W = 9, PERSON_H = 13;

    private static VentInspectionPacket snapshot = VentInspectionPacket.hidden();
    private static int receivedTick = Integer.MIN_VALUE;

    private VentInspectionClient() {
    }

    public static void accept(VentInspectionPacket packet) {
        snapshot = packet;
        Minecraft minecraft = Minecraft.getInstance();
        receivedTick = minecraft.player == null ? Integer.MIN_VALUE : minecraft.player.tickCount;
    }

    @SubscribeEvent
    public static void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !snapshot.isVisible() || !WrenchItem.holdingWrench(minecraft.player)) return;
        if (minecraft.player.tickCount - receivedTick > 3) return;

        MatrixStack matrix = event.getMatrixStack();
        int cx = minecraft.getWindow().getGuiScaledWidth() / 2;
        int cy = minecraft.getWindow().getGuiScaledHeight() / 2 + 24;
        if (snapshot.isBlocked()) {
            String blocked = "BLOCKED";
            int x = cx - minecraft.font.width(blocked) / 2;
            minecraft.font.drawShadow(matrix, blocked, x, cy - 4, 0xFFFF4B4B);
            minecraft.font.drawShadow(matrix, blocked, x + 1, cy - 4, 0xFFFF4B4B);
            return;
        }

        if (snapshot.isNoFlow()) {
            String noFlow = "NO NET AIRFLOW";
            minecraft.font.draw(matrix, noFlow, cx - minecraft.font.width(noFlow) / 2.0F, cy - 4, 0xFFFFFFFF);
            return;
        }

        drawCapabilityIcon(matrix, minecraft, cx - 34, cy, snapshot.isPlayerCapable());
        drawDirectionArrow(matrix, minecraft, cx, cy, snapshot.getDirection(), minecraft.player.getViewVector(1.0F));
        String speed = String.format(Locale.ROOT, "%.1f b/s", snapshot.getBlocksPerSecond());
        minecraft.font.drawShadow(matrix, speed, cx + 19, cy - 4, 0xFFFFFFFF);
    }

    private static void drawCapabilityIcon(MatrixStack matrix, Minecraft minecraft, int x, int y, boolean capable) {
        minecraft.getTextureManager().bind(capable ? PERSON_CAPABLE : PERSON_INCAPABLE);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        AbstractGui.blit(matrix, x - PERSON_W / 2, y - PERSON_H / 2, 0.0F, 0.0F, PERSON_W, PERSON_H, PERSON_W, PERSON_H);
    }

    private static void drawDirectionArrow(MatrixStack matrix, Minecraft minecraft, int x, int y, Vector3d flow, Vector3d view) {
        Vector3d worldUp = new Vector3d(0.0D, 1.0D, 0.0D);
        Vector3d right = view.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) right = new Vector3d(1.0D, 0.0D, 0.0D);
        else right = right.normalize();
        Vector3d up = right.cross(view).normalize();
        double sx = flow.dot(right), sy = flow.dot(up);
        if (sx * sx + sy * sy < 0.02D) {
            sx = 0.0D;
            sy = flow.dot(view) >= 0.0D ? -1.0D : 1.0D;
        }
        float angle = (float) Math.toDegrees(Math.atan2(-sy, sx));

        minecraft.getTextureManager().bind(ARROW);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        matrix.pushPose();
        matrix.translate(x, y, 0.0D);
        matrix.mulPose(Vector3f.ZP.rotationDegrees(angle));
        AbstractGui.blit(matrix, -ARROW_W / 2, -ARROW_H / 2, 0.0F, 0.0F, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
        matrix.popPose();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        snapshot = VentInspectionPacket.hidden();
        receivedTick = Integer.MIN_VALUE;
    }
}
