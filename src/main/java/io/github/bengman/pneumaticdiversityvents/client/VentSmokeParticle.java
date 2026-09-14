package io.github.bengman.pneumaticdiversityvents.client;

import io.github.bengman.pneumaticdiversityvents.shared.VentBlock;

import net.minecraft.client.particle.IAnimatedSprite;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteTexturedParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/** Campfire-smoke visuals with only one custom behaviour: stop at the vent mouth. */
public final class VentSmokeParticle extends SpriteTexturedParticle {
    private final IAnimatedSprite sprites;

    private VentSmokeParticle(ClientWorld world, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, IAnimatedSprite sprites) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);

        this.sprites = sprites;
        this.xd = velocityX;
        this.yd = velocityY;
        this.zd = velocityZ;
        this.lifetime = 40;
        this.quadSize *= 3.0F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        double nextX = this.x + this.xd;
        double nextY = this.y + this.yd;
        double nextZ = this.z + this.zd;
        if (isVent(nextX, nextY, nextZ)) {
            this.remove();
            return;
        }

        this.move(this.xd, this.yd, this.zd);
        this.setSpriteFromAge(this.sprites);
    }

    private boolean isVent(double x, double y, double z) {
        return this.level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof VentBlock;
    }

    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory implements IParticleFactory<BasicParticleType> {
        private final IAnimatedSprite sprites;

        public Factory(IAnimatedSprite sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(BasicParticleType type, ClientWorld world, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ) {
            return new VentSmokeParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.sprites);
        }
    }
}
