package ly.anoma.hex.android;

/** Full-screen placement within system-bar/cutout bounds, with no edge snapping. */
final class BubblePosition {
    record Area(int left, int top, int right, int bottom) { }
    record Point(int x, int y) { }
    record Saved(float x, float y) { }
    static Point clamp(int x, int y, Area area, int size) {
        return new Point(Math.max(area.left(), Math.min(x, Math.max(area.left(), area.right() - size))),
                Math.max(area.top(), Math.min(y, Math.max(area.top(), area.bottom() - size))));
    }
    static Point restore(Saved saved, Area area, int size) {
        int width = Math.max(0, area.right() - area.left() - size);
        int height = Math.max(0, area.bottom() - area.top() - size);
        float x = Float.isFinite(saved.x()) ? Math.max(0, Math.min(1, saved.x())) : 1;
        float y = Float.isFinite(saved.y()) ? Math.max(0, Math.min(1, saved.y())) : 0.5f;
        return clamp(area.left() + Math.round(width * x), area.top() + Math.round(height * y), area, size);
    }
    static Saved save(Point point, Area area, int size) {
        Point bounded = clamp(point.x(), point.y(), area, size);
        int width = Math.max(0, area.right() - area.left() - size);
        int height = Math.max(0, area.bottom() - area.top() - size);
        return new Saved(width == 0 ? 0 : (float)(bounded.x() - area.left()) / width,
                height == 0 ? 0 : (float)(bounded.y() - area.top()) / height);
    }
}
