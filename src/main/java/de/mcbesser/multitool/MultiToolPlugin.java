package de.mcbesser.multitool;

import org.bukkit.plugin.java.JavaPlugin;

public final class MultiToolPlugin extends JavaPlugin {
    private MultiToolManager multiToolManager;
    private McMMOHook mcMMOHook;

    @Override
    public void onEnable() {
        this.multiToolManager = new MultiToolManager(this);
        this.mcMMOHook = new McMMOHook(this, multiToolManager);
        multiToolManager.registerRecipes();
        getServer().getPluginManager().registerEvents(new MultiToolListener(multiToolManager), this);
        mcMMOHook.registerIfPresent();
        getServer().getOnlinePlayers().forEach(multiToolManager::discoverRecipes);
    }

    @Override
    public void onDisable() {
        if (multiToolManager != null) {
            multiToolManager.clearAllSidebars();
        }
    }

    public MultiToolManager getMultiToolManager() {
        return multiToolManager;
    }

    public McMMOHook getMcMMOHook() {
        return mcMMOHook;
    }
}
