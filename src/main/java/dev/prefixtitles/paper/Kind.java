package dev.prefixtitles.paper;

/** Тип записи: префикс (перед ником) или титул (после ника). */
public enum Kind {
    PREFIX("prefix", "Префикс", "Префиксы"),
    TITLE("title", "Титул", "Титулы");

    public final String key;
    public final String single;
    public final String plural;

    Kind(String key, String single, String plural) {
        this.key = key;
        this.single = single;
        this.plural = plural;
    }

    public static Kind byKey(String key) {
        if (key == null) return null;
        for (Kind kind : values()) {
            if (kind.key.equalsIgnoreCase(key)) return kind;
        }
        return null;
    }
}
