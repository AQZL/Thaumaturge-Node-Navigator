package dev.thaumaturge.nodenavigator.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class NodeTrackerScreen extends Screen {
    private static final String PREFIX = "screen.thaumaturge_node_navigator.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Set<String> SELECTED_ASPECTS = new LinkedHashSet<>();
    private static boolean matchAll = true;
    private static String sort = "distance";
    private final SmoothScroll nodeScroll = new SmoothScroll();
    private final SmoothScroll aspectScroll = new SmoothScroll();
    private final List<RowButtons> rowButtons = new ArrayList<>();
    private final List<AspectButton> aspectButtons = new ArrayList<>();
    private NavigatorLayout layout;
    private int seenRevision;
    private long lastFrameNanos;
    private double pointerX;
    private double pointerY;
    private ScrollArea draggingScrollbar;
    private double scrollbarGrabOffset;
    private List<NodeRecord> visible = List.of();
    private List<String> availableAspects = List.of();

    private record RowButtons(int index, NavigatorButton mark, NavigatorButton delete) {}
    private record AspectButton(int index, AspectFilterButton button) {}
    private record ScrollArea(SmoothScroll scroll, int left, int top, int right, int bottom) {
        boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
        int height() { return bottom - top; }
        int barX() { return right - 5; }
        int thumbHeight() { return Math.min(height(), Math.max(16, (int) Math.round(height() * (double) height() / (height() + scroll.maximum())))); }
        int thumbY() {
            return top + (scroll.maximum() <= 0 ? 0 : (int) Math.round((height() - thumbHeight()) * scroll.position() / scroll.maximum()));
        }
    }

    public NodeTrackerScreen() { super(label("title")); }
    private static Component label(String key, Object... args) { return Component.translatable(PREFIX + key, args); }

    @Override
    protected void init() {
        seenRevision = NodeTrackerClient.revision();
        availableAspects = NodeTrackerClient.availableAspects();
        layout = NavigatorLayout.of(width, height, availableAspects.size());
        SELECTED_ASPECTS.retainAll(availableAspects);
        if (!sort.equals("distance") && !availableAspects.contains(sort)) sort = "distance";
        visible = NodeTrackerClient.sorted(SELECTED_ASPECTS, matchAll, sort);
        nodeScroll.setMaximum(Math.max(0, (double) visible.size() * layout.rowHeight() - layout.listViewportHeight()));
        int aspectContentRows = (availableAspects.size() + layout.aspectColumns() - 1) / layout.aspectColumns();
        aspectScroll.setMaximum(Math.max(0, aspectContentRows * 32.0 - layout.aspectViewportHeight()));
        lastFrameNanos = 0;
        draggingScrollbar = null;
        rowButtons.clear();
        aspectButtons.clear();

        for (int i = 0; i < visible.size(); i++) {
            NodeRecord node = visible.get(i);
            int markX = layout.compact() ? layout.left() + 7 : layout.listContentRight() - 104;
            NavigatorButton mark = button(markX, 0, 52, 20, label(isMarked(node) ? "marked" : "mark"), () -> {
                NodeTrackerClient.toggleMark(node);
                rebuildWidgets();
            });
            mark.setTooltip(Tooltip.create(label(isMarked(node) ? "unmark_hint" : "mark_hint")));
            NavigatorButton delete = button(markX + 56, 0, 42, 20, label("delete"),
                    () -> minecraft.setScreen(new ConfirmScreen(confirmed -> {
                        if (confirmed) NodeTrackerClient.delete(node);
                        minecraft.setScreen(this);
                    }, label("delete_title"), label("delete_question", coordinates(node)))));
            mark.setViewport(layout.left(), layout.listTop(), layout.listContentRight(), layout.listBottom());
            delete.setViewport(layout.left(), layout.listTop(), layout.listContentRight(), layout.listBottom());
            rowButtons.add(new RowButtons(i, mark, delete));
        }

        for (int i = 0; i < availableAspects.size(); i++) {
            String id = availableAspects.get(i);
            int x = layout.sidebarX() + i % layout.aspectColumns() * layout.aspectCellWidth();
            boolean selected = SELECTED_ASPECTS.contains(id);
            AspectFilterButton aspect = addRenderableWidget(new AspectFilterButton(x, 0, layout.aspectCellWidth() - 3, 29,
                    NodeDisplay.aspectName(id), selected, id, () -> {
                        if (!SELECTED_ASPECTS.remove(id)) SELECTED_ASPECTS.add(id);
                        nodeScroll.reset();
                        rebuildWidgets();
                    }));
            aspect.setTooltip(Tooltip.create(label("filter_tooltip", NodeDisplay.aspectName(id),
                    label(selected ? "filter_on" : "filter_off"))));
            aspect.setViewport(layout.sidebarX(), layout.gridTop(), layout.sidebarX() + layout.sidebarWidth() - 8, layout.aspectBottom());
            aspectButtons.add(new AspectButton(i, aspect));
        }

        button(layout.sidebarX(), layout.controlsTop(), layout.sidebarWidth(), 20,
                label(matchAll ? "match_all" : "match_any"), () -> { matchAll = !matchAll; nodeScroll.reset(); rebuildWidgets(); });
        button(layout.sidebarX(), layout.controlsTop() + 24, layout.sidebarWidth(), 20,
                label("sort_value", sort.equals("distance") ? label("sort.distance") : NodeDisplay.aspectName(sort)),
                () -> cycleSort(1)).withSecondaryAction(() -> cycleSort(-1));
        button(layout.sidebarX(), layout.doneY(), layout.sidebarWidth(), 20, Component.translatable("gui.done"), this::onClose);
        positionScrollWidgets();
    }

    private ScrollArea nodeArea() {
        return new ScrollArea(nodeScroll, layout.left(), layout.listTop(), layout.left() + layout.listWidth(), layout.listBottom());
    }
    private ScrollArea aspectArea() {
        return new ScrollArea(aspectScroll, layout.sidebarX(), layout.gridTop(), layout.sidebarX() + layout.sidebarWidth(), layout.aspectBottom());
    }
    private void cycleSort(int direction) {
        sort = NodeFilters.nextSort(sort, availableAspects, direction);
        nodeScroll.reset();
        rebuildWidgets();
    }
    private NavigatorButton button(int x, int y, int width, int height, Component text, Runnable action) {
        NavigatorButton button = addRenderableWidget(new NavigatorButton(x, y, width, height, text, false, action));
        if (font.width(text) > width - 6) button.setTooltip(Tooltip.create(text));
        return button;
    }
    private void positionScrollWidgets() {
        int nodeOffset = (int) Math.round(nodeScroll.position());
        for (RowButtons row : rowButtons) {
            int y = layout.listTop() + row.index() * layout.rowHeight() - nodeOffset + (layout.compact() ? 54 : 1);
            row.mark().setY(y);
            row.delete().setY(y);
            boolean inView = y + 20 > layout.listTop() && y < layout.listBottom();
            row.mark().visible = row.delete().visible = inView;
        }
        int aspectOffset = (int) Math.round(aspectScroll.position());
        for (AspectButton entry : aspectButtons) {
            int y = layout.gridTop() + entry.index() / layout.aspectColumns() * 32 - aspectOffset;
            entry.button().setY(y);
            entry.button().visible = y + 29 > layout.gridTop() && y < layout.aspectBottom();
        }
    }
    private void advanceScrolling() {
        long now = System.nanoTime();
        double seconds = lastFrameNanos == 0 ? 0 : Math.min(0.1, Math.max(0, (now - lastFrameNanos) / 1_000_000_000.0));
        lastFrameNanos = now;
        nodeScroll.advance(seconds);
        aspectScroll.advance(seconds);
        positionScrollWidgets();
    }
    private static String coordinates(NodeRecord node) {
        var pos = node.blockPos();
        return "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
    }
    private static boolean isMarked(NodeRecord node) {
        var marked = NodeTrackerClient.marked();
        return marked != null && marked.key().equals(node.key());
    }
    private static Component nodeType(NodeRecord node) {
        return node.modifier.isEmpty() ? label("type." + node.type)
                : label("type_modifier", label("type." + node.type), label("modifier." + node.modifier));
    }
    private void clipped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        String result = font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
        graphics.text(font, result, x, y, color, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        advanceScrolling();
        graphics.text(font, label("records", visible.size()), layout.left() + 3, layout.headingY(), 0xFFF0F3F5);
        graphics.text(font, label("filters"), layout.sidebarX() + 3, layout.headingY(), 0xFFF4E2B8);
        clipped(graphics, label("filter_count", SELECTED_ASPECTS.size(), availableAspects.size()).getString(),
                layout.sidebarX() + 3, layout.listTop() + 8, layout.sidebarWidth() - 6, 0xFFD1DADE);

        ScrollArea list = nodeArea();
        graphics.enableScissor(list.left(), list.top(), layout.listContentRight(), list.bottom());
        int offset = (int) Math.round(nodeScroll.position());
        int first = Math.max(0, offset / layout.rowHeight());
        int end = Math.min(visible.size(), (offset + layout.listViewportHeight() + layout.rowHeight() - 1) / layout.rowHeight());
        for (int i = first; i < end; i++) {
            NodeRecord node = visible.get(i);
            int y = layout.listTop() + i * layout.rowHeight() - offset;
            int right = layout.listContentRight();
            int bottom = y + layout.rowHeight() - 5;
            graphics.fill(layout.left(), y, right, bottom, isMarked(node) ? 0xB43C492E : 0x701E2934);
            graphics.horizontalLine(layout.left(), right, bottom, isMarked(node) ? 0xFFE0C779 : 0xFF455867);
            if (isMarked(node)) graphics.fill(layout.left(), y, layout.left() + 2, bottom, 0xFFE0C779);
            int textWidth = right - layout.left() - (layout.compact() ? 14 : 114);
            clipped(graphics, coordinates(node), layout.left() + 7, y + 5, textWidth, 0xFFF3F5F2);
            String distance = node.dimension.equals(NodeTrackerClient.currentDimension().toString())
                    ? label("distance", NodeTrackerClient.distance(node)).getString() : label("other_dimension").getString();
            String metadata = NodeDisplay.dimensionName(node.dimension).getString() + " · " + nodeType(node).getString() + " · " + distance;
            clipped(graphics, metadata, layout.left() + 7, y + (layout.compact() ? 19 : 23),
                    right - layout.left() - 14, 0xFFE8D29A);
            boolean inViewport = list.contains(mouseX, mouseY) && mouseX < right;
            if (inViewport && mouseY >= y && mouseY < bottom)
                graphics.setComponentTooltipForNextFrame(font, nodeTooltip(node), mouseX, mouseY);
            drawAspects(graphics, node, layout.left() + 7, y + 34, right - layout.left() - 14,
                    inViewport ? mouseX : -1, inViewport ? mouseY : -1);
        }
        if (visible.isEmpty()) {
            graphics.textWithWordWrap(font, label(NodeTrackerClient.ready() ? "empty" : "waiting"),
                    layout.left() + 7, layout.listTop() + 12, layout.listWidth() - 22, 0xFFD1DADE, true);
        }
        graphics.disableScissor();
        if (availableAspects.isEmpty()) {
            graphics.textWithWordWrap(font, label("no_aspects"), layout.sidebarX() + 3, layout.gridTop() + 4,
                    layout.sidebarWidth() - 12, 0xFFD1DADE, true);
        }
        if (NodeTrackerClient.storageError()) {
            int errorY = layout.controlsTop() + 52;
            if (errorY + 34 < layout.doneY()) {
                graphics.textWithWordWrap(font, label("storage_error"), layout.sidebarX() + 3, errorY,
                        layout.sidebarWidth() - 6, 0xFFFF9B88, true);
            } else {
                graphics.text(font, Component.literal("!"), layout.sidebarX() - 8, layout.doneY() + 5, 0xFFFF9B88, true);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawScrollbar(graphics, list, mouseX, mouseY);
        drawScrollbar(graphics, aspectArea(), mouseX, mouseY);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, ScrollArea area, int mouseX, int mouseY) {
        if (area.scroll().maximum() <= 0) return;
        int x = area.barX();
        graphics.fill(x, area.top(), x + 4, area.bottom(), 0xFF080C11);
        boolean highlighted = draggingScrollbar != null && draggingScrollbar.scroll() == area.scroll()
                || mouseX >= x - 1 && mouseX < x + 5 && mouseY >= area.top() && mouseY < area.bottom();
        int y = area.thumbY();
        graphics.fill(x, y, x + 4, y + area.thumbHeight(), highlighted ? 0xFFD2DBDF : 0xFF8294A2);
        graphics.fill(x, y, x + 1, y + area.thumbHeight(), 0xFFC0CDD4);
    }
    private boolean grabScrollbar(ScrollArea area, MouseButtonEvent event) {
        if (area.scroll().maximum() <= 0 || event.x() < area.barX() - 2 || event.x() >= area.right()
                || event.y() < area.top() || event.y() >= area.bottom()) return false;
        draggingScrollbar = area;
        int thumbY = area.thumbY();
        scrollbarGrabOffset = event.y() >= thumbY && event.y() < thumbY + area.thumbHeight()
                ? event.y() - thumbY : area.thumbHeight() / 2.0;
        dragScrollbar(event.y());
        return true;
    }
    private void dragScrollbar(double mouseY) {
        ScrollArea area = draggingScrollbar;
        int travel = area.height() - area.thumbHeight();
        area.scroll().jumpTo(travel <= 0 ? 0 : (mouseY - area.top() - scrollbarGrabOffset) / travel * area.scroll().maximum());
        positionScrollWidgets();
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        pointerX = mouseX;
        pointerY = mouseY;
        if (nodeArea().contains(mouseX, mouseY)) {
            nodeScroll.scroll(-vertical * 44.0);
            return true;
        }
        if (aspectArea().contains(mouseX, mouseY)) {
            aspectScroll.scroll(-vertical * 32.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && (grabScrollbar(nodeArea(), event) || grabScrollbar(aspectArea(), event))) return true;
        if (nodeArea().contains(event.x(), event.y())) nodeScroll.jumpTo(nodeScroll.position());
        if (aspectArea().contains(event.x(), event.y())) aspectScroll.jumpTo(aspectScroll.position());
        return super.mouseClicked(event, doubleClick);
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingScrollbar = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    private List<Component> nodeTooltip(NodeRecord node) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(coordinates(node)));
        tooltip.add(NodeDisplay.dimensionName(node.dimension));
        tooltip.add(nodeType(node));
        for (var entry : node.aspects.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            tooltip.add(label("aspect_amount", NodeDisplay.aspectName(entry.getKey()), entry.getValue()));
        }
        tooltip.add(label("last_scanned", DATE.format(Instant.ofEpochMilli(node.lastScanned))));
        tooltip.add(label("snapshot"));
        return tooltip;
    }
    private void drawAspects(GuiGraphicsExtractor graphics, NodeRecord node, int startX, int y, int maxWidth, int mouseX, int mouseY) {
        var entries = node.aspects.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(java.util.Map.Entry.comparingByKey()).toList();
        int x = startX;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            Component text = label("aspect_amount", NodeDisplay.aspectName(entry.getKey()), entry.getValue());
            int badgeWidth = 18 + font.width(text) + 10;
            int overflowWidth = i + 1 < entries.size() ? font.width("+" + (entries.size() - i)) + 4 : 0;
            if (x + badgeWidth + overflowWidth > startX + maxWidth) {
                graphics.text(font, "+" + (entries.size() - i), x, y + 4, 0xFFD1DADE, true);
                break;
            }
            NodeDisplay.drawAspect(graphics, entry.getKey(), x, y, 16, true);
            graphics.text(font, text, x + 19, y + 4, 0xFFF0F3F5, true);
            if (mouseX >= x && mouseX < x + badgeWidth && mouseY >= y && mouseY < y + 17)
                graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
            x += badgeWidth;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }
    @Override
    public void tick() { if (seenRevision != NodeTrackerClient.revision()) rebuildWidgets(); }
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (NodeNavigatorClientSetup.OPEN_TRACKER.matches(event)) { onClose(); return true; }
        SmoothScroll scroll = aspectArea().contains(pointerX, pointerY) ? aspectScroll : nodeScroll;
        int viewport = scroll == aspectScroll ? layout.aspectViewportHeight() : layout.listViewportHeight();
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) { scroll.scroll(viewport * 0.9); return true; }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) { scroll.scroll(-viewport * 0.9); return true; }
        if (event.key() == GLFW.GLFW_KEY_HOME) { scroll.jumpTo(0); positionScrollWidgets(); return true; }
        if (event.key() == GLFW.GLFW_KEY_END) { scroll.jumpTo(scroll.maximum()); positionScrollWidgets(); return true; }
        return super.keyPressed(event);
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
