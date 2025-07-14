package com.project;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import com.project.graphics.Camera;
import com.project.graphics.Crosshair;
import com.project.graphics.Hotbar;
import com.project.menu.StartScreen;
import com.project.world.Inventory;
import com.project.world.Player;
import com.project.world.World;

/**
 * Classe principale del clone di Minecraft.
 * Gestisce il ciclo di vita dell'applicazione, dalla schermata di menu al gioco.
 */
public class Main {
    
    // ==================== COSTANTI ====================
    private static final String SEED_FILE = "seed.txt";
    
    // ==================== VARIABILI STATICHE ====================
    private static long menuWindow;
    private static long gameWindow;
    private static World world;
    public static Player player;
    public static Inventory inventory = new Inventory();
    public static int seed;
    public static String worldName;
    public static Path worldDir;
    private static boolean running;
    
    // ==================== VARIABILI DI ISTANZA ====================
    private Camera camera;
    private Crosshair crosshair;
    private Hotbar hotbar;
    
    // ==================== METODO MAIN ====================
    /**
     * Punto di ingresso dell'applicazione.
     * Accetta un seed opzionale come parametro da linea di comando.
     * 
     * @param args Argomenti da linea di comando (primo argomento: seed)
     * @throws IOException Se si verifica un errore di I/O
     */
    public static void main(String[] args) throws IOException {
        // Seed da linea di comando (se presente)
        if (args.length > 0) {
            System.out.println("Seed ricevuto: " + args[0]);
            seed = Integer.parseInt(args[0]);
        }
        new Main().run();
    }
    
    // ==================== METODI PRINCIPALI ====================
    /**
     * Metodo principale che gestisce il ciclo di vita dell'applicazione.
     * Alterna tra menu e gioco fino alla chiusura dell'applicazione.
     * 
     * @throws IOException Se si verifica un errore di I/O
     */
    public void run() throws IOException {
        initGLFW();

        // Creazione e configurazione finestra menu
        menuWindow = createWindow("Minecraft Clone - Menu");
        setupOpenGL(0.0f, 0.0f, 0.0f, 1.0f);

        // Ciclo principale: menu → gioco → menu
        while (true) {
            // Porta il contesto sul menu e mostra la finestra
            glfwMakeContextCurrent(menuWindow);
            glfwShowWindow(menuWindow);

            // Mostra schermata iniziale e ottieni nome del mondo
            StartScreen menu = new StartScreen(menuWindow);
            worldName = menu.selectWorld();
            if (worldName == null) {
                // Utente ha chiuso il menu → esci completamente
                break;
            }

            // Nasconde il menu mentre si gioca
            glfwHideWindow(menuWindow);

            // Prepara directory mondo e seed
            worldDir = Path.of("world", worldName);
            prepareWorldDirectory(worldDir);

            // Creazione e configurazione finestra di gioco
            gameWindow = createWindow("Minecraft Clone");
            setupOpenGL(0.53f, 0.81f, 0.98f, 1.0f);

            running = true;
            gameLoop();

            // Distruggi solo la finestra di gioco
            glfwDestroyWindow(gameWindow);
            // Dopo chiusura, torniamo automaticamente al menu nel prossimo ciclo
        }

        // Pulizia finale del menu e terminate GLFW
        glfwDestroyWindow(menuWindow);
        glfwTerminate();
    }

    /**
     * Ciclo principale del gioco.
     * Gestisce rendering, input e aggiornamenti del mondo di gioco.
     * 
     * @throws IOException Se si verifica un errore di I/O
     */
    private void gameLoop() throws IOException {
        // Inizializzazione componenti di gioco
        crosshair = new Crosshair();

        // Caricamento e verifica shader
        int shaderProgram = Camera.loadShader();
        if (shaderProgram == 0) {
            System.err.println("Errore nel caricamento dello shader.");
            System.exit(-1);
        }

        // Inizializzazione mondo, camera, player e hotbar
        world = new World(shaderProgram, 4);
        camera = new Camera(0.0f, 100.0f, 200.0f, world);
        player = new Player(camera, world);
        hotbar = new Hotbar();

        // Configurazione input mouse
        glfwSetCursorPosCallback(gameWindow, camera.getMouseCallback());
        glfwSetInputMode(gameWindow, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Ciclo di rendering principale
        double lastTime = glfwGetTime();
        while (running && !glfwWindowShouldClose(gameWindow)) {
            // Calcolo deltaTime per animazioni fluide
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // Pulizia buffer
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glUseProgram(shaderProgram);

            // Aggiornamento e rendering componenti
            player.handleInput(gameWindow, deltaTime);
            camera.update(shaderProgram);
            world.update(camera);
            world.render(camera);
            world.printLookedBlock(camera, 88f);
            hotbar.render(1200, 800, inventory);
            crosshair.render();

            // Swap buffer e polling eventi
            glfwSwapBuffers(gameWindow);
            glfwPollEvents();
        }

        cleanup(shaderProgram);
    }

    // ==================== METODI DI UTILITÀ ====================
    /**
     * Inizializza GLFW con le configurazioni necessarie.
     * Configura versione OpenGL, profilo e proprietà della finestra.
     */
    private void initGLFW() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        
        // Configurazione OpenGL 3.3 Core Profile
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        
        // Configurazione finestra
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
    }

    /**
     * Crea una finestra GLFW con il titolo specificato.
     * 
     * @param title Titolo della finestra
     * @return Handle della finestra creata
     */
    private long createWindow(String title) {
        long win = glfwCreateWindow(1200, 800, title, 0, 0);
        if (win == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        glfwMakeContextCurrent(win);
        glfwShowWindow(win);
        return win;
    }

    /**
     * Configura OpenGL con le impostazioni base.
     * Imposta colore di sfondo e abilita test di profondità e culling.
     * 
     * @param r Componente rossa del colore di sfondo (0.0-1.0)
     * @param g Componente verde del colore di sfondo (0.0-1.0)
     * @param b Componente blu del colore di sfondo (0.0-1.0)
     * @param a Componente alpha del colore di sfondo (0.0-1.0)
     */
    private void setupOpenGL(float r, float g, float b, float a) {
        org.lwjgl.opengl.GL.createCapabilities();
        glClearColor(r, g, b, a);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    /**
     * Prepara la directory del mondo e gestisce il seed.
     * Se il seed non è specificato, lo legge dal file o ne genera uno nuovo.
     * 
     * @param dir Directory del mondo da preparare
     * @throws IOException Se si verifica un errore di I/O
     */
    private void prepareWorldDirectory(Path dir) throws IOException {
        Path seedPath = dir.resolve(SEED_FILE);
        Files.createDirectories(dir);
        
        if (seed == 0) {
            if (Files.exists(seedPath)) {
                // Carica seed esistente
                seed = Integer.parseInt(Files.readString(seedPath).trim());
            } else {
                // Genera nuovo seed e salvalo
                seed = new Random().nextInt();
                Files.writeString(seedPath, Integer.toString(seed));
            }
        }
        
        System.out.println("Seed in uso: " + seed);
    }

    /**
     * Pulisce le risorse del gioco prima della chiusura.
     * 
     * @param shaderProgram Program shader da eliminare
     */
    private void cleanup(int shaderProgram) {
        world.cleanup();
        glDeleteProgram(shaderProgram);
    }

    // ==================== METODI STATICI ====================
    /**
     * Esce dal gioco e torna al menu principale.
     * Pulisce le risorse e ripristina il cursore normale.
     * 
     * @param shaderProgram Program shader da eliminare
     */
    public static void exitToMenu(int shaderProgram) {
        running = false;
        world.cleanup();
        glDeleteProgram(shaderProgram);
        glfwSetInputMode(gameWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }
}