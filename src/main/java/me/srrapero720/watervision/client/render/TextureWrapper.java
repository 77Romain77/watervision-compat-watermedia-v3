package me.srrapero720.watervision.client.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class TextureWrapper extends AbstractTexture {
    public TextureWrapper(final int id, final int width, final int height) {
        this.texture = new ExternalGlTexture(width, height, id);
        this.textureView = new GlTextureView((GlTexture) this.texture, 1, 1) {};
    }
}
