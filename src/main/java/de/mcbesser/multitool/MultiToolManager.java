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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.HumanEntity;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
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
            Material.PUMPKIN_STEM, Material.ATTACHED_PUMPKIN_STEM, Material.COCOA
    );

    private final MultiToolPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey baseMaterialKey;
    private final NamespacedKey selectedToolKey;
    private final NamespacedKey storedTotemKey;
    private final NamespacedKey storedBindingKey;
    private final NamespacedKey multitoolRecipeKey;
    private final Map<ToolKind, NamespacedKey> toolKeys = new EnumMap<>(ToolKind.class);
    private final Map<PreferenceTarget, NamespacedKey> preferenceKeys = new EnumMap<>(PreferenceTarget.class);
    private final Set<Material> spearMaterials;
    private final MultiToolSidebar sidebar;

    public MultiToolManager(MultiToolPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "multitool");
        this.baseMaterialKey = new NamespacedKey(plugin, "base_material");
        this.selectedToolKey = new NamespacedKey(plugin, "selected_tool");
        this.storedTotemKey = new NamespacedKey(plugin, "stored_totem");
        this.storedBindingKey = new NamespacedKey(plugin, "stored_binding");
        this.multitoolRecipeKey = new NamespacedKey(plugin, "multitool");
        this.spearMaterials = resolveSpearMaterials();
        this.sidebar = new MultiToolSidebar(this);
        for (ToolKind toolKind : ToolKind.values()) {
            toolKeys.put(toolKind, new NamespacedKey(plugin, "tool_" + toolKind.name().toLowerCase()));
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
        meta.lore(buildMultitoolLore(null, false, false));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(baseMaterialKey, PersistentDataType.STRING, baseMaterial.name());
        meta.getPersistentDataContainer().set(selectedToolKey, PersistentDataType.STRING, "");
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

    public Material getBaseMaterial(ItemStack item) {
        if (!isMultitool(item)) {
            return Material.OAK_SHELF;
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(baseMaterialKey, PersistentDataType.STRING);
        Material material = stored == null ? null : Material.matchMaterial(stored);
        return material == null ? Material.OAK_SHELF : material;
    }

    public ItemStack getStoredTool(ItemStack multitool, ToolKind toolKind) {
        if (!isMultitool(multitool)) {
            return null;
        }
        String encoded = multitool.getItemMeta().getPersistentDataContainer().get(toolKeys.get(toolKind), PersistentDataType.STRING);
        return encoded == null || encoded.isBlank() ? null : deserializeItem(encoded);
    }

    public void setStoredTool(ItemStack multitool, ToolKind toolKind, ItemStack tool) {
        if (!isMultitool(multitool)) {
            return;
        }
        ItemMeta meta = multitool.getItemMeta();
        if (tool == null || tool.getType().isAir()) {
            meta.getPersistentDataContainer().remove(toolKeys.get(toolKind));
        } else {
            meta.getPersistentDataContainer().set(toolKeys.get(toolKind), PersistentDataType.STRING, serializeItem(tool));
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
        inventory.setItem(13, createMenuDisplay(multitool));
        inventory.setItem(10, createToolButton(multitool, ToolKind.AXE));
        inventory.setItem(11, createToolButton(multitool, ToolKind.SHOVEL));
        inventory.setItem(12, createToolButton(multitool, ToolKind.SPEAR));
        inventory.setItem(14, createToolButton(multitool, ToolKind.ROD));
        inventory.setItem(15, createToolButton(multitool, ToolKind.BOW));
        inventory.setItem(16, createToolButton(multitool, ToolKind.SWORD));
        inventory.setItem(18, createSettingsButton(multitool));
        inventory.setItem(21, createToolButton(multitool, ToolKind.PICKAXE));
        inventory.setItem(22, createToolButton(multitool, ToolKind.HOE));
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
        return inventory;
    }

    public Inventory createSelfUpgradeMenu(Player player, ItemStack multitool) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.SELF_UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), null),
                9,
                Component.text("Multitool Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(2, createTotemInfo());
        inventory.setItem(3, cloneOrNull(getStoredTotem(multitool)));
        inventory.setItem(5, createBindingInfo());
        inventory.setItem(6, cloneOrNull(getStoredBindingBook(multitool)));
        inventory.setItem(8, createMenuDisplay(multitool));
        return inventory;
    }

    public Inventory createUpgradeMenu(Player player, ItemStack multitool, ToolKind toolKind) {
        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(MenuHolder.MenuType.UPGRADE, player.getUniqueId(), player.getInventory().getHeldItemSlot(), toolKind),
                InventoryType.HOPPER,
                Component.text(toolKind.getDisplayName() + " Upgrade")
        );
        fillWithBlockedSlots(inventory);
        inventory.setItem(0, createBackButton());
        inventory.setItem(2, createUpgradeInfo(toolKind));
        inventory.setItem(4, cloneOrNull(getStoredTool(multitool, toolKind)));
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
        ItemStack stored = inventory.getItem(4);
        if (stored == null || stored.getType().isAir()) {
            setStoredTool(multitool, toolKind, null);
            return;
        }
        if (isAllowedUpgradeItem(toolKind, stored)) {
            setStoredTool(multitool, toolKind, stored.clone());
        }
    }

    public ItemStack getStoredTotem(ItemStack multitool) {
        return getStoredItem(multitool, storedTotemKey);
    }

    public ItemStack getStoredBindingBook(ItemStack multitool) {
        return getStoredItem(multitool, storedBindingKey);
    }

    public void saveSelfUpgradeMenu(ItemStack multitool, Inventory inventory) {
        ItemStack totem = inventory.getItem(3);
        ItemStack binding = inventory.getItem(6);
        setStoredItem(multitool, storedTotemKey, isAllowedSelfUpgradeItem(3, totem) ? cloneOrNull(totem) : null);
        setStoredItem(multitool, storedBindingKey, isAllowedSelfUpgradeItem(6, binding) ? cloneOrNull(binding) : null);
    }

    public boolean isAllowedSelfUpgradeItem(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        if (slot == 3) {
            return item.getType() == Material.TOTEM_OF_UNDYING;
        }
        if (slot == 6) {
            return isBindingBook(item);
        }
        return false;
    }

    public boolean hasStoredTotem(ItemStack multitool) {
        ItemStack totem = getStoredTotem(multitool);
        return totem != null && !totem.getType().isAir();
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

    public boolean consumeStoredTotem(ItemStack multitool) {
        ItemStack totem = getStoredTotem(multitool);
        if (totem == null || totem.getType().isAir()) {
            return false;
        }
        if (totem.getAmount() <= 1) {
            setStoredItem(multitool, storedTotemKey, null);
        } else {
            ItemStack reduced = totem.clone();
            reduced.setAmount(totem.getAmount() - 1);
            setStoredItem(multitool, storedTotemKey, reduced);
        }
        return true;
    }

    public void refreshHeldMultitool(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMultitool(item)) {
            sidebar.clear(player);
            return;
        }
        ToolKind next = determineTool(player, item);
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

    public void syncDamageFromUse(ItemStack itemInHand, int damageAmount) {
        if (!isMultitool(itemInHand)) {
            return;
        }
        ToolKind selected = getSelectedTool(itemInHand);
        if (selected == null) {
            return;
        }
        ItemStack stored = getStoredTool(itemInHand, selected);
        if (stored == null || !(stored.getItemMeta() instanceof Damageable storedMeta) || !(itemInHand.getItemMeta() instanceof Damageable handMeta)) {
            return;
        }
        int updatedDamage = handMeta.getDamage() + Math.max(damageAmount, 0);
        storedMeta.setDamage(updatedDamage);
        stored.setItemMeta((ItemMeta) storedMeta);
        setStoredTool(itemInHand, selected, stored);
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

    private void applySelectedDisplay(ItemStack multitool, ToolKind desiredTool) {
        ItemMeta oldMeta = multitool.getItemMeta();
        PersistentDataContainer oldData = oldMeta.getPersistentDataContainer();
        Material base = getBaseMaterial(multitool);
        ItemStack display = desiredTool == null ? new ItemStack(base) : cloneOrNull(getStoredTool(multitool, desiredTool));
        if (display == null || display.getType().isAir() || !isUsable(display)) {
            display = new ItemStack(base);
            desiredTool = null;
        }

        multitool.setType(display.getType());
        ItemMeta displayMeta = display.getItemMeta();
        displayMeta.displayName(MULTITOOL_NAME);
        displayMeta.lore(buildMultitoolLore(desiredTool, hasStoredTotem(multitool), hasBindingUpgrade(multitool)));
        displayMeta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        displayMeta.getPersistentDataContainer().set(baseMaterialKey, PersistentDataType.STRING, base.name());
        displayMeta.getPersistentDataContainer().set(selectedToolKey, PersistentDataType.STRING, desiredTool == null ? "" : desiredTool.name());
        for (ToolKind toolKind : ToolKind.values()) {
            String encoded = oldData.get(toolKeys.get(toolKind), PersistentDataType.STRING);
            if (encoded == null || encoded.isBlank()) {
                displayMeta.getPersistentDataContainer().remove(toolKeys.get(toolKind));
            } else {
                displayMeta.getPersistentDataContainer().set(toolKeys.get(toolKind), PersistentDataType.STRING, encoded);
            }
        }
        for (PreferenceTarget target : PreferenceTarget.values()) {
            copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), preferenceKeys.get(target));
        }
        copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), storedTotemKey);
        copyStoredValue(oldData, displayMeta.getPersistentDataContainer(), storedBindingKey);
        if (desiredTool == null && displayMeta.getEnchants().isEmpty()) {
            displayMeta.addEnchant(Enchantment.INFINITY, 1, true);
        }
        multitool.setItemMeta(displayMeta);
    }

    private ItemStack createMenuDisplay(ItemStack multitool) {
        ItemStack item = new ItemStack(getBaseMaterial(multitool));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MULTITOOL_NAME);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Werkzeug-Slots"));
        for (ToolKind toolKind : ToolKind.values()) {
            ItemStack stored = getStoredTool(multitool, toolKind);
            lore.add(Component.text(toolKind.getDisplayName() + ": " + (stored == null ? "leer" : stored.getType().name())));
        }
        lore.add(Component.text("Totem: " + (hasStoredTotem(multitool) ? "gespeichert" : "leer")));
        lore.add(Component.text("Bindung: " + (hasBindingUpgrade(multitool) ? "aktiv" : "leer")));
        lore.add(Component.text("Klick in der Mitte für Regal-Upgrades"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToolButton(ItemStack multitool, ToolKind toolKind) {
        ItemStack stored = getStoredTool(multitool, toolKind);
        ItemStack item = new ItemStack(stored == null ? getMenuMaterial(toolKind) : stored.getType());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName()));
        meta.lore(List.of(
                Component.text(stored == null ? "Kein Werkzeug gespeichert" : "Gespeichert: " + stored.getType().name()),
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

    private ItemStack createUpgradeInfo(ToolKind toolKind) {
        ItemStack item = new ItemStack(getMenuMaterial(toolKind));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(toolKind.getDisplayName()));
        meta.lore(List.of(Component.text("Slot rechts: Werkzeug einsetzen"), Component.text("Oder wieder herausnehmen")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTotemInfo() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Totem-Slot"));
        meta.lore(List.of(
                Component.text("Rechts daneben Totem einlegen"),
                Component.text("Beim Tod wird es automatisch genutzt")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBindingInfo() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Bindungsbuch-Slot"));
        meta.lore(List.of(
                Component.text("Rechts daneben Buch mit Fluch der Bindung"),
                Component.text("Dann kann das Multitool nicht gedroppt oder abgelegt werden")
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

    private List<Component> buildMultitoolLore(ToolKind activeTool, boolean hasTotem, boolean hasBinding) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(activeTool == null ? "Aktiv: Regal" : "Aktiv: " + activeTool.getDisplayName()));
        lore.add(Component.text("Wechselt automatisch zum passenden Werkzeug."));
        lore.add(Component.text("Ducken + Rechtsklick oeffnet das Menue."));
        lore.add(Component.text("Im Settings-Menue sind Ziel-Prioritaeten einstellbar."));
        lore.add(Component.text("Werkzeuge koennen intern gespeichert werden."));
        lore.add(Component.text("Werkzeuge mit 1 Haltbarkeit werden deaktiviert."));
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
        if (!playerInWater && (type == Material.WATER || block.getRelative(BlockFace.UP).getType() == Material.WATER)) {
            return ToolKind.ROD;
        }
        if (!playerInWater && block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return ToolKind.ROD;
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
        return isUsable(getStoredTool(multitool, toolKind));
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
