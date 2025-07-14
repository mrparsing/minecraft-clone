package com.project.world;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import com.project.Main;
import com.project.graphics.Camera;
import com.project.math.OpenSimplex2F;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

public class Chunk {
    private final int vao, vbo;
    final int sizeX, sizeY, sizeZ;
    private int vertexCount;
    private int offsetX, offsetY, offsetZ;
    private final BlockType[][][] blockTypes;
    private final OpenSimplex2F noise;
    private final Random random = new Random(4444);
    private final int textureID;
    private boolean dirty = false;

    // --- getter ---
    public boolean isDirty() {
        return dirty;
    }

    // --- marca il chunk come modificato ---
    private void markDirty() {
        dirty = true;
    }

    public Chunk(int shaderProgram, int sx, int sy, int sz) {
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        blockTypes = new BlockType[sizeX][sizeY][sizeZ];
        noise = new OpenSimplex2F(Main.seed);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        textureID = glGenTextures();
        loadTexture("/textures/25w10a_blocks.png-atlas.png");
    }

    private void loadTexture(String path) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null)
                throw new RuntimeException("Texture non trovata: " + path);
            BufferedImage img = ImageIO.read(is);
            int w = img.getWidth(), h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            for (int y = h - 1; y >= 0; y--) {
                for (int x = 0; x < w; x++) {
                    int p = pixels[y * w + x];
                    buf.put((byte) ((p >> 16) & 0xFF));
                    buf.put((byte) ((p >> 8) & 0xFF));
                    buf.put((byte) (p & 0xFF));
                    buf.put((byte) ((p >> 24) & 0xFF));
                }
            }
            buf.flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            glGenerateMipmap(GL_TEXTURE_2D);
        } catch (Exception e) {
            throw new RuntimeException("Errore caricamento texture: " + path, e);
        }
    }

    public void setWorldOffset(int x, int y, int z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    public void generateTerrain() {
        /* --- quote di riferimento --- */
        final int SEA_LEVEL = (int) (sizeY * 0.20); // livello mare
        final int SAND_END = SEA_LEVEL + 2; // fine transizione spiaggia
        final int SNOW_START = (int) (sizeY * 0.60); // inizio transizione neve
        final int SNOW_END = (int) (sizeY * 0.80); // neve certa

        int[][] heightMap = new int[sizeX][sizeZ];
        double[][] biomeMap = new double[sizeX][sizeZ];

        /* ---------- altezza per colonna ---------- */
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                double wx = x + offsetX;
                double wz = z + offsetZ;

                double biomeNoise = (noise.noise2(wx * 0.002, wz * 0.002) + 1) * 0.5;
                double detailNoise = noise.noise2(wx * 0.04, wz * 0.04) * 0.5;
                biomeMap[x][z] = biomeNoise;

                /* profili altitudine di massima */
                double plain = SEA_LEVEL + 4 + detailNoise * 2;
                double hill = SEA_LEVEL + 12 + detailNoise * 6;
                double mount = SEA_LEVEL + 25 + detailNoise * 16;

                double hD; // altezza double
                if (biomeNoise < 0.30)
                    hD = lerp(SEA_LEVEL - 4, plain, smoothstep(0.00, 0.30, biomeNoise));
                else if (biomeNoise < 0.55)
                    hD = lerp(plain, hill, smoothstep(0.30, 0.55, biomeNoise));
                else if (biomeNoise < 0.80)
                    hD = lerp(hill, mount, smoothstep(0.55, 0.80, biomeNoise));
                else
                    hD = lerp(mount, sizeY - 6, smoothstep(0.80, 1.00, biomeNoise));

                int h = Math.max(0, Math.min((int) hD, sizeY - 1));
                heightMap[x][z] = h;

                /* ---------- riempimento blocchi ---------- */
                for (int y = 0; y < sizeY; y++) {

                    /* aria / acqua sopra la superficie */
                    if (y > h) {
                        blockTypes[x][y][z] = (y <= SEA_LEVEL) ? BlockType.WATER : null;
                        continue;
                    }

                    /* fondali sotto il livello del mare */
                    if (h < SEA_LEVEL) {
                        blockTypes[x][y][z] = BlockType.SAND;
                        continue;
                    }

                    /* --- definizione blocco di superficie --- */
                    if (y == h) {
                        // --- fascia sabbia graduale ---
                        if (h <= SEA_LEVEL) {
                            blockTypes[x][y][z] = BlockType.SAND; // sempre sabbia sotto il mare
                        } else if (h <= SEA_LEVEL) {
                            // probabilità sabbia decrescente man mano che ci si allontana dal mare
                            double t = (double) (h - SEA_LEVEL) / 6.0; // t ∈ [0,1]
                            double p = 1.0 - t * t; // curva decrescente
                            blockTypes[x][y][z] = (random.nextDouble() < p) ? BlockType.SAND : BlockType.GRASS;
                        }
                        // --- fascia neve graduale ---
                        else if (h >= SNOW_START) {
                            if (h >= SNOW_END) {
                                blockTypes[x][y][z] = BlockType.SNOW;
                            } else {
                                double t = (double) (h - SNOW_START) / (SNOW_END - SNOW_START);
                                double p = t * t;
                                blockTypes[x][y][z] = (random.nextDouble() < p) ? BlockType.SNOW : BlockType.GRASS;
                            }
                        }
                        // --- fascia normale ---
                        else {
                            blockTypes[x][y][z] = BlockType.GRASS;
                        }
                    }
                    /* sottosuolo immediato: terra, poi pietra */
                    else if (y >= h - 3) {
                        blockTypes[x][y][z] = BlockType.DIRT;
                    } else {
                        blockTypes[x][y][z] = BlockType.STONE;
                    }
                }
            }
        }

        /*
         * ---------- alberi solo su erba, in fascia priva di neve/spiaggia ----------
         */
        int treesPerChunk = 10 + random.nextInt(10);
        for (int i = 0; i < treesPerChunk; i++) {
            int x = random.nextInt(sizeX);
            int z = random.nextInt(sizeZ);
            int h = heightMap[x][z];

            if (h > SEA_LEVEL + 2 && h < SNOW_START - 4 && h > SAND_END + 1 &&
                    blockTypes[x][h][z] == BlockType.GRASS) {
                generateTree(x, h + 1, z);
            }
        }


        final int IRON_MIN_Y = (int) (sizeY * 0.10);
        final int IRON_MAX_Y = (int) (sizeY * 0.64);
        final int COAL_MIN_Y = (int) (sizeY * 0.15);
        final int COAL_MAX_Y = sizeY - 1;
        final int DIAMOND_MIN_Y = (int) (sizeY * 0.0);
        final int DIAMOND_MAX_Y = (int) (sizeY * 0.10);


        generateOreVeins(BlockType.IRON_ORE, IRON_MIN_Y, IRON_MAX_Y, 1600, 18);
        generateOreVeins(BlockType.COAL_ORE, COAL_MIN_Y, COAL_MAX_Y, 1550, 22);
        generateOreVeins(BlockType.DIAMOND_ORE, DIAMOND_MIN_Y, DIAMOND_MAX_Y, 200, 22);

        rebuildMesh();
    }

    /**
     * Genera vene di un minerale tramite walk 3D casuale.
     * 
     * @param ore    tipo di blocco minerale
     * @param minY   livello inferiore
     * @param maxY   livello superiore
     * @param veins  numero di vene per chunk
     * @param length lunghezza massima di ogni vena
     */
    private void generateOreVeins(BlockType ore, int minY, int maxY, int veins, int length) {
        for (int i = 0; i < veins; i++) {
            int x = random.nextInt(sizeX);
            int y = random.nextInt(maxY - minY + 1) + minY;
            int z = random.nextInt(sizeZ);

            for (int j = 0; j < length; j++) {
                if (x < 0 || x >= sizeX || y < minY || y > maxY || z < 0 || z >= sizeZ)
                    break;
                if (blockTypes[x][y][z] == BlockType.STONE) {
                    blockTypes[x][y][z] = ore;
                }
                // passo casuale
                x += random.nextInt(3) - 1;
                y += random.nextInt(3) - 1;
                z += random.nextInt(3) - 1;
            }
        }
    }

    public boolean isBlockSolid(int x, int y, int z) {
        return isSolid(x, y, z);
    }

    private void generateTree(int trunkX, int trunkBaseY, int trunkZ) {
        int treeType = random.nextInt(3);
        int trunkHeight, leafRadius;

        if (treeType == 0) {
            trunkHeight = 4 + random.nextInt(2);
            leafRadius = 2;
        } else if (treeType == 1) {
            trunkHeight = 5 + random.nextInt(2);
            leafRadius = 2;
        } else {
            trunkHeight = 6 + random.nextInt(3);
            leafRadius = 3;
        }

        for (int y = trunkBaseY; y < trunkBaseY + trunkHeight; y++) {
            if (y < sizeY)
                blockTypes[trunkX][y][trunkZ] = BlockType.WOOD;
        }

        int leafStartY = trunkBaseY + trunkHeight - 2;
        int leafEndY = trunkBaseY + trunkHeight + 1;

        for (int ly = leafStartY; ly <= leafEndY; ly++) {
            if (ly < 0 || ly >= sizeY)
                continue;
            int currentRadius = leafRadius;
            if (ly == leafStartY)
                currentRadius -= 1;
            if (ly == leafEndY)
                currentRadius = 1;

            for (int dx = -currentRadius; dx <= currentRadius; dx++) {
                for (int dz = -currentRadius; dz <= currentRadius; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > currentRadius)
                        continue;

                    float density = (distance < currentRadius / 2f) ? 1.0f : 0.8f;

                    if (random.nextFloat() < density) {
                        int x = trunkX + dx;
                        int z = trunkZ + dz;
                        if (x >= 0 && x < sizeX && z >= 0 && z < sizeZ &&
                                (dx != 0 || dz != 0 || ly > leafEndY - 1) &&
                                blockTypes[x][ly][z] == null) {
                            blockTypes[x][ly][z] = BlockType.LEAVES;
                        }
                    }
                }
            }
        }
    }

    private void rebuildMesh() {
        List<Float> data = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockType type = blockTypes[x][y][z];
                    if (type == null)
                        continue;
                    for (Direction dir : Direction.values()) {
                        int nx = x + dir.dx, ny = y + dir.dy, nz = z + dir.dz;
                        BlockType neigh = (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && nz >= 0 && nz < sizeZ)
                                ? blockTypes[nx][ny][nz]
                                : null;
                        if (neigh == null) {
                            Cube.addFace(data, new Vector3f(x, y, z), dir, type);
                        }
                    }
                }
            }
        }

        FloatBuffer buf = BufferUtils.createFloatBuffer(data.size());
        data.forEach(buf::put);
        buf.flip();
        vertexCount = data.size() / 5; // ora sono 5 componenti per vertice

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);

        // posizione: 3 float
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // UV: 2 float
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // rimuovi completamente l'attributo 2 (AO)
    }

    public boolean isInFrustum(Camera cam) {
        return cam.isAABBInFrustum(
                new Vector3f(offsetX, offsetY, offsetZ),
                new Vector3f(offsetX + sizeX, offsetY + sizeY, offsetZ + sizeZ));
    }

    public void render(int shaderProgram) {
        int modelLoc = glGetUniformLocation(shaderProgram, "model");
        Matrix4f model = new Matrix4f().translation(offsetX, offsetY, offsetZ);
        float[] modelMat = new float[16];
        model.get(modelMat);
        glUniformMatrix4fv(modelLoc, false, modelMat);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureID);
        glUniform1i(glGetUniformLocation(shaderProgram, "ourTexture"), 0);

        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }

    public boolean isSolid(int x, int y, int z) {
        if (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ) {
            return blockTypes[x][y][z] != null;
        }
        return false;
    }

    public boolean removeBlock(int x, int y, int z, BlockType type) {
        if (!isSolid(x, y, z))
            return false;
        Main.inventory.addItem(type, 1);
        blockTypes[x][y][z] = null;
        markDirty(); // <—
        rebuildMesh();
        return true;
    }

    public boolean addBlock(int x, int y, int z, BlockType type) {
        if (isSolid(x, y, z))
            return false;
        Main.inventory.removeFromSelected(1);
        blockTypes[x][y][z] = type;
        markDirty();
        rebuildMesh();
        return true;
    }

    public BlockType getBlockType(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ)
            return null;
        return blockTypes[x][y][z];
    }

    public void save(Path path) throws IOException {
        // crea le cartelle se mancano
        Files.createDirectories(path.getParent());

        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(path)))) {

            // 1-byte firma per futura espansione
            out.writeByte(1); // versione file
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);

            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockType bt = blockTypes[x][y][z];
                        out.writeByte(bt == null ? -1 : bt.ordinal());
                    }
                }
            }
        }
        dirty = false; // ora è in sync con il disco
    }

    public static Chunk load(int shaderProgram, Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(path)))) {

            int version = in.readByte(); // per eventuali futuri cambi
            if (version != 1)
                throw new IOException("Versione file non supportata");

            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();

            Chunk c = new Chunk(shaderProgram, sx, sy, sz);

            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        int id = in.readByte();
                        c.blockTypes[x][y][z] = (id == -1) ? null : BlockType.values()[id];
                    }
                }
            }
            c.rebuildMesh(); // importantissimo!
            c.dirty = false; // è già sincronizzato
            return c;
        }
    }
}