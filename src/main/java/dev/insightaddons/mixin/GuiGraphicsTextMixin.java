package dev.insightaddons.mixin;

import dev.insightaddons.client.GlassSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 투명한 유리 위에서는 Sophisticated의 어두운 회색 글씨가 안 보이므로,
 * Sophisticated 저장소 화면이 열려 있는 동안만 어두운 글씨를 밝은 색 + 그림자로 바꿉니다.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsTextMixin {
    private static final String STRING_DRAW =
            "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I";
    private static final String SEQUENCE_DRAW =
            "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I";

    @ModifyVariable(method = STRING_DRAW, at = @At("HEAD"), ordinal = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$colorString(int color) {
        return GlassSkin.recolor(color);
    }

    @ModifyVariable(method = SEQUENCE_DRAW, at = @At("HEAD"), ordinal = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$colorSequence(int color) {
        return GlassSkin.recolor(color);
    }

    @ModifyVariable(method = STRING_DRAW, at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private boolean insightaddons$shadowString(boolean shadow) {
        return shadow || GlassSkin.inStorageScreen();
    }

    @ModifyVariable(method = SEQUENCE_DRAW, at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private boolean insightaddons$shadowSequence(boolean shadow) {
        return shadow || GlassSkin.inStorageScreen();
    }
}
