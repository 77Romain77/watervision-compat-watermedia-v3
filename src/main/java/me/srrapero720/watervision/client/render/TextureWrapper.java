package me.srrapero720.watervision.client.render;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

public class TextureWrapper extends AbstractTexture {
    private final IntSupplier textureSupplier;

    public TextureWrapper(final int id) {
        this(() -> id);
    }

    public TextureWrapper(final IntSupplier textureSupplier) {
        this.textureSupplier = textureSupplier;
    }

    @Override
    public int getId() {
        return this.textureSupplier.getAsInt();
    }

    @Override public void load(@NotNull ResourceManager manager) { }
    @Override public void releaseId() { }
    @Override public void close() { }
}
