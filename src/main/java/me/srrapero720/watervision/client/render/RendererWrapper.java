package me.srrapero720.watervision.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import me.srrapero720.watervision.WaterVision;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.NotNull;
import org.watermedia.api.image.ImageRenderer;

@Environment(EnvType.CLIENT)
public class RendererWrapper extends AbstractTexture {
    private final ImageRenderer renderer;
    private final GlTexture[] glTextures;

    public RendererWrapper(final ImageRenderer imageRenderer) {
        super();
        this.renderer = imageRenderer;
        this.glTextures = new GlTexture[this.renderer.textures.length];
        for (int i = 0; i < this.glTextures.length; i++) {
            this.glTextures[i] = new GlTexture("rendererwrapper_" + imageRenderer.texture(i), TextureFormat.RGBA8, imageRenderer.width, imageRenderer.height, 1, imageRenderer.texture(i)) {
                @Override public void close() {}
            };
            this.glTextures[i].setTextureFilter(FilterMode.NEAREST, false);
            GlStateManager._bindTexture(0); // RESET
        }
        this.texture = this.glTextures[0];
    }

    @Override
    public @NotNull GpuTexture getTexture() {
        final int id = this.renderer.texture(WaterVision.getTicks(), WaterVision.deltaFrames(), true);
        for (int i = 0; i < this.renderer.textures.length; i++) {
            if (this.glTextures[i].glId() == id) {
                return this.texture = this.glTextures[i];
            }
        }
        GlStateManager._bindTexture(0); // RESET
        return this.texture = this.glTextures[0];
    }

    @Override
    public void close() {
        // NO OP
    }
}
