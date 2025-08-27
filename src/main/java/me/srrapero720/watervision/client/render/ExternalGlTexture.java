package me.srrapero720.watervision.client.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.watermedia.api.image.ImageRenderer;

public class ExternalGlTexture extends GlTexture {
    protected ExternalGlTexture(int width, int height, int glId) {
        super(1, "texturewrapper_" + glId, TextureFormat.RGBA8, width, height, 1, 1, glId);
    }
}
