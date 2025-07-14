package com.project.graphics;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20.*;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;

import com.project.world.World;

/**
 * Classe Camera che gestisce la vista del giocatore nel mondo 3D.
 * Include controlli di movimento, fisica del giocatore, collisioni e rendering.
 */
public class Camera {
    
    // ==================== COSTANTI ====================
    private static final float GRAVITY = 12f;
    private static final float JUMP_SPEED = 3.5f;
    static final float EYE_HEIGHT = 1.8f;
    private static final float MAX_STEP_HEIGHT = 0.7f;
    private static final float AIR_RESISTANCE = 0.85f;
    private static final float PLAYER_WIDTH = 0.8f;
    static final float PLAYER_HEIGHT = 1.8f;
    private static final float STEP = 0.5f;
    private static final long DOUBLE_JUMP_THRESHOLD = 250;
    
    // ==================== VARIABILI DI POSIZIONE E ORIENTAMENTO ====================
    private Vector3f position = new Vector3f();
    private Vector3f front = new Vector3f(0, 0, -1);
    private Vector3f up = new Vector3f(0, 2, 0);
    private Vector3f right;
    private Vector3f worldUp = new Vector3f(0, 1, 0);
    private float yaw = -90f;
    private float pitch = 0f;
    
    // ==================== VARIABILI DI MOVIMENTO ====================
    private float speed = 5f;
    private float sensitivity = 0.1f;
    private float vy = 0f; // velocità verticale
    private boolean onGround = false;
    private boolean isFlying = false;
    
    // ==================== VARIABILI MOUSE ====================
    private double lastX = 400;
    private double lastY = 300;
    private boolean firstMouse = true;
    
    // ==================== VARIABILI CONTROLLI ====================
    private boolean jumpKeyWasDown = false;
    private long lastJumpEventTime = 0;
    
    // ==================== VARIABILI RENDERING ====================
    private FrustumIntersection frustum = new FrustumIntersection();
    
    // ==================== RIFERIMENTI ESTERNI ====================
    private final World world;
    
    // ==================== COSTRUTTORE ====================
    /**
     * Costruisce una nuova camera nella posizione specificata.
     * 
     * @param x Coordinata X iniziale
     * @param y Coordinata Y iniziale
     * @param z Coordinata Z iniziale
     * @param world Riferimento al mondo per le collisioni
     */
    public Camera(float x, float y, float z, World world) {
        position.set(x, y, z);
        this.world = world;
        updateVectors();
    }
    
    // ==================== METODI PRINCIPALI ====================
    /**
     * Aggiorna le matrici di vista e proiezione per il rendering.
     * 
     * @param shaderProgram ID del programma shader
     */
    public void update(int shaderProgram) {
        // Calcola matrice di vista
        Matrix4f view = new Matrix4f().lookAt(position, position.add(front, new Vector3f()), up);
        
        // Calcola matrice di proiezione
        Matrix4f proj = new Matrix4f()
            .perspective((float) Math.toRadians(45f), 1200f / 800f,
                0.02f, // near plane ridotto
                300f);
        
        // Calcola matrice combinata per il frustum culling
        Matrix4f viewProj = proj.mul(view, new Matrix4f());
        frustum.set(viewProj);
        
        // Invia le matrici al shader
        int viewLoc = glGetUniformLocation(shaderProgram, "view");
        int projLoc = glGetUniformLocation(shaderProgram, "projection");
        glUniformMatrix4fv(viewLoc, false, view.get(new float[16]));
        glUniformMatrix4fv(projLoc, false, proj.get(new float[16]));
    }
    
    /**
     * Processa l'input da tastiera per il movimento del giocatore.
     * 
     * @param window Handle della finestra GLFW
     * @param dt Delta time per movimento fluido
     */
    public void processKeyboard(long window, float dt) {
        Vector3f delta = new Vector3f();
        Vector3f velocity = new Vector3f();
        
        // Configurazione velocità base e sprint
        final float baseSpeed = 5f;
        final float sprintMultiplier = 2.5f;
        boolean shiftDown = glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS;
        float currentSpeed = shiftDown ? baseSpeed * sprintMultiplier : baseSpeed;
        float v = currentSpeed * dt;
        
        // Gestione salto e volo
        handleJumpAndFly(window, dt);
        
        // Movimento orizzontale
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS)
            delta.add(new Vector3f(front).mul(v));
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS)
            delta.sub(new Vector3f(front).mul(v));
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS)
            delta.sub(new Vector3f(right).mul(v));
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS)
            delta.add(new Vector3f(right).mul(v));
        
        // Salvataggio mondo
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS)
            world.save();
        
        // Applicazione movimento orizzontale
        applyHorizontalMovement(delta);
        
        // Movimento verticale (abbassarsi)
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS)
            position.sub(up.mul(v, new Vector3f()));
        
        // Applicazione movimento con controllo collisioni
        Vector3f horizontalMovement = new Vector3f(
            front.x * velocity.z * dt,
            0, // Movimento solo sull'asse X/Z
            front.z * velocity.z * dt);
        
        Vector3f newHorizontalPosition = new Vector3f(position).add(horizontalMovement);
        if (!isColliding(newHorizontalPosition)) {
            position.x = newHorizontalPosition.x;
            position.z = newHorizontalPosition.z;
        }
        
        // Applica fisica verticale
        processPhysics(dt);
    }
    
    /**
     * Applica la fisica del giocatore (gravità, atterraggio).
     * 
     * @param dt Delta time
     */
    public void processPhysics(float dt) {
        if (isFlying)
            return; // niente gravità in volo
        
        // Integrazione velocità verticale
        vy -= GRAVITY * dt;
        
        // Quota del terreno sotto i piedi
        float groundY = world.getGroundUnder(
            position.x, position.z, (int) (position.y - EYE_HEIGHT));
        
        // Posizione prevista al prossimo passo
        float nextY = position.y + vy * dt;
        
        // Se stiamo scendendo e finiremmo sotto terra, blocca l'atterraggio
        if (vy < 0 && nextY < groundY + EYE_HEIGHT) {
            position.y = groundY + EYE_HEIGHT;
            vy = 0;
            onGround = true;
        } else {
            position.y = nextY;
            onGround = false;
        }
    }
    
    // ==================== METODI DI MOVIMENTO ====================
    /**
     * Gestisce il salto e il volo del giocatore.
     * 
     * @param window Handle della finestra GLFW
     * @param dt Delta time
     */
    private void handleJumpAndFly(long window, float dt) {
        boolean jumpKeyDown = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
        
        // Rilevo il "key down" (solo all'istante in cui viene premuto)
        if (jumpKeyDown && !jumpKeyWasDown) {
            long now = System.currentTimeMillis();
            if (now - lastJumpEventTime <= DOUBLE_JUMP_THRESHOLD) {
                // Doppio salto -> attiva/disattiva volo
                isFlying = !isFlying;
                // se entro in volo, azzero la velocità verticale
                if (isFlying) {
                    vy = 0;
                    onGround = false;
                }
            } else {
                // Singola pressione -> salto se sono a terra
                if (!isFlying && onGround) {
                    vy = JUMP_SPEED;
                    onGround = false;
                }
            }
            lastJumpEventTime = now;
        }
        
        // Aggiorno lo stato "wasDown" per il prossimo frame
        jumpKeyWasDown = jumpKeyDown;
        
        // Se sono in volo e tengo premuto space, continuo a salire
        if (isFlying && jumpKeyDown) {
            position.y += speed * dt;
        }
    }
    
    /**
     * Applica il movimento orizzontale con controllo delle collisioni.
     * 
     * @param delta Vettore di movimento
     */
    private void applyHorizontalMovement(Vector3f delta) {
        if (delta.x != 0 || delta.z != 0) {
            float targetX = position.x + delta.x;
            float targetZ = position.z + delta.z;
            
            // Applica attrito aereo se non siamo a terra
            if (!onGround) {
                delta.mul(AIR_RESISTANCE); // Riduci il movimento in aria
            }
            
            if (canMoveHorizontally(targetX, targetZ)) {
                position.x = targetX;
                position.z = targetZ;
            }
        }
    }
    
    // ==================== METODI DI CONTROLLO COLLISIONI ====================
    /**
     * Verifica se il giocatore può muoversi orizzontalmente nella posizione target.
     * 
     * @param targetX Coordinata X target
     * @param targetZ Coordinata Z target
     * @return true se il movimento è possibile
     */
    private boolean canMoveHorizontally(float targetX, float targetZ) {
        float currentFeetY = position.y - EYE_HEIGHT;
        float newGroundY = world.getGroundUnder(targetX, targetZ,
            (int) (position.y - EYE_HEIGHT));
        
        System.out.println("currentFeetY: " + currentFeetY + "\nnewGroundY: " + newGroundY + "\n");
        return (newGroundY - currentFeetY) <= MAX_STEP_HEIGHT;
    }
    
    /**
     * Verifica se il giocatore è in collisione nella posizione specificata.
     * 
     * @param pos Posizione da verificare
     * @return true se c'è collisione
     */
    private boolean isColliding(Vector3f pos) {
        float feetY = pos.y - EYE_HEIGHT;
        
        // Controlla collisioni in un box intorno al giocatore
        for (float xOffset = -PLAYER_WIDTH; xOffset <= PLAYER_WIDTH; xOffset += STEP) {
            for (float yOffset = 0; yOffset <= PLAYER_HEIGHT; yOffset += STEP) {
                for (float zOffset = -PLAYER_WIDTH; zOffset <= PLAYER_WIDTH; zOffset += STEP) {
                    int checkX = (int) Math.floor(pos.x + xOffset);
                    int checkY = (int) Math.floor(feetY + yOffset);
                    int checkZ = (int) Math.floor(pos.z + zOffset);
                    
                    if (world.isBlockSolid(checkX, checkY, checkZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    // ==================== METODI DI CONTROLLO MOUSE ====================
    /**
     * Restituisce il callback per la gestione del movimento del mouse.
     * 
     * @return Callback per il movimento del mouse
     */
    public GLFWCursorPosCallbackI getMouseCallback() {
        return (win, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }
            
            float xoff = (float) (xpos - lastX) * sensitivity;
            float yoff = (float) (lastY - ypos) * sensitivity;
            
            lastX = xpos;
            lastY = ypos;
            
            yaw += xoff;
            pitch += yoff;
            
            // Limita il pitch per evitare rotazioni eccessive
            if (pitch > 89)
                pitch = 89;
            if (pitch < -89)
                pitch = -89;
            
            updateVectors();
        };
    }
    
    /**
     * Aggiorna i vettori di direzione della camera basandosi su yaw e pitch.
     */
    private void updateVectors() {
        // Calcola il vettore front basandosi su yaw e pitch
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) (Math.sin(Math.toRadians(pitch)));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.normalize();
        
        // Calcola vettori right e up
        right = front.cross(worldUp, new Vector3f()).normalize();
        up = right.cross(front, new Vector3f()).normalize();
    }
    
    // ==================== METODI DI RENDERING ====================
    /**
     * Verifica se un AABB (Axis-Aligned Bounding Box) è visibile nel frustum.
     * 
     * @param min Punto minimo del bounding box
     * @param max Punto massimo del bounding box
     * @return true se il box è visibile
     */
    public boolean isAABBInFrustum(Vector3f min, Vector3f max) {
        return frustum.testAab(min.x, min.y, min.z, max.x, max.y, max.z);
    }
    
    // ==================== METODI STATICI ====================
    /**
     * Carica e compila gli shader necessari per il rendering.
     * 
     * @return ID del programma shader compilato
     */
    public static int loadShader() {
        return ShaderUtils.createProgram();
    }
    
    // ==================== GETTER ====================
    /**
     * Restituisce la posizione corrente della camera.
     * 
     * @return Vettore posizione
     */
    public Vector3f getPosition() {
        return position;
    }
    
    /**
     * Restituisce il vettore di direzione frontale della camera.
     * 
     * @return Vettore front
     */
    public Vector3f getFront() {
        return front;
    }
}