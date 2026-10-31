package dev.prefixtitles.paper;

import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Шрифты, которые есть в ванильном Minecraft (ресурспак не нужен).
 * Первый элемент (null) — обычный шрифт.
 */
public final class Fonts {

    public record Font(String id, String name, boolean asciiOnly) {
        public Key key() {
            return id == null ? null : Key.key(id);
        }
    }

    /** По порядку переключения в меню. */
    public static final List<Font> ALL = List.of(
            new Font(null, "Обычный", false),
            new Font("minecraft:uniform", "Uniform (Unifont)", false),
            new Font("minecraft:alt", "Зачарование (Galactic)", true),
            new Font("minecraft:illageralt", "Иллагерский (руны)", true)
    );

    private Fonts() {
    }

    public static Font byId(String id) {
        for (Font font : ALL) {
            if (java.util.Objects.equals(font.id(), id)) return font;
        }
        return ALL.get(0);
    }

    public static Font next(String currentId) {
        int index = ALL.indexOf(byId(currentId));
        return ALL.get((index + 1) % ALL.size());
    }
}
