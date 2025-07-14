package com.project.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

/**
 * Classe Crosshair che gestisce il rendering del mirino al centro dello schermo.
 * Il mirino è composto da due linee che formano una croce (+).
 */
public class Crosshair {
    
    // ==================== COSTANTI ====================
    private static final float SIZE = 0.02f;        // Dimensione del mirino
    private static final float THICKNESS = 0.002f;  // Spessore delle linee
    
    // ==================== VARIABILI RENDERING ====================
    private int vao;           // Vertex Array Object
    private int vbo;           // Vertex Buffer Object
    private int shaderProgram; // Programma shader
    
    // ==================== COSTRUTTORE ====================
    /**
     * Costruisce un nuovo mirino inizializzando i buffer OpenGL e gli shader.
     */
    public Crosshair() {
        initializeGeometry();
        shaderProgram = createCrosshairShader();
    }
    
    // ==================== METODI PRINCIPALI ====================
    /**
     * Renderizza il mirino al centro dello schermo.
     * Disabilita temporaneamente il depth test per assicurare la visibilità.
     */
    public void render() {
        // Disabilita depth test per renderizzare sempre sopra tutto
        glDisable(GL_DEPTH_TEST);
        
        // Attiva il programma shader
        glUseProgram(shaderProgram);
        
        // Configura proiezione ortogonale
        setupOrthographicProjection();
        
        // Renderizza le linee del mirino
        renderCrosshairLines();
        
        // Riabilita depth test
        glEnable(GL_DEPTH_TEST);
    }
    
    // ==================== METODI DI INIZIALIZZAZIONE ====================
    /**
     * Inizializza la geometria del mirino creando i buffer OpenGL.
     */
    private void initializeGeometry() {
        // Definisce i vertici per una croce (+)
        float[] vertices = createCrosshairVertices();
        
        // Setup VAO/VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        
        // Configura il buffer dei vertici
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        // Configura gli attributi dei vertici
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        
        // Unbind per sicurezza
        glBindVertexArray(0);
    }
    
    /**
     * Crea l'array di vertici per il mirino.
     * 
     * @return Array di vertici che rappresentano una croce
     */
    private float[] createCrosshairVertices() {
        return new float[] {
            // Linea orizzontale (rettangolo)
            -SIZE, -THICKNESS,  // Bottom-left
             SIZE, -THICKNESS,  // Bottom-right
             SIZE,  THICKNESS,  // Top-right
            -SIZE,  THICKNESS,  // Top-left
            
            // Linea verticale (rettangolo)
            -THICKNESS, -SIZE,  // Bottom-left
             THICKNESS, -SIZE,  // Bottom-right
             THICKNESS,  SIZE,  // Top-right
            -THICKNESS,  SIZE   // Top-left
        };
    }
    
    /**
     * Crea e compila gli shader per il mirino.
     * 
     * @return ID del programma shader compilato
     */
    private int createCrosshairShader() {
        String vertexShader = createVertexShaderSource();
        String fragmentShader = createFragmentShaderSource();
        
        return ShaderUtils.createProgram(vertexShader, fragmentShader);
    }
    
    /**
     * Crea il codice sorgente del vertex shader.
     * 
     * @return Codice sorgente del vertex shader
     */
    private String createVertexShaderSource() {
        return "#version 150\n" +
               "uniform mat4 projection;\n" +
               "in vec2 position;\n" +
               "void main() {\n" +
               "    gl_Position = projection * vec4(position, 0.0, 1.0);\n" +
               "}";
    }
    
    /**
     * Crea il codice sorgente del fragment shader.
     * 
     * @return Codice sorgente del fragment shader
     */
    private String createFragmentShaderSource() {
        return "#version 150\n" +
               "out vec4 FragColor;\n" +
               "void main() {\n" +
               "    FragColor = vec4(1.0, 1.0, 1.0, 1.0);\n" + // Bianco
               "}";
    }
    
    // ==================== METODI DI RENDERING ====================
    /**
     * Configura la proiezione ortogonale per il rendering 2D del mirino.
     */
    private void setupOrthographicProjection() {
        Matrix4f projection = new Matrix4f().ortho(-1, 1, -1, 1, -1, 1);
        
        int projectionLocation = glGetUniformLocation(shaderProgram, "projection");
        glUniformMatrix4fv(projectionLocation, false, 
            projection.get(BufferUtils.createFloatBuffer(16)));
    }
    
    /**
     * Renderizza le linee del mirino utilizzando i buffer configurati.
     */
    private void renderCrosshairLines() {
        glBindVertexArray(vao);
        
        // Disegna entrambe le linee del mirino
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4); // Linea orizzontale
        glDrawArrays(GL_TRIANGLE_FAN, 4, 4); // Linea verticale
        
        glBindVertexArray(0);
    }
}