package de.mcbesser.multitool;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    public enum MenuType {
        MAIN,
        UPGRADE,
        TOTEM_UPGRADE,
        SELF_UPGRADE,
        SHELF_UPGRADE,
        SETTINGS
    }

    private final MenuType type;
    private final UUID playerId;
    private final int hotbarSlot;
    private final ToolKind toolKind;

    public MenuHolder(MenuType type, UUID playerId, int hotbarSlot, ToolKind toolKind) {
        this.type = type;
        this.playerId = playerId;
        this.hotbarSlot = hotbarSlot;
        this.toolKind = toolKind;
    }

    public MenuType getType() {
        return type;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    public ToolKind getToolKind() {
        return toolKind;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
