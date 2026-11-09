package dev.insightaddons.extras.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** 보관함 이름표 입력 화면. 비우고 Enter를 누르면 이름표를 지웁니다. */
public final class NameScreen extends Screen {
    private final BlockPos pos;
    private EditBox box;

    public NameScreen(BlockPos pos) {
        super(Component.translatable("insight_addons.name.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        box = new EditBox(font, width / 2 - 110, height / 2 - 10, 220, 20, title);
        box.setMaxLength(40);
        box.setHint(Component.translatable("insight_addons.name.hint"));
        Minecraft mc = Minecraft.getInstance();
        String current = mc.level == null ? null : ContainerNames.get(mc.level, pos);
        if (current != null) {
            box.setValue(current);
        }
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                ContainerNames.set(mc.level, pos, box.getValue());
            }
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("insight_addons.name.enter"), width / 2, height / 2 + 18, 0xFFB0B0B0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
