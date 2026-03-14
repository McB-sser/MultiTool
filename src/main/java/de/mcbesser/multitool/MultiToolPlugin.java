package de.mcbesser.multitool;

import org.bukkit.plugin.java.JavaPlugin;

public final class MultiToolPlugin extends JavaPlugin {
    private MultiToolManager multiToolManager;

    @Override
    public void onEnable() {
        this.multiToolManager = new MultiToolManager(this);
        if (!multiToolManager.hasNativeSpearMaterial()) {
            getLogger().warning("Kein natives Spear-Material gefunden. Erwartet WOODEN_SPEAR oder SPEAR. Das Multitool-Rezept bleibt ohne Speer-Unterstuetzung deaktiviert.");
        }
        getServer().getPluginManager().registerEvents(new MultiToolListener(multiToolManager), this);
    }

    public MultiToolManager getMultiToolManager() {
        return multiToolManager;
    }
}
