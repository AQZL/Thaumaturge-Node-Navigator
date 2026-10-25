package dev.thaumaturge.nodenavigator.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class NavigatorButton extends Button {
    private Runnable secondaryAction;
    private boolean hasViewport;
    private int viewportLeft;
    private int viewportTop;
    private int viewportRight;
    private int viewportBottom;

    public NavigatorButton(int x, int y, int width, int height, Component label, boolean selected, Runnable action) {
        super(x, y, width, height, label, button -> action.run(), DEFAULT_NARRATION);
    }

    public NavigatorButton withSecondaryAction(Runnable action) {
        secondaryAction = action;
        return this;
    }

    public void setViewport(int left, int top, int right, int bottom) {
        hasViewport = true;
        viewportLeft = left;
        viewportTop = top;
        viewportRight = Math.max(left, right);
        viewportBottom = Math.max(top, bottom);
    }

    private boolean withinViewport(double mouseX, double mouseY) {
        return !hasViewport || mouseX >= viewportLeft && mouseX < viewportRight
                && mouseY >= viewportTop && mouseY < viewportBottom;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return withinViewport(mouseX, mouseY) && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return withinViewport(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        isHovered &= withinViewport(mouseX, mouseY);
        if (hasViewport) graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        try {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        } finally {
            if (hasViewport) graphics.disableScissor();
        }
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo button) {
        return button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || secondaryAction != null && button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && secondaryAction != null) secondaryAction.run();
        else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) super.onClick(event, doubleClick);
    }
}