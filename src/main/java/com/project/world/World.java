package com.project.world;

import org.joml.Vector3f;

import com.project.Main;
import com.project.graphics.Camera;
import com.project.math.Point2i;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform3i;
import static org.lwjgl.opengl.GL20.glUseProgram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class World {

    // sotto alle altre variabili d’istanza
    private static final double AUTOSAVE_INTERVAL = 30.0; // secondi
    private double lastAutosave = 0.0;

    // ---------------- RaycastResult ----------------
    public static class RaycastResult {
        public final Vector3f blockPos;
        public final Vector3f faceNormal;

        public RaycastResult(Vector3f pos, Vector3f normal) {
            this.blockPos = pos;
            this.faceNormal = normal;
        }
    }

    // -------------------------------------------------

    private static final int CHUNK_SIZE = 64;
    private final int viewDistance;

    private final int shaderProgram;
    private final Map<Point2i, Chunk> chunks = new HashMap<>();

    private final org.joml.Vector3i lastPrinted = new org.joml.Vector3i(Integer.MIN_VALUE);
    // evidenziazione blocco puntato
    private final org.joml.Vector3i highlight = new org.joml.Vector3i(Integer.MIN_VALUE); // disattivo finché non c’è un
                                                                                          // blocco

    public World(int shaderProgram, int viewDistance) {
        this.shaderProgram = shaderProgram;
        this.viewDistance = viewDistance;
    }

    // --- utilità per ricavare il tipo di blocco in coordinate mondo ---
    private BlockType getBlockTypeAt(int wx, int wy, int wz) {
        int cx = Math.floorDiv(wx, CHUNK_SIZE);
        int cz = Math.floorDiv(wz, CHUNK_SIZE);
        Chunk c = chunks.get(new Point2i(cx, cz));
        if (c == null)
            return null;
        return c.getBlockType(wx - cx * CHUNK_SIZE, wy, wz - cz * CHUNK_SIZE);
    }

    /**
     * Stampa una sola volta il blocco sotto il mirino (maxDistance in metri).
     */
    public void printLookedBlock(Camera cam, float maxDistance) {
        RaycastResult hit = raycast(cam.getPosition(), cam.getFront(), maxDistance);
        if (hit == null) {
            lastPrinted.set(Integer.MIN_VALUE);
            return;
        }

        int x = (int) hit.blockPos.x;
        int y = (int) hit.blockPos.y;
        int z = (int) hit.blockPos.z;

        // evita spam: stampa solo quando cambia il blocco puntato
        if (!lastPrinted.equals(x, y, z)) {
            lastPrinted.set(x, y, z);
            BlockType type = getBlockTypeAt(x, y, z);
            System.out.printf("Guardando blocco %s a (%d, %d, %d)%n", type, x, y, z);
        }
    }

    public void update(Camera cam) throws IOException {
        double now = org.lwjgl.glfw.GLFW.glfwGetTime();
        if (now - lastAutosave >= AUTOSAVE_INTERVAL) {
            for (Map.Entry<Point2i, Chunk> e : chunks.entrySet()) {
                Chunk c = e.getValue();
                if (c.isDirty()) {
                    c.save(chunkFile(e.getKey()));
                }
            }
            lastAutosave = now;
        }
        updateHighlight(cam, 10.0f);
        Point2i camChunk = worldToChunk(cam.getPosition());
        // genera nuovi chunk intorno
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                Point2i key = new Point2i(camChunk.x + dx, camChunk.y + dz);

                if (!chunks.containsKey(key)) {
                    Path p = Path.of("world", Main.worldName, key.x + "_" + key.y + ".nbt");
                    Chunk c;

                    if (Files.exists(p)) {
                        c = Chunk.load(shaderProgram, p); // ricrea blocchi e mesh
                        c.setWorldOffset(key.x * CHUNK_SIZE, // ← AGGIUNGA QUESTA
                                0,
                                key.y * CHUNK_SIZE);
                    } else {
                        c = new Chunk(shaderProgram, CHUNK_SIZE, 128, CHUNK_SIZE);
                        c.setWorldOffset(key.x * CHUNK_SIZE, 0, key.y * CHUNK_SIZE);
                        c.generateTerrain();
                    }
                    chunks.put(key, c);
                }
            }
        }
        Iterator<Map.Entry<Point2i, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Point2i, Chunk> e = it.next();
            Point2i k = e.getKey();
            if (Math.abs(k.x - camChunk.x) > viewDistance ||
                    Math.abs(k.y - camChunk.y) > viewDistance) {

                Chunk c = e.getValue();
                if (c.isDirty())
                    c.save(Path.of("world", Main.worldName, k.x + "_" + k.y + ".nbt"));
                it.remove();
            }
        }
    }

    public void render(Camera cam) {
        // --- evidenziazione blocco puntato ---
        glUseProgram(shaderProgram); // assicura che il programma sia attivo
        glUniform3i(glGetUniformLocation(shaderProgram, "uHighlightBlock"),
                highlight.x, highlight.y, highlight.z); // highlight impostato da updateHighlight()

        // --- draw dei chunk ---
        for (Chunk c : chunks.values()) {
            if (c.isInFrustum(cam)) {
                c.render(shaderProgram);
            }
        }
    }

    /**
     * Aggiorna le coordinate del blocco evidenziato.
     * Se non si sta guardando alcun blocco solido imposta INT_MIN
     * per disattivare l'evidenziazione.
     */
    public void updateHighlight(Camera cam, float maxDistance) {
        RaycastResult hit = raycast(cam.getPosition(), cam.getFront(), maxDistance);
        if (hit == null) {
            highlight.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        } else {
            highlight.set((int) hit.blockPos.x,
                    (int) hit.blockPos.y,
                    (int) hit.blockPos.z);
        }
    }

    public void cleanup() {
        for (Map.Entry<Point2i, Chunk> e : chunks.entrySet()) {
            Chunk c = e.getValue();
            if (c.isDirty()) {
                try {
                    c.save(Path.of("world",
                            Main.worldName,
                            e.getKey().x + "_" + e.getKey().y + ".nbt"));
                } catch (IOException ex) {
                    ex.printStackTrace(); // logga l’errore ma continua
                }
            }
            c.cleanup(); // libera VBO/VAO
        }
    }

    private Point2i worldToChunk(Vector3f pos) {
        int cx = (int) Math.floor(pos.x / CHUNK_SIZE);
        int cz = (int) Math.floor(pos.z / CHUNK_SIZE);
        return new Point2i(cx, cz);
    }

    /**
     * Restituisce il terreno più alto **sotto** il giocatore.
     *
     * @param worldX coordinate X mondiali
     * @param worldZ coordinate Z mondiali
     * @param fromY  piano da cui iniziare a cercare (di norma: (int)playerPos.y-1)
     * @return quota del blocco su cui appoggiare i piedi, −1 se assente
     */
    public int getGroundUnder(float worldX, float worldZ, int fromY) {
        int cx = (int) Math.floor(worldX / CHUNK_SIZE);
        int cz = (int) Math.floor(worldZ / CHUNK_SIZE);
        Point2i key = new Point2i(cx, cz);
        Chunk c = chunks.get(key);
        if (c == null)
            return -1;

        int lx = (int) (worldX - cx * CHUNK_SIZE);
        int lz = (int) (worldZ - cz * CHUNK_SIZE);

        int startY = Math.min(fromY, c.sizeY - 1); // limite superiore di ricerca
        for (int y = startY; y >= 0; y--) {
            if (c.isSolid(lx, y, lz)) {
                return y + 1; // livello del pavimento
            }
        }
        return -1;
    }

    public boolean breakBlock(Vector3f worldPos) {
        int cx = (int) Math.floor(worldPos.x / CHUNK_SIZE);
        int cz = (int) Math.floor(worldPos.z / CHUNK_SIZE);
        Point2i key = new Point2i(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk == null)
            return false;

        int lx = (int) (worldPos.x - cx * CHUNK_SIZE);
        int ly = (int) worldPos.y;
        int lz = (int) (worldPos.z - cz * CHUNK_SIZE);

        System.out.println("BLOCK: " + lx + " - " + ly + " - " + lz);

        return chunk.removeBlock(lx, ly, lz, chunk.getBlockType(lx, ly, lz));
    }

    /**
     * Posiziona un blocco di tipo `type` nella cella adiacente a quella colpita.
     */
    public boolean placeBlock(RaycastResult hit) {
        Vector3f target = new Vector3f(hit.blockPos).add(hit.faceNormal);
        int wx = (int) target.x;
        int wy = (int) target.y;
        int wz = (int) target.z;

        int cx = (int) Math.floor(wx / (float) CHUNK_SIZE);
        int cz = (int) Math.floor(wz / (float) CHUNK_SIZE);
        Point2i key = new Point2i(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk == null)
            return false;

        int lx = wx - cx * CHUNK_SIZE;
        int ly = wy;
        int lz = wz - cz * CHUNK_SIZE;

        return chunk.addBlock(lx, ly, lz, Main.inventory.getBlockTypeAt(Main.inventory.getSelectedHotbarIndex()));
    }

    /**
     * Raycast DDA: restituisce il blocco colpito e la normale della faccia.
     */
    public RaycastResult raycast(Vector3f origin, Vector3f direction, float maxDistance) {
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = direction.x > 0 ? 1 : -1;
        int stepY = direction.y > 0 ? 1 : -1;
        int stepZ = direction.z > 0 ? 1 : -1;

        float tMaxX = intBound(origin.x, direction.x);
        float tMaxY = intBound(origin.y, direction.y);
        float tMaxZ = intBound(origin.z, direction.z);

        float tDeltaX = stepX / direction.x;
        float tDeltaY = stepY / direction.y;
        float tDeltaZ = stepZ / direction.z;

        float distance = 0f;
        Vector3f lastNormal = new Vector3f(0, 0, 0);

        while (distance < maxDistance) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                distance = tMaxX;
                tMaxX += tDeltaX;
                lastNormal.set(-stepX, 0, 0);
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                distance = tMaxY;
                tMaxY += tDeltaY;
                lastNormal.set(0, -stepY, 0);
            } else {
                z += stepZ;
                distance = tMaxZ;
                tMaxZ += tDeltaZ;
                lastNormal.set(0, 0, -stepZ);
            }

            if (distance > maxDistance)
                break;
            if (isBlockSolid(x, y, z)) {
                return new RaycastResult(new Vector3f(x, y, z), new Vector3f(lastNormal));
            }
        }
        return null;
    }

    private float intBound(float s, float ds) {
        if (ds == 0)
            return Float.POSITIVE_INFINITY;
        if (ds < 0) {
            s = -s;
            ds = -ds;
        }
        s = s - (float) Math.floor(s);
        return (1 - s) / ds;
    }

    public boolean isBlockSolid(int worldX, int worldY, int worldZ) {
        int cx = (int) Math.floor(worldX / (float) CHUNK_SIZE);
        int cz = (int) Math.floor(worldZ / (float) CHUNK_SIZE);
        Point2i key = new Point2i(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk == null)
            return false;

        int lx = worldX - cx * CHUNK_SIZE;
        int ly = worldY;
        int lz = worldZ - cz * CHUNK_SIZE;
        return chunk.isSolid(lx, ly, lz);
    }

    private Path chunkFile(Point2i key) {
        return Main.worldDir.resolve(key.x + "_" + key.y + ".nbt");
    }

    public void save() {
        for (Map.Entry<Point2i, Chunk> e : chunks.entrySet()) {
            Chunk c = e.getValue();
            if (c.isDirty()) {
                try {
                    c.save(Main.worldDir.resolve(e.getKey().x + "_" + e.getKey().y + ".nbt"));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        Main.exitToMenu(shaderProgram);
    }
}