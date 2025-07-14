// Cube.java
package com.project.world;
import java.util.List;
import org.joml.Vector3f;

/**
 * Gestisce i vertici e le coordinate UV di un cubo
 * in base a un texture-atlas 64 × 32 (1024 × 512 px).
 */
public final class Cube {

    private static final int ATLAS_COLS = 64; // colonne nel texture-atlas
    private static final int ATLAS_ROWS = 32; // righe nel texture-atlas

    private static final float TILE_U = 1f / ATLAS_COLS; // larghezza UV di un tassello
    private static final float TILE_V = 1f / ATLAS_ROWS; // altezza UV di un tassello

    private Cube() {
        // Classe statica, non instanziabile
    }

    /**
     * Aggiunge i vertici e le UV per una faccia del cubo.
     *
     * @param data lista di float (x, y, z, u, v, ...)
     * @param pos  posizione del blocco
     * @param dir  direzione della faccia da disegnare
     * @param type tipo di blocco (dirt, stone, ecc.)
     */
    public static void addFace(List<Float> data,
            Vector3f pos,
            Direction dir,
            BlockType type) {

        float[][] verts = CubeFaceVertices.forDirection(dir); // sei vertici per due triangoli
        BlockType.UV uv = type.getUV(dir); // coordinate del tassello

        // Coordinate UV del tassello sulla texture
        float u0 = uv.x() * TILE_U;
        float v0 = uv.y() * TILE_V;
        float u1 = (uv.x() + 1) * TILE_U;
        float v1 = (uv.y() + 1) * TILE_V;

        // Mappa vertici → corner UV (ordine compatibile con triangolazione)
        int[] cornerIndex = { 0, 1, 2, 2, 3, 0 };

        for (int i = 0; i < 6; i++) {
            float[] vertex = verts[i];

            int cornerIdx = cornerIndex[i];

            float u = switch (cornerIdx) {
                case 0, 3 -> u0;
                case 1, 2 -> u1;
                default -> u0;
            };
            float v = switch (cornerIdx) {
                case 0, 1 -> v0;
                case 2, 3 -> v1;
                default -> v0;
            };

            data.add(pos.x + vertex[0]);
            data.add(pos.y + vertex[1]);
            data.add(pos.z + vertex[2]);
            data.add(u);
            data.add(v);
        }
    }
}