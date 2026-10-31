package dev.prefixtitles.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrefixTitlesPlugin extends JavaPlugin implements Listener {

    private Store store;
    private NameManager names;
    private MenuManager menus;

    @Override
    public void onEnable() {
        store = new Store(this);
        store.load();
        names = new NameManager(this);
        menus = new MenuManager(this);
        names.cleanup();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menus, this);

        PtCommand command = new PtCommand(this);
        PluginCommand pt = getCommand("pt");
        if (pt == null) {
            getLogger().severe("Missing /pt in plugin.yml, disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pt.setExecutor(command);
        pt.setTabCompleter(command);

        Bukkit.getScheduler().runTask(this, names::refreshAll);
        getLogger().info("PrefixTitles loaded: " + store.list(Kind.PREFIX).size() + " prefixes, "
                + store.list(Kind.TITLE).size() + " titles.");
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.clearAllPending();
        if (names != null) names.cleanup();
        if (store != null) store.save();
    }

    public Store store() {
        return store;
    }

    public NameManager names() {
        return names;
    }

    public MenuManager menus() {
        return menus;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> names.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.clearPending(event.getPlayer());
        names.remove(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
                names.decoratedName(source).append(Component.text(": ")).append(message));
    }
}
