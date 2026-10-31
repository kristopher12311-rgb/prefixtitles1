package dev.prefixtitles.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Показывает префикс и титул в нике (над головой, в табе) и хранит готовое имя для чата. */
public final class NameManager {

    private static final String TEAM_PREFIX = "pt_";

    private final PrefixTitlesPlugin plugin;
    private final Map<UUID, Component> renderedNames = new ConcurrentHashMap<>();

    public NameManager(PrefixTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    private String teamName(Player player) {
        return TEAM_PREFIX + player.getUniqueId().toString().replace("-", "").substring(0, 12);
    }

    /** Запись, которая сейчас реально применяется к игроку (с учётом прав), или null. */
    public Store.Entry active(Player player, Kind kind) {
        Store store = plugin.store();
        String selected = store.selected(player.getUniqueId(), kind);
        if (Store.CUSTOM_ID.equals(selected)) {
            Store.Entry custom = store.custom(player.getUniqueId(), kind);
            boolean ok = custom != null && !custom.display.isBlank() && player.hasPermission("prefixtitles.custom");
            return ok ? custom : null;
        }
        Store.Entry entry = store.find(kind, selected);
        return entry != null && store.canUse(player, kind, entry) ? entry : null;
    }

    private Component prefix(Store.Entry entry) {
        return TextUtil.render(entry).append(Component.text(plugin.store().settings().prefixSpacer));
    }

    private Component title(Store.Entry entry) {
        return Component.text(plugin.store().settings().titleSpacer).append(TextUtil.render(entry));
    }

    private Component calculate(Player player) {
        return build(player, active(player, Kind.PREFIX), active(player, Kind.TITLE));
    }

    private Component build(Player player, Store.Entry prefix, Store.Entry title) {
        Component result = Component.empty();
        if (prefix != null) result = result.append(prefix(prefix));
        result = result.append(Component.text(player.getName()));
        if (title != null) result = result.append(title(title));
        return result;
    }

    public Component decoratedName(Player player) {
        return renderedNames.getOrDefault(player.getUniqueId(), Component.text(player.getName()));
    }

    /** Как будет выглядеть ник, если вместо выбранной записи этого типа поставить entry. */
    public Component preview(Player player, Kind kind, Store.Entry entry) {
        Store.Entry prefix = kind == Kind.PREFIX ? entry : active(player, Kind.PREFIX);
        Store.Entry title = kind == Kind.TITLE ? entry : active(player, Kind.TITLE);
        return build(player, prefix, title);
    }

    public void refresh(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(player);
        Team current = board.getEntryTeam(player.getName());
        // Чат продолжает показывать пользовательское имя даже тогда, когда
        // команды scoreboard уже контролируются другим плагином.
        renderedNames.put(player.getUniqueId(), calculate(player));
        // В чужую команду не вмешиваемся: её префикс/суффикс принадлежит другому плагину.
        if (current != null && !current.getName().startsWith(TEAM_PREFIX)) {
            return;
        }
        if (current != null && !current.getName().equals(teamName)) {
            current.removeEntry(player.getName());
            if (current.getEntries().isEmpty()) current.unregister();
        }

        Store.Entry prefix = active(player, Kind.PREFIX);
        Store.Entry title = active(player, Kind.TITLE);
        if (prefix == null && title == null) {
            remove(player);
            return;
        }
        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);
        team.prefix(prefix == null ? Component.empty() : prefix(prefix));
        team.suffix(title == null ? Component.empty() : title(title));
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }

    public void refreshAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            refresh(player);
        }
    }

    public void remove(Player player) {
        renderedNames.remove(player.getUniqueId());
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(teamName(player));
        if (team != null) {
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) team.unregister();
        }
    }

    public void cleanup() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) team.unregister();
        }
    }
}
