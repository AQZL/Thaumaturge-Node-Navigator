package dev.thaumaturge.nodenavigator.client;

public record NavigatorLayout(int left, int listWidth, int sidebarX, int sidebarWidth,
                              int listTop, int rowHeight, int rows, int footerY,
                              int gridTop, int aspectColumns, int aspectRows, int aspectCellWidth) {
    public static NavigatorLayout of(int width, int height) {
        return of(width, height, Integer.MAX_VALUE);
    }
    public static NavigatorLayout of(int width, int height, int aspectCount) {
        int margin = width < 400 ? 10 : 16;
        int total = Math.min(720, width - margin * 2);
        int left = (width - total) / 2;
        int gap = width < 400 ? 8 : 12;
        int side = Math.max(102, Math.min(180, total / 3));
        int list = total - gap - side;
        int row = list >= 340 ? 60 : 80;
        int panelHeight = Math.min(400, height);
        int top = (height - panelHeight) / 2;
        int listTop = top + 43;
        int footer = top + panelHeight - 28;
        int rows = Math.max(1, (footer - listTop - 6) / row);
        int columns = Math.max(3, (side - 8) / 34);
        int gridTop = listTop + (panelHeight < 200 ? 20 : 24);
        int neededRows = (Math.max(1, aspectCount) - 1) / columns + 1;
        int availableRows = Math.max(1, (footer - gridTop - 56) / 32);
        int aspectRows = Math.min(neededRows, Math.min(4, availableRows));
        return new NavigatorLayout(left, list, left + list + gap, side, listTop, row, rows,
                footer, gridTop, columns, aspectRows, (side - 8) / columns);
    }
    public int titleY() { return listTop - 31; }
    public int headingY() { return listTop - 12; }
    public int listBottom() { return Math.max(footerY - 8, controlsTop() + 68); }
    public int doneY() { return listBottom() - 20; }
    public int listViewportHeight() { return listBottom() - listTop; }
    public int listContentRight() { return left + listWidth - 8; }
    public int aspectBottom() { return gridTop + aspectRows * 32; }
    public int aspectViewportHeight() { return aspectRows * 32; }
    public int controlsTop() { return gridTop + aspectRows * 32 + 8; }
    public boolean compact() { return listWidth < 340; }
}
