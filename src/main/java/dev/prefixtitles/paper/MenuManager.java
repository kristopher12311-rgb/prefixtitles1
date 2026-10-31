package dev.prefixtitles.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MenuManager implements Listener {

    enum View { MAIN, PLAYER_LIST, ADMIN_MAIN, ADMIN_LIST, EDIT, COLORS, GRANT, CUSTOM_EDIT, CUSTOM_COLORS, EMOJI }

    record Input(String prompt, Consumer<String> accept, Runnable cancel) {
    }

    static final class MenuHolder implements InventoryHolder {
        final View view;
        final Kind kind;
        final String id;
        final int colorSlot;
        final int page;
        boolean deleteArmed;
        Inventory inventory;

        MenuHolder(View view, Kind kind, String id, int colorSlot, int page) {
            this.view = view;
            this.kind = kind;
            this.id = id;
            this.colorSlot = colorSlot;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int PAGE_SIZE = 45;
    /** Слоты палитры 4×4 в меню цветов. */
    private static final int[] PALETTE_SLOTS = {11, 12, 13, 14, 20, 21, 22, 23, 29, 30, 31, 32, 38, 39, 40, 41};

    private final PrefixTitlesPlugin plugin;
    private final Map<UUID, Input> pending = new ConcurrentHashMap<>();

    public MenuManager(PrefixTitlesPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // ввод через чат
    // ------------------------------------------------------------------

    public void clearPending(Player player) {
        pending.remove(player.getUniqueId());
    }

    public void clearAllPending() {
        pending.clear();
    }

    public boolean cancelInput(Player player) {
        Input input = pending.remove(player.getUniqueId());
        if (input == null) return false;
        player.sendMessage(Component.text("Ввод отменён.", NamedTextColor.GRAY));
        input.cancel().run();
        return true;
    }

    private void prompt(Player player, String message, Consumer<String> accept, Runnable cancel) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Input(message, accept, cancel));
        player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    /** Перехватывает сообщение, если игрок сейчас вводит текст для меню. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPromptChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Input input = pending.get(player.getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.get(player.getUniqueId()) != input) return;
            pending.remove(player.getUniqueId());
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Ввод отменён.", NamedTextColor.GRAY));
                input.cancel().run();
                return;
            }
            input.accept().accept(text);
        });
    }

    // ------------------------------------------------------------------
    // вспомогательные методы
    // ------------------------------------------------------------------

    private Inventory make(Player player, View view, String title, int size, Kind kind, String id, int colorSlot, int page) {
        MenuHolder holder = new MenuHolder(view, kind, id, colorSlot, page);
        holder.inventory = Bukkit.createInventory(holder, size, Component.text(title));
        fill(holder.inventory);
        player.openInventory(holder.inventory);
        return holder.inventory;
    }

    private void fill(Inventory inventory) {
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, glass);
    }

    private ItemStack item(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Component line : lore) {
                lines.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(lines);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String label, NamedTextColor color, boolean glow, String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) lore.add(Component.text(line, NamedTextColor.GRAY));
        return item(material, Component.text(label, color == null ? NamedTextColor.WHITE : color), lore, glow);
    }

    private ItemStack button(Material material, String label, NamedTextColor color, String... lines) {
        return button(material, label, color, false, lines);
    }

    private void put(Inventory inventory, int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, stack);
    }

    private int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int paletteIndex(Integer rgb) {
        if (rgb == null) return -1;
        for (int i = 0; i < TextUtil.CONCRETE_RGB.length; i++) {
            if (TextUtil.CONCRETE_RGB[i] == rgb) return i;
        }
        return -1;
    }

    private Material concrete(int index) {
        return switch (index) {
            case 0 -> Material.WHITE_CONCRETE;
            case 1 -> Material.ORANGE_CONCRETE;
            case 2 -> Material.MAGENTA_CONCRETE;
            case 3 -> Material.LIGHT_BLUE_CONCRETE;
            case 4 -> Material.YELLOW_CONCRETE;
            case 5 -> Material.LIME_CONCRETE;
            case 6 -> Material.PINK_CONCRETE;
            case 7 -> Material.GRAY_CONCRETE;
            case 8 -> Material.LIGHT_GRAY_CONCRETE;
            case 9 -> Material.CYAN_CONCRETE;
            case 10 -> Material.PURPLE_CONCRETE;
            case 11 -> Material.BLUE_CONCRETE;
            case 12 -> Material.BROWN_CONCRETE;
            case 13 -> Material.GREEN_CONCRETE;
            case 14 -> Material.RED_CONCRETE;
            default -> Material.BLACK_CONCRETE;
        };
    }

    private boolean can(Player player, String node) {
        return player.hasPermission("prefixtitles.custom." + node);
    }

    private Component nickPreview(Player player, Kind kind, Store.Entry entry) {
        return plugin.names().preview(player, kind, entry);
    }

    private void send(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    private void changedAll() {
        plugin.store().save();
        plugin.names().refreshAll();
    }

    private void changedFor(Player player) {
        plugin.store().save();
        plugin.names().refresh(player);
    }

    private String hex(Integer rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    // ------------------------------------------------------------------
    // главное меню игрока
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        Store store = plugin.store();
        UUID uuid = player.getUniqueId();
        Inventory inv = make(player, View.MAIN, "Префиксы и титулы", 36, null, null, 0, 0);
        put(inv, 4, item(Material.PAPER, nickPreview(player, Kind.PREFIX, plugin.names().active(player, Kind.PREFIX)),
                List.of(Component.text("Ваш ник сейчас", NamedTextColor.GRAY)), false));
        put(inv, 11, button(Material.NAME_TAG, "Префиксы", NamedTextColor.GOLD, "Выбрать перед ником"));
        put(inv, 15, button(Material.WRITABLE_BOOK, "Титулы", NamedTextColor.AQUA, "Выбрать после ника"));
        if (player.hasPermission("prefixtitles.custom")) {
            for (Kind kind : Kind.values()) {
                Store.Entry custom = store.custom(uuid, kind);
                boolean has = custom != null && !custom.display.isBlank();
                boolean selected = Store.CUSTOM_ID.equals(store.selected(uuid, kind));
                String name = kind == Kind.PREFIX ? "Свой префикс" : "Свой титул";
                put(inv, kind == Kind.PREFIX ? 20 : 24,
                        button(kind == Kind.PREFIX ? Material.ANVIL : Material.ENCHANTED_BOOK, name, NamedTextColor.GREEN,
                                has && selected,
                                has ? "Сейчас: " + custom.display : "Ещё не создан",
                                "Свой текст, цвета, градиент, шрифты и эмодзи"));
            }
        }
        put(inv, 31, button(Material.BARRIER, "Снять всё", NamedTextColor.RED));
        if (store.isAdmin(player)) {
            put(inv, 35, button(Material.COMMAND_BLOCK, "Админ-панель", NamedTextColor.LIGHT_PURPLE,
                    "Создать, изменить, удалить и выдать"));
        }
    }

    private void clickMain(Player player, int slot) {
        Store store = plugin.store();
        switch (slot) {
            case 11 -> openPlayerList(player, Kind.PREFIX, 0);
            case 15 -> openPlayerList(player, Kind.TITLE, 0);
            case 20 -> {
                if (player.hasPermission("prefixtitles.custom")) openCustomEdit(player, Kind.PREFIX);
            }
            case 24 -> {
                if (player.hasPermission("prefixtitles.custom")) openCustomEdit(player, Kind.TITLE);
            }
            case 31 -> {
                store.setSelected(player.getUniqueId(), Kind.PREFIX, null);
                store.setSelected(player.getUniqueId(), Kind.TITLE, null);
                changedFor(player);
                openMain(player);
            }
            case 35 -> {
                if (store.isAdmin(player)) openAdminMain(player);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // список записей для игрока
    // ------------------------------------------------------------------

    public void openPlayerList(Player player, Kind kind, int page) {
        Store store = plugin.store();
        List<Store.Entry> entries = store.list(kind);
        int totalPages = pages(entries.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        Inventory inv = make(player, View.PLAYER_LIST, kind.plural, 54, kind, null, 0, page);
        String selected = store.selected(player.getUniqueId(), kind);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) break;
            Store.Entry entry = entries.get(index);
            boolean usable = store.canUse(player, kind, entry);
            boolean isSelected = entry.id.equals(selected);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Пример:", NamedTextColor.GRAY));
            lore.add(nickPreview(player, kind, entry));
            if (!usable) lore.add(Component.text("Недоступно — выдаётся администрацией", NamedTextColor.RED));
            else if (isSelected) lore.add(Component.text("Выбрано — нажмите, чтобы снять", NamedTextColor.GREEN));
            else lore.add(Component.text("Нажмите, чтобы выбрать", NamedTextColor.YELLOW));
            put(inv, i, item(usable ? Material.PAPER : Material.GRAY_DYE, TextUtil.render(entry), lore, isSelected));
        }
        if (page > 0) put(inv, 45, button(Material.ARROW, "← Назад", NamedTextColor.YELLOW));
        put(inv, 47, button(Material.BARRIER, "Снять " + kind.single.toLowerCase(Locale.ROOT), NamedTextColor.RED));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "В главное меню", NamedTextColor.GRAY));
        put(inv, 51, item(Material.PAPER, nickPreview(player, kind, plugin.names().active(player, kind)),
                List.of(Component.text("Ваш ник сейчас", NamedTextColor.GRAY)), false));
        if (page < totalPages - 1) put(inv, 53, button(Material.ARROW, "Вперёд →", NamedTextColor.YELLOW));
    }

    private void clickPlayerList(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        Kind kind = holder.kind;
        if (slot < PAGE_SIZE) {
            List<Store.Entry> entries = store.list(kind);
            int index = holder.page * PAGE_SIZE + slot;
            if (index >= entries.size()) return;
            Store.Entry entry = entries.get(index);
            if (!store.canUse(player, kind, entry)) {
                send(player, "Этот элемент вам не выдан.", NamedTextColor.RED);
                return;
            }
            boolean same = entry.id.equals(store.selected(player.getUniqueId(), kind));
            store.setSelected(player.getUniqueId(), kind, same ? null : entry.id);
            changedFor(player);
            openPlayerList(player, kind, holder.page);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (holder.page > 0) openPlayerList(player, kind, holder.page - 1);
            }
            case 47 -> {
                store.setSelected(player.getUniqueId(), kind, null);
                changedFor(player);
                openPlayerList(player, kind, holder.page);
            }
            case 49 -> openMain(player);
            case 53 -> {
                if (holder.page < pages(store.list(kind).size()) - 1) openPlayerList(player, kind, holder.page + 1);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // админ-панель
    // ------------------------------------------------------------------

    public void openAdminMain(Player player) {
        Store store = plugin.store();
        if (!store.isAdmin(player)) {
            send(player, "Нет прав.", NamedTextColor.RED);
            return;
        }
        Inventory inv = make(player, View.ADMIN_MAIN, "Админ-панель", 27, null, null, 0, 0);
        put(inv, 11, button(Material.NAME_TAG, "Префиксы", NamedTextColor.GOLD,
                "Всего: " + store.list(Kind.PREFIX).size()));
        put(inv, 15, button(Material.WRITABLE_BOOK, "Титулы", NamedTextColor.AQUA,
                "Всего: " + store.list(Kind.TITLE).size()));
        put(inv, 22, button(Material.DARK_OAK_DOOR, "Назад", NamedTextColor.GRAY));
    }

    private void clickAdminMain(Player player, int slot) {
        switch (slot) {
            case 11 -> openAdminList(player, Kind.PREFIX, 0);
            case 15 -> openAdminList(player, Kind.TITLE, 0);
            case 22 -> openMain(player);
            default -> {
            }
        }
    }

    public void openAdminList(Player player, Kind kind, int page) {
        Store store = plugin.store();
        if (!store.isAdmin(player)) return;
        List<Store.Entry> entries = store.list(kind);
        int totalPages = pages(entries.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        Inventory inv = make(player, View.ADMIN_LIST, "Админ: " + kind.plural, 54, kind, null, 0, page);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= entries.size()) break;
            Store.Entry entry = entries.get(index);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("ID: " + entry.id, NamedTextColor.GRAY));
            lore.add(Component.text(entry.restricted ? "Только выданным" : "Доступно всем",
                    entry.restricted ? NamedTextColor.RED : NamedTextColor.GREEN));
            lore.add(Component.text("Нажмите, чтобы редактировать", NamedTextColor.YELLOW));
            put(inv, i, item(Material.PAPER, TextUtil.render(entry), lore, false));
        }
        if (page > 0) put(inv, 45, button(Material.ARROW, "← Назад", NamedTextColor.YELLOW));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "К админ-панели", NamedTextColor.GRAY));
        put(inv, 51, button(Material.EMERALD, "+ Создать " + kind.single.toLowerCase(Locale.ROOT), NamedTextColor.GREEN,
                "Введите текст в чат"));
        if (page < totalPages - 1) put(inv, 53, button(Material.ARROW, "Вперёд →", NamedTextColor.YELLOW));
    }

    private void clickAdminList(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        Kind kind = holder.kind;
        if (slot < PAGE_SIZE) {
            List<Store.Entry> entries = store.list(kind);
            int index = holder.page * PAGE_SIZE + slot;
            if (index < entries.size()) openEdit(player, kind, entries.get(index).id);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (holder.page > 0) openAdminList(player, kind, holder.page - 1);
            }
            case 49 -> openAdminMain(player);
            case 51 -> askAdminNewText(player, kind, holder.page);
            case 53 -> {
                if (holder.page < pages(store.list(kind).size()) - 1) openAdminList(player, kind, holder.page + 1);
            }
            default -> {
            }
        }
    }

    private static final String ADMIN_HINT = "Отправьте текст одним сообщением. Поддерживаются &a, &c, &#ff8800 "
            + "и стили &l/&o/&n/&m. Для отмены напишите «отмена» или выполните /pt cancel.";

    private void askAdminNewText(Player player, Kind kind, int page) {
        prompt(player, ADMIN_HINT, text -> {
            String error = plugin.store().validate(text);
            if (error != null) {
                send(player, error, NamedTextColor.RED);
                askAdminNewText(player, kind, page);
                return;
            }
            Store.Entry entry = plugin.store().create(kind, text);
            changedAll();
            send(player, "Создано: " + entry.id, NamedTextColor.GREEN);
            openEdit(player, kind, entry.id);
        }, () -> openAdminList(player, kind, page));
    }

    // ------------------------------------------------------------------
    // редактор записи (админ)
    // ------------------------------------------------------------------

    public void openEdit(Player player, Kind kind, String id) {
        if (!plugin.store().isAdmin(player)) return;
        Store.Entry entry = plugin.store().find(kind, id);
        if (entry == null) {
            openAdminList(player, kind, 0);
            return;
        }
        Inventory inv = make(player, View.EDIT, "Редактор: " + id, 54, kind, id, 0, 0);
        renderEdit(player, (MenuHolder) inv.getHolder());
    }

    private ItemStack colorItem(String label, Integer rgb, String empty, boolean second) {
        Material material;
        int idx = paletteIndex(rgb);
        if (rgb == null) material = second ? Material.GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
        else material = idx >= 0 ? concrete(idx) : Material.PAPER;
        Component name = Component.text(label, rgb == null ? NamedTextColor.WHITE : TextColor.color(rgb));
        List<Component> lore = List.of(Component.text(rgb == null ? empty : hex(rgb), NamedTextColor.GRAY),
                Component.text("Нажмите, чтобы изменить", NamedTextColor.YELLOW));
        return item(material, name, lore, rgb != null);
    }

    private ItemStack style(Material material, String label, boolean on) {
        return button(material, label, on ? NamedTextColor.GREEN : NamedTextColor.GRAY, on,
                on ? "Включено — нажмите для отключения" : "Выключено — нажмите для включения");
    }

    private ItemStack fontItem(Store.Entry entry) {
        Fonts.Font current = Fonts.byId(entry.font);
        List<Component> lore = new ArrayList<>();
        for (Fonts.Font font : Fonts.ALL) {
            boolean active = font.equals(current);
            lore.add(Component.text((active ? "▶ " : "   ") + font.name(), active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        lore.add(Component.text("Нажмите, чтобы переключить", NamedTextColor.YELLOW));
        return item(Material.BOOK, Component.text("Шрифт", NamedTextColor.WHITE), lore, current.id() != null);
    }

    private void renderEdit(Player player, MenuHolder holder) {
        Store.Entry entry = plugin.store().find(holder.kind, holder.id);
        if (entry == null) return;
        Inventory inv = holder.inventory;
        fill(inv);
        put(inv, 4, item(Material.PAPER, nickPreview(player, holder.kind, entry),
                List.of(Component.text("Пример ника", NamedTextColor.GRAY)), false));
        put(inv, 10, button(Material.ANVIL, "Изменить текст", NamedTextColor.GOLD,
                "Текущее: " + TextUtil.strip(entry.display), "Введите новый текст в чат"));
        put(inv, 12, colorItem("Цвет текста", entry.color, "По умолчанию", false));
        put(inv, 13, colorItem("Градиент: второй цвет", entry.color2, "Выключен", true));
        put(inv, 14, button(Material.BARRIER, "Сбросить цвета", NamedTextColor.RED));
        put(inv, 15, fontItem(entry));
        put(inv, 20, style(Material.NETHERITE_INGOT, "Жирный", entry.bold));
        put(inv, 21, style(Material.FEATHER, "Курсив", entry.italic));
        put(inv, 22, style(Material.STRING, "Подчёркнутый", entry.underlined));
        put(inv, 23, style(Material.SHEARS, "Зачёркнутый", entry.strikethrough));
        put(inv, 24, style(Material.ENDER_EYE, "Мерцающий", entry.obfuscated));
        put(inv, 29, button(entry.restricted ? Material.RED_DYE : Material.LIME_DYE,
                entry.restricted ? "Доступ: только выданным" : "Доступ: всем",
                entry.restricted ? NamedTextColor.RED : NamedTextColor.GREEN,
                "Право LuckPerms: prefixtitles.use." + holder.kind.key + "." + entry.id));
        put(inv, 31, button(Material.PLAYER_HEAD, "Выдать игрокам", NamedTextColor.AQUA, "Игроки онлайн"));
        put(inv, 33, button(Material.TNT, "Удалить", NamedTextColor.RED,
                holder.deleteArmed ? "Нажмите ещё раз для удаления" : "Двойное нажатие для подтверждения"));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "Назад к списку", NamedTextColor.GRAY));
    }

    private void clickEdit(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        if (!store.isAdmin(player)) return;
        Kind kind = holder.kind;
        Store.Entry entry = store.find(kind, holder.id);
        if (entry == null) {
            openAdminList(player, kind, 0);
            return;
        }
        boolean rerender = true;
        switch (slot) {
            case 10 -> {
                rerender = false;
                askAdminEditText(player, kind, entry.id);
            }
            case 12 -> {
                rerender = false;
                openColors(player, kind, entry.id, 0);
            }
            case 13 -> {
                rerender = false;
                openColors(player, kind, entry.id, 1);
            }
            case 14 -> {
                entry.color = null;
                entry.color2 = null;
            }
            case 15 -> entry.font = Fonts.next(entry.font).id();
            case 20 -> entry.bold = !entry.bold;
            case 21 -> entry.italic = !entry.italic;
            case 22 -> entry.underlined = !entry.underlined;
            case 23 -> entry.strikethrough = !entry.strikethrough;
            case 24 -> entry.obfuscated = !entry.obfuscated;
            case 29 -> entry.restricted = !entry.restricted;
            case 31 -> {
                rerender = false;
                openGrant(player, kind, entry.id, 0);
            }
            case 33 -> {
                if (!holder.deleteArmed) {
                    holder.deleteArmed = true;
                } else {
                    store.delete(kind, entry.id);
                    changedAll();
                    send(player, "Удалено: " + entry.id, NamedTextColor.YELLOW);
                    openAdminList(player, kind, 0);
                    return;
                }
            }
            case 49 -> {
                rerender = false;
                openAdminList(player, kind, 0);
            }
            default -> rerender = false;
        }
        if (slot != 33) holder.deleteArmed = false;
        if (rerender) {
            changedAll();
            renderEdit(player, holder);
        }
    }

    private void askAdminEditText(Player player, Kind kind, String id) {
        prompt(player, ADMIN_HINT, text -> {
            Store.Entry entry = plugin.store().find(kind, id);
            if (entry == null) {
                openAdminList(player, kind, 0);
                return;
            }
            String error = plugin.store().validate(text);
            if (error != null) {
                send(player, error, NamedTextColor.RED);
                askAdminEditText(player, kind, id);
                return;
            }
            entry.display = text;
            changedAll();
            openEdit(player, kind, id);
        }, () -> openEdit(player, kind, id));
    }

    // ------------------------------------------------------------------
    // выбор цвета (общий для админа и игрока)
    // ------------------------------------------------------------------

    /** Редактируемая запись для меню цветов: из общего списка или своя у игрока. */
    private Store.Entry colorTarget(Player player, MenuHolder holder) {
        if (holder.view == View.CUSTOM_COLORS) return plugin.store().custom(player.getUniqueId(), holder.kind);
        return plugin.store().find(holder.kind, holder.id);
    }

    public void openColors(Player player, Kind kind, String id, int colorSlot) {
        Inventory inv = make(player, View.COLORS, colorSlot == 0 ? "Цвет текста" : "Градиент: второй цвет", 54, kind, id,
                colorSlot, 0);
        renderColors(player, (MenuHolder) inv.getHolder());
    }

    public void openCustomColors(Player player, Kind kind, int colorSlot) {
        Inventory inv = make(player, View.CUSTOM_COLORS, colorSlot == 0 ? "Цвет текста" : "Градиент: второй цвет", 54,
                kind, Store.CUSTOM_ID, colorSlot, 0);
        renderColors(player, (MenuHolder) inv.getHolder());
    }

    private void renderColors(Player player, MenuHolder holder) {
        Store.Entry entry = colorTarget(player, holder);
        if (entry == null) return;
        Inventory inv = holder.inventory;
        fill(inv);
        put(inv, 4, item(Material.PAPER, nickPreview(player, holder.kind, entry),
                List.of(Component.text("Пример ника", NamedTextColor.GRAY)), false));
        Integer current = holder.colorSlot == 0 ? entry.color : entry.color2;
        int selectedIndex = paletteIndex(current);
        for (int i = 0; i < PALETTE_SLOTS.length; i++) {
            boolean selected = i == selectedIndex;
            put(inv, PALETTE_SLOTS[i], item(concrete(i),
                    Component.text(TextUtil.CONCRETE_NAMES[i], TextColor.color(TextUtil.CONCRETE_RGB[i])),
                    List.of(Component.text(hex(TextUtil.CONCRETE_RGB[i]), NamedTextColor.GRAY),
                            selected ? Component.text("Выбран", NamedTextColor.GREEN)
                                    : Component.text("Нажмите, чтобы выбрать", NamedTextColor.YELLOW)),
                    selected));
        }
        put(inv, 45, button(Material.BARRIER, holder.colorSlot == 0 ? "Без цвета" : "Выключить градиент", NamedTextColor.RED));
        boolean hexAllowed = holder.view == View.COLORS || can(player, "hex");
        if (hexAllowed) {
            put(inv, 47, button(Material.AMETHYST_SHARD, "Свой HEX-цвет", NamedTextColor.LIGHT_PURPLE,
                    current == null ? "Любой цвет в формате #RRGGBB" : "Сейчас: " + hex(current),
                    "Нажмите и введите код в чат"));
        }
        put(inv, 49, button(Material.DARK_OAK_DOOR, "Назад", NamedTextColor.GRAY));
    }

    private void clickColors(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        boolean custom = holder.view == View.CUSTOM_COLORS;
        if (!custom && !store.isAdmin(player)) return;
        if (custom && !player.hasPermission("prefixtitles.custom")) return;
        Store.Entry entry = colorTarget(player, holder);
        if (entry == null) {
            openMain(player);
            return;
        }
        if (slot == 49) {
            if (custom) openCustomEdit(player, holder.kind);
            else openEdit(player, holder.kind, holder.id);
            return;
        }
        if (slot == 45) {
            if (holder.colorSlot == 0) entry.color = null;
            else entry.color2 = null;
            applyColorChange(player, holder, custom);
            return;
        }
        if (slot == 47) {
            if (custom && !can(player, "hex")) return;
            askHex(player, holder);
            return;
        }
        for (int i = 0; i < PALETTE_SLOTS.length; i++) {
            if (PALETTE_SLOTS[i] == slot) {
                if (holder.colorSlot == 0) entry.color = TextUtil.CONCRETE_RGB[i];
                else entry.color2 = TextUtil.CONCRETE_RGB[i];
                applyColorChange(player, holder, custom);
                return;
            }
        }
    }

    private void applyColorChange(Player player, MenuHolder holder, boolean custom) {
        if (custom) changedFor(player);
        else changedAll();
        renderColors(player, holder);
    }

    private void askHex(Player player, MenuHolder holder) {
        boolean custom = holder.view == View.CUSTOM_COLORS;
        Kind kind = holder.kind;
        String id = holder.id;
        int colorSlot = holder.colorSlot;
        Runnable back = () -> {
            if (custom) openCustomColors(player, kind, colorSlot);
            else openColors(player, kind, id, colorSlot);
        };
        prompt(player, "Отправьте цвет в формате #RRGGBB, например #ff8800. Для отмены — «отмена» или /pt cancel.",
                text -> {
                    Integer rgb = TextUtil.parseHex(text);
                    if (rgb == null) {
                        send(player, "Не похоже на цвет. Пример: #ff8800", NamedTextColor.RED);
                        askHex(player, holder);
                        return;
                    }
                    Store.Entry entry = custom ? plugin.store().custom(player.getUniqueId(), kind) : plugin.store().find(kind, id);
                    if (entry == null) {
                        openMain(player);
                        return;
                    }
                    if (colorSlot == 0) entry.color = rgb;
                    else entry.color2 = rgb;
                    if (custom) changedFor(player);
                    else changedAll();
                    back.run();
                }, back);
    }

    // ------------------------------------------------------------------
    // выдача записи игрокам (админ)
    // ------------------------------------------------------------------

    public void openGrant(Player player, Kind kind, String id, int page) {
        Store store = plugin.store();
        if (!store.isAdmin(player)) return;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int totalPages = pages(players.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        Inventory inv = make(player, View.GRANT, "Выдать: " + id, 54, kind, id, 0, page);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= players.size()) break;
            Player target = players.get(index);
            boolean granted = store.isGranted(target.getUniqueId(), kind, id);
            ItemStack head = item(Material.PLAYER_HEAD, Component.text(target.getName(), granted ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    List.of(Component.text(granted ? "Выдано — нажмите, чтобы забрать" : "Нажмите, чтобы выдать",
                            granted ? NamedTextColor.GREEN : NamedTextColor.YELLOW)), granted);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer((OfflinePlayer) target);
                head.setItemMeta(skull);
            }
            put(inv, i, head);
        }
        if (page > 0) put(inv, 45, button(Material.ARROW, "← Назад", NamedTextColor.YELLOW));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "Назад к записи", NamedTextColor.GRAY));
        if (page < totalPages - 1) put(inv, 53, button(Material.ARROW, "Вперёд →", NamedTextColor.YELLOW));
    }

    private void clickGrant(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        if (!store.isAdmin(player)) return;
        if (slot < PAGE_SIZE) {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            int index = holder.page * PAGE_SIZE + slot;
            if (index >= players.size()) return;
            Player target = players.get(index);
            boolean granted = store.isGranted(target.getUniqueId(), holder.kind, holder.id);
            store.setGranted(target.getUniqueId(), holder.kind, holder.id, !granted);
            store.save();
            plugin.names().refresh(target);
            target.sendMessage(Component.text((granted ? "Доступ отозван: " : "Вам выдан доступ: ") + holder.id,
                    granted ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            openGrant(player, holder.kind, holder.id, holder.page);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (holder.page > 0) openGrant(player, holder.kind, holder.id, holder.page - 1);
            }
            case 49 -> openEdit(player, holder.kind, holder.id);
            case 53 -> openGrant(player, holder.kind, holder.id, holder.page + 1);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // СВОИ префикс и титул игрока
    // ------------------------------------------------------------------

    public void openCustomEdit(Player player, Kind kind) {
        if (!player.hasPermission("prefixtitles.custom")) {
            send(player, "Нет прав: prefixtitles.custom", NamedTextColor.RED);
            return;
        }
        plugin.store().ensureCustom(player.getUniqueId(), kind);
        Inventory inv = make(player, View.CUSTOM_EDIT, "Свой " + kind.single.toLowerCase(Locale.ROOT), 54, kind,
                Store.CUSTOM_ID, 0, 0);
        renderCustomEdit(player, (MenuHolder) inv.getHolder());
    }

    private ItemStack locked(String label, String node) {
        return button(Material.GRAY_STAINED_GLASS_PANE, label, NamedTextColor.DARK_GRAY,
                "Недоступно", "Нужно право prefixtitles.custom." + node);
    }

    private ItemStack customStyle(Player player, Material material, String label, String node, boolean on) {
        return can(player, node) ? style(material, label, on) : locked(label, node);
    }

    private void renderCustomEdit(Player player, MenuHolder holder) {
        Store store = plugin.store();
        UUID uuid = player.getUniqueId();
        Kind kind = holder.kind;
        Store.Entry entry = store.ensureCustom(uuid, kind);
        Inventory inv = holder.inventory;
        fill(inv);
        boolean hasText = !entry.display.isBlank();
        Component preview = hasText ? nickPreview(player, kind, entry) : Component.text(player.getName());
        put(inv, 4, item(Material.PAPER, preview, List.of(Component.text("Так выглядит ваш ник", NamedTextColor.GRAY)), false));

        int max = store.settings().customMaxLength;
        put(inv, 10, button(Material.ANVIL, "Изменить текст", NamedTextColor.GOLD,
                hasText ? "Сейчас: " + entry.display : "Текст ещё не задан",
                "Введите текст в чат (до " + max + " символов)"));
        if (can(player, "emoji")) {
            put(inv, 11, button(Material.NETHER_STAR, "Эмодзи и символы", NamedTextColor.LIGHT_PURPLE,
                    "Только те, что показывает Minecraft", "Добавляются в конец текста"));
        } else {
            put(inv, 11, locked("Эмодзи и символы", "emoji"));
        }

        if (can(player, "color")) {
            put(inv, 12, colorItem("Цвет текста", entry.color, "По умолчанию", false));
        } else {
            put(inv, 12, locked("Цвет текста", "color"));
        }
        if (can(player, "gradient")) {
            ItemStack gradient = colorItem("Градиент: второй цвет", entry.color2, "Выключен", true);
            put(inv, 13, gradient);
        } else {
            put(inv, 13, locked("Градиент", "gradient"));
        }
        put(inv, 14, button(Material.BARRIER, "Сбросить цвета", NamedTextColor.RED));
        if (can(player, "font")) put(inv, 15, fontItem(entry));
        else put(inv, 15, locked("Шрифт", "font"));

        put(inv, 20, customStyle(player, Material.NETHERITE_INGOT, "Жирный", "bold", entry.bold));
        put(inv, 21, customStyle(player, Material.FEATHER, "Курсив", "italic", entry.italic));
        put(inv, 22, customStyle(player, Material.STRING, "Подчёркнутый", "underline", entry.underlined));
        put(inv, 23, customStyle(player, Material.SHEARS, "Зачёркнутый", "strikethrough", entry.strikethrough));
        put(inv, 24, customStyle(player, Material.ENDER_EYE, "Мерцающий", "obfuscated", entry.obfuscated));

        boolean selected = Store.CUSTOM_ID.equals(store.selected(uuid, kind));
        put(inv, 29, button(selected ? Material.LIME_DYE : Material.GRAY_DYE,
                selected ? "Используется" : "Не используется",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY, selected,
                selected ? "Нажмите, чтобы снять" : "Нажмите, чтобы надеть"));
        put(inv, 33, button(Material.TNT, "Удалить свой " + kind.single.toLowerCase(Locale.ROOT), NamedTextColor.RED,
                holder.deleteArmed ? "Нажмите ещё раз для удаления" : "Двойное нажатие для подтверждения"));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "В главное меню", NamedTextColor.GRAY));
    }

    private void clickCustomEdit(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        if (!player.hasPermission("prefixtitles.custom")) return;
        UUID uuid = player.getUniqueId();
        Kind kind = holder.kind;
        Store.Entry entry = store.ensureCustom(uuid, kind);
        boolean changed = false;
        switch (slot) {
            case 10 -> {
                askCustomText(player, kind);
                return;
            }
            case 11 -> {
                if (can(player, "emoji")) openEmoji(player, kind, 0);
                else deny(player, "emoji");
                return;
            }
            case 12 -> {
                if (can(player, "color")) openCustomColors(player, kind, 0);
                else deny(player, "color");
                return;
            }
            case 13 -> {
                if (can(player, "gradient")) openCustomColors(player, kind, 1);
                else deny(player, "gradient");
                return;
            }
            case 14 -> {
                entry.color = null;
                entry.color2 = null;
                changed = true;
            }
            case 15 -> {
                if (!can(player, "font")) {
                    deny(player, "font");
                    return;
                }
                changed = cycleFont(player, entry);
            }
            case 20 -> {
                if (!can(player, "bold")) {
                    deny(player, "bold");
                    return;
                }
                entry.bold = !entry.bold;
                changed = true;
            }
            case 21 -> {
                if (!can(player, "italic")) {
                    deny(player, "italic");
                    return;
                }
                entry.italic = !entry.italic;
                changed = true;
            }
            case 22 -> {
                if (!can(player, "underline")) {
                    deny(player, "underline");
                    return;
                }
                entry.underlined = !entry.underlined;
                changed = true;
            }
            case 23 -> {
                if (!can(player, "strikethrough")) {
                    deny(player, "strikethrough");
                    return;
                }
                entry.strikethrough = !entry.strikethrough;
                changed = true;
            }
            case 24 -> {
                if (!can(player, "obfuscated")) {
                    deny(player, "obfuscated");
                    return;
                }
                entry.obfuscated = !entry.obfuscated;
                changed = true;
            }
            case 29 -> {
                boolean selected = Store.CUSTOM_ID.equals(store.selected(uuid, kind));
                if (!selected && entry.display.isBlank()) {
                    send(player, "Сначала задайте текст.", NamedTextColor.RED);
                    return;
                }
                store.setSelected(uuid, kind, selected ? null : Store.CUSTOM_ID);
                changed = true;
            }
            case 33 -> {
                if (!holder.deleteArmed) {
                    holder.deleteArmed = true;
                    renderCustomEdit(player, holder);
                } else {
                    store.clearCustom(uuid, kind);
                    changedFor(player);
                    send(player, "Ваш " + kind.single.toLowerCase(Locale.ROOT) + " удалён.", NamedTextColor.YELLOW);
                    openMain(player);
                }
                return;
            }
            case 49 -> {
                openMain(player);
                return;
            }
            default -> {
                return;
            }
        }
        holder.deleteArmed = false;
        if (changed) changedFor(player);
        renderCustomEdit(player, holder);
    }

    private void deny(Player player, String node) {
        send(player, "Нет права: prefixtitles.custom." + node, NamedTextColor.RED);
    }

    /** Переключает шрифт на следующий подходящий для текущего текста. */
    private boolean cycleFont(Player player, Store.Entry entry) {
        Fonts.Font candidate = Fonts.byId(entry.font);
        String lastError = null;
        for (int i = 0; i < Fonts.ALL.size() - 1; i++) {
            candidate = Fonts.next(candidate.id());
            if (candidate.id() == null || entry.display.isBlank()) {
                entry.font = candidate.id();
                return true;
            }
            String error = CustomText.validate(plugin.store(), player, entry.display, candidate.id());
            if (error == null) {
                entry.font = candidate.id();
                return true;
            }
            lastError = error;
        }
        if (lastError != null) send(player, lastError, NamedTextColor.RED);
        return false;
    }

    private void askCustomText(Player player, Kind kind) {
        int max = plugin.store().settings().customMaxLength;
        prompt(player, "Отправьте текст для вашего " + kind.single.toLowerCase(Locale.ROOT) + "а одним сообщением (до "
                        + max + " символов). Цвета и стили настраиваются в меню. Для отмены — «отмена» или /pt cancel.",
                text -> {
                    Store store = plugin.store();
                    if (!player.hasPermission("prefixtitles.custom")) return;
                    String clean = CustomText.sanitize(text);
                    Store.Entry entry = store.ensureCustom(player.getUniqueId(), kind);
                    String error = CustomText.validate(store, player, clean, entry.font);
                    if (error != null) {
                        send(player, error, NamedTextColor.RED);
                        askCustomText(player, kind);
                        return;
                    }
                    entry.display = clean;
                    entry.plain = true;
                    store.setSelected(player.getUniqueId(), kind, Store.CUSTOM_ID);
                    changedFor(player);
                    plugin.getLogger().info(player.getName() + " set custom " + kind.key + ": " + clean);
                    openCustomEdit(player, kind);
                }, () -> openCustomEdit(player, kind));
    }

    // ------------------------------------------------------------------
    // выбор эмодзи
    // ------------------------------------------------------------------

    public void openEmoji(Player player, Kind kind, int page) {
        int totalPages = pages(Emoji.LIST.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        Store.Entry entry = plugin.store().ensureCustom(player.getUniqueId(), kind);
        Inventory inv = make(player, View.EMOJI, "Эмодзи и символы", 54, kind, Store.CUSTOM_ID, 0, page);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= Emoji.LIST.size()) break;
            String symbol = Emoji.LIST.get(index);
            put(inv, i, item(Material.PAPER, Component.text(symbol, NamedTextColor.WHITE),
                    List.of(Component.text("Нажмите, чтобы добавить в конец", NamedTextColor.YELLOW)), false));
        }
        if (page > 0) put(inv, 45, button(Material.ARROW, "← Назад", NamedTextColor.YELLOW));
        put(inv, 47, button(Material.RED_DYE, "Стереть последний символ", NamedTextColor.RED));
        put(inv, 49, button(Material.DARK_OAK_DOOR, "Назад к редактору", NamedTextColor.GRAY));
        boolean hasText = !entry.display.isBlank();
        put(inv, 51, item(Material.PAPER, hasText ? nickPreview(player, kind, entry) : Component.text(player.getName()),
                List.of(Component.text("Текст: " + (hasText ? entry.display : "(пусто)"), NamedTextColor.GRAY)), false));
        if (page < totalPages - 1) put(inv, 53, button(Material.ARROW, "Вперёд →", NamedTextColor.YELLOW));
    }

    private void clickEmoji(Player player, MenuHolder holder, int slot) {
        Store store = plugin.store();
        if (!player.hasPermission("prefixtitles.custom") || !can(player, "emoji")) return;
        Kind kind = holder.kind;
        Store.Entry entry = store.ensureCustom(player.getUniqueId(), kind);
        if (slot < PAGE_SIZE) {
            int index = holder.page * PAGE_SIZE + slot;
            if (index >= Emoji.LIST.size()) return;
            String text = entry.display + Emoji.LIST.get(index);
            String error = CustomText.validate(store, player, text, entry.font);
            if (error != null) {
                send(player, error, NamedTextColor.RED);
                return;
            }
            entry.display = text;
            entry.plain = true;
            store.setSelected(player.getUniqueId(), kind, Store.CUSTOM_ID);
            changedFor(player);
            openEmoji(player, kind, holder.page);
            return;
        }
        switch (slot) {
            case 45 -> {
                if (holder.page > 0) openEmoji(player, kind, holder.page - 1);
            }
            case 47 -> {
                if (!entry.display.isEmpty()) {
                    int end = entry.display.offsetByCodePoints(entry.display.length(), -1);
                    entry.display = entry.display.substring(0, end);
                    if (entry.display.isBlank()) {
                        entry.display = "";
                        if (Store.CUSTOM_ID.equals(store.selected(player.getUniqueId(), kind))) {
                            store.setSelected(player.getUniqueId(), kind, null);
                        }
                    }
                    changedFor(player);
                    openEmoji(player, kind, holder.page);
                }
            }
            case 49 -> openCustomEdit(player, kind);
            case 53 -> {
                if (holder.page < pages(Emoji.LIST.size()) - 1) openEmoji(player, kind, holder.page + 1);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // события инвентаря
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getRawSlot();
        switch (holder.view) {
            case MAIN -> clickMain(player, slot);
            case PLAYER_LIST -> clickPlayerList(player, holder, slot);
            case ADMIN_MAIN -> clickAdminMain(player, slot);
            case ADMIN_LIST -> clickAdminList(player, holder, slot);
            case EDIT -> clickEdit(player, holder, slot);
            case COLORS, CUSTOM_COLORS -> clickColors(player, holder, slot);
            case GRANT -> clickGrant(player, holder, slot);
            case CUSTOM_EDIT -> clickCustomEdit(player, holder, slot);
            case EMOJI -> clickEmoji(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
}
