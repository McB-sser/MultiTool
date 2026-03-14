package de.mcbesser.multitool;

import java.util.List;
import org.bukkit.Material;

public enum PreferenceTarget {
    HOSTILE_NEAR("Feindlich nah", Material.ZOMBIE_HEAD, ToolKind.SWORD, List.of(ToolKind.SWORD, ToolKind.AXE, ToolKind.SPEAR, ToolKind.BOW)),
    HOSTILE_FAR("Feindlich fern", Material.SKELETON_SKULL, ToolKind.BOW, List.of(ToolKind.BOW, ToolKind.SPEAR, ToolKind.SWORD, ToolKind.AXE)),
    PASSIVE_NEAR("Friedlich nah", Material.WHEAT, ToolKind.SPEAR, List.of(ToolKind.SPEAR, ToolKind.SWORD, ToolKind.AXE, ToolKind.BOW)),
    PASSIVE_FAR("Friedlich fern", Material.HAY_BLOCK, ToolKind.BOW, List.of(ToolKind.BOW, ToolKind.SPEAR, ToolKind.SWORD, ToolKind.AXE)),
    WATER_ENTITY("Wasser-Mob", Material.TROPICAL_FISH_BUCKET, ToolKind.ROD, List.of(ToolKind.ROD, ToolKind.SPEAR, ToolKind.BOW, ToolKind.SWORD)),
    UNKNOWN_NEAR("Unbekannt nah", Material.ENDER_EYE, ToolKind.SWORD, List.of(ToolKind.SWORD, ToolKind.AXE, ToolKind.SPEAR, ToolKind.BOW)),
    UNKNOWN_FAR("Unbekannt fern", Material.ENDER_PEARL, ToolKind.BOW, List.of(ToolKind.BOW, ToolKind.SPEAR, ToolKind.SWORD, ToolKind.AXE));

    private final String displayName;
    private final Material icon;
    private final ToolKind defaultTool;
    private final List<ToolKind> allowedTools;

    PreferenceTarget(String displayName, Material icon, ToolKind defaultTool, List<ToolKind> allowedTools) {
        this.displayName = displayName;
        this.icon = icon;
        this.defaultTool = defaultTool;
        this.allowedTools = allowedTools;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public ToolKind getDefaultTool() {
        return defaultTool;
    }

    public List<ToolKind> getAllowedTools() {
        return allowedTools;
    }
}
