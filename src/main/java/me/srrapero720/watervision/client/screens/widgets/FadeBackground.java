package me.srrapero720.watervision.client.screens.widgets;

import net.minecraft.client.gui.GuiGraphics;

public class FadeBackground {
    private float fadeTick = 0.0f;
    private final float fadeTickDuration; // Duration in ticks (1 tick = 1/20 second)

    public FadeBackground(final float duration) {
        this.fadeTickDuration = Math.max(0.01f, duration);
    }

    public void render(final GuiGraphics graphics, final int width, final int height, final boolean fadeIn, float partialTicks) {
        if (fadeIn) {
            if (this.fadeTick < this.fadeTickDuration) {
                this.fadeTick += partialTicks;
            }
            if (this.fadeTick > this.fadeTickDuration) this.fadeTick = this.fadeTickDuration;
        } else {
            if (this.fadeTick > 0) {
                this.fadeTick -= partialTicks;
            }
            if (this.fadeTick < 0) this.fadeTick = 0;
        }

        final float progress = this.fadeTick / this.fadeTickDuration;
        final float easedProgress = progress * progress;
        final int alpha = (int) (255.0f * easedProgress);

        graphics.fill(0, 0, width, height, argb(alpha, 0, 0, 0));
    }

    private static int argb(final int alpha, final int red, final int green, final int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    public boolean isFadedOut() {
        return this.fadeTick <= 0.0f;
    }
    public boolean isFadedIn() {
        return this.fadeTick >= this.fadeTickDuration;
    }

    public void forceFadeOut() {
        this.fadeTick = 0.0f;
    }

    public void forceFadeIn() {
        this.fadeTick = this.fadeTickDuration;
    }
}
