package com.project.math;
public class Point2i {
    public final int x, y;
    public Point2i(int x, int y) { this.x = x; this.y = y; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Point2i)) return false;
        Point2i p = (Point2i)o;
        return p.x == x && p.y == y;
    }
    @Override public int hashCode() { return 31*x + y; }
}