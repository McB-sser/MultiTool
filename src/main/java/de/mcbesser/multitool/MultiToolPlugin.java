package de.mcbesser.multitool;

import org.bukkit.plugin.java.JavaPlugin;

public final class MultiToolPlugin extends JavaPlugin {
    private MultiToolManager multiToolManager;

    @Override
    public void onEnable() {
        this.multiToolManager = new MultiToolManager(this);
        getServer().getPluginManager().registerEvents(new MultiToolListener(multiToolManager), this);
    }

    public MultiToolManager getMultiToolManager() {
        return multiToolManager;
    }
}
