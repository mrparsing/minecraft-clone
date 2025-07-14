package com.project.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import com.project.world.Direction;
import com.project.world.Inventory;
import com.project.world.BlockType.UV;
import com.project.world.Inventory.ItemStack;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Hotbar rendering system: gestisce la visualizzazione dell'hotbar con slot, icone e contatori numerici.
 * Utilizza atlanti di texture per il rendering delle icone e segmenti 7-segment per i numeri.
 */
public class Hotbar {
    
    // ===== COSTANTI =====
    
    /** Numero di slot nell'hotbar */
    private static final int SLOT_COUNT = Inventory.HOTBAR_SIZE;
    
    /** Dimensione di ogni slot in pixel */
    private static final float SLOT_SIZE = 35f;
    
    /** Spaziatura tra gli slot */
    private static final float SLOT_PADDING = 4f;
    
    /** Dimensione delle cifre per i contatori */
    private static final float DIGIT_SIZE = 10f;
    
    /** Margine interno per le icone negli slot */
    private static final float ICON_INSET = 5f;
    
    /** Numero di colonne nell'atlante delle texture */
    private static final int ATLAS_COLS = 64;
    
    /** Numero di righe nell'atlante delle texture */
    private static final int ATLAS_ROWS = 32;
    
    /** Spessore del bordo di selezione */
    private static final float BORDER_THICKNESS = 4f;
    
    /** Posizione verticale dell'hotbar dal basso dello schermo */
    private static final float HOTBAR_Y_POSITION = 10f;
    
    /** Margine per il posizionamento dei contatori */
    private static final float COUNTER_MARGIN = 4f;
    
    /** Spaziatura tra le cifre */
    private static final float DIGIT_SPACING_MULTIPLIER = 1.1f;
    
    // ===== DEFINIZIONE SEGMENTI PER CIFRE =====
    
    /**
     * Mappa che definisce i segmenti per ogni cifra (0-9) in formato 7-segment.
     * Ogni segmento è definito come [x, y, width, height] in un sistema di coordinate 8x8.
     */
    private static final Map<Character, List<float[]>> DIGIT_SEGMENTS = new HashMap<>();
    
    static {
        // Inizializzazione dei segmenti per ogni cifra
        DIGIT_SEGMENTS.put('0', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 0, 2, 2, 4 },   // Segmento sinistro
                new float[] { 6, 2, 2, 4 }    // Segmento destro
        ));
        
        DIGIT_SEGMENTS.put('1', List.of(
                new float[] { 3, 0, 2, 8 }    // Segmento centrale verticale
        ));
        
        DIGIT_SEGMENTS.put('2', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 0, 0, 2, 4 },   // Segmento sinistro inferiore
                new float[] { 6, 3, 2, 4 }    // Segmento destro superiore
        ));
        
        DIGIT_SEGMENTS.put('3', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 6, 0, 2, 8 }    // Segmento destro completo
        ));
        
        DIGIT_SEGMENTS.put('4', List.of(
                new float[] { 6, 0, 2, 8 },   // Segmento destro completo
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 6, 4, 2, 4 },   // Segmento destro superiore
                new float[] { 0, 4, 2, 4 }    // Segmento sinistro superiore
        ));
        
        DIGIT_SEGMENTS.put('5', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 6, 0, 2, 4 },   // Segmento destro inferiore
                new float[] { 0, 3, 2, 4 }    // Segmento sinistro superiore
        ));
        
        DIGIT_SEGMENTS.put('6', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 0, 0, 2, 8 },   // Segmento sinistro completo
                new float[] { 6, 0, 2, 4 },   // Segmento destro inferiore
                new float[] { 0, 0, 2, 4 }    // Segmento sinistro inferiore (duplicato)
        ));
        
        DIGIT_SEGMENTS.put('7', List.of(
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 6, 0, 2, 8 }    // Segmento destro completo
        ));
        
        DIGIT_SEGMENTS.put('8', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 0, 2, 2, 4 },   // Segmento sinistro
                new float[] { 6, 2, 2, 4 }    // Segmento destro
        ));
        
        DIGIT_SEGMENTS.put('9', List.of(
                new float[] { 0, 0, 8, 2 },   // Segmento inferiore
                new float[] { 0, 3, 8, 2 },   // Segmento centrale
                new float[] { 0, 6, 8, 2 },   // Segmento superiore
                new float[] { 6, 2, 2, 6 },   // Segmento destro superiore esteso
                new float[] { 0, 3, 2, 4 },   // Segmento sinistro superiore
                new float[] { 6, 3, 2, 4 }    // Segmento destro superiore
        ));
    }
    
    // ===== VARIABILI DI ISTANZA =====
    
    /** Shader program per il rendering degli slot e delle icone */
    private int slotShader;
    
    /** Shader program per il rendering dei colori solidi */
    private int colorShader;
    
    /** Vertex Array Object per il rendering con texture */
    private int vao;
    
    /** Vertex Buffer Object per il rendering con texture */
    private int vbo;
    
    /** Vertex Array Object per il rendering di quad colorati */
    private int quadVao;
    
    /** Vertex Buffer Object per il rendering di quad colorati */
    private int quadVbo;
    
    /** ID texture per gli slot dell'hotbar */
    private int slotTextureID;
    
    /** ID texture per l'atlante dei blocchi */
    private int atlasTextureID;
    
    // ===== COSTRUTTORE =====
    
    /**
     * Inizializza il sistema di rendering dell'hotbar.
     * Crea shaders, carica texture e prepara i buffer OpenGL.
     */
    public Hotbar() {
        // Creazione degli shader
        slotShader = createSlotShader();
        colorShader = createColorShader();
        
        // Caricamento delle texture
        slotTextureID = glGenTextures();
        loadTexture("/textures/slot.png", slotTextureID);
        
        atlasTextureID = glGenTextures();
        loadTexture("/textures/25w10a_blocks.png-atlas.png", atlasTextureID);
        
        // Inizializzazione dei buffer per il rendering con texture
        initializeTextureBuffers();
        
        // Inizializzazione dei buffer per il rendering di colori solidi
        initializeColorBuffers();
    }
    
    // ===== METODI DI INIZIALIZZAZIONE =====
    
    /**
     * Inizializza i buffer OpenGL per il rendering con texture.
     * Configura VAO e VBO per vertici con coordinate di posizione e UV.
     */
    private void initializeTextureBuffers() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);
        
        // Attributo 0: posizione (x, y)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // Attributo 1: coordinate UV
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    /**
     * Inizializza i buffer OpenGL per il rendering di quad colorati.
     * Configura VAO e VBO per vertici con solo coordinate di posizione.
     */
    private void initializeColorBuffers() {
        quadVao = glGenVertexArrays();
        quadVbo = glGenBuffers();
        
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 2 * Float.BYTES, GL_DYNAMIC_DRAW);
        
        // Attributo 0: posizione (x, y)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    // ===== CREAZIONE SHADERS =====
    
    /**
     * Crea lo shader program per il rendering di slot e icone con texture.
     * @return ID del shader program
     */
    private int createSlotShader() {
        String vertexShader = "#version 330 core\n" +
                "layout(location=0) in vec2 aPos;\n" +
                "layout(location=1) in vec2 aUV;\n" +
                "uniform mat4 uProj;\n" +
                "out vec2 vUV;\n" +
                "void main(){ gl_Position = uProj * vec4(aPos,0,1); vUV = aUV;}\n";
        
        String fragmentShader = "#version 330 core\n" +
                "in vec2 vUV;\n" +
                "out vec4 Frag;\n" +
                "uniform sampler2D uTexture;\n" +
                "void main(){ Frag = texture(uTexture, vUV);}\n";
        
        return ShaderUtils.createProgram(vertexShader, fragmentShader);
    }
    
    /**
     * Crea lo shader program per il rendering di colori solidi.
     * @return ID del shader program
     */
    private int createColorShader() {
        String vertexShader = "#version 330 core\n" +
                "layout(location=0) in vec2 aPos;\n" +
                "uniform mat4 uProj;\n" +
                "void main(){ gl_Position = uProj * vec4(aPos,0,1);}\n";
        
        String fragmentShader = "#version 330 core\n" +
                "uniform vec4 uColor;\n" +
                "out vec4 Frag;\n" +
                "void main(){ Frag = uColor;}\n";
        
        return ShaderUtils.createProgram(vertexShader, fragmentShader);
    }
    
    // ===== CARICAMENTO TEXTURE =====
    
    /**
     * Carica una texture da file e la carica in OpenGL.
     * @param resourcePath Percorso del file texture
     * @param textureID ID della texture OpenGL
     */
    private void loadTexture(String resourcePath, int textureID) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        
        // Configurazione parametri texture
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Texture non trovata: " + resourcePath);
            }
            
            // Caricamento dell'immagine
            BufferedImage image = ImageIO.read(inputStream);
            int width = image.getWidth();
            int height = image.getHeight();
            
            // Conversione dei pixel in formato RGBA
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            
            // Conversione pixel con flip verticale
            for (int y = height - 1; y >= 0; y--) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF))  // Red
                          .put((byte) ((pixel >> 8) & 0xFF))   // Green
                          .put((byte) (pixel & 0xFF))          // Blue
                          .put((byte) ((pixel >> 24) & 0xFF)); // Alpha
                }
            }
            buffer.flip();
            
            // Caricamento della texture in OpenGL
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            
        } catch (Exception e) {
            throw new RuntimeException("Errore nel caricamento della texture: " + resourcePath, e);
        } finally {
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }
    
    // ===== RENDERING PRINCIPALE =====
    
    /**
     * Renderizza l'hotbar completo con slot, icone, bordo di selezione e contatori.
     * @param width Larghezza della finestra
     * @param height Altezza della finestra
     * @param inventory Inventario da visualizzare
     */
    public void render(int width, int height, Inventory inventory) {
        // Configurazione stati OpenGL per il rendering 2D
        setupRenderingStates();
        
        // Calcolo posizioni
        Matrix4f projectionMatrix = new Matrix4f().ortho2D(0, width, 0, height);
        float totalWidth = SLOT_COUNT * (SLOT_SIZE + SLOT_PADDING) - SLOT_PADDING;
        float startX = (width - totalWidth) / 2f;
        float y = HOTBAR_Y_POSITION;
        
        // Rendering degli slot
        renderSlots(projectionMatrix, startX, y);
        
        // Rendering delle icone
        renderIcons(projectionMatrix, startX, y, inventory);
        
        // Rendering del bordo di selezione
        renderSelectionBorder(projectionMatrix, startX, y, inventory.getSelectedHotbarIndex());
        
        // Rendering dei contatori
        renderItemCounts(projectionMatrix, startX, y, inventory);
        
        // Ripristino stati OpenGL
        restoreRenderingStates();
    }
    
    // ===== METODI DI RENDERING SPECIFICI =====
    
    /**
     * Configura gli stati OpenGL per il rendering 2D dell'hotbar.
     */
    private void setupRenderingStates() {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Ripristina gli stati OpenGL dopo il rendering dell'hotbar.
     */
    private void restoreRenderingStates() {
        glUseProgram(0);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }
    
    /**
     * Renderizza gli slot dell'hotbar.
     * @param projectionMatrix Matrice di proiezione
     * @param startX Posizione X iniziale
     * @param y Posizione Y
     */
    private void renderSlots(Matrix4f projectionMatrix, float startX, float y) {
        glUseProgram(slotShader);
        glUniformMatrix4fv(glGetUniformLocation(slotShader, "uProj"), false,
                projectionMatrix.get(BufferUtils.createFloatBuffer(16)));
        
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(glGetUniformLocation(slotShader, "uTexture"), 0);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, slotTextureID);
        
        for (int i = 0; i < SLOT_COUNT; i++) {
            float x = startX + i * (SLOT_SIZE + SLOT_PADDING);
            drawTexturedQuad(x, y, SLOT_SIZE, SLOT_SIZE, 0, 0, 1, 1);
        }
        
        glBindVertexArray(0);
    }
    
    /**
     * Renderizza le icone degli oggetti negli slot.
     * @param projectionMatrix Matrice di proiezione
     * @param startX Posizione X iniziale
     * @param y Posizione Y
     * @param inventory Inventario contenente gli oggetti
     */
    private void renderIcons(Matrix4f projectionMatrix, float startX, float y, Inventory inventory) {
        glUseProgram(slotShader);
        glUniformMatrix4fv(glGetUniformLocation(slotShader, "uProj"), false,
                projectionMatrix.get(BufferUtils.createFloatBuffer(16)));
        
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(glGetUniformLocation(slotShader, "uTexture"), 0);
        glBindVertexArray(vao);
        glBindTexture(GL_TEXTURE_2D, atlasTextureID);
        
        for (int i = 0; i < SLOT_COUNT; i++) {
            float x = startX + i * (SLOT_SIZE + SLOT_PADDING);
            ItemStack itemStack = inventory.getItem(i);
            
            if (itemStack != null) {
                // Calcolo coordinate UV nell'atlante
                UV uvCoordinates = itemStack.getType().getUV(Direction.UP);
                float u0 = uvCoordinates.x() / (float) ATLAS_COLS;
                float v0 = uvCoordinates.y() / (float) ATLAS_ROWS;
                float deltaU = 1f / ATLAS_COLS;
                float deltaV = 1f / ATLAS_ROWS;
                
                // Rendering dell'icona con margine interno
                drawTexturedQuad(
                        x + ICON_INSET, y + ICON_INSET,
                        SLOT_SIZE - ICON_INSET * 2, SLOT_SIZE - ICON_INSET * 2,
                        u0, v0 + deltaV,    // Coordinata UV inferiore sinistra
                        u0 + deltaU, v0     // Coordinata UV superiore destra
                );
            }
        }
        
        glBindVertexArray(0);
    }
    
    /**
     * Renderizza il bordo di selezione attorno allo slot selezionato.
     * @param projectionMatrix Matrice di proiezione
     * @param startX Posizione X iniziale
     * @param y Posizione Y
     * @param selectedIndex Indice dello slot selezionato
     */
    private void renderSelectionBorder(Matrix4f projectionMatrix, float startX, float y, int selectedIndex) {
        float selectedX = startX + selectedIndex * (SLOT_SIZE + SLOT_PADDING);
        
        glUseProgram(colorShader);
        glUniformMatrix4fv(glGetUniformLocation(colorShader, "uProj"), false,
                projectionMatrix.get(BufferUtils.createFloatBuffer(16)));
        glUniform4f(glGetUniformLocation(colorShader, "uColor"), 0f, 0f, 0f, 1f); // Nero
        
        glBindVertexArray(quadVao);
        
        // Rendering dei quattro lati del bordo
        drawColorQuad(selectedX, y, SLOT_SIZE, BORDER_THICKNESS);                           // Inferiore
        drawColorQuad(selectedX, y + SLOT_SIZE - BORDER_THICKNESS, SLOT_SIZE, BORDER_THICKNESS); // Superiore
        drawColorQuad(selectedX, y, BORDER_THICKNESS, SLOT_SIZE);                           // Sinistro
        drawColorQuad(selectedX + SLOT_SIZE - BORDER_THICKNESS, y, BORDER_THICKNESS, SLOT_SIZE); // Destro
        
        glBindVertexArray(0);
    }
    
    /**
     * Renderizza i contatori numerici per gli oggetti con quantità > 1.
     * @param projectionMatrix Matrice di proiezione
     * @param startX Posizione X iniziale
     * @param y Posizione Y
     * @param inventory Inventario contenente gli oggetti
     */
    private void renderItemCounts(Matrix4f projectionMatrix, float startX, float y, Inventory inventory) {
        glUseProgram(colorShader);
        glUniformMatrix4fv(glGetUniformLocation(colorShader, "uProj"), false,
                projectionMatrix.get(BufferUtils.createFloatBuffer(16)));
        glUniform4f(glGetUniformLocation(colorShader, "uColor"), 1f, 1f, 1f, 1f); // Bianco
        
        glBindVertexArray(quadVao);
        
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack itemStack = inventory.getItem(i);
            
            if (itemStack != null && itemStack.getCount() > 1) {
                String countString = String.valueOf(itemStack.getCount());
                float digitSpacing = DIGIT_SIZE * DIGIT_SPACING_MULTIPLIER;
                float totalWidth = countString.length() * digitSpacing;
                
                // Posizionamento del contatore nell'angolo inferiore destro dello slot
                float baseX = startX + i * (SLOT_SIZE + SLOT_PADDING) + SLOT_SIZE - totalWidth - COUNTER_MARGIN;
                float baseY = y + COUNTER_MARGIN;
                
                // Rendering di ogni cifra
                for (int digitIndex = 0; digitIndex < countString.length(); digitIndex++) {
                    drawDigit(baseX + digitIndex * digitSpacing, baseY, DIGIT_SIZE, countString.charAt(digitIndex));
                }
            }
        }
        
        glBindVertexArray(0);
    }
    
    // ===== METODI DI RENDERING PRIMITIVI =====
    
    /**
     * Disegna un quad con texture.
     * @param x Posizione X
     * @param y Posizione Y
     * @param width Larghezza
     * @param height Altezza
     * @param u0 Coordinata U iniziale
     * @param v0 Coordinata V iniziale
     * @param u1 Coordinata U finale
     * @param v1 Coordinata V finale
     */
    private void drawTexturedQuad(float x, float y, float width, float height, 
                                  float u0, float v0, float u1, float v1) {
        float[] vertices = {
            x,         y,          u0, v1,  // Vertice inferiore sinistro
            x + width, y,          u1, v1,  // Vertice inferiore destro
            x + width, y + height, u1, v0,  // Vertice superiore destro
            x,         y,          u0, v1,  // Vertice inferiore sinistro (secondo triangolo)
            x + width, y + height, u1, v0,  // Vertice superiore destro (secondo triangolo)
            x,         y + height, u0, v0   // Vertice superiore sinistro
        };
        
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
    
    /**
     * Disegna un quad colorato.
     * @param x Posizione X
     * @param y Posizione Y
     * @param width Larghezza
     * @param height Altezza
     */
    private void drawColorQuad(float x, float y, float width, float height) {
        float[] vertices = {
            x,         y,          // Vertice inferiore sinistro
            x + width, y,          // Vertice inferiore destro
            x + width, y + height, // Vertice superiore destro
            x,         y,          // Vertice inferiore sinistro (secondo triangolo)
            x + width, y + height, // Vertice superiore destro (secondo triangolo)
            x,         y + height  // Vertice superiore sinistro
        };
        
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }
    
    /**
     * Disegna una singola cifra utilizzando segmenti 7-segment.
     * @param x Posizione X
     * @param y Posizione Y
     * @param size Dimensione della cifra
     * @param digit Cifra da disegnare
     */
    private void drawDigit(float x, float y, float size, char digit) {
        List<float[]> segments = DIGIT_SEGMENTS.getOrDefault(digit, Collections.emptyList());
        float scale = size / 8f; // I segmenti sono definiti per una griglia 8x8
        
        for (float[] segment : segments) {
            float segmentX = x + segment[0] * scale;
            float segmentY = y + segment[1] * scale;
            float segmentWidth = segment[2] * scale;
            float segmentHeight = segment[3] * scale;
            
            drawColorQuad(segmentX, segmentY, segmentWidth, segmentHeight);
        }
    }
}