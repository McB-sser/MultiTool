package de.mcbesser.multitool;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRepairBlockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null || !manager.isBlockedRepairBlock(event.getClickedBlock().getType())) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!manager.isMultitool(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "Multitool kann nicht direkt mit Repair/Salvage benutzt werden. Nimm das Werkzeug vorher heraus."
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isMultitool(event.getItemInHand())) {
            return;
        }
        event.setCancelled(true);
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!manager.isMultitool(item)) {
            return;
        }
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!manager.isMultitool(item)) {
            return;
        }
        manager.refreshHeldMultitool(event.getPlayer());
        MultiToolPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(MultiToolPlugin.class);
        if (plugin.getMcMMOHook() != null) {
            plugin.getMcMMOHook().handleBlockBreak(event.getPlayer(), item, event.getBlock());
        }
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
        if (holder.getType() == MenuHolder.MenuType.TOTEM_UPGRADE) {
            handleTotemUpgradeMenuClick(event, holder, player, multitool);
            return;
        }
        if (holder.getType() == MenuHolder.MenuType.SHELF_UPGRADE) {
            handleShelfUpgradeMenuClick(event, player, multitool);
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
        } else if (holder.getType() == MenuHolder.MenuType.TOTEM_UPGRADE) {
            manager.saveTotemUpgradeMenu(multitool, inventory);
        } else if (holder.getType() == MenuHolder.MenuType.SHELF_UPGRADE) {
            manager.saveShelfUpgradeMenu(multitool, inventory);
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

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        scheduleSidebarRecovery(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        scheduleSidebarRecovery(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        handleManualCycle(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickEntity(PlayerPickEntityEvent event) {
        handleManualCycle(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!manager.isMultitool(event.getItem())) {
            return;
        }
        manager.syncDamageFromUse(event.getPlayer(), event.getItem(), event.getDamage());
        manager.refreshHeldMultitool(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!manager.isMultitool(itemInHand)) {
            return;
        }
        if (manager.getSelectedTool(itemInHand) == ToolKind.SPEAR) {
            manager.syncDamageFromUse(player, itemInHand, 1);
        }
        manager.refreshHeldMultitool(player);
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
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= topSize) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        ToolKind clicked = switch (rawSlot) {
            case 9 -> ToolKind.PICKAXE;
            case 10 -> ToolKind.AXE;
            case 11 -> ToolKind.SHOVEL;
            case 12 -> ToolKind.HOE;
            case 13 -> ToolKind.ROD;
            case 14 -> ToolKind.BOW;
            case 15 -> ToolKind.SWORD;
            case 16 -> ToolKind.SPEAR;
            default -> null;
        };
        ToolKind durabilityTarget = switch (rawSlot) {
            case 0 -> ToolKind.PICKAXE;
            case 1 -> ToolKind.AXE;
            case 2 -> ToolKind.SHOVEL;
            case 3 -> ToolKind.HOE;
            case 4 -> ToolKind.ROD;
            case 5 -> ToolKind.BOW;
            case 6 -> ToolKind.SWORD;
            case 7 -> ToolKind.SPEAR;
            default -> null;
        };
        if (durabilityTarget != null) {
            handleMainMenuDurabilityClick(event, player, multitool, durabilityTarget);
        } else if (rawSlot == 8) {
            handleMainMenuTotemDurabilityClick(event, player, multitool);
        } else if (rawSlot == 17) {
            player.openInventory(manager.createTotemUpgradeMenu(player, multitool));
        } else if (rawSlot == 18) {
            player.openInventory(manager.createShelfUpgradeMenu(player, multitool));
        } else if (rawSlot == 26) {
            player.openInventory(manager.createSettingsMenu(player, multitool));
        } else if (clicked != null) {
            player.openInventory(manager.createUpgradeMenu(player, multitool, clicked));
        }
    }

    private void handleSelfUpgradeMenuClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveSelfUpgradeMenu(multitool, event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 1 || rawSlot == 7 || rawSlot == 8) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 2) {
            if (tryMoveTopItemToPlayerInventory(event, player, rawSlot)) {
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !manager.isAllowedSelfUpgradeItem(rawSlot, cursor)) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot >= topSize) {
            if (event.isShiftClick() && moveSelfUpgradeItem(event, rawSlot)) {
                return;
            }
            return;
        }
        if (event.isShiftClick() && moveSelfUpgradeItem(event, rawSlot)) {
            return;
        }
        if (rawSlot < topSize) {
            event.setCancelled(true);
        }
    }

    private boolean moveSelfUpgradeItem(InventoryClickEvent event, int rawSlot) {
        if (rawSlot < event.getView().getTopInventory().getSize()) {
            return false;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return false;
        }

        int targetSlot = -1;
        for (int slot : new int[] {2, 4, 6}) {
            if (manager.isAllowedSelfUpgradeItem(slot, clicked) && isEmpty(event.getInventory().getItem(slot))) {
                targetSlot = slot;
                break;
            }
        }
        if (targetSlot < 0) {
            return false;
        }

        event.setCancelled(true);
        event.getInventory().setItem(targetSlot, clicked.clone());
        event.setCurrentItem(null);
        return true;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private boolean handleSelfUpgradeInfoProxyClick(InventoryClickEvent event, Player player, int rawSlot) {
        int targetSlot = switch (rawSlot) {
            case 1 -> 2;
            case 3 -> 4;
            case 5 -> 6;
            default -> -1;
        };
        if (targetSlot < 0) {
            return false;
        }

        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor)) {
            if (!manager.isAllowedSelfUpgradeItem(targetSlot, cursor)) {
                event.setCancelled(true);
                return true;
            }
            if (!isEmpty(event.getInventory().getItem(targetSlot))) {
                event.setCancelled(true);
                return true;
            }
            event.setCancelled(true);
            event.getInventory().setItem(targetSlot, cursor.clone());
            event.getView().setCursor(null);
            return true;
        }

        return tryMoveTopItemToPlayerInventory(event, player, targetSlot);
    }

    private void handleTotemUpgradeMenuClick(InventoryClickEvent event, MenuHolder holder, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveTotemUpgradeMenu(multitool, event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 1) {
            if (handleDurabilityUpgradeProxyClick(event, player, multitool, holder)) {
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 7 || rawSlot == 8) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= 2 && rawSlot <= 5) {
            if (!manager.isUnlockedUpgradeSlot(multitool, null, rawSlot)) {
                event.setCancelled(true);
                return;
            }
            ItemStack current = event.getInventory().getItem(rawSlot);
            ItemStack cursor = event.getCursor();
            if (manager.isAvailableUpgradeSlotPane(current) && cursor != null && !cursor.getType().isAir()) {
                if (cursor.getType() != org.bukkit.Material.TOTEM_OF_UNDYING) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                event.getInventory().setItem(rawSlot, cursor.clone());
                event.getView().setCursor(null);
                return;
            }
            if (tryMoveTopItemToPlayerInventory(event, player, rawSlot)) {
                return;
            }
            if (cursor != null && !cursor.getType().isAir() && cursor.getType() != org.bukkit.Material.TOTEM_OF_UNDYING) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot >= topSize) {
            return;
        }
        event.setCancelled(true);
    }

    private void handleShelfUpgradeMenuClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveShelfUpgradeMenu(multitool, event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 1) {
            if (tryMoveTopItemToPlayerInventory(event, player, rawSlot)) {
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !manager.isAllowedSelfUpgradeItem(6, cursor)) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot == 7 || rawSlot == 8) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= topSize) {
            return;
        }
        event.setCancelled(true);
    }

    private void handleUpgradeMenuClick(InventoryClickEvent event, MenuHolder holder, Player player, ItemStack multitool) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot == 0) {
            event.setCancelled(true);
            manager.saveUpgradeMenu(multitool, holder.getToolKind(), event.getInventory());
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        if (rawSlot == 1) {
            if (handleDurabilityUpgradeProxyClick(event, player, multitool, holder)) {
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (rawSlot == 7 || rawSlot == 8) {
            event.setCancelled(true);
            return;
        }
        if (rawSlot >= 2 && rawSlot <= 5) {
            if (!manager.isUnlockedUpgradeSlot(multitool, holder.getToolKind(), rawSlot)) {
                event.setCancelled(true);
                return;
            }
            ItemStack current = event.getInventory().getItem(rawSlot);
            ItemStack cursor = event.getCursor();
            if (manager.isAvailableUpgradeSlotPane(current) && cursor != null && !cursor.getType().isAir()) {
                if (!manager.isAllowedUpgradeItem(holder.getToolKind(), cursor)) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                event.getInventory().setItem(rawSlot, cursor.clone());
                event.getView().setCursor(null);
                return;
            }
            if (tryMoveTopItemToPlayerInventory(event, player, rawSlot)) {
                return;
            }
            if (cursor != null && !cursor.getType().isAir() && !manager.isAllowedUpgradeItem(holder.getToolKind(), cursor)) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot >= topSize) {
            return;
        }
        if (rawSlot < topSize) {
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
        if (rawSlot == 24) {
            event.setCancelled(true);
            manager.toggleManualMode(multitool);
            manager.refreshHeldMultitool(player);
            player.openInventory(manager.createSettingsMenu(player, multitool));
            return;
        }
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

    private void handleManualCycle(Player player, org.bukkit.event.Cancellable event) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!manager.isMultitool(item) || !manager.isManualMode(item)) {
            return;
        }
        event.setCancelled(true);
        manager.cycleManualTool(item, false);
        player.getInventory().setItemInMainHand(item);
        manager.refreshHeldMultitool(player);
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

    private boolean tryMoveTopItemToPlayerInventory(InventoryClickEvent event, Player player, int rawSlot) {
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            return false;
        }
        if (event.getClick().isShiftClick()) {
            return false;
        }
        if (!isEmpty(event.getCursor())) {
            return false;
        }
        ItemStack current = event.getInventory().getItem(rawSlot);
        if (isEmpty(current)) {
            return false;
        }
        if (player.getInventory().firstEmpty() < 0) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Kein freier Platz im Inventar."));
            return true;
        }
        event.setCancelled(true);
        player.getInventory().addItem(current.clone());
        ItemStack replacement = manager.createAvailableUpgradeSlotPane(rawSlot);
        event.getInventory().setItem(rawSlot, replacement);
        return true;
    }

    private boolean handleDurabilityUpgradeProxyClick(
            InventoryClickEvent event,
            Player player,
            ItemStack multitool,
            MenuHolder holder
    ) {
        if (holder.getType() == MenuHolder.MenuType.UPGRADE) {
            manager.saveUpgradeMenu(multitool, holder.getToolKind(), event.getInventory());
        } else if (holder.getType() == MenuHolder.MenuType.TOTEM_UPGRADE) {
            manager.saveTotemUpgradeMenu(multitool, event.getInventory());
        }
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor)) {
            if (!manager.isAllowedSelfUpgradeItem(2, cursor)) {
                event.setCancelled(true);
                return true;
            }
            event.setCancelled(true);
            if (holder.getType() == MenuHolder.MenuType.UPGRADE) {
                manager.setStoredDurabilityBook(multitool, holder.getToolKind(), cursor.clone());
            } else {
                manager.setStoredTotemDurabilityBook(multitool, cursor.clone());
            }
            event.getView().setCursor(null);
            reopenUpgradeMenu(player, multitool, holder);
            return true;
        }

        ItemStack stored = holder.getType() == MenuHolder.MenuType.UPGRADE
                ? manager.getStoredDurabilityBook(multitool, holder.getToolKind())
                : manager.getStoredTotemDurabilityBook(multitool);
        if (isEmpty(stored)) {
            return false;
        }
        event.setCancelled(true);
        if (holder.getType() == MenuHolder.MenuType.UPGRADE) {
            if (!removeToolDurabilityBook(player, multitool, holder.getToolKind())) {
                return true;
            }
        } else {
            if (!removeTotemDurabilityBook(player, multitool)) {
                return true;
            }
        }
        reopenUpgradeMenu(player, multitool, holder);
        return true;
    }

    private void reopenUpgradeMenu(Player player, ItemStack multitool, MenuHolder holder) {
        if (holder.getType() == MenuHolder.MenuType.UPGRADE) {
            player.openInventory(manager.createUpgradeMenu(player, multitool, holder.getToolKind()));
        } else if (holder.getType() == MenuHolder.MenuType.TOTEM_UPGRADE) {
            player.openInventory(manager.createTotemUpgradeMenu(player, multitool));
        }
    }

    private void handleMainMenuDurabilityClick(InventoryClickEvent event, Player player, ItemStack multitool, ToolKind toolKind) {
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor)) {
            if (!manager.isAllowedSelfUpgradeItem(2, cursor)) {
                return;
            }
            manager.setStoredDurabilityBook(multitool, toolKind, cursor.clone());
            event.getView().setCursor(null);
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        ItemStack stored = manager.getStoredDurabilityBook(multitool, toolKind);
        if (isEmpty(stored)) {
            return;
        }
        if (!removeToolDurabilityBook(player, multitool, toolKind)) {
            return;
        }
        player.openInventory(manager.createMainMenu(player, multitool));
    }

    private void handleMainMenuTotemDurabilityClick(InventoryClickEvent event, Player player, ItemStack multitool) {
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor)) {
            if (!manager.isAllowedSelfUpgradeItem(2, cursor)) {
                return;
            }
            manager.setStoredTotemDurabilityBook(multitool, cursor.clone());
            event.getView().setCursor(null);
            player.openInventory(manager.createMainMenu(player, multitool));
            return;
        }
        ItemStack stored = manager.getStoredTotemDurabilityBook(multitool);
        if (isEmpty(stored)) {
            return;
        }
        if (!removeTotemDurabilityBook(player, multitool)) {
            return;
        }
        player.openInventory(manager.createMainMenu(player, multitool));
    }

    private boolean removeToolDurabilityBook(Player player, ItemStack multitool, ToolKind toolKind) {
        ItemStack storedBook = manager.getStoredDurabilityBook(multitool, toolKind);
        if (isEmpty(storedBook)) {
            return false;
        }

        List<ItemStack> itemsToReturn = new ArrayList<>();
        itemsToReturn.add(storedBook.clone());
        int unlockedSlots = manager.getUnlockedToolSlots(multitool, toolKind);
        for (int slotIndex = 1; slotIndex < unlockedSlots; slotIndex++) {
            ItemStack storedTool = manager.getStoredTool(multitool, toolKind, slotIndex);
            if (!isEmpty(storedTool)) {
                itemsToReturn.add(storedTool.clone());
            }
        }

        if (!canFitAllItems(player.getInventory(), itemsToReturn)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("Nicht genug Platz im Inventar fuer Buch und gesperrte Werkzeuge."));
            return false;
        }

        player.getInventory().addItem(itemsToReturn.toArray(new ItemStack[0]));
        manager.setStoredDurabilityBook(multitool, toolKind, null);
        for (int slotIndex = 1; slotIndex < unlockedSlots; slotIndex++) {
            manager.setStoredTool(multitool, toolKind, slotIndex, null);
        }
        return true;
    }

    private boolean removeTotemDurabilityBook(Player player, ItemStack multitool) {
        ItemStack storedBook = manager.getStoredTotemDurabilityBook(multitool);
        if (isEmpty(storedBook)) {
            return false;
        }

        List<ItemStack> itemsToReturn = new ArrayList<>();
        itemsToReturn.add(storedBook.clone());
        int unlockedSlots = manager.getUnlockedTotemSlots(multitool);
        for (int slotIndex = 1; slotIndex < unlockedSlots; slotIndex++) {
            ItemStack storedTotem = manager.getStoredTotem(multitool, slotIndex);
            if (!isEmpty(storedTotem)) {
                itemsToReturn.add(storedTotem.clone());
            }
        }

        if (!canFitAllItems(player.getInventory(), itemsToReturn)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("Nicht genug Platz im Inventar fuer Buch und gesperrte Inhalte."));
            return false;
        }

        player.getInventory().addItem(itemsToReturn.toArray(new ItemStack[0]));
        manager.setStoredTotemDurabilityBook(multitool, null);
        for (int slotIndex = 1; slotIndex < unlockedSlots; slotIndex++) {
            manager.setStoredTotem(multitool, slotIndex, null);
        }
        return true;
    }

    private boolean canFitAllItems(Inventory inventory, List<ItemStack> items) {
        ItemStack[] simulated = inventory.getStorageContents().clone();
        for (ItemStack item : items) {
            if (isEmpty(item)) {
                continue;
            }
            int remaining = item.getAmount();
            int maxStackSize = Math.min(item.getMaxStackSize(), item.getType().getMaxStackSize());

            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                ItemStack current = simulated[slot];
                if (isEmpty(current) || !current.isSimilar(item)) {
                    continue;
                }
                int space = maxStackSize - current.getAmount();
                if (space <= 0) {
                    continue;
                }
                int moved = Math.min(space, remaining);
                current.setAmount(current.getAmount() + moved);
                remaining -= moved;
            }

            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                if (!isEmpty(simulated[slot])) {
                    continue;
                }
                ItemStack placed = item.clone();
                int moved = Math.min(maxStackSize, remaining);
                placed.setAmount(moved);
                simulated[slot] = placed;
                remaining -= moved;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void scheduleSidebarRecovery(Player player) {
        manager.refreshHeldMultitool(player);
        for (long delay : new long[] {1L, 2L, 5L, 10L, 20L}) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(MultiToolListener.class),
                    () -> manager.refreshHeldMultitool(player),
                    delay
            );
        }
    }
}
