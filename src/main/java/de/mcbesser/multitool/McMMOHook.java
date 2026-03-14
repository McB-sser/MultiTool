package de.mcbesser.multitool;

import java.lang.reflect.Method;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class McMMOHook {
    private static final List<String> REPAIR_EVENT_NAMES = List.of(
            "com.gmail.nossr50.events.skills.repair.McMMOPlayerRepairCheckEvent",
            "com.gmail.nossr50.events.skills.McMMOPlayerRepairCheckEvent"
    );
    private static final List<String> SALVAGE_EVENT_NAMES = List.of(
            "com.gmail.nossr50.events.skills.salvage.McMMOPlayerSalvageCheckEvent",
            "com.gmail.nossr50.events.skills.repair.McMMOPlayerSalvageCheckEvent",
            "com.gmail.nossr50.events.skills.McMMOPlayerSalvageCheckEvent"
    );

    private final MultiToolPlugin plugin;
    private final MultiToolManager manager;
    private final Listener listener = new Listener() { };

    public McMMOHook(MultiToolPlugin plugin, MultiToolManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void registerIfPresent() {
        Plugin mcmmo = Bukkit.getPluginManager().getPlugin("mcMMO");
        if (mcmmo == null || !mcmmo.isEnabled()) {
            return;
        }

        boolean registeredRepair = registerCandidates(REPAIR_EVENT_NAMES);
        boolean registeredSalvage = registerCandidates(SALVAGE_EVENT_NAMES);
        if (registeredRepair || registeredSalvage) {
            plugin.getLogger().info("mcMMO hooks fuer Repair/Salvage aktiviert.");
        } else {
            plugin.getLogger().warning("mcMMO erkannt, aber keine passenden Repair/Salvage-Events gefunden.");
        }
    }

    private boolean registerCandidates(List<String> candidates) {
        for (String className : candidates) {
            try {
                Class<?> rawClass = Class.forName(className);
                if (!Event.class.isAssignableFrom(rawClass)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
                registerEvent(eventClass);
                return true;
            } catch (ClassNotFoundException ignored) {
                // Try next candidate.
            }
        }
        return false;
    }

    private void registerEvent(Class<? extends Event> eventClass) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvent(eventClass, listener, EventPriority.LOWEST, new EventExecutor() {
            @Override
            public void execute(Listener ignored, Event event) throws EventException {
                handleEvent(event);
            }
        }, plugin, true);
    }

    private void handleEvent(Event event) {
        if (!(event instanceof Cancellable cancellable)) {
            return;
        }

        Player player = invokePlayer(event);
        ItemStack item = invokeItem(event);
        if (player == null || !manager.isMultitool(item)) {
            return;
        }

        cancellable.setCancelled(true);
        player.sendActionBar(Component.text(
                "mcMMO Repair/Salvage ist fuer das Multitool blockiert. Nimm das Werkzeug vorher heraus."
        ));
    }

    private Player invokePlayer(Event event) {
        for (String methodName : List.of("getPlayer", "getWho")) {
            Object value = invokeNoArgs(event, methodName);
            if (value instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private ItemStack invokeItem(Event event) {
        for (String methodName : List.of("getRepairedObject", "getRepairObject", "getSalvageItem", "getItem", "getItemStack")) {
            Object value = invokeNoArgs(event, methodName);
            if (value instanceof ItemStack itemStack) {
                return itemStack;
            }
        }
        return null;
    }

    private Object invokeNoArgs(Event event, String methodName) {
        try {
            Method method = event.getClass().getMethod(methodName);
            return method.invoke(event);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
