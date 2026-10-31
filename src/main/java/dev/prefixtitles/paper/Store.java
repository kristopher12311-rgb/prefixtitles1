package dev.prefixtitles.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Хранилище данных (prefixtitles.json). Формат совместим с прежней версией плагина. */
public final class Store {

    /** Специальный id: «свой» префикс/титул игрока, а не запись из общего списка. */
    public static final String CUSTOM_ID = "~custom";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Entry {
        public String id;
        public String display;
        public boolean restricted;
        public Integer color;
        public Integer color2;
        public boolean bold;
        public boolean italic;
        public boolean underlined;
        public boolean strikethrough;
        public boolean obfuscated;
        /** id шрифта, например minecraft:uniform; null — обычный. */
        public String font;
        /** true — текст выводится как есть, без кодов &a / &#rrggbb (записи игроков). */
        public boolean plain;
    }

    public static final class PlayerData {
        public String prefix;
        public String title;
        public Set<String> grantedPrefixes = new HashSet<>();
        public Set<String> grantedTitles = new HashSet<>();
        /** Собственные префикс и титул игрока. */
        public Entry customPrefix;
        public Entry customTitle;
    }

    public static final class Root {
        public int nextId = 1;
        public int maxLength = 32;
        /** Максимальная длина собственного префикса/титула (видимых символов). */
        public int customMaxLength = 16;
        public String prefixSpacer = " ";
        public String titleSpacer = " ";
        /** Запрещённые слова для собственных префиксов/титулов (проверка без регистра и пробелов). */
        public List<String> blockedWords = new ArrayList<>(List.of(
                "admin", "owner", "moderator", "staff", "админ", "модер", "владелец", "оператор"));
        public List<Entry> prefixes = new ArrayList<>();
        public List<Entry> titles = new ArrayList<>();
        public Map<String, PlayerData> players = new HashMap<>();
    }

    private final PrefixTitlesPlugin plugin;
    private final Path file;
    private Root root = new Root();

    public Store(PrefixTitlesPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("prefixtitles.json");
    }

    public synchronized void load() {
        try {
            if (!Files.exists(file)) {
                root = new Root();
                save();
                return;
            }
            Root loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Root.class);
            root = loaded == null ? new Root() : loaded;
            if (root.prefixes == null) root.prefixes = new ArrayList<>();
            if (root.titles == null) root.titles = new ArrayList<>();
            if (root.players == null) root.players = new HashMap<>();
            if (root.prefixSpacer == null) root.prefixSpacer = " ";
            if (root.titleSpacer == null) root.titleSpacer = " ";
            if (root.maxLength < 1) root.maxLength = 32;
            if (root.customMaxLength < 1) root.customMaxLength = 16;
            if (root.blockedWords == null) root.blockedWords = new ArrayList<>();
            if (root.nextId < 1) root.nextId = 1;
            root.prefixes.removeIf(Objects::isNull);
            root.titles.removeIf(Objects::isNull);
            root.prefixes.forEach(Store::normalizeEntry);
            root.titles.forEach(Store::normalizeEntry);
            // Повреждённые/неполные записи отдельных игроков не должны ломать загрузку всего файла.
            root.players.values().removeIf(Objects::isNull);
            root.players.values().forEach(Store::normalizePlayer);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read " + file + "; keeping current data", ex);
        }
    }

    private static void normalizeEntry(Entry entry) {
        if (entry == null) return;
        if (entry.id == null) entry.id = "unknown";
        if (entry.display == null) entry.display = "";
    }

    private static void normalizePlayer(PlayerData data) {
        if (data.grantedPrefixes == null) data.grantedPrefixes = new HashSet<>();
        if (data.grantedTitles == null) data.grantedTitles = new HashSet<>();
        normalizeEntry(data.customPrefix);
        normalizeEntry(data.customTitle);
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + file, ex);
        }
    }

    public Root settings() {
        return root;
    }

    public List<Entry> list(Kind kind) {
        return kind == Kind.PREFIX ? root.prefixes : root.titles;
    }

    public Entry find(Kind kind, String id) {
        if (id == null) return null;
        for (Entry entry : list(kind)) {
            if (id.equals(entry.id)) return entry;
        }
        return null;
    }

    public Entry create(Kind kind, String display) {
        String id;
        do {
            id = kind.key.charAt(0) + Integer.toString(root.nextId++);
        } while (find(kind, id) != null);
        Entry entry = new Entry();
        entry.id = id;
        entry.display = display;
        list(kind).add(entry);
        return entry;
    }

    public void delete(Kind kind, String id) {
        list(kind).removeIf(entry -> id.equals(entry.id));
        for (PlayerData data : root.players.values()) {
            normalizePlayer(data);
            if (kind == Kind.PREFIX) {
                if (Objects.equals(data.prefix, id)) data.prefix = null;
                data.grantedPrefixes.remove(id);
            } else {
                if (Objects.equals(data.title, id)) data.title = null;
                data.grantedTitles.remove(id);
            }
        }
    }

    /** Проверка текста записи, которую создаёт администратор. null — всё в порядке. */
    public String validate(String text) {
        if (text == null || text.isBlank()) return "Текст не может быть пустым.";
        if (text.length() > 120) return "Максимум 120 символов с кодами цвета.";
        String stripped = TextUtil.strip(text);
        int visible = stripped.codePointCount(0, stripped.length());
        if (visible == 0) return "В тексте нет видимых символов.";
        if (visible > root.maxLength) return "Слишком длинно: " + visible + " из " + root.maxLength + " символов.";
        return null;
    }

    public PlayerData data(UUID uuid) {
        PlayerData data = root.players.computeIfAbsent(uuid.toString(), key -> new PlayerData());
        normalizePlayer(data);
        return data;
    }

    public String selected(UUID uuid, Kind kind) {
        PlayerData data = root.players.get(uuid.toString());
        if (data == null) return null;
        return kind == Kind.PREFIX ? data.prefix : data.title;
    }

    public void setSelected(UUID uuid, Kind kind, String id) {
        PlayerData data = data(uuid);
        if (kind == Kind.PREFIX) data.prefix = id;
        else data.title = id;
    }

    public boolean isGranted(UUID uuid, Kind kind, String id) {
        PlayerData data = root.players.get(uuid.toString());
        if (data == null) return false;
        normalizePlayer(data);
        return (kind == Kind.PREFIX ? data.grantedPrefixes : data.grantedTitles).contains(id);
    }

    public void setGranted(UUID uuid, Kind kind, String id, boolean granted) {
        PlayerData data = data(uuid);
        Set<String> set = kind == Kind.PREFIX ? data.grantedPrefixes : data.grantedTitles;
        if (granted) set.add(id);
        else set.remove(id);
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("prefixtitles.admin");
    }

    public boolean canUse(Player player, Kind kind, Entry entry) {
        return !entry.restricted
                || isAdmin(player)
                || isGranted(player.getUniqueId(), kind, entry.id)
                || player.hasPermission("prefixtitles.use." + kind.key + "." + entry.id);
    }

    // ---------- собственные записи игроков ----------

    /** Собственная запись игрока или null, если ещё не создана. */
    public Entry custom(UUID uuid, Kind kind) {
        PlayerData data = root.players.get(uuid.toString());
        if (data == null) return null;
        return kind == Kind.PREFIX ? data.customPrefix : data.customTitle;
    }

    /** Собственная запись игрока; создаётся пустая, если её ещё нет. */
    public Entry ensureCustom(UUID uuid, Kind kind) {
        PlayerData data = data(uuid);
        Entry entry = kind == Kind.PREFIX ? data.customPrefix : data.customTitle;
        if (entry == null) {
            entry = new Entry();
            entry.id = CUSTOM_ID;
            entry.display = "";
            entry.plain = true;
            if (kind == Kind.PREFIX) data.customPrefix = entry;
            else data.customTitle = entry;
        }
        return entry;
    }

    /** Удаляет собственную запись и снимает её, если она была выбрана. */
    public void clearCustom(UUID uuid, Kind kind) {
        PlayerData data = data(uuid);
        if (kind == Kind.PREFIX) {
            data.customPrefix = null;
            if (CUSTOM_ID.equals(data.prefix)) data.prefix = null;
        } else {
            data.customTitle = null;
            if (CUSTOM_ID.equals(data.title)) data.title = null;
        }
    }
}
