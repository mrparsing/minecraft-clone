package com.project.world;

import java.util.EnumMap;
import java.util.Map;

public enum BlockType {
    GRASS(
            new UV(21, 14), // sopra: erba
            new UV(24, 29), // sotto: terra
            new UV(28, 24)// lato: erba-lato
    ),
    DIRT(new UV(24, 29)), // stesso UV per tutte le facce
    STONE(new UV(22, 3)), // stesso UV per tutte le facce
    IRON_ORE(new UV(31, 25)),
    COAL_ORE(new UV(3, 16)),
    DIAMOND_ORE(new UV(24, 31)),
    WOOD(new UV(23, 12),
            new UV(23, 12),
            new UV(22, 12)),
    LEAVES(new UV(4, 24)),
    WATER(new UV(0, 22)),
    SAND(new UV(30, 6)),
    SNOW(new UV(11, 8),
            new UV(24, 29),
            new UV(28, 22)),
    PLANKS(new UV(24, 12)),
    BRICK(new UV(23, 3));

    private final Map<Direction, UV> faceUVs = new EnumMap<>(Direction.class);

    BlockType(UV allFaces) {
        for (Direction dir : Direction.values()) {
            faceUVs.put(dir, allFaces);
        }
    }

    BlockType(UV top, UV bottom, UV side) {
        faceUVs.put(Direction.UP, top);
        faceUVs.put(Direction.DOWN, bottom);
        for (Direction dir : Direction.values()) {
            if (dir != Direction.UP && dir != Direction.DOWN)
                faceUVs.put(dir, side);
        }
    }

    public UV getUV(Direction dir) {
        return faceUVs.get(dir);
    }

    public record UV(int x, int y) {
    }

}