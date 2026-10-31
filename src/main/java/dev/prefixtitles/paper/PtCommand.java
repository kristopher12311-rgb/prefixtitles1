package dev.prefixtitles.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PtCommand implements CommandExecutor, TabCompleter {

    private final PrefixTitlesPlugin plugin;

    public PtCommand(PrefixTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.menus().openMain(player);
            } else {
                sender.sendMessage("Open /pt from in-game. Console can use grant, revoke, resetcustom and reload.");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "cancel" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player-only command.");
                    return true;
                }
                if (!plugin.menus().cancelInput(player)) {
                    player.sendMessage(Component.text("Сейчас нет активного ввода.", NamedTextColor.GRAY));
                }
            }
            case "admin" -> {
                if (!admin(sender)) return true;
                if (sender instanceof Player player) plugin.menus().openAdminMain(player);
                else sender.sendMessage("Admin menu is available in-game.");
            }
            case "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Use /pt clear in-game.");
                    return true;
                }
                plugin.store().setSelected(player.getUniqueId(), Kind.PREFIX, null);
                plugin.store().setSelected(player.getUniqueId(), Kind.TITLE, null);
                plugin.store().save();
                plugin.names().refresh(player);
                player.sendMessage(Component.text("Префикс и титул сняты.", NamedTextColor.GREEN));
            }
            case "custom" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player-only command.");
                    return true;
                }
                if (!player.hasPermission("prefixtitles.custom")) {
                    player.sendMessage(Component.text("Нет прав: prefixtitles.custom", NamedTextColor.RED));
                    return true;
                }
                Kind kind = args.length > 1 ? Kind.byKey(args[1]) : null;
                if (kind == null) plugin.menus().openMain(player);
                else plugin.menus().openCustomEdit(player, kind);
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.store().load();
                plugin.names().refreshAll();
                sender.sendMessage(Component.text("Данные PrefixTitles перезагружены.", NamedTextColor.GREEN));
            }
            case "grant", "revoke" -> {
                if (!admin(sender)) return true;
                boolean grant = args[0].equalsIgnoreCase("grant");
                if (args.length < 4) {
                    sender.sendMessage("Usage: /pt " + args[0].toLowerCase(Locale.ROOT) + " <player> <prefix|title> <id>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок должен быть в сети.", NamedTextColor.RED));
                    return true;
                }
                Kind kind = Kind.byKey(args[2]);
                if (kind == null) {
                    sender.sendMessage("Type must be prefix or title.");
                    return true;
                }
                Store.Entry entry = plugin.store().find(kind, args[3]);
                if (entry == null) {
                    sender.sendMessage("No entry with ID " + args[3]);
                    return true;
                }
                plugin.store().setGranted(target.getUniqueId(), kind, entry.id, grant);
                plugin.store().save();
                plugin.names().refresh(target);
                sender.sendMessage((grant ? "Granted " : "Revoked ") + entry.id + (grant ? " to " : " from ") + target.getName());
                if (grant) {
                    target.sendMessage(Component.text("Вам выдан доступ к записи: " + entry.id + ". Откройте /pt",
                            NamedTextColor.GREEN));
                }
            }
            case "resetcustom" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("Usage: /pt resetcustom <player> [prefix|title|all]");
                    return true;
                }
                UUID uuid = resolve(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                    return true;
                }
                String what = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "all";
                Kind only = Kind.byKey(what);
                if (only == null && !what.equals("all")) {
                    sender.sendMessage("Type must be prefix, title or all.");
                    return true;
                }
                for (Kind kind : Kind.values()) {
                    if (only == null || only == kind) plugin.store().clearCustom(uuid, kind);
                }
                plugin.store().save();
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    plugin.names().refresh(online);
                    online.sendMessage(Component.text("Администратор сбросил ваш собственный префикс/титул.",
                            NamedTextColor.YELLOW));
                }
                sender.sendMessage("Custom entries reset for " + args[1] + " (" + what + ").");
            }
            case "help" -> {
                sender.sendMessage("/pt — меню; /pt custom [prefix|title] — свой префикс/титул; /pt clear — снять всё.");
                if (sender.hasPermission("prefixtitles.admin")) {
                    sender.sendMessage("/pt admin, grant, revoke, resetcustom, reload");
                }
            }
            default -> sender.sendMessage(Component.text("Неизвестная команда. Используйте /pt help.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("prefixtitles.admin")) return true;
        sender.sendMessage(Component.text("Нет прав: prefixtitles.admin", NamedTextColor.RED));
        return false;
    }

    @SuppressWarnings("deprecation")
    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        var offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("prefixtitles.admin");
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("clear", "cancel", "help", "custom"));
            if (admin) options.addAll(List.of("admin", "grant", "revoke", "resetcustom", "reload"));
            return match(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("custom") && args.length == 2) return match(List.of("prefix", "title"), args[1]);
        if (!admin) return List.of();
        boolean grantLike = sub.equals("grant") || sub.equals("revoke");
        if ((grantLike || sub.equals("resetcustom")) && args.length == 2) {
            return match(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (sub.equals("resetcustom") && args.length == 3) return match(List.of("prefix", "title", "all"), args[2]);
        if (grantLike && args.length == 3) return match(List.of("prefix", "title"), args[2]);
        if (grantLike && args.length == 4) {
            Kind kind = Kind.byKey(args[2]);
            if (kind == null) return List.of();
            return match(plugin.store().list(kind).stream().map(entry -> entry.id).toList(), args[3]);
        }
        return List.of();
    }

    private List<String> match(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
