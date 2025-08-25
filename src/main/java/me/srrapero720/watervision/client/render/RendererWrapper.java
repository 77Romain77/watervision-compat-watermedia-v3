package me.srrapero720.watervision.client.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import me.srrapero720.watervision.WaterVision;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.watermedia.api.image.ImageRenderer;

@OnlyIn(Dist.CLIENT)
public class RendererWrapper extends AbstractTexture {
    private final ImageRenderer renderer;
    private final GlTexture[] glTextures;

    public RendererWrapper(final ImageRenderer imageRenderer) {
        super();
        this.renderer = imageRenderer;
        this.glTextures = new GlTexture[this.renderer.textures.length];
        for (int i = 0; i < this.glTextures.length; i++) {
            this.glTextures[i] = new ExternalGlTexture(this.renderer.width, this.renderer.height, this.renderer.texture(i));
        }
        this.texture = this.glTextures[0];
    }

    @Override
    public void setFilter(final boolean p_117961_, final boolean p_117962_) {
        this.getTexture();
        super.setFilter(p_117961_, p_117962_);
    }

    @Override
    public void setClamp(final boolean p_377282_) {
        this.getTexture();
        super.setClamp(p_377282_);
    }

    @Override
    public void setBlurMipmap(final boolean blur, final boolean mipmap) {
        this.getTexture();
        super.setBlurMipmap(blur, mipmap);
    }

    @Override
    public @NotNull GpuTexture getTexture() {
        final int id = this.renderer.texture(WaterVision.getTicks(), WaterVision.deltaFrames(), true);
        for (int i = 0; i < this.renderer.textures.length; i++) {
            if (this.glTextures[i].glId() == id) {
                return this.texture = this.glTextures[i];
            }
        }
        return this.texture = this.glTextures[0];
    }

    @Override
    public void close() {
        // NO OP
    }
}
