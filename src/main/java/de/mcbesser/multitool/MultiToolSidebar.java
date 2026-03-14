package de.mcbesser.multitool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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

    private final MultiToolManager manager;
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeBoards = new HashMap<>();

    public MultiToolSidebar(MultiToolManager manager) {
        this.manager = manager;
    }

    public void update(Player player, ItemStack multitool) {
        if (!manager.isMultitool(multitool)) {
            clear(player);
            return;
        }

        Scoreboard scoreboard = activeBoards.computeIfAbsent(player.getUniqueId(), ignored -> createBoard(player));
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text("Multitool"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(Component.text("Multitool"));

        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        ToolKind selected = manager.getSelectedTool(multitool);
        int score = ToolKind.values().length;
        int index = 0;
        for (ToolKind toolKind : ToolKind.values()) {
            ItemStack stored = manager.getStoredTool(multitool, toolKind);
            String entry = uniqueEntry(index++);
            Team team = getOrCreateTeam(scoreboard, "line" + index, entry);
            team.prefix(Component.text(buildLine(toolKind, stored, selected == toolKind)));
            objective.getScore(entry).setScore(score--);
        }
    }

    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard active = activeBoards.remove(uuid);
        if (active == null) {
            return;
        }
        Scoreboard previous = previousBoards.remove(uuid);
        player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        previousBoards.clear();
        activeBoards.clear();
    }

    private Scoreboard createBoard(Player player) {
        previousBoards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
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

    private String buildLine(ToolKind toolKind, ItemStack stored, boolean selected) {
        String marker = selected ? ChatColor.GOLD + ">" : ChatColor.DARK_GRAY + ">";
        if (stored == null || stored.getType().isAir()) {
            return marker + ChatColor.GRAY + toolKind.getDisplayName() + ": leer";
        }

        int percent = durabilityPercent(stored);
        String color = percent > 60 ? ChatColor.GREEN.toString() : percent > 25 ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
        String bar = color + durabilityBar(percent) + ChatColor.GRAY + " " + percent + "%";
        if (remainingDurability(stored) <= 1) {
            bar = ChatColor.DARK_RED + "inaktiv";
        }
        return marker + ChatColor.WHITE + toolKind.getDisplayName() + ": " + bar;
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
        return "[" + "|".repeat(filled) + ChatColor.DARK_GRAY + "|".repeat(10 - filled) + ChatColor.RESET + "]";
    }

    private String uniqueEntry(int index) {
        return ChatColor.values()[index].toString();
    }
}
