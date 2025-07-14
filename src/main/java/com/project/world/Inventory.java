package com.project.world;

import java.util.Arrays;

/**
 * Inventory simile a Minecraft: 9-slot hotbar + 27-slot main inventory.
 * Ogni slot contiene uno stack di BlockType (max 64).
 */
public class Inventory {
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 27;
    public static final int TOTAL_SIZE = HOTBAR_SIZE + MAIN_SIZE;

    private final ItemStack[] slots;
    private int selectedHotbarIndex = 0;

    public Inventory() {
        slots = new ItemStack[TOTAL_SIZE];
        addItem(BlockType.PLANKS, 64);
        addItem(BlockType.BRICK, 64);
    }

    /**
     * Aggiunge count elementi di tipo type nell'inventario.
     * Comporta stacking come in Minecraft:
     * prima riempie stack esistenti, poi usa slot vuoti.
     * Restituisce il numero di elementi non aggiunti (overflow).
     */
    public int addItem(BlockType type, int count) {
        // 1) stack esistenti
        for (int i = 0; i < TOTAL_SIZE; i++) {
            ItemStack s = slots[i];
            if (s != null && s.getType() == type && s.getCount() < ItemStack.MAX_STACK) {
                int free = ItemStack.MAX_STACK - s.getCount();
                int toAdd = Math.min(free, count);
                s.add(toAdd);
                count -= toAdd;
                if (count == 0)
                    return 0;
            }
        }
        // 2) slot vuoti
        for (int i = 0; i < TOTAL_SIZE; i++) {
            if (slots[i] == null) {
                int toAdd = Math.min(ItemStack.MAX_STACK, count);
                slots[i] = new ItemStack(type, toAdd);
                count -= toAdd;
                if (count == 0)
                    return 0;
            }
        }
        return count; // numero di block non aggiunti
    }

    /**
     * Rimuove count elementi dallo slot hotbar selezionato.
     * Se il numero scende a zero, svuota lo slot.
     * Restituisce il numero effettivamente rimosso.
     */
    public int removeFromSelected(int count) {
        ItemStack s = getSelected();
        if (s == null)
            return 0;
        int removed = Math.min(count, s.getCount());
        s.remove(removed);
        if (s.getCount() == 0)
            slots[selectedHotbarIndex] = null;
        return removed;
    }

    /**
     * Seleziona lo slot hotbar (0-8).
     */
    public void selectHotbarSlot(int index) {
        if (index >= 0 && index < HOTBAR_SIZE) {
            selectedHotbarIndex = index;
        }
    }

    /**
     * Scorri hotbar avanti (wheel-up) o indietro (wheel-down).
     */
    public void scrollHotbar(int delta) {
        int idx = (selectedHotbarIndex + delta) % HOTBAR_SIZE;
        if (idx < 0)
            idx += HOTBAR_SIZE;
        selectedHotbarIndex = idx;
    }

    /**
     * Restituisce lo stack attualmente selezionato in hotbar.
     */
    public ItemStack getSelected() {
        return slots[selectedHotbarIndex];
    }

    /**
     * Restituisce lo stack in uno slot generico (0-TOTAL_SIZE-1).
     */
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= TOTAL_SIZE)
            return null;
        return slots[slot];
    }

    /**
     * Scambia il contenuto di due slot.
     */
    public void swapSlots(int a, int b) {
        if (a < 0 || a >= TOTAL_SIZE || b < 0 || b >= TOTAL_SIZE)
            return;
        ItemStack tmp = slots[a];
        slots[a] = slots[b];
        slots[b] = tmp;
    }

    /**
     * Restituisce il tipo di blocco presente in uno slot specifico.
     * 
     * @param slot l'indice dello slot (0 - TOTAL_SIZE - 1)
     * @return il tipo di blocco oppure null se lo slot è vuoto o non valido
     */
    public BlockType getBlockTypeAt(int slot) {
        if (slot < 0 || slot >= TOTAL_SIZE)
            return null;
        ItemStack s = slots[slot];
        return s != null ? s.getType() : null;
    }

    /**
     * Usa un pick-block (come tasto medio): copia lo stack selezionato
     * in hotbar slot se già presente, altrimenti sostituisce lo slot.
     */
    public void pickBlock(int hotbarSlot) {
        ItemStack s = getSelected();
        if (s == null)
            return;
        BlockType type = s.getType();
        // cerca nello hotbar stesso tipo
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack st = slots[i];
            if (st != null && st.getType() == type) {
                selectedHotbarIndex = i;
                return;
            }
        }
        // altrimenti sostituisci lo slot selezionato
        slots[selectedHotbarIndex] = new ItemStack(type, 1);
    }

    public int getSelectedHotbarIndex() {
        return selectedHotbarIndex;
    }

    public int getSize() {
        return TOTAL_SIZE;
    }

    @Override
    public String toString() {
        return "Inventory{" +
                "slots=" + Arrays.toString(slots) +
                ", selectedHotbarIndex=" + selectedHotbarIndex +
                '}';
    }

    /**
     * Rappresenta uno stack di blocchi.
     */
    public static class ItemStack {
        public static final int MAX_STACK = 64;
        private final BlockType type;
        private int count;

        public ItemStack(BlockType type, int count) {
            this.type = type;
            this.count = Math.min(count, MAX_STACK);
        }

        public BlockType getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public void add(int amount) {
            count = Math.min(count + amount, MAX_STACK);
        }

        public void remove(int amount) {
            count = Math.max(count - amount, 0);
        }

        @Override
        public String toString() {
            return type + " x" + count;
        }
    }
}