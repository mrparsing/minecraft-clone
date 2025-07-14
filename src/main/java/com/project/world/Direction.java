// Direction.java
package com.project.world;

/**
 * Rappresenta le 6 direzioni di una faccia e i relativi UV nel texture‐atlas.
 */
public enum Direction {
    UP   ( 0,  1,  0),
    DOWN ( 0, -1,  0),
    NORTH( 0,  0, -1),
    SOUTH( 0,  0,  1),
    EAST ( 1,  0,  0),
    WEST (-1,  0,  0);

    public final int dx, dy, dz;
    float[] uv;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }
    
}