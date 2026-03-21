package de.mcbesser.multitool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.HumanEntity;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Squid;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class MultiToolManager {
    private static final Component MULTITOOL_NAME = Component.text("Multitool");
    private static final int MAX_TOOL_SLOTS = 4;
    private static final int[] MAIN_MENU_TOOL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16};
    private static final ToolKind[] MAIN_MENU_TOOL_ORDER = {
            ToolKind.PICKAXE,
            ToolKind.AXE,
            ToolKind.SHOVEL,
            ToolKind.HOE,
            ToolKind.ROD,
            ToolKind.BOW,
            ToolKind.SWORD,
            ToolKind.SPEAR
    };
    private static final int[] MAIN_MENU_INFO_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] UPGRADE_STORAGE_SLOTS = {2, 3, 4, 5};
    private static final Set<Material> SHELF_MATERIALS = Set.of(
            Material.OAK_SHELF, Material.SPRUCE_SHELF, Material.BIRCH_SHELF,
            Material.JUNGLE_SHELF, Material.ACACIA_SHELF, Material.DARK_OAK_SHELF,
            Material.MANGROVE_SHELF, Material.CHERRY_SHELF, Material.BAMBOO_SHELF,
            Material.CRIMSON_SHELF, Material.WARPED_SHELF
    );
    private static final Set<Material> HOE_PREFERRED = Set.of(
            Material.FARMLAND, Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.VINE, Material.GLOW_LICHEN,
            Material.SWEET_BERRY_BUSH, Material.NETHER_WART, Material.WHEAT, Material.CARROTS,
            Material.POTATOES, Material.BEETROOTS, Material.MELON_STEM, Material.ATTACHED_MELON_STEM,
            Material.PUMPKIN_STEM, Material.ATTACHED_PUMPKIN_STEM
    );
    private static final Set<Material> BLOCKED_REPAIR_BLOCKS = Set.of(
            Material.IRON_BLOCK,
            Material.GOLD_BLOCK,
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.GRINDSTONE,
            Material.SMITHING_TABLE
    );

    private final MultiToolPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey baseMaterialKey;
    private final NamespacedKey selectedToolKey;
    private final NamespacedKey selectedToolSlotKey;
    private final NamespacedKey manualModeKey;
    private final NamespacedKey storedDurabilityKey;
    private final NamespacedKey storedTotemKey;
    private final NamespacedKey storedBindingKey;
    private final NamespacedKey multitoolRecipeKey;
    private final Map<ToolKind, List<NamespacedKey>> toolKeys = new EnumMap<>(ToolKind.class);
    private final Map<ToolKind, NamespacedKey> durabilityBookKeys = new EnumMap<>(ToolKind.class);
    private final List<NamespacedKey> totemKeys = new ArrayList<>();
    private final Map<PreferenceTarget, NamespacedKey> preferenceKeys = new EnumMap<>(PreferenceTarget.class);
    private final Set<Material> spearMaterials;
    private final MultiToolSidebar sidebar;

    public MultiToolManager(MultiToolPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "multitool");
        this.baseMaterialKey = new NamespacedKey(plugin, "base_material");
        this.selectedToolKey = new NamespacedKey(plugin, "selected_tool");
        this.selectedToolSlotKey = new NamespacedKey(plugin, "selected_tool_slot");
        this.manualModeKey = new NamespacedKey(plugin, "manual_mode");
        this.storedDurabilityKey = new NamespacedKey(plugin, "stored_durability");
        this.storedTotemKey = new NamespacedKey(plugin, "stored_totem");
        this.storedBindingKey = new NamespacedKey(plugin, "stored_binding");
        this.multitoolRecipeKey = new NamespacedKey(plugin, "multitool");
        this.spearMaterials = resolveSpearMaterials();
        this.sidebar = new MultiToolSidebar(this);
        this.totemKeys.add(new NamespacedKey(plugin, "stored_totem"));
        for (int slot = 1; slot < MAX_TOOL_SLOTS; slot++) {
            this.totemKeys.add(new NamespacedKey(plugin, "stored_totem_" + (slot + 1)));
        }
        for (ToolKind toolKind : ToolKind.values()) {
            List<NamespacedKey> keys = new ArrayList<>();
            String prefix = "tool_" + toolKind.name().toLowerCase();
            keys.add(new NamespacedKey(plugin, prefix));
            for (int slot = 1; slot < MAX_TOOL_SLOTS; slot++) {
                keys.add(new NamespacedKey(plugin, prefix + "_" + (slot + 1)));
            }
            toolKeys.put(toolKind, List.copyOf(keys));
            durabilityBookKeys.put(toolKind, new NamespacedKey(plugin, "durability_" + toolKind.name().toLowerCase()));
        }
        for (PreferenceTarget target : PreferenceTarget.values()) {
            preferenceKeys.put(target, new NamespacedKey(plugin, "pref_" + target.name().toLowerCase()));
        }
    }

    public void registerRecipes() {
        ShapedRecipe recipe = new ShapedRecipe(multitoolRecipeKey, createMultitool(Material.OAK_SHELF));
        recipe.shape("ASP", "RLB", "KCH");
        recipe.setIngredient('A', Material.WOODEN_AXE);
        recipe.setIngredient('S', Material.WOODEN_SHOVEL);
        recipe.setIngredient('P', Material.WOODEN_SPEAR);
        recipe.setIngredient('R', Material.FISHING_ROD);
        recipe.setIngredient('L', new RecipeChoice.MaterialChoice(new ArrayList<>(SHELF_MATERIALS)));
        recipe.setIngredient('B', Material.BOW);
        recipe.setIngredient('K', Material.WOODEN_SWORD);
        recipe.setIngredient('C', Material.WOODEN_PICKAXE);
        recipe.setIngredient('H', Material.WOODEN_HOE);
        Bukkit.removeRecipe(multitoolRecipeKey);
        Bukkit.addRecipe(recipe);
    }

    public void discoverRecipes(HumanEntity player) {
        player.discoverRecipe(multitoolRecipeKey);
    }

    public ItemStack createMultitool(Material baseMaterial) {
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MULTITOOL_NAME);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.lore(buildMultitoolLore(null, false, false, false));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(baseMaterialKey, PersistentDataType.STRING, baseMaterial.name());
        meta.getPersistentDataContainer().set(selectedToolKey, PersistentDataType.STRING, "");
        meta.getPersistentDataContainer().set(selectedToolSlotKey, PersistentDataType.INTEGER, 0);
        meta.getPersistentDataContainer().set(manualModeKey, PersistentDataType.BYTE, (byte) 0);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMultitool(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    public boolean isShelfMaterial(Material material) {
        return SHELF_MATERIALS.contains(material);
    }

    public boolean isBlockedRepairBlock(Material material) {
        return BLOCKED_REPAIR_BLOCKS.contains(material);
    }

    public Material getBaseMaterial(ItemStack item) {
        if (!isMultitool(item)) {
            return Material.OAK_SHELF;
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(baseMaterialKey, PersistentDataType.STRING);
        Material material = stored == null ? null : Material.matchMaterial(stored);
        return material == null ? Material.OAK_SHELF : material;
    }

    public ItemStack getStoredTool(ItemStack multitool, ToolKind toolKind) {
        return getStoredTool(multitool, toolKind, 0);
    }

    public ItemStack getStoredTool(ItemStack multitool, ToolKind toolKind, int slotIndex) {
        if (!isMultitool(multitool) || slotIndex < 0 || slotIndex >= MAX_TOOL_SLOTS) {
            return null;
        }
        String encoded = multitool.getItemMeta().getPersistentDataContainer()
                .get(toolKeys.get(toolKind).get(slotIndex), PersistentDataType.STRING);
        return encoded == null || encoded.isBlank() ? null : deserializeItem(encoded);
    }

    public List<ItemStack> getStoredTools(ItemStack multitool, ToolKind toolKind) {
        List<ItemStack> storedTools = new ArrayList<>(MAX_TOOL_SLOTS);
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            storedTools.add(getStoredTool(multitool, toolKind, slotIndex));
        }
        return storedTools;
    }

    public void setStoredTool(ItemStack multitool, ToolKind toolKind, ItemStack tool) {
        setStoredTool(multitool, toolKind, 0, tool);
    }

    public void setStoredTool(ItemStack multitool, ToolKind toolKind, int slotIndex, ItemStack tool) {
        if (!isMultitool(multitool) || slotIndex < 0 || slotIndex >= MAX_TOOL_SLOTS) {
            return;
        }
        ItemMeta meta = multitool.getItemMeta();
        if (tool == null || tool.getType().isAir()) {
            meta.getPersistentDataContainer().remove(toolKeys.get(toolKind).get(slotIndex));
        } else {
            meta.getPersistentDataContainer().set(toolKeys.get(toolKind).get(slotIndex), PersistentDataType.STRING, serializeItem(tool));
        }
        multitool.setItemMeta(meta);
    }

    public Inventory createMainMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.MAIN, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                27,
                Component.text("Multitool")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(18, createMenuDisplay(multitool));
        inventory.setItem(26, createSettingsButton(multitool));
        for (int i = 0; i < MAIN_MENU_TOOL_ORDER.length; i++) {
            inventory.setItem(MAIN_MENU_INFO_SLOTS[i], createDurabilityBookButton(multitool, MAIN_MENU_TOOL_ORDER[i]));
        }
        for (int i = 0; i < MAIN_MENU_TOOL_ORDER.length; i++) {
            inventory.setItem(MAIN_MENU_TOOL_SLOTS[i], createToolButton(multitool, MAIN_MENU_TOOL_ORDER[i]));
        }
        inventory.setItem(8, createTotemDurabilityBookButton(multitool));
        inventory.setItem(17, createTotemButton(multitool));
        return inventory;
    }

    public Inventory createSettingsMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.SETTINGS, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                27,
                Component.text("Multitool Settings")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        int[] slots = {10, 11, 12, 14, 15, 16, 22};
        PreferenceTarget[] targets = PreferenceTarget.values();
        for (int i = 0; i < targets.length && i < slots.length; i++) {
            inventory.setItem(slots[i], createPreferenceButton(multitool, targets[i]));
        }
        inventory.setItem(13, createSettingsInfo());
        inventory.setItem(24, createManualModeButton(multitool));
        return inventory;
    }

    public Inventory createSelfUpgradeMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.SELF_UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                9,
                Component.text("Haltbarkeits-Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(1, createDurabilityInfo());
        inventory.setItem(2, cloneOrNull(getStoredTotemDurabilityBook(multitool)));
        inventory.setItem(7, createDurabilityMenuInfo(multitool));
        inventory.setItem(8, createMenuDisplay(multitool));
        return inventory;
    }

    public Inventory createTotemUpgradeMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.TOTEM_UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                9,
                Component.text("Totem Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(1, createTotemDurabilityBookButton(multitool));
        int unlockedSlots = getUnlockedTotemSlots(multitool);
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            int inventorySlot = UPGRADE_STORAGE_SLOTS[slotIndex];
            if (slotIndex < unlockedSlots) {
                ItemStack stored = getStoredTotem(multitool, slotIndex);
                inventory.setItem(inventorySlot, stored == null ? createAvailableToolSlotPane(slotIndex) : cloneOrNull(stored));
            } else {
                inventory.setItem(inventorySlot, createLockedToolSlotPane(slotIndex));
            }
        }
        inventory.setItem(7, createTotemInfo());
        inventory.setItem(8, createMenuDisplay(multitool));
        return inventory;
    }

    public Inventory createShelfUpgradeMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.SHELF_UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                9,
                Component.text("Regal Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(1, createBindingBookButton(multitool));
        inventory.setItem(7, createBindingInfo());
        inventory.setItem(8, createMenuDisplay(multitool));
        return inventory;
    }

    public Inventory createUpgradeMenu(Player player, ItemStack multitool, ToolKind toolKind) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), toolKind),
                9,
                Component.text(toolKind.getDisplayName() + " Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(1, createDurabilityBookButton(multitool, toolKind));
        int unlockedSlots = getUnlockedToolSlots(multitool, toolKind);
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            int inventorySlot = UPGRADE_STORAGE_SLOTS[slotIndex];
            if (slotIndex < unlockedSlots) {
                ItemStack stored = getStoredTool(multitool, toolKind, slotIndex);
                inventory.setItem(inventorySlot, stored == null ? createAvailableToolSlotPane(slotIndex) : cloneOrNull(stored));
            } else {
                inventory.setItem(inventorySlot, createLockedToolSlotPane(slotIndex));
            }
        }
        inventory.setItem(7, createUpgradeToolIcon(toolKind));
        inventory.setItem(8, createMenuDisplay(multitool));
        return inventory;
    }

    public boolean isAllowedUpgradeItem(ToolKind toolKind, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        return switch (toolKind) {
            case AXE -> Tag.ITEMS_AXES.isTagged(item.getType());
            case SHOVEL -> Tag.ITEMS_SHOVELS.isTagged(item.getType());
            case PICKAXE -> Tag.ITEMS_PICKAXES.isTagged(item.getType());
            case HOE -> Tag.ITEMS_HOES.isTagged(item.getType());
            case SWORD -> Tag.ITEMS_SWORDS.isTagged(item.getType());
            case BOW -> item.getType() == Material.BOW;
            case ROD -> item.getType() == Material.FISHING_ROD;
            case SPEAR -> isSpear(item);
        };
    }

    public void saveUpgradeMenu(ItemStack multitool, ToolKind toolKind, Inventory inventory) {
        int unlockedSlots = getUnlockedToolSlots(multitool, toolKind);
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            if (slotIndex >= unlockedSlots) {
                continue;
            }
            ItemStack stored = inventory.getItem(UPGRADE_STORAGE_SLOTS[slotIndex]);
            if (stored == null || stored.getType().isAir() || isAvailableUpgradeSlotPane(stored) || !isAllowedUpgradeItem(toolKind, stored)) {
                setStoredTool(multitool, toolKind, slotIndex, null);
                continue;
            }
            setStoredTool(multitool, toolKind, slotIndex, stored.clone());
        }
    }

    public ItemStack getStoredDurabilityBook(ItemStack multitool) {
        return getStoredTotemDurabilityBook(multitool);
    }

    public void setStoredDurabilityBook(ItemStack multitool, ItemStack book) {
        setStoredTotemDurabilityBook(multitool, book);
    }

    public ItemStack getStoredDurabilityBook(ItemStack multitool, ToolKind toolKind) {
        return getStoredItem(multitool, durabilityBookKeys.get(toolKind));
    }

    public void setStoredDurabilityBook(ItemStack multitool, ToolKind toolKind, ItemStack book) {
        setStoredItem(multitool, durabilityBookKeys.get(toolKind), cloneOrNull(book));
    }

    public ItemStack getStoredTotemDurabilityBook(ItemStack multitool) {
        return getStoredItem(multitool, storedDurabilityKey);
    }

    public void setStoredTotemDurabilityBook(ItemStack multitool, ItemStack book) {
        setStoredItem(multitool, storedDurabilityKey, cloneOrNull(book));
    }

    public ItemStack getStoredTotem(ItemStack multitool) {
        return getStoredTotem(multitool, 0);
    }

    public ItemStack getStoredTotem(ItemStack multitool, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= MAX_TOOL_SLOTS) {
            return null;
        }
        return getStoredItem(multitool, totemKeys.get(slotIndex));
    }

    public void setStoredTotem(ItemStack multitool, int slotIndex, ItemStack totem) {
        if (slotIndex < 0 || slotIndex >= MAX_TOOL_SLOTS) {
            return;
        }
        setStoredItem(multitool, totemKeys.get(slotIndex), cloneOrNull(totem));
    }

    public ItemStack getStoredBindingBook(ItemStack multitool) {
        return getStoredItem(multitool, storedBindingKey);
    }

    public void saveSelfUpgradeMenu(ItemStack multitool, Inventory inventory) {
        ItemStack durability = inventory.getItem(2);
        setStoredTotemDurabilityBook(multitool, isAllowedSelfUpgradeItem(2, durability) ? cloneOrNull(durability) : null);
    }

    public void saveTotemUpgradeMenu(ItemStack multitool, Inventory inventory) {
        int unlockedSlots = getUnlockedTotemSlots(multitool);
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            if (slotIndex >= unlockedSlots) {
                continue;
            }
            ItemStack stored = inventory.getItem(UPGRADE_STORAGE_SLOTS[slotIndex]);
            if (stored == null || stored.getType().isAir() || isAvailableUpgradeSlotPane(stored) || stored.getType() != Material.TOTEM_OF_UNDYING) {
                setStoredTotem(multitool, slotIndex, null);
                continue;
            }
            setStoredTotem(multitool, slotIndex, stored.clone());
        }
    }

    public void saveShelfUpgradeMenu(ItemStack multitool, Inventory inventory) {
        ItemStack binding = inventory.getItem(2);
        setStoredItem(multitool, storedBindingKey, isAllowedSelfUpgradeItem(6, binding) ? cloneOrNull(binding) : null);
    }

    public boolean isAllowedSelfUpgradeItem(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        if (slot == 2) {
            return isDurabilityBook(item);
        }
        if (slot == 6) {
            return isBindingBook(item);
        }
        return false;
    }

    public boolean hasStoredTotem(ItemStack multitool) {
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            ItemStack totem = getStoredTotem(multitool, slotIndex);
            if (totem != null && !totem.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasBindingUpgrade(ItemStack multitool) {
        ItemStack binding = getStoredBindingBook(multitool);
        return binding != null && !binding.getType().isAir();
    }

    public ToolKind getPreferredTool(ItemStack multitool, PreferenceTarget target) {
        if (!isMultitool(multitool)) {
            return target.getDefaultTool();
        }
        String raw = multitool.getItemMeta().getPersistentDataContainer().get(preferenceKeys.get(target), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return target.getDefaultTool();
        }
        try {
            ToolKind toolKind = ToolKind.valueOf(raw);
            return target.getAllowedTools().contains(toolKind) ? toolKind : target.getDefaultTool();
        } catch (IllegalArgumentException ignored) {
            return target.getDefaultTool();
        }
    }

    public void cyclePreferredTool(ItemStack multitool, PreferenceTarget target, boolean reverse) {
        if (!isMultitool(multitool)) {
            return;
        }
        List<ToolKind> allowed = target.getAllowedTools();
        ToolKind current = getPreferredTool(multitool, target);
        int index = allowed.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        int nextIndex = reverse ? (index - 1 + allowed.size()) % allowed.size() : (index + 1) % allowed.size();
        ItemMeta meta = multitool.getItemMeta();
        meta.getPersistentDataContainer().set(preferenceKeys.get(target), PersistentDataType.STRING, allowed.get(nextIndex).name());
        multitool.setItemMeta(meta);
    }

    public boolean isManualMode(ItemStack multitool) {
        if (!isMultitool(multitool)) {
            return false;
        }
        Byte value = multitool.getItemMeta().getPersistentDataContainer().get(manualModeKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void toggleManualMode(ItemStack multitool) {
        if (!isMultitool(multitool)) {
            return;
        }
        ItemMeta meta = multitool.getItemMeta();
        meta.getPersistentDataContainer().set(manualModeKey, PersistentDataType.BYTE, isManualMode(multitool) ? (byte) 0 : (byte) 1);
        multitool.setItemMeta(meta);
    }

    public void cycleManualTool(ItemStack multitool, boolean reverse) {
        if (!isMultitool(multitool)) {
            return;
        }
        List<ToolKind> usable = getUsableStoredTools(multitool);
        if (usable.isEmpty()) {
            applySelectedDisplay(multitool, null);
            return;
        }
        ToolKind current = getSelectedTool(multitool);
        int index = usable.indexOf(current);
        if (index < 0) {
            index = reverse ? 0 : -1;
        }
        int nextIndex = reverse ? (index - 1 + usable.size()) % usable.size() : (index + 1) % usable.size();
        applySelectedDisplay(multitool, usable.get(nextIndex));
    }

    public boolean consumeStoredTotem(ItemStack multitool) {
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            ItemStack totem = getStoredTotem(multitool, slotIndex);
            if (totem == null || totem.getType().isAir()) {
                continue;
            }
            if (totem.getAmount() <= 1) {
                setStoredTotem(multitool, slotIndex, null);
            } else {
                ItemStack reduced = totem.clone();
                reduced.setAmount(totem.getAmount() - 1);
                setStoredTotem(multitool, slotIndex, reduced);
            }
            return true;
        }
        return false;
    }

    public void refreshHeldMultitool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMultitool(item)) {
            sidebar.clear(player);
            return;
        }
        ToolKind next;
        if (isManualMode(item)) {
            next = resolveCurrentOrFallback(item);
        } else if (isSelectionLocked(player, item)) {
            next = resolveCurrentOrFallback(item);
        } else {
            next = determineTool(player, item);
        }
        applySelectedDisplay(item, next);
        player.getInventory().setItemInMainHand(item);
        sidebar.update(player, item);
    }

    public void clearSidebar(Player player) {
        sidebar.clear(player);
    }

    public void clearAllSidebars() {
        sidebar.clearAll();
    }

    public void scheduleRefreshHeldMultitool(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshHeldMultitool(player));
    }

    public void syncDamageFromUse(Player player, ItemStack itemInHand, int damageAmount) {
        if (!isMultitool(itemInHand)) {
            return;
        }
        ToolKind selected = getSelectedTool(itemInHand);
        if (selected == null) {
            return;
        }
        int selectedSlot = getSelectedToolSlot(itemInHand);
        ItemStack stored = getStoredTool(itemInHand, selected, selectedSlot);
        if (stored == null || !(stored.getItemMeta() instanceof Damageable storedMeta) || !(itemInHand.getItemMeta() instanceof Damageable handMeta)) {
            return;
        }
        int updatedDamage = handMeta.getDamage() + Math.max(damageAmount, 0);
        storedMeta.setDamage(updatedDamage);
        stored.setItemMeta((ItemMeta) storedMeta);
        setStoredTool(itemInHand, selected, selectedSlot, stored);
        if (remainingDurability(stored) == 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8F, 1.0F);
            applySelectedDisplay(itemInHand, selected);
        }
    }

    public boolean matchesMultitoolRecipe(ItemStack[] matrix) {
        return matrix.length >= 9
                && matches(matrix[0], Material.WOODEN_AXE)
                && matches(matrix[1], Material.WOODEN_SHOVEL)
                && matches(matrix[2], Material.WOODEN_SPEAR)
                && matches(matrix[3], Material.FISHING_ROD)
                && matrix[4] != null
                && isShelfMaterial(matrix[4].getType())
                && matches(matrix[5], Material.BOW)
                && matches(matrix[6], Material.WOODEN_SWORD)
                && matches(matrix[7], Material.WOODEN_PICKAXE)
                && matches(matrix[8], Material.WOODEN_HOE);
    }

    public ItemStack createRecipeResult(ItemStack[] matrix) {
        return createMultitool(matrix[4].getType());
    }

    public ToolKind getSelectedTool(ItemStack multitool) {
        if (!isMultitool(multitool)) {
            return null;
        }
        String raw = multitool.getItemMeta().getPersistentDataContainer().get(selectedToolKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ToolKind.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int getSelectedToolSlot(ItemStack multitool) {
        if (!isMultitool(multitool)) {
            return 0;
        }
        Integer raw = multitool.getItemMeta().getPersistentDataContainer().get(selectedToolSlotKey, PersistentDataType.INTEGER);
        if (raw == null || raw < 0 || raw >= MAX_TOOL_SLOTS) {
            return 0;
        }
        return raw;
    }

    private void applySelectedDisplay(ItemStack multitool, ToolKind desiredTool) {
        ItemMeta oldMeta = multitool.getItemMeta();
        PersistentDataContainer oldData = oldMeta.getPersistentDataContainer();
        Material base = getBaseMaterial(multitool);
        int selectedSlot = 0;
        ItemStack display = desiredTool == null ? new ItemStack(base) : cloneOrNull(getFirstUsableTool(multitool, desiredTool));
        if (desiredTool != null) {
            selectedSlot = findFirstUsableToolSlot(multitool, desiredTool);
        }
        if (display == null || display.getType().isAir() || !isUsable(display)) {
            display = new ItemStack(base);
            desiredTool = null;
            selectedSlot = 0;
        }

        multitool.setType(display.getType());
        ItemMeta displayMeta = display.getItemMeta();
        displayMeta.displayName(createActiveMultitoolName(desiredTool));
        displayMeta.lore(buildMultitoolLore(desiredTool, hasStoredTotem(multitool), hasBindingUpgrade(multitool), isManualMode(multitool)));
        displayMeta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        displayMeta.getPersistentDataContainer().set(baseMaterialKey, PersistentDataType.STRING, base.name());
        displayMeta.getPersistentDataContainer().set(selectedToolKey, PersistentDataType.STRING, desiredTool == null ? "" : desiredTool.name());
        displayMeta.getPersistentDataContainer().set(selectedToolSlotKey, PersistentDataType.INTEGER, selectedSlot);
        for (ToolKind toolKind : ToolKind.values()) {
            for (NamespacedKey key : toolKeys.get(toolKind)) {
                String encoded = oldData.get(key, PersistentDataType.STRING);
                if (encoded == null || encoded.isBlank()) {
                    displayMeta.getPersistentDataContainer().remove(key);
                } else {
                    displayMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, encoded);
                }
            }
        }
        for (PreferenceTarget target : PreferenceTarget.values()) {
            copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), preferenceKeys.get(target));
        }
        Byte manualMode = oldData.get(manualModeKey, PersistentDataType.BYTE);
        displayMeta.getPersistentDataContainer().set(manualModeKey, PersistentDataType.BYTE, manualMode == null ? (byte) 0 : manualMode);
        copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), storedDurabilityKey);
        for (ToolKind toolKind : ToolKind.values()) {
            copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), durabilityBookKeys.get(toolKind));
        }
        for (NamespacedKey key : totemKeys) {
            copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), key);
        }
        copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), storedBindingKey);
        if (desiredTool == null && displayMeta.getEnchants().isEmpty()) {
            displayMeta.addEnchant(Enchantment.INFINITY, 1, true);
        }
        multitool.setItemMeta(displayMeta);
    }

    private ItemStack createMenuDisplay(ItemStack multitool) {
        ItemStack item = new ItemStack(getBaseMaterial(multitool));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(createActiveMultitoolName(getSelectedTool(multitool)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Werkzeug-Slots"));
        for (ToolKind toolKind : ToolKind.values()) {
            lore.add(Component.text(toolKind.getDisplayName() + ": " + countStoredTools(multitool, toolKind) + "/" + getUnlockedToolSlots(multitool) + " belegt"));
        }
        lore.add(Component.text("Totem: " + (hasStoredTotem(multitool) ? "gespeichert" : "leer")));
        lore.add(Component.text("Bindung: " + (hasBindingUpgrade(multitool) ? "aktiv" : "leer")));
        lore.add(Component.text("Haltbarkeit I-III schaltet bis zu 3 weitere Slots frei"));
        lore.add(Component.text("Klick fuer Upgrade- und Regal-Slots"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component createActiveMultitoolName(ToolKind activeTool) {
        return Component.text("Multitool", activeTool == null ? NamedTextColor.GOLD : NamedTextColor.GREEN);
    }

    private ItemStack createToolButton(ItemStack multitool, ToolKind toolKind) {
        ItemStack stored = getFirstStoredTool(multitool, toolKind);
        ItemStack item = new ItemStack(stored == null ? getMenuMaterial(toolKind) : stored.getType());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName()));
        meta.lore(List.of(
                Component.text("Belegt: " + countStoredTools(multitool, toolKind) + "/" + getUnlockedToolSlots(multitool, toolKind)),
                Component.text(stored == null ? "Kein Werkzeug gespeichert" : "Erstes Werkzeug: " + stored.getType().name()),
                Component.text("Klick zum Bearbeiten")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDurabilityBookButton(ItemStack multitool, ToolKind toolKind) {
        ItemStack stored = getStoredDurabilityBook(multitool, toolKind);
        ItemStack item = stored == null ? new ItemStack(Material.PURPLE_STAINED_GLASS_PANE) : stored.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName() + "-Buch"));
        meta.lore(List.of(
                Component.text(stored == null ? "Kein Haltbarkeitsbuch eingesetzt" : "Aktiv: Haltbarkeit " + getDurabilityBookLevel(stored)),
                Component.text("Lege hier ein Haltbarkeitsbuch ab oder nimm es heraus."),
                Component.text("Freie Slots: " + getUnlockedToolSlots(multitool, toolKind) + "/" + MAX_TOOL_SLOTS)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTotemDurabilityBookButton(ItemStack multitool) {
        ItemStack stored = getStoredTotemDurabilityBook(multitool);
        ItemStack item = stored == null ? new ItemStack(Material.PURPLE_STAINED_GLASS_PANE) : stored.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Totem-Buch"));
        meta.lore(List.of(
                Component.text(stored == null ? "Kein Haltbarkeitsbuch eingesetzt" : "Aktiv: Haltbarkeit " + getDurabilityBookLevel(stored)),
                Component.text("Lege hier ein Haltbarkeitsbuch ab oder nimm es heraus."),
                Component.text("Freie Slots: " + getUnlockedTotemSlots(multitool) + "/" + MAX_TOOL_SLOTS)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBindingBookButton(ItemStack multitool) {
        ItemStack stored = getStoredBindingBook(multitool);
        ItemStack item = stored == null ? new ItemStack(Material.PURPLE_STAINED_GLASS_PANE) : stored.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Bindungsbuch"));
        meta.lore(List.of(
                Component.text(stored == null ? "Kein Buch des Verschwindens eingesetzt" : "Buch des Verschwindens ist eingesetzt"),
                Component.text("Lege hier ein Buch mit Fluch der Bindung ab oder nimm es heraus.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTotemButton(ItemStack multitool) {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Totem"));
        meta.lore(List.of(
                Component.text("Belegt: " + countStoredTotems(multitool) + "/" + getUnlockedTotemSlots(multitool)),
                Component.text("Klick zum Bearbeiten")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsButton(ItemStack multitool) {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Auto-Auswahl Settings"));
        meta.lore(List.of(
                Component.text("Klick zum Oeffnen"),
                Component.text("Lege fest, welches Tool bei Mobs bevorzugt wird")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreferenceButton(ItemStack multitool, PreferenceTarget target) {
        ItemStack item = new ItemStack(target.getIcon());
        ItemMeta meta = item.getItemMeta();
        ToolKind current = getPreferredTool(multitool, target);
        meta.displayName(Component.text(target.getDisplayName()));
        meta.lore(List.of(
                Component.text("Aktuell: " + current.getDisplayName()),
                Component.text("Linksklick: naechstes Tool"),
                Component.text("Rechtsklick: vorheriges Tool"),
                Component.text("Moeglich: " + joinToolNames(target.getAllowedTools()))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsInfo() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Settings"));
        meta.lore(List.of(
                Component.text("Hier stellst du die Bevorzugung fuer Ziele ein."),
                Component.text("Aktuell steuerbar: feindliche und friedliche Mobs, Wasser-Mobs und unbekannte Ziele.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Zurück"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradeInfo(ToolKind toolKind, int unlockedSlots) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName() + "-Upgrade"));
        meta.lore(List.of(
                Component.text("Freie Slots: " + unlockedSlots + "/" + MAX_TOOL_SLOTS),
                Component.text("Standardmaessig ist 1 Slot frei."),
                Component.text("Haltbarkeit I-III schaltet pro Stufe 1 weiteren Slot frei."),
                Component.text("Lege im Haltbarkeits-Upgrade ein Haltbarkeitsbuch ab, um Slots freizuschalten.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradeToolIcon(ToolKind toolKind) {
        ItemStack item = new ItemStack(getMenuMaterial(toolKind));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName()));
        meta.lore(List.of(
                Component.text("Lege Werkzeuge in die freien Slots links."),
                Component.text("Verwendet werden die belegten Slots der Reihe nach.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSlotInfoPane(ItemStack multitool) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Werkzeug-Slots"));
        meta.lore(List.of(
                Component.text("Standardmaessig ist 1 Slot frei."),
                Component.text("Lege ein Haltbarkeitsbuch im Upgrade-Menue ab, um weitere Slots freizuschalten."),
                Component.text("Haltbarkeit I-III schaltet bis zu 3 weitere Slots frei."),
                Component.text("Insgesamt lassen sich bis zu 4 Werkzeuge pro Typ speichern."),
                Component.text("Klicke hier, um das Haltbarkeits-Upgrade zu oeffnen."),
                Component.text("Aktuell frei: " + getUnlockedToolSlots(multitool) + "/" + MAX_TOOL_SLOTS)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedToolSlotPane(int slotIndex) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Slot " + (slotIndex + 1) + " gesperrt"));
        meta.lore(List.of(Component.text("Schalte weitere Slots mit Haltbarkeit I-III frei.")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAvailableToolSlotPane(int slotIndex) {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Slot " + (slotIndex + 1) + " frei"));
        meta.lore(List.of(Component.text("Lege hier ein passendes Item ab.")));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAvailableUpgradeSlotPane(int rawSlot) {
        for (int slotIndex = 0; slotIndex < UPGRADE_STORAGE_SLOTS.length; slotIndex++) {
            if (UPGRADE_STORAGE_SLOTS[slotIndex] == rawSlot) {
                return createAvailableToolSlotPane(slotIndex);
            }
        }
        return null;
    }

    public boolean isAvailableUpgradeSlotPane(ItemStack item) {
        return item != null
                && item.getType() == Material.LIME_STAINED_GLASS_PANE
                && item.hasItemMeta()
                && item.getItemMeta().displayName() != null;
    }

    private ItemStack createTotemInfo() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Totem-Slots"));
        meta.lore(List.of(
                Component.text("Lege Totems in die freien Slots links."),
                Component.text("Beim Tod werden die belegten Slots der Reihe nach genutzt.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDurabilityInfo() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Haltbarkeits-Upgrade"));
        meta.lore(List.of(
                Component.text("Lege ein Haltbarkeitsbuch in den Slot rechts."),
                Component.text("Du kannst das Buch auch direkt auf dieses Feld legen."),
                Component.text("Zum Entfernen wird das Buch in dein Inventar gelegt."),
                Component.text("Haltbarkeit I-III schaltet 1 bis 3 Zusatzslots frei.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDurabilityMenuInfo(ItemStack multitool) {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Slot-Freischaltung"));
        meta.lore(List.of(
                Component.text("Lege rechts ein Haltbarkeitsbuch ab."),
                Component.text("Aktuell frei: " + getUnlockedTotemSlots(multitool) + "/" + MAX_TOOL_SLOTS),
                Component.text("Haltbarkeit I-III schaltet weitere Slots frei.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBindingInfo() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Regal-Bindung"));
        meta.lore(List.of(
                Component.text("Lege links ein Buch mit Fluch der Bindung ab."),
                Component.text("Dann kann das Multitool nicht gedroppt oder abgelegt werden.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void fillWithBlockedSlots(Inventory inventory) {
        ItemStack blocked = createBlockedPane();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, blocked);
        }
    }

    private ItemStack createBlockedPane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildMultitoolLore(ToolKind activeTool, boolean hasTotem, boolean hasBinding, boolean manualMode) {
        List<Component> lore = new ArrayList<>();
        if (activeTool == null) {
            lore.add(Component.text("Aktiv: ", NamedTextColor.WHITE).append(Component.text("Regal", NamedTextColor.GOLD)));
        } else {
            lore.add(Component.text("Aktiv: ", NamedTextColor.WHITE).append(Component.text(activeTool.getDisplayName(), NamedTextColor.GREEN)));
        }
        lore.add(Component.text("Modus: " + (manualMode ? "Manuell" : "Automatisch")));
        lore.add(Component.text(manualMode
                ? "Middle-Click schaltet durch gespeicherte Werkzeuge."
                : "Wechselt automatisch zum passenden Werkzeug."));
        lore.add(Component.text("Ducken + Rechtsklick oeffnet das Menue."));
        lore.add(Component.text("Im Settings-Menue sind Ziel-Prioritaeten einstellbar."));
        lore.add(Component.text("Pro Werkzeugtyp koennen bis zu 4 Werkzeuge intern gespeichert werden."));
        lore.add(Component.text("Werkzeuge mit 1 Haltbarkeit werden deaktiviert und uebersprungen."));
        lore.add(Component.text("Totem: " + (hasTotem ? "gespeichert" : "nicht gespeichert")));
        lore.add(Component.text("Bindung: " + (hasBinding ? "aktiv" : "nicht aktiv")));
        return lore;
    }

    private String joinToolNames(List<ToolKind> toolKinds) {
        List<String> names = new ArrayList<>();
        for (ToolKind toolKind : toolKinds) {
            names.add(toolKind.getDisplayName());
        }
        return String.join(", ", names);
    }

    private ItemStack createManualModeButton(ItemStack multitool) {
        ItemStack item = new ItemStack(isManualMode(multitool) ? Material.LEVER : Material.REDSTONE_TORCH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Manueller Modus"));
        meta.lore(List.of(
                Component.text("Status: " + (isManualMode(multitool) ? "aktiv" : "inaktiv")),
                Component.text("Aktiv: Middle-Click schaltet durch gespeicherte Tools"),
                Component.text("Auto-Modus: Tool wird automatisch gewaehlt")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ToolKind resolveCurrentOrFallback(ItemStack multitool) {
        ToolKind current = getSelectedTool(multitool);
        if (current != null && hasUsableTool(multitool, current)) {
            return current;
        }
        List<ToolKind> usable = getUsableStoredTools(multitool);
        return usable.isEmpty() ? null : usable.get(0);
    }

    private List<ToolKind> getUsableStoredTools(ItemStack multitool) {
        List<ToolKind> usable = new ArrayList<>();
        for (ToolKind toolKind : ToolKind.values()) {
            if (hasUsableTool(multitool, toolKind)) {
                usable.add(toolKind);
            }
        }
        return usable;
    }

    private boolean isSelectionLocked(Player player, ItemStack multitool) {
        ToolKind selected = getSelectedTool(multitool);
        if (selected == null) {
            return false;
        }
        if ((selected == ToolKind.BOW || selected == ToolKind.SPEAR) && player.isHandRaised()) {
            return true;
        }
        return selected == ToolKind.ROD && player.getFishHook() != null;
    }

    private ToolKind determineTool(Player player, ItemStack multitool) {
        Block block = getRelevantTargetBlock(player);
        double blockDistance = block == null ? Double.MAX_VALUE : player.getEyeLocation().distance(block.getLocation().toCenterLocation());

        RayTraceResult entityTrace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                24.0D,
                entity -> entity instanceof LivingEntity && entity != player
        );
        if (entityTrace != null && entityTrace.getHitEntity() != null) {
            double distance = player.getEyeLocation().distance(entityTrace.getHitEntity().getLocation());
            if (distance <= blockDistance) {
                ToolKind entityTool = determineEntityTool(multitool, entityTrace.getHitEntity(), distance);
                if (entityTool != null && hasUsableTool(multitool, entityTool)) {
                    return entityTool;
                }
            }
        }

        if (block != null) {
            ToolKind blockTool = determineBlockTool(block, isPlayerInWater(player));
            if (blockTool != null && hasUsableTool(multitool, blockTool)) {
                return blockTool;
            }
        }
        return null;
    }

    private ToolKind determineEntityTool(ItemStack multitool, Entity entity, double distance) {
        if (entity.getType() == EntityType.COPPER_GOLEM) {
            return ToolKind.AXE;
        }
        if (entity instanceof WaterMob || entity instanceof Squid) {
            return getPreferredTool(multitool, PreferenceTarget.WATER_ENTITY);
        }
        if (entity instanceof Monster) {
            return getPreferredTool(multitool, distance > 10.0D ? PreferenceTarget.HOSTILE_FAR : PreferenceTarget.HOSTILE_NEAR);
        }
        if (entity instanceof Animals) {
            return getPreferredTool(multitool, distance > 10.0D ? PreferenceTarget.PASSIVE_FAR : PreferenceTarget.PASSIVE_NEAR);
        }
        return getPreferredTool(multitool, distance > 10.0D ? PreferenceTarget.UNKNOWN_FAR : PreferenceTarget.UNKNOWN_NEAR);
    }

    private ToolKind determineBlockTool(Block block, boolean playerInWater) {
        Material type = block.getType();
        if (type == Material.BAMBOO) {
            return ToolKind.SWORD;
        }
        if (!playerInWater && (type == Material.WATER || block.getRelative(BlockFace.UP).getType() == Material.WATER)) {
            return ToolKind.ROD;
        }
        if (!playerInWater && block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return ToolKind.ROD;
        }
        if (type == Material.COCOA) {
            return ToolKind.AXE;
        }
        if (Tag.LEAVES.isTagged(type) || HOE_PREFERRED.contains(type) || Tag.MINEABLE_HOE.isTagged(type)) {
            return ToolKind.HOE;
        }
        if (Tag.MINEABLE_AXE.isTagged(type)
                || Tag.LOGS.isTagged(type)
                || type.name().endsWith("_WOOD")
                || type.name().endsWith("_HYPHAE")) {
            return ToolKind.AXE;
        }
        if (Tag.MINEABLE_PICKAXE.isTagged(type)) {
            return ToolKind.PICKAXE;
        }
        if (Tag.MINEABLE_SHOVEL.isTagged(type) || type == Material.SNOW || type == Material.SNOW_BLOCK) {
            return ToolKind.SHOVEL;
        }
        return null;
    }

    private Block getRelevantTargetBlock(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return null;
        }
        if (!isWaterBlock(block.getType()) || !isPlayerInWater(player)) {
            return block;
        }

        Block replacement = findFirstNonWaterBlock(player);
        return replacement != null ? replacement : block;
    }

    private Block findFirstNonWaterBlock(Player player) {
        BlockIterator iterator = new BlockIterator(player, 6);
        while (iterator.hasNext()) {
            Block next = iterator.next();
            if (!isWaterBlock(next.getType())) {
                return next;
            }
        }
        return null;
    }

    private boolean isPlayerInWater(Player player) {
        return isWaterBlock(player.getEyeLocation().getBlock().getType())
                || isWaterBlock(player.getLocation().getBlock().getType());
    }

    private boolean isWaterBlock(Material material) {
        return material == Material.WATER;
    }

    private boolean hasUsableTool(ItemStack multitool, ToolKind toolKind) {
        return findFirstUsableToolSlot(multitool, toolKind) >= 0;
    }

    public ItemStack getFirstStoredTool(ItemStack multitool, ToolKind toolKind) {
        for (int slotIndex = 0; slotIndex < MAX_TOOL_SLOTS; slotIndex++) {
            ItemStack stored = getStoredTool(multitool, toolKind, slotIndex);
            if (stored != null && !stored.getType().isAir()) {
                return stored;
            }
        }
        return null;
    }

    public ItemStack getFirstUsableTool(ItemStack multitool, ToolKind toolKind) {
        int slotIndex = findFirstUsableToolSlot(multitool, toolKind);
        return slotIndex < 0 ? null : getStoredTool(multitool, toolKind, slotIndex);
    }

    private int findFirstUsableToolSlot(ItemStack multitool, ToolKind toolKind) {
        int unlockedSlots = getUnlockedToolSlots(multitool, toolKind);
        for (int slotIndex = 0; slotIndex < unlockedSlots; slotIndex++) {
            if (isUsable(getStoredTool(multitool, toolKind, slotIndex))) {
                return slotIndex;
            }
        }
        return -1;
    }

    public int getUnlockedToolSlots(ItemStack multitool) {
        return getUnlockedTotemSlots(multitool);
    }

    public int getUnlockedToolSlots(ItemStack multitool, ToolKind toolKind) {
        if (multitool == null || multitool.getType().isAir()) {
            return 1;
        }
        int extraSlots = Math.max(0, getDurabilityBookLevel(getStoredDurabilityBook(multitool, toolKind)));
        return Math.max(1, Math.min(MAX_TOOL_SLOTS, 1 + extraSlots));
    }

    public int getUnlockedTotemSlots(ItemStack multitool) {
        if (multitool == null || multitool.getType().isAir()) {
            return 1;
        }
        ItemStack book = getStoredTotemDurabilityBook(multitool);
        int extraSlots = book == null ? 0 : Math.max(0, getDurabilityBookLevel(book));
        return Math.max(1, Math.min(MAX_TOOL_SLOTS, 1 + extraSlots));
    }

    public boolean isUnlockedUpgradeSlot(ItemStack multitool, int rawSlot) {
        return isUnlockedUpgradeSlot(multitool, null, rawSlot);
    }

    public boolean isUnlockedUpgradeSlot(ItemStack multitool, ToolKind toolKind, int rawSlot) {
        for (int slotIndex = 0; slotIndex < UPGRADE_STORAGE_SLOTS.length; slotIndex++) {
            if (UPGRADE_STORAGE_SLOTS[slotIndex] == rawSlot) {
                return slotIndex < (toolKind == null ? getUnlockedTotemSlots(multitool) : getUnlockedToolSlots(multitool, toolKind));
            }
        }
        return false;
    }

    private int countStoredTools(ItemStack multitool, ToolKind toolKind) {
        int count = 0;
        int unlockedSlots = getUnlockedToolSlots(multitool, toolKind);
        for (int slotIndex = 0; slotIndex < unlockedSlots; slotIndex++) {
            ItemStack stored = getStoredTool(multitool, toolKind, slotIndex);
            if (stored != null && !stored.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private int countStoredTotems(ItemStack multitool) {
        int count = 0;
        int unlockedSlots = getUnlockedTotemSlots(multitool);
        for (int slotIndex = 0; slotIndex < unlockedSlots; slotIndex++) {
            ItemStack stored = getStoredTotem(multitool, slotIndex);
            if (stored != null && !stored.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    private boolean isUsable(ItemStack item) {
        return item != null && !item.getType().isAir() && remainingDurability(item) > 1;
    }

    private int remainingDurability(ItemStack item) {
        if (item.getType().getMaxDurability() <= 0 || !(item.getItemMeta() instanceof Damageable damageable)) {
            return Integer.MAX_VALUE;
        }
        return item.getType().getMaxDurability() - damageable.getDamage();
    }

    private boolean matches(ItemStack item, Material type) {
        return item != null && item.getType() == type;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private Material getMenuMaterial(ToolKind toolKind) {
        if (toolKind == ToolKind.SPEAR) {
            if (spearMaterials.contains(Material.WOODEN_SPEAR)) {
                return Material.WOODEN_SPEAR;
            }
            if (!spearMaterials.isEmpty()) {
                return spearMaterials.iterator().next();
            }
        }
        return toolKind.getDefaultMaterial();
    }

    private boolean isSpear(ItemStack item) {
        return item != null && spearMaterials.contains(item.getType());
    }

    private Set<Material> resolveSpearMaterials() {
        Set<Material> materials = new HashSet<>();
        addMaterialIfPresent(materials, "WOODEN_SPEAR");
        addMaterialIfPresent(materials, "SPEAR");
        addMaterialIfPresent(materials, "STONE_SPEAR");
        addMaterialIfPresent(materials, "IRON_SPEAR");
        addMaterialIfPresent(materials, "GOLDEN_SPEAR");
        addMaterialIfPresent(materials, "DIAMOND_SPEAR");
        addMaterialIfPresent(materials, "NETHERITE_SPEAR");
        return materials;
    }

    private void addMaterialIfPresent(Set<Material> materials, String name) {
        Material material = Material.matchMaterial(name);
        if (material != null) {
            materials.add(material);
        }
    }

    private ItemStack getStoredItem(ItemStack multitool, NamespacedKey key) {
        if (!isMultitool(multitool)) {
            return null;
        }
        String encoded = multitool.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return encoded == null || encoded.isBlank() ? null : deserializeItem(encoded);
    }

    private void setStoredItem(ItemStack multitool, NamespacedKey key, ItemStack item) {
        if (!isMultitool(multitool)) {
            return;
        }
        ItemMeta meta = multitool.getItemMeta();
        if (item == null || item.getType().isAir()) {
            meta.getPersistentDataContainer().remove(key);
        } else {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, serializeItem(item));
        }
        multitool.setItemMeta(meta);
    }

    private boolean isBindingBook(ItemStack item) {
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return meta.hasStoredEnchant(Enchantment.BINDING_CURSE);
        }
        return item.getEnchantments().containsKey(Enchantment.BINDING_CURSE);
    }

    private boolean isDurabilityBook(ItemStack item) {
        return getDurabilityBookLevel(item) > 0;
    }

    private int getDurabilityBookLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return Math.min(3, Math.max(0, meta.getStoredEnchantLevel(Enchantment.UNBREAKING)));
        }
        return Math.min(3, Math.max(0, item.getEnchantmentLevel(Enchantment.UNBREAKING)));
    }

    private void copyStoredValue(PersistentDataContainer source, PersistentDataContainer target, NamespacedKey key) {
        String encoded = source.get(key, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            target.remove(key);
        } else {
            target.set(key, PersistentDataType.STRING, encoded);
        }
    }

    private String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOut = new BukkitObjectOutputStream(out)) {
            dataOut.writeObject(item);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize tool", exception);
        }
    }

    private ItemStack deserializeItem(String encoded) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream dataIn = new BukkitObjectInputStream(in)) {
            Object object = dataIn.readObject();
            return object instanceof ItemStack item ? item : null;
        } catch (IOException | ClassNotFoundException exception) {
            plugin.getLogger().warning("Konnte Werkzeug nicht laden: " + exception.getMessage());
            return null;
        }
    }
}
