package dev.prefixtitles.paper;

import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/** Очистка и проверка текста, который игроки вводят для своих префиксов и титулов. */
public final class CustomText {

    private static final String PUNCTUATION = ".,!?:;-_+=/|()[]{}'\"~*#@%$^<>";

    /** Похожие кириллические буквы приводятся к латинским, чтобы «аdмin» не обходил фильтр. */
    private static final Map<Character, Character> LOOKALIKES = Map.ofEntries(
            Map.entry('а', 'a'), Map.entry('в', 'b'), Map.entry('е', 'e'), Map.entry('ё', 'e'),
            Map.entry('к', 'k'), Map.entry('м', 'm'), Map.entry('н', 'h'), Map.entry('о', 'o'),
            Map.entry('р', 'p'), Map.entry('с', 'c'), Map.entry('т', 't'), Map.entry('у', 'y'),
            Map.entry('х', 'x'), Map.entry('і', 'i'), Map.entry('ѕ', 's'));

    private CustomText() {
    }

    /** Убирает коды форматирования, управляющие символы и лишние пробелы. */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String text = TextUtil.strip(raw).replace("§", "");
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) {
                out.append(' ');
                return;
            }
            if (Character.isISOControl(cp)) return;
            out.appendCodePoint(cp);
        });
        return out.toString().trim().replaceAll(" {2,}", " ");
    }

    /** Возвращает текст ошибки или null, если текст подходит. */
    public static String validate(Store store, Player player, String text, String fontId) {
        if (text == null || text.isBlank()) return "Текст не может быть пустым.";
        int length = text.codePointCount(0, text.length());
        int max = store.settings().customMaxLength;
        if (length > max) return "Слишком длинно: " + length + " из " + max + " символов.";

        Fonts.Font font = Fonts.byId(fontId);
        boolean emojiPermitted = player.hasPermission("prefixtitles.custom.emoji");
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') continue;
            if (Emoji.isAllowed(cp)) {
                if (!emojiPermitted) return "У вас нет права использовать символы и эмодзи.";
                if (font.asciiOnly()) return "Шрифт «" + font.name() + "» поддерживает только латиницу и цифры.";
                continue;
            }
            if (font.asciiOnly()) {
                boolean ascii = cp < 128 && (Character.isLetterOrDigit(cp) || PUNCTUATION.indexOf(cp) >= 0);
                if (!ascii) {
                    return "Шрифт «" + font.name() + "» поддерживает только латиницу и цифры. Недопустимо: "
                            + new String(Character.toChars(cp));
                }
                continue;
            }
            if (Character.isLetterOrDigit(cp) || (cp < 128 && PUNCTUATION.indexOf(cp) >= 0)) continue;
            return "Символ «" + new String(Character.toChars(cp))
                    + "» нельзя использовать. Эмодзи выбирайте в меню — там только поддерживаемые Minecraft.";
        }

        if (!player.hasPermission("prefixtitles.custom.bypass")) {
            String flat = normalize(text);
            for (String word : store.settings().blockedWords) {
                String needle = normalize(word);
                if (!needle.isEmpty() && flat.contains(needle)) return "Этот текст запрещён сервером.";
            }
        }
        return null;
    }

    private static String normalize(String text) {
        StringBuilder out = new StringBuilder();
        for (char ch : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) continue;
            out.append(LOOKALIKES.getOrDefault(ch, ch));
        }
        return out.toString();
    }
}
