package com.project.world;

import org.joml.Vector3f;

import com.project.Main;
import com.project.graphics.Camera;
import com.project.world.Inventory.ItemStack;

import static org.lwjgl.glfw.GLFW.*;

public class Player {
    private final Camera camera;
    private final World world;
    private boolean leftMousePressed = false;
    private boolean rightMousePressed = false;
    private final float reachDistance = 10.0f;
    private final float blockBreakCooldown = 0.1f;
    private float currentCooldown = 0;

    public Player(Camera camera, World world) {
        this.camera = camera;
        this.world = world;
    }

    public void handleInput(long window, float deltaTime) {
        camera.processKeyboard(window, deltaTime);

        if (currentCooldown > 0) {
            currentCooldown -= deltaTime;
            return;
        }

        for (int i = GLFW_KEY_1; i <= GLFW_KEY_9; i++) {
            if (glfwGetKey(window, i) == GLFW_PRESS) {
                int index = i - GLFW_KEY_1;
                Main.inventory.selectHotbarSlot(index);
                break;
            }
        }

        boolean currentLeftMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean currentRightMouse = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;

        // BREAK block
        if (currentLeftMouse && !leftMousePressed) {
            Vector3f direction = camera.getFront();
            Vector3f origin = camera.getPosition();

            World.RaycastResult result = world.raycast(origin, direction, reachDistance);
            if (result != null) {
                if (result.blockPos.distance(camera.getPosition()) > 1.5f) {
                    world.breakBlock(result.blockPos);
                    currentCooldown = blockBreakCooldown;
                }
            }
        }
        leftMousePressed = currentLeftMouse;

        ItemStack item = Main.inventory.getSelected();
        if (item != null) {
            // PLACE block
            if (currentRightMouse && !rightMousePressed) {
                Vector3f direction = camera.getFront();
                Vector3f origin = camera.getPosition();

                World.RaycastResult result = world.raycast(origin, direction, reachDistance);
                if (result != null) {
                    if (result.blockPos.distance(camera.getPosition()) > 1.5f) {
                        world.placeBlock(result);
                        currentCooldown = blockBreakCooldown;
                    }
                }
            }
            rightMousePressed = currentRightMouse;
        }
    }
}
