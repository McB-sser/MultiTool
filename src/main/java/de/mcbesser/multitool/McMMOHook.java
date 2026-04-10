package de.mcbesser.multitool;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
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
    private final Map<ToolKind, List<Method>> activeAbilityMethods = new EnumMap<>(ToolKind.class);
    private Method userManagerGetPlayerMethod;
    private Method getMiningManagerMethod;
    private Method miningBlockCheckMethod;
    private Method getExcavationManagerMethod;
    private Method excavationBlockCheckMethod;
    private Method gigaDrillBreakerMethod;
    private boolean abilityApiAvailable;

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
        this.abilityApiAvailable = resolveAbilityApi();
        resolveManagerMethods();
        if (registeredRepair || registeredSalvage || abilityApiAvailable) {
            plugin.getLogger().info("mcMMO hooks f\u00fcr Repair/Salvage aktiviert.");
        } else {
            plugin.getLogger().warning("mcMMO erkannt, aber keine passenden Repair/Salvage-Events gefunden.");
        }
    }

    public ToolKind getActiveAbilityTool(Player player, ItemStack multitool) {
        if (!abilityApiAvailable || player == null || !manager.isMultitool(multitool)) {
            return null;
        }
        for (Map.Entry<ToolKind, List<Method>> entry : activeAbilityMethods.entrySet()) {
            if (!manager.hasUsableToolForMcMMO(multitool, entry.getKey())) {
                continue;
            }
            for (Method method : entry.getValue()) {
                try {
                    Object result = method.invoke(null, player);
                    if (result instanceof Boolean enabled && enabled) {
                        return entry.getKey();
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Ignore and continue with remaining abilities.
                }
            }
        }
        return null;
    }

    public void handleBlockBreak(Player player, ItemStack multitool, Block block) {
        if (player == null || block == null || !manager.isMultitool(multitool)) {
            return;
        }
        Object mcmmoPlayer = getMcMMOPlayer(player);
        if (mcmmoPlayer == null) {
            return;
        }

        ToolKind activeAbility = getActiveAbilityTool(player, multitool);
        if (activeAbility == ToolKind.PICKAXE) {
            invoke(miningBlockCheckMethod, invoke(getMiningManagerMethod, mcmmoPlayer), block);
            return;
        }
        if (activeAbility == ToolKind.SHOVEL) {
            Object excavationManager = invoke(getExcavationManagerMethod, mcmmoPlayer);
            if (excavationManager == null) {
                return;
            }
            invoke(excavationBlockCheckMethod, excavationManager, block);
            invoke(gigaDrillBreakerMethod, excavationManager, block);
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
                "mcMMO Repair/Salvage ist f\u00fcr das Multitool blockiert. Nimm das Werkzeug vorher heraus."
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

    private boolean resolveAbilityApi() {
        try {
            Class<?> abilityApiClass = Class.forName("com.gmail.nossr50.api.AbilityAPI");
            registerAbilityMethod(abilityApiClass, ToolKind.PICKAXE, "superBreakerEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.SHOVEL, "gigaDrillBreakerEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.HOE, "greenTerraEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.SWORD, "serratedStrikesEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.AXE, "treeFellerEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.AXE, "skullSplitterEnabled");
            registerAbilityMethod(abilityApiClass, ToolKind.SPEAR, "spearsSuperAbilityEnabled");
            return !activeAbilityMethods.isEmpty();
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void registerAbilityMethod(Class<?> abilityApiClass, ToolKind toolKind, String methodName) {
        Method method = resolveMethod(abilityApiClass, methodName);
        if (method != null) {
            activeAbilityMethods.computeIfAbsent(toolKind, ignored -> new java.util.ArrayList<>()).add(method);
        }
    }

    private Method resolveMethod(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName, Player.class);
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void resolveManagerMethods() {
        try {
            Class<?> userManagerClass = Class.forName("com.gmail.nossr50.util.player.UserManager");
            Class<?> mcmmoPlayerClass = Class.forName("com.gmail.nossr50.datatypes.player.McMMOPlayer");
            Class<?> miningManagerClass = Class.forName("com.gmail.nossr50.skills.mining.MiningManager");
            Class<?> excavationManagerClass = Class.forName("com.gmail.nossr50.skills.excavation.ExcavationManager");

            userManagerGetPlayerMethod = userManagerClass.getMethod("getPlayer", Player.class);
            getMiningManagerMethod = mcmmoPlayerClass.getMethod("getMiningManager");
            miningBlockCheckMethod = miningManagerClass.getMethod("miningBlockCheck", Block.class);
            getExcavationManagerMethod = mcmmoPlayerClass.getMethod("getExcavationManager");
            excavationBlockCheckMethod = excavationManagerClass.getMethod("excavationBlockCheck", Block.class);
            gigaDrillBreakerMethod = excavationManagerClass.getMethod("gigaDrillBreaker", Block.class);
        } catch (ReflectiveOperationException ignored) {
            userManagerGetPlayerMethod = null;
            getMiningManagerMethod = null;
            miningBlockCheckMethod = null;
            getExcavationManagerMethod = null;
            excavationBlockCheckMethod = null;
            gigaDrillBreakerMethod = null;
        }
    }

    private Object getMcMMOPlayer(Player player) {
        return invoke(userManagerGetPlayerMethod, null, player);
    }

    private Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

}
