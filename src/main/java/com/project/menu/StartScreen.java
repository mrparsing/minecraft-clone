package com.project.menu;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <h2>StartScreen</h2>
 * Gestisce il menu iniziale:
 * <ul>
 * <li>Selezione di un mondo esistente</li>
 * <li>Creazione di un nuovo mondo con nome e seed facoltativo</li>
 * <li>Eliminazione di un mondo con conferma (X poi ENTER / ESC)</li>
 * <li>Uscita dall'applicazione</li>
 * </ul>
 */
public class StartScreen {

    /* ====================================================================== */
    /* Costanti */
    /* ====================================================================== */

    private static final String NEW_WORLD_LABEL = "[ Crea nuovo mondo ]";
    private static final String EXIT_LABEL = "[ Esci ]";

    private static final int MAX_CHARS = 2048;
    private static final int MAX_VERTS = MAX_CHARS * 10 * 6;
    private static final ByteBuffer QUAD_BUF = BufferUtils.createByteBuffer(MAX_CHARS * 270);
    private static final FloatBuffer TRI_BUF = BufferUtils.createFloatBuffer(MAX_VERTS * 2);

    /* ====================================================================== */
    /* Stato runtime */
    /* ====================================================================== */

    private final long window;
    private final Path rootDir;
    private final List<Path> worlds;

    private int selected = 0;
    private boolean typingName = false;
    private boolean typingSeed = false;
    private boolean confirmingDelete = false;

    private final StringBuilder nameBuf = new StringBuilder();
    private final StringBuilder seedBuf = new StringBuilder();
    private final boolean[] prevKey = new boolean[GLFW_KEY_LAST + 1];

    /* ====================================================================== */
    /* OpenGL: VAO, VBO e shader */
    /* ====================================================================== */

    private static final int vao;
    private static final int vbo;
    private static final int menuProgram;
    private static final int uViewportLoc;
    private static final int uOffsetLoc;
    private static final int uColorLoc;
    private static final int uScaleLoc;

    static {
        // Setup VAO / VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, MAX_VERTS * 2L * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, Float.BYTES * 2, 0);
        glEnableVertexAttribArray(0);
        glBindVertexArray(0);

        // Shader semplice per testo 2D
        String vert = """
            #version 330 core
            layout(location = 0) in vec2 inPos;
            uniform vec2 uViewport;
            uniform vec2 uOffset;
            uniform vec2 uScale;
            void main() {
                vec2 p  = inPos * uScale + uOffset;
                vec2 ndc = vec2(p.x / uViewport.x * 2.0 - 1.0,
                                1.0 - p.y / uViewport.y * 2.0);
                gl_Position = vec4(ndc, 0.0, 1.0);
            }
        """;

        String frag = """
            #version 330 core
            out vec4 FragColor;
            uniform vec3 uColor;
            void main() { FragColor = vec4(uColor, 1.0); }
        """;

        menuProgram = compileProgram(vert, frag);
        uViewportLoc = glGetUniformLocation(menuProgram, "uViewport");
        uOffsetLoc   = glGetUniformLocation(menuProgram, "uOffset");
        uColorLoc    = glGetUniformLocation(menuProgram, "uColor");
        uScaleLoc    = glGetUniformLocation(menuProgram, "uScale");
    }

    private static int compileProgram(String vsrc, String fsrc) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vsrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) != GL_TRUE)
            throw new IllegalStateException("Vertex shader error:\n" + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fsrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) != GL_TRUE)
            throw new IllegalStateException("Fragment shader error:\n" + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) != GL_TRUE)
            throw new IllegalStateException("Program link error:\n" + glGetProgramInfoLog(prog));

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    /* ====================================================================== */
    /* Costruttore */
    /* ====================================================================== */

    public StartScreen(long window) throws IOException {
        this.window = window;
        this.rootDir = Paths.get("world");
        Files.createDirectories(rootDir);
        worlds = Files.list(rootDir)
                .filter(Files::isDirectory)
                .sorted()
                .collect(Collectors.toList());
    }

    /* ====================================================================== */
    /* Loop principale del menu */
    /* ====================================================================== */

    public String selectWorld() throws IOException {
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        glfwSetCharCallback(window, (win, codepoint) -> {
            if ((typingName || typingSeed) && codepoint >= 32 && codepoint < 127)
                (typingName ? nameBuf : seedBuf).append((char) codepoint);
        });

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0.05f, 0.07f, 0.11f, 1.0f);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT);
            int[] w = new int[1], h = new int[1];
            glfwGetFramebufferSize(window, w, h);

            float H = h[0];
            float headerY = H * 0.15f;
            float firstItemY = H * 0.30f;
            float lineStep = H * 0.06f;

            renderText("MINECRAFT CLONE", 0, headerY * 0.2f, true);
            renderText("SELEZIONA MONDO", 0, headerY, true);

            // Conferma eliminazione
            if (confirmingDelete) {
                renderText("Confermare eliminazione? ENTER = si / ESC = no", 0, firstItemY, true);
                if (keyPressed(GLFW_KEY_ENTER)) {
                    Path dir = worlds.get(selected - 1);
                    deleteDirectory(dir);
                    worlds.remove(selected - 1);
                    confirmingDelete = false;
                    if (selected >= worlds.size() + 1)
                        selected = Math.max(0, worlds.size());
                    continue;
                }
                if (keyPressed(GLFW_KEY_ESCAPE)) {
                    confirmingDelete = false;
                    continue;
                }
            }

            // Inserimento nome
            else if (typingName) {
                renderText("Nome del mondo:", 0, firstItemY, true);
                renderText("› " + nameBuf + "▌", 0, firstItemY + lineStep, true);
                if (keyPressed(GLFW_KEY_ENTER)) {
                    typingName = false;
                    typingSeed = true;
                    continue;
                }
                if (keyPressed(GLFW_KEY_ESCAPE)) {
                    typingName = false;
                    nameBuf.setLength(0);
                    continue;
                }
                if (keyPressed(GLFW_KEY_BACKSPACE) && nameBuf.length() > 0)
                    nameBuf.setLength(nameBuf.length() - 1);
            }

            // Inserimento seed
            else if (typingSeed) {
                renderText("Seed (vuoto = casuale):", 0, firstItemY, true);
                renderText("› " + seedBuf + "▌", 0, firstItemY + lineStep, true);
                if (keyPressed(GLFW_KEY_ENTER)) {
                    String worldName = nameBuf.toString().trim();
                    if (worldName.isEmpty())
                        worldName = generateDefaultName();
                    long seed = parseSeed(seedBuf.toString().trim());
                    Path dir = rootDir.resolve(worldName);
                    try {
                        Files.createDirectory(dir);
                    } catch (FileAlreadyExistsException ignored) {}
                    Files.writeString(dir.resolve("seed.txt"), Long.toString(seed), StandardCharsets.UTF_8);
                    worlds.add(dir);
                    return exitMenu(dir);
                }
                if (keyPressed(GLFW_KEY_ESCAPE)) {
                    typingSeed = false;
                    seedBuf.setLength(0);
                    continue;
                }
                if (keyPressed(GLFW_KEY_BACKSPACE) && seedBuf.length() > 0)
                    seedBuf.setLength(seedBuf.length() - 1);
            }

            // Menu principale
            else {
                String label0 = (selected == 0) ? "> " + NEW_WORLD_LABEL + " <" : NEW_WORLD_LABEL;
                renderText(label0, 0, firstItemY, true);

                for (int i = 0; i < worlds.size(); i++) {
                    String name = worlds.get(i).getFileName().toString();
                    if (selected == i + 1)
                        name = "> " + name + " <";
                    renderText(name, 0, firstItemY + (i + 1) * lineStep, true);
                }

                int exitIndex = worlds.size() + 1;
                String exitLabel = (selected == exitIndex) ? "> " + EXIT_LABEL + " <" : EXIT_LABEL;
                renderText(exitLabel, 0, firstItemY + exitIndex * lineStep, true);

                int items = worlds.size() + 2;
                if (keyPressed(GLFW_KEY_DOWN)) selected = (selected + 1) % items;
                if (keyPressed(GLFW_KEY_UP)) selected = (selected - 1 + items) % items;

                if (keyPressed(GLFW_KEY_ENTER)) {
                    if (selected == 0) {
                        typingName = true;
                        nameBuf.setLength(0);
                        seedBuf.setLength(0);
                        continue;
                    } else if (selected == exitIndex) {
                        glfwSetWindowShouldClose(window, true);
                        return null;
                    } else {
                        return exitMenu(worlds.get(selected - 1));
                    }
                }

                if (keyPressed(GLFW_KEY_X) && selected > 0 && selected < exitIndex) {
                    confirmingDelete = true;
                    continue;
                }
            }

            renderText("^/v : spostati    ENTER : seleziona    X : elimina    ESC : indietro", 40, h[0] - 50, false);
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        return null;
    }

    /* ====================================================================== */
    /* Utility */
    /* ====================================================================== */

    private long parseSeed(String s) {
        if (s.isEmpty())
            return new Random().nextInt() & 0x7FFFFFFF;
        try {
            long v = Long.parseLong(s);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE)
                throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            return Math.abs(s.hashCode());
        }
    }

    private String exitMenu(Path dir) {
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        return dir.getFileName().toString();
    }

    private String generateDefaultName() {
        int n = 1;
        String base = "NuovoMondo";
        while (Files.exists(rootDir.resolve(base + n))) n++;
        return base + n;
    }

    private static void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException("Impossibile eliminare " + p, e);
                    }
                });
        }
    }

    private boolean keyPressed(int key) {
        boolean down = glfwGetKey(window, key) == GLFW_PRESS;
        boolean fired = down && !prevKey[key];
        prevKey[key] = down;
        return fired;
    }

    private void renderText(String txt, float x, float y, boolean centerX) {
        if (txt.isEmpty()) return;
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        int width = w[0], height = h[0];

        QUAD_BUF.clear();
        int quadCount = STBEasyFont.stb_easy_font_print(0, 0, txt, null, QUAD_BUF);
        QUAD_BUF.position(quadCount * 4 * 16);
        QUAD_BUF.flip();

        TRI_BUF.clear();
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;

        for (int q = 0; q < quadCount; q++) {
            int base = q * 64;
            float x0 = QUAD_BUF.getFloat(base), y0 = QUAD_BUF.getFloat(base + 4);
            float x1 = QUAD_BUF.getFloat(base + 16), y1 = QUAD_BUF.getFloat(base + 20);
            float x2 = QUAD_BUF.getFloat(base + 32), y2 = QUAD_BUF.getFloat(base + 36);
            float x3 = QUAD_BUF.getFloat(base + 48), y3 = QUAD_BUF.getFloat(base + 52);
            TRI_BUF.put(x0).put(y0).put(x1).put(y1).put(x2).put(y2);
            TRI_BUF.put(x0).put(y0).put(x2).put(y2).put(x3).put(y3);
            minX = Math.min(minX, Math.min(Math.min(x0, x1), Math.min(x2, x3)));
            maxX = Math.max(maxX, Math.max(Math.max(x0, x1), Math.max(x2, x3)));
        }

        TRI_BUF.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, TRI_BUF);
        glUseProgram(menuProgram);
        glBindVertexArray(vao);
        glUniform2f(uViewportLoc, width, height);
        float scale = Math.max(1.5f, height / 400.0f);
        glUniform2f(uScaleLoc, scale, scale);
        float rawWidth = maxX - minX;
        float xPos = centerX ? (width - rawWidth * scale) * 0.5f : x;
        glUniform2f(uOffsetLoc, xPos, y);
        glUniform3f(uColorLoc, 1.0f, 1.0f, 1.0f);
        glDrawArrays(GL_TRIANGLES, 0, TRI_BUF.limit() / 2);
        glBindVertexArray(0);
    }
}