package de.mcbesser.multitool;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class MultiToolListener implements Listener {
    private final MultiToolManager manager;

    public MultiToolListener(MultiToolManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (manager.matchesMultitoolRecipe(matrix)) {
            event.getInventory().setResult(manager.createRecipeResult(matrix));
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (manager.matchesMultitoolRecipe(matrix)) {
            event.getCurrentItem().setAmount(1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!manager.isMultitool(item)) {
            return;
        }

        boolean openMenu = player.isSneaking()
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK);
        if (openMenu) {
            event.setCancelled(true);
            event.setUseItemInHand(Result.DENY);
            event.setUseInteractedBlock(Result.DENY);
            player.openInventory(manager.createMainMenu(player, item));
            return;
        }
        manager.refreshHeldMultitool(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isMultitool(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (cancelBoundInventoryMove(event)) {
            return;
        }

        InventoryView view = event.getView();
        if (!(view.getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack multitool = player.getInventory().getItem(holder.getHotbarSlot());
        if (!manager.isMultitool(multitool)) {
            event.setCancelled(true);
            return;
        }

        if (holder.getType() == MenuHolder.MenuType.MAIN) {
            handleMainMenuClick(event, player, multitool);
            return;
        }
        if (holder.getType() == MenuHolder.MenuType.SELF_UPGRADE) {
            handleSelfUpgradeMenuClick(event, player, multitool);
            return;
        }
        if (holder.getType() == MenuHolder.MenuType.SETTINGS) {
            handleSettingsMenuClick(event, player, multitool);
            return;
        }
        handleUpgradeMenuClick(event, holder, player, multitool);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        ItemStack multitool = player.getInventory().getItem(holder.getHotbarSlot());
        if (!manager.isMultitool(multitool)) {
            return;
        }
        if (holder.getType() == MenuHolder.MenuType.UPGRADE) {
            manager.saveUpgradeMenu(multitool, holder.getToolKind(), inventory);
        } else if (holder.getType() == MenuHolder.MenuType.SELF_UPGRADE) {
            manager.saveSelfUpgradeMenu(multitool, inventory);
        }
        manager.refreshHeldMultitool(player);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getYaw() == event.getTo().getYaw()
                && event.getFrom().getPitch() == event.getTo().getPitch()
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        manager.scheduleRefreshHeldMultitool(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.discoverRecipes(event.getPlayer());
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!manager.isMultitool(event.getItem())) {
            return;
        }
        manager.syncDamageFromUse(event.getItem(), event.getDamage());
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (manager.isMultitool(player.getInventory().getItemInMainHand())) {
            manager.refreshHeldMultitool(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isMultitool(event.getItemDrop().getItemStack())
                && manager.hasBindingUpgrade(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (manager.isMultitool(item) && manager.hasBindingUpgrade(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isCancelled()) {
            return;
        }
        ItemStack multitool = findMultitoolWithTotem(player);
        if (multitool == null || !manager.consumeStoredTotem(multitool)) {
            return;
        }
        event.setCancelled(false);
        manager.refreshHeldMultitool(player);
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        event.setCancelled(true);
        ToolKind clicked = switch (event.getRawSlot()) {
            case 13 -> null;
            case 10 -> ToolKind.AXE;
            case 11 -> ToolKind.SHOVEL;
            case 12 -> ToolKind.SPEAR;
            case 14 -> ToolKind.ROD;
            case 15 -> ToolKind.BOW;
            case 16 -> ToolKind.SWORD;
            case 18 -> null;
            case 21 -> ToolKind.PICKAXE;
            case 22 -> ToolKind.HOE;
            default -> null;
        };
        if (event.getRawSlot() == 13) {
            player.openInventory(manager.createSelfUpgradeMenu(player, multitool));
        } else if (event.getRawSlot() == 18) {
            player.openInventory(manager.createSettingsMenu(player, multitool));
        } else if (clicked != null) {
            player.openInventory(manager.createUpgradeMenu(player, multitool, clicked));
        }
    }

    private void handleSelfUpgradeMenuClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveSelfUpgradeMenu(multitool, event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 2 || rawSlot == 5 || rawSlot == 8) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 3 || rawSlot == 6) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !manager.isAllowedSelfUpgradeItem(rawSlot, cursor)) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        }
    }

    private void handleUpgradeMenuClick(InventoryClickEvent event, MenuHolder holder, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveUpgradeMenu(multitool, holder.getToolKind(), event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 2) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 4) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !manager.isAllowedUpgradeItem(holder.getToolKind(), cursor)) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && !clicked.getType().isAir() && !manager.isAllowedUpgradeItem(holder.getToolKind(), clicked)) {
            event.setCancelled(true);
        }
    }

    private void handleSettingsMenuClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.refreshHeldMultitool(player);
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        PreferenceTarget target = switch (rawSlot) {
            case 10 -> PreferenceTarget.HOSTILE_NEAR;
            case 11 -> PreferenceTarget.HOSTILE_FAR;
            case 12 -> PreferenceTarget.PASSIVE_NEAR;
            case 14 -> PreferenceTarget.PASSIVE_FAR;
            case 15 -> PreferenceTarget.WATER_ENTITY;
            case 16 -> PreferenceTarget.UNKNOWN_NEAR;
            case 22 -> PreferenceTarget.UNKNOWN_FAR;
            default -> null;
        };
        if (target == null) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        boolean reverse = event.isRightClick();
        manager.cyclePreferredTool(multitool, target, reverse);
        manager.refreshHeldMultitool(player);
        player.openInventory(manager.createSettingsMenu(player, multitool));
    }

    private ItemStack findMultitoolWithTotem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (manager.isMultitool(item) && manager.hasStoredTotem(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean cancelBoundInventoryMove(InventoryClickEvent event) {
        InventoryView view = event.getView();
        boolean externalInventoryOpen = view.getTopInventory().getType() != InventoryType.CRAFTING
                && view.getTopInventory().getType() != InventoryType.CREATIVE;

        ItemStack current = event.getCurrentItem();
        if (externalInventoryOpen
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getWhoClicked().getInventory())
                && manager.isMultitool(current)
                && manager.hasBindingUpgrade(current)) {
            event.setCancelled(true);
            return true;
        }

        ItemStack cursor = event.getCursor();
        if (externalInventoryOpen
                && event.getRawSlot() < view.getTopInventory().getSize()
                && manager.isMultitool(cursor)
                && manager.hasBindingUpgrade(cursor)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }
}
