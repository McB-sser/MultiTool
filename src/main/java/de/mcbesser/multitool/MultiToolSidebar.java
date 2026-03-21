package de.mcbesser.multitool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class MultiToolSidebar {
    private static final String OBJECTIVE_NAME = "multitool";
    private static final int MAX_SLOTS = 4;

    private final MultiToolManager manager;
    private final Map<UUID, BoardState> activeBoards = new HashMap<>();

    public MultiToolSidebar(MultiToolManager manager) {
        this.manager = manager;
    }

    public void update(Player player, ItemStack multitool) {
        if (!manager.isMultitool(multitool)) {
            clear(player);
            return;
        }

        BoardState boardState = activeBoards.computeIfAbsent(player.getUniqueId(), ignored -> new BoardState(createBoard()));
        Scoreboard scoreboard = boardState.scoreboard();
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    Component.text("Multitool", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(Component.text("Multitool", NamedTextColor.GOLD, TextDecoration.BOLD));

        ToolKind selected = manager.getSelectedTool(multitool);
        List<String> renderedLines = boardState.renderedLines();
        for (ToolKind toolKind : ToolKind.values()) {
            int lineIndex = toolKind.ordinal();
            String line = buildLine(multitool, toolKind, selected == toolKind);
            String entry = uniqueEntry(lineIndex);
            Team team = getOrCreateTeam(scoreboard, "line" + lineIndex, entry);
            if (!line.equals(renderedLines.get(lineIndex))) {
                team.prefix(Component.text(line));
                renderedLines.set(lineIndex, line);
            }
            objective.getScore(entry).setScore(ToolKind.values().length - lineIndex);
        }
    }

    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        BoardState active = activeBoards.remove(uuid);
        if (active == null) {
            return;
        }
        if (player.getScoreboard() == active.scoreboard()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        activeBoards.clear();
    }

    private Scoreboard createBoard() {
        return Bukkit.getScoreboardManager().getNewScoreboard();
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name, String entry) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        if (!team.hasEntry(entry)) {
            Set<String> existingEntries = Set.copyOf(team.getEntries());
            for (String existing : existingEntries) {
                team.removeEntry(existing);
            }
            team.addEntry(entry);
        }
        return team;
    }

    private String buildLine(ItemStack multitool, ToolKind toolKind, boolean selected) {
        String marker = selected ? ChatColor.GOLD + ">" : ChatColor.DARK_GRAY + ">";
        String slotSquares = buildSlotSquares(multitool, toolKind);
        ItemStack displayTool = resolveDisplayTool(multitool, toolKind);
        String durability = buildDurabilityDisplay(displayTool);
        String durabilityValue = buildDurabilityValue(displayTool);
        return marker
                + ChatColor.GRAY + " "
                + slotSquares
                + ChatColor.GRAY + " "
                + durability
                + ChatColor.GRAY + " "
                + marker
                + ChatColor.WHITE + toolKind.getDisplayName()
                + ChatColor.GRAY + " "
                + durabilityValue;
    }

    private String buildSlotSquares(ItemStack multitool, ToolKind toolKind) {
        int unlockedSlots = manager.getUnlockedToolSlots(multitool, toolKind);
        StringBuilder squares = new StringBuilder();
        for (int slotIndex = 0; slotIndex < MAX_SLOTS; slotIndex++) {
            if (slotIndex >= unlockedSlots) {
                squares.append(ChatColor.BLACK).append("▮");
                continue;
            }
            ItemStack stored = manager.getStoredTool(multitool, toolKind, slotIndex);
            if (stored == null || stored.getType().isAir()) {
                squares.append(ChatColor.GRAY).append("▮");
                continue;
            }
            if (remainingDurability(stored) <= 1) {
                squares.append(ChatColor.RED).append("▮");
                continue;
            }
            squares.append(ChatColor.GREEN).append("▮");
        }
        return squares.toString();
    }

    private ItemStack resolveDisplayTool(ItemStack multitool, ToolKind toolKind) {
        ItemStack usable = manager.getFirstUsableTool(multitool, toolKind);
        if (usable != null && !usable.getType().isAir()) {
            return usable;
        }
        return manager.getFirstStoredTool(multitool, toolKind);
    }

    private String buildDurabilityDisplay(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ChatColor.DARK_GRAY + "[----------]";
        }
        if (remainingDurability(item) <= 1) {
            return ChatColor.RED + "[----------]";
        }
        int percent = durabilityPercent(item);
        String color = percent > 60 ? ChatColor.GREEN.toString() : percent > 25 ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
        return color + durabilityBar(percent);
    }

    private String buildDurabilityValue(ItemStack item) {
        int remaining = item == null || item.getType().isAir() ? 0 : Math.max(0, remainingDurability(item));
        return ChatColor.GRAY + Integer.toString(remaining);
    }

    private int durabilityPercent(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return 100;
        }
        int max = item.getType().getMaxDurability();
        int remaining = Math.max(0, max - damageable.getDamage());
        return (int) Math.round((remaining * 100.0D) / max);
    }

    private int remainingDurability(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return Integer.MAX_VALUE;
        }
        return item.getType().getMaxDurability() - damageable.getDamage();
    }

    private String durabilityBar(int percent) {
        int filled = Math.max(0, Math.min(10, (int) Math.round(percent / 10.0D)));
        return "[" + "|".repeat(filled) + ChatColor.DARK_GRAY + "|".repeat(10 - filled) + "]";
    }

    private String uniqueEntry(int index) {
        return ChatColor.values()[index].toString();
    }

    private record BoardState(Scoreboard scoreboard, List<String> renderedLines) {
        private BoardState(Scoreboard scoreboard) {
            this(scoreboard, createRenderedLines());
        }

        private static List<String> createRenderedLines() {
            return new ArrayList<>(Collections.nCopies(ToolKind.values().length, null));
        }
    }
}
