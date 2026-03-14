package de.mcbesser.multitool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
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

    public MultiToolManager(MultiToolPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "multitool");
        this.baseMaterialKey = new NamespacedKey(plugin, "base_material");
        this.selectedToolKey = new NamespacedKey(plugin, "selected_tool");
        this.storedTotemKey = new NamespacedKey(plugin, "stored_totem");
        this.storedBindingKey = new NamespacedKey(plugin, "stored_binding");
        this.multitoolRecipeKey = new NamespacedKey(plugin, "multitool");
        for (ToolKind toolKind : ToolKind.values()) {
            toolKeys.put(toolKind, new NamespacedKey(plugin, "tool_" + toolKind.name().toLowerCase()));
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
        inventory.setItem(21, createToolButton(multitool, ToolKind.PICKAXE));
        inventory.setItem(22, createToolButton(multitool, ToolKind.HOE));
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
            case SPEAR -> item.getType() == Material.WOODEN_SPEAR;
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
            return;
        }
        ToolKind next = determineTool(player, item);
        applySelectedDisplay(item, next);
        player.getInventory().setItemInMainHand(item);
    }

    public void syncDamageFromUse(ItemStack itemInHand) {
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
        storedMeta.setDamage(handMeta.getDamage());
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
        displayMeta.lore(List.of(Component.text(desiredTool == null ? "Nur Regal aktiv" : "Aktiv: " + desiredTool.getDisplayName())));
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

    private ToolKind determineTool(Player player, ItemStack multitool) {
        RayTraceResult entityTrace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                24.0D
        );
        if (entityTrace != null && entityTrace.getHitEntity() != null) {
            double distance = player.getEyeLocation().distance(entityTrace.getHitEntity().getLocation());
            ToolKind entityTool = determineEntityTool(entityTrace.getHitEntity(), distance);
            if (entityTool != null && hasUsableTool(multitool, entityTool)) {
                return entityTool;
            }
        }

        Block block = player.getTargetBlockExact(6);
        if (block != null) {
            ToolKind blockTool = determineBlockTool(block);
            if (blockTool != null && hasUsableTool(multitool, blockTool)) {
                return blockTool;
            }
        }
        return null;
    }

    private ToolKind determineEntityTool(Entity entity, double distance) {
        if (entity instanceof WaterMob || entity instanceof Squid) {
            return ToolKind.ROD;
        }
        if (entity instanceof Monster) {
            return distance > 10.0D ? ToolKind.BOW : ToolKind.SWORD;
        }
        if (entity instanceof Animals) {
            return distance > 10.0D ? ToolKind.BOW : ToolKind.SPEAR;
        }
        return distance > 10.0D ? ToolKind.BOW : ToolKind.SWORD;
    }

    private ToolKind determineBlockTool(Block block) {
        Material type = block.getType();
        if (type == Material.WATER || block.getRelative(BlockFace.UP).getType() == Material.WATER) {
            return ToolKind.ROD;
        }
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return ToolKind.ROD;
        }
        if (Tag.LEAVES.isTagged(type) || HOE_PREFERRED.contains(type) || Tag.MINEABLE_HOE.isTagged(type)) {
            return ToolKind.HOE;
        }
        if (Tag.LOGS.isTagged(type) || type.name().endsWith("_WOOD") || type.name().endsWith("_HYPHAE")) {
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
        return toolKind.getDefaultMaterial();
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
