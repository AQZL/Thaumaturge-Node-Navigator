package dev.thaumaturge.nodenavigator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.network.chat.Component;

public final class AspectFilterButton extends AbstractWidget {
    private final Runnable action;
    private final boolean selected;
    private final String aspect;
    private boolean hasViewport;
    private int viewportLeft;
    private int viewportTop;
    private int viewportRight;
    private int viewportBottom;

    public AspectFilterButton(int x, int y, int width, int height, Component label, boolean selected,
                              String aspect, Runnable action) {
        super(x, y, width, height, label);
        this.selected = selected;
        this.aspect = aspect;
        this.action = action;
        setTooltip(Tooltip.create(label));
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return withinViewport(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        isHovered &= withinViewport(mouseX, mouseY);
        if (hasViewport) graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        try {
            var font = Minecraft.getInstance().font;
            NodeDisplay.drawAspect(graphics, aspect, getX() + (width - 16) / 2, getY() + 3, 16, selected);
            String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 4));
            graphics.drawCenteredString(font, text, getX() + width / 2, getBottom() - 10,
                    selected ? 0xFFF4E2B8 : 0xFFB0BABD);
            if (isHoveredOrFocused()) {
                graphics.hLine(getX() + width / 2 - 5, getX() + width / 2 + 5,
                        getBottom() - 1, 0xFFC6D4D7);
            }
        } finally {
            if (hasViewport) graphics.disableScissor();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        action.run();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isActive() && isFocused() && CommonInputs.selected(keyCode)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            action.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
