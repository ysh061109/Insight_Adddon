package dev.insightaddons.extras.mixin;

import dev.insightaddons.extras.client.AeGlass;
import dev.insightaddons.extras.client.AeTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 유리 창 위에서는 AE2의 어두운 글씨가 안 보이므로, 단말기가 열려 있는 동안만 어두운 글씨를 밝은 색 + 그림자로 바꿉니다. */
@Mixin(GuiGraphics.class)
public abstract class AeGlassTextMixin {
    private static final String STRING_DRAW = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I";
    private static final String SEQUENCE_DRAW = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I";

    private static boolean insightaddons$glassNow() {
        return AeGlass.on() && AeTerminal.isTerminalScreen(Minecraft.getInstance().screen);
    }

    @ModifyVariable(method = STRING_DRAW, at = @At("HEAD"), ordinal = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$aeColorString(int color) {
        return insightaddons$glassNow() ? AeGlass.recolor(color) : color;
    }

    @ModifyVariable(method = SEQUENCE_DRAW, at = @At("HEAD"), ordinal = 2, argsOnly = true, remap = false, require = 0)
    private int insightaddons$aeColorSequence(int color) {
        return insightaddons$glassNow() ? AeGlass.recolor(color) : color;
    }

    @ModifyVariable(method = STRING_DRAW, at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private boolean insightaddons$aeShadowString(boolean shadow) {
        return shadow || insightaddons$glassNow();
    }

    @ModifyVariable(method = SEQUENCE_DRAW, at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false, require = 0)
    private boolean insightaddons$aeShadowSequence(boolean shadow) {
        return shadow || insightaddons$glassNow();
    }
}
