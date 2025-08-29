package me.srrapero720.watervision.client.render;

import me.srrapero720.watervision.WaterVision;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.watermedia.api.image.ImageRenderer;

public class TextureWrapper extends AbstractTexture {
    public TextureWrapper(int id) {
        this.id = id;
    }

    @Override public int getId() {
        return this.id;
    }

    @Override public void load(@NotNull ResourceManager manager) { /* NO OP */ }
    @Override public void releaseId() { /* NO OP */ }
    @Override public void close() { /* NO OP */}

    @OnlyIn(Dist.CLIENT)
    public static class Renderer extends TextureWrapper {
        private final ImageRenderer renderer;

        public Renderer(final ImageRenderer imageRenderer) {
            super(-1);
            this.renderer = imageRenderer;
        }

        @Override
        public int getId() {
            return this.renderer.texture(WaterVision.getTicks(), WaterVision.deltaFrames(), true);
        }
    }
}
