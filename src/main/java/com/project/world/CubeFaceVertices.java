package com.project.world;

import java.util.HashMap;
import java.util.Map;
/**
 * Precalco dei vertici (sei triangoli) per ogni faccia di un cubo unitario,
 * orientati correttamente per il backface culling.
 */
public class CubeFaceVertices {
    private static final Map<Direction, float[][]> MAP = new HashMap<>();

    static {
        // UP (Y+): ordinamento CCW visto da sopra
        MAP.put(Direction.UP, new float[][] {
            {0f,1f,0f}, {0f,1f,1f}, {1f,1f,1f},
            {1f,1f,1f}, {1f,1f,0f}, {0f,1f,0f}
        });

        // DOWN (Y-): ordinamento CCW visto da sotto
        MAP.put(Direction.DOWN, new float[][] {
            {0f,0f,0f}, {1f,0f,0f}, {1f,0f,1f},
            {1f,0f,1f}, {0f,0f,1f}, {0f,0f,0f}
        });

        // NORTH (Z-): fronte verso -Z
        MAP.put(Direction.NORTH, new float[][] {
            {1f,0f,0f}, {0f,0f,0f}, {0f,1f,0f},
            {0f,1f,0f}, {1f,1f,0f}, {1f,0f,0f}
        });

        // SOUTH (Z+): fronte verso +Z
        MAP.put(Direction.SOUTH, new float[][] {
            {0f,0f,1f}, {1f,0f,1f}, {1f,1f,1f},
            {1f,1f,1f}, {0f,1f,1f}, {0f,0f,1f}
        });

        // EAST (X+): fronte verso +X
        MAP.put(Direction.EAST, new float[][] {
            {1f,0f,1f}, {1f,0f,0f}, {1f,1f,0f},
            {1f,1f,0f}, {1f,1f,1f}, {1f,0f,1f}
        });

        // WEST (X-): fronte verso -X
        MAP.put(Direction.WEST, new float[][] {
            {0f,0f,0f}, {0f,0f,1f}, {0f,1f,1f},
            {0f,1f,1f}, {0f,1f,0f}, {0f,0f,0f}
        });
    }

    /**
     * Restituisce l'array [6][3] di vertici per la faccia indicata.
     */
    public static float[][] forDirection(Direction dir) {
        return MAP.get(dir);
    }
}
