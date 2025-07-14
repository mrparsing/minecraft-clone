package com.project.graphics;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

/**
 * Utility class per la compilazione e il link degli shader.
 *
 * <p>
 * Questa versione include nei sorgenti l'evidenziazione del blocco puntato:
 * il frammento appartenente al blocco le cui coordinate (x,y,z) coincidono con
 * l'uniform `uHighlightBlock` viene schiarito del 35 %.
 *
 * <p>
 * Per disattivare l'evidenziazione impostare l'uniform a
 * <code>ivec3(INT_MIN)</code> (ad es. <code>Integer.MIN_VALUE</code>).
 */
public class ShaderUtils {

    // ---------------------------------------------------------------------------------
    // Sorgenti shader (GLSL 150 core)
    // ---------------------------------------------------------------------------------

    // Vertex shader per oggetti con texture
    private static final String VERT_SRC = """
        #version 150 core
        in  vec3 aPos;
        in  vec2 aTexCoord;
        uniform mat4 model;
        uniform mat4 view;
        uniform mat4 projection;
        out vec2 vTex;
        out vec3 vWorldPos;
        void main() {
            vec4 wp   = model * vec4(aPos, 1.0);
            vWorldPos = wp.xyz;
            vTex      = aTexCoord;
            gl_Position = projection * view * wp;
        }
    """;

    // Fragment shader con evidenziazione blocco
    private static final String FRAG_SRC = """
        #version 150 core
        in  vec2 vTex;
        in  vec3 vWorldPos;
        uniform sampler2D ourTexture;
        uniform ivec3 uHighlightBlock;
        out vec4 FragColor;
        void main() {
            vec4 col = texture(ourTexture, vTex);

            // Tolleranza per compensare la precisione in virgola mobile
            const float EPS = 1e-4;

            // Distanza del frammento dalle facce min e max del cubo evidenziato
            vec3 diffMin = vWorldPos - vec3(uHighlightBlock);
            vec3 diffMax = vec3(uHighlightBlock) + 1.0 - vWorldPos;

            bool inside = all(greaterThanEqual(diffMin, vec3(-EPS))) &&
                          all(greaterThanEqual(diffMax, vec3(-EPS)));

            if (inside)
                col.rgb = mix(col.rgb, vec3(1.0), 0.35); // Schiarisce del 35%

            FragColor = col;
        }
    """;

    // ---------------------------------------------------------------------------------
    // Programmi shader pubblici
    // ---------------------------------------------------------------------------------

    /**
     * Crea un programma shader standard con evidenziazione blocchi.
     */
    public static int createProgram() {
        return compileAndLink(VERT_SRC, FRAG_SRC);
    }

    /**
     * Crea un programma shader parametrico con sorgenti personalizzate.
     */
    public static int createProgram(String vertexSource, String fragmentSource) {
        return compileAndLink(vertexSource, fragmentSource);
    }

    /**
     * Crea un programma shader per particelle (point sprites).
     */
    public static int createParticleProgram() {
        String vs = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec4 aColor;

            out vec4 vColor;

            uniform mat4 view;
            uniform mat4 projection;

            void main() {
                vColor = aColor;
                gl_Position = projection * view * vec4(aPos, 1.0);
                gl_PointSize = 6.0; // punto-sprite 6×6 px
            }
        """;

        String fs = """
            #version 330 core
            in  vec4 vColor;
            out vec4 FragColor;
            void main() {
                FragColor = vColor;
            }
        """;

        return createProgram(vs, fs); // Riusa il metodo parametrico
    }

    // ---------------------------------------------------------------------------------
    // Compilazione e link degli shader
    // ---------------------------------------------------------------------------------

    /**
     * Compila e collega un programma shader a partire da due sorgenti GLSL.
     */
    private static int compileAndLink(String vertSrc, String fragSrc) {
        // Compilazione shader di vertice
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertSrc);
        glCompileShader(vs);
        checkCompileErrors(vs, "VERTEX");

        // Compilazione shader di frammento
        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragSrc);
        glCompileShader(fs);
        checkCompileErrors(fs, "FRAGMENT");

        // Creazione e link del programma
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);

        // Associazione esplicita degli attributi
        glBindAttribLocation(program, 0, "aPos");
        glBindAttribLocation(program, 1, "aTexCoord");

        glLinkProgram(program);
        checkLinkErrors(program);

        // Cleanup: eliminazione shader singoli
        glDeleteShader(vs);
        glDeleteShader(fs);

        return program;
    }

    // ---------------------------------------------------------------------------------
    // Gestione degli errori di compilazione e link
    // ---------------------------------------------------------------------------------

    private static void checkCompileErrors(int shader, String type) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("Errore compilazione shader (" + type + "):");
            System.err.println(glGetShaderInfoLog(shader));
        }
    }

    private static void checkLinkErrors(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Errore link programma:");
            System.err.println(glGetProgramInfoLog(program));
        }
    }
}