package de.mcbesser.multitool;

import org.bukkit.Material;

public enum ToolKind {
    AXE("Axt", Material.WOODEN_AXE),
    SHOVEL("Schaufel", Material.WOODEN_SHOVEL),
    SPEAR("Speer", Material.WOODEN_SPEAR),
    ROD("Angel", Material.FISHING_ROD),
    BOW("Bogen", Material.BOW),
    SWORD("Schwert", Material.WOODEN_SWORD),
    PICKAXE("Spitzhacke", Material.WOODEN_PICKAXE),
    HOE("Harke", Material.WOODEN_HOE);

    private final String displayName;
    private final Material defaultMaterial;

    ToolKind(String displayName, Material defaultMaterial) {
        this.displayName = displayName;
        this.defaultMaterial = defaultMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getDefaultMaterial() {
        return defaultMaterial;
    }
}
