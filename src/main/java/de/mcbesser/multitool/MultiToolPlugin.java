package de.mcbesser.multitool;

import org.bukkit.plugin.java.JavaPlugin;

public final class MultiToolPlugin extends JavaPlugin {
    private MultiToolManager multiToolManager;
    private McMMOHook mcMMOHook;
    private int sidebarTaskId = -1;

    @Override
    public void onEnable() {
        this.multiToolManager = new MultiToolManager(this);
        this.mcMMOHook = new McMMOHook(this, multiToolManager);
        multiToolManager.registerRecipes();
        getServer().getPluginManager().registerEvents(new MultiToolListener(multiToolManager), this);
        mcMMOHook.registerIfPresent();
        getServer().getOnlinePlayers().forEach(multiToolManager::discoverRecipes);
        this.sidebarTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () ->
                getServer().getOnlinePlayers().forEach(multiToolManager::refreshHeldMultitool), 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (sidebarTaskId != -1) {
            getServer().getScheduler().cancelTask(sidebarTaskId);
            sidebarTaskId = -1;
        }
        if (multiToolManager != null) {
            multiToolManager.clearAllSidebars();
        }
    }

    public MultiToolManager getMultiToolManager() {
        return multiToolManager;
    }
}
