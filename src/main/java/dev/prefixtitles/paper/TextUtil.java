package dev.prefixtitles.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HEX_INPUT = Pattern.compile("^#?([0-9a-fA-F]{6})$");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Цвета бетона в том же порядке, что и в меню (индекс 15 — чёрный). */
    public static final int[] CONCRETE_RGB = {
            16383998, 16351261, 13061821, 3847130, 16701501, 8439583, 15961002, 4673362,
            10329495, 1481884, 8991416, 3949738, 8606770, 6192150, 11546150, 1908001
    };
    public static final String[] CONCRETE_NAMES = {
            "Белый", "Оранжевый", "Пурпурный", "Голубой", "Жёлтый", "Лаймовый", "Розовый", "Тёмно-серый",
            "Серый", "Бирюзовый", "Фиолетовый", "Синий", "Коричневый", "Зелёный", "Красный", "Чёрный"
    };

    private TextUtil() {
    }

    /** Текст с кодами &a, &l и &#rrggbb → Component. */
    public static Component parse(String text) {
        Matcher matcher = HEX.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            StringBuilder legacy = new StringBuilder("§x");
            for (char ch : matcher.group(1).toCharArray()) {
                legacy.append('§').append(ch);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(buffer);
        return LEGACY.deserialize(buffer.toString().replaceAll("(?i)&([0-9a-fk-or])", "§$1"));
    }

    /** Текст без кодов форматирования. */
    public static String strip(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if (next == '#' && i + 8 <= text.length() && isHex(text.substring(i + 2, i + 8))) {
                    i += 7;
                    continue;
                }
                if ("0123456789abcdefklmnor".indexOf(next) >= 0) {
                    i += 1;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    public static Component render(Store.Entry entry) {
        Component component;
        if (entry.color != null && entry.color2 != null) {
            int[] points = strip(entry.display).codePoints().toArray();
            TextComponent.Builder builder = Component.text();
            for (int i = 0; i < points.length; i++) {
                float t = points.length <= 1 ? 0f : (float) i / (points.length - 1);
                builder.append(Component.text(new String(points, i, 1),
                        TextColor.color(lerp(entry.color, entry.color2, t))));
            }
            component = builder.build();
        } else {
            component = entry.plain ? Component.text(entry.display) : parse(entry.display);
            if (entry.color != null) component = component.colorIfAbsent(TextColor.color(entry.color));
        }
        component = flags(component, entry);
        Fonts.Font font = Fonts.byId(entry.font);
        if (font.key() != null) component = component.font(font.key());
        return component;
    }

    private static Component flags(Component component, Store.Entry entry) {
        if (entry.bold) component = component.decorate(TextDecoration.BOLD);
        if (entry.italic) component = component.decorate(TextDecoration.ITALIC);
        if (entry.underlined) component = component.decorate(TextDecoration.UNDERLINED);
        if (entry.strikethrough) component = component.decorate(TextDecoration.STRIKETHROUGH);
        if (entry.obfuscated) component = component.decorate(TextDecoration.OBFUSCATED);
        return component;
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static boolean isHex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    /** "#ff8800" или "ff8800" → 0xFF8800; иначе null. */
    public static Integer parseHex(String input) {
        if (input == null) return null;
        Matcher matcher = HEX_INPUT.matcher(input.trim());
        return matcher.matches() ? Integer.parseInt(matcher.group(1), 16) : null;
    }
}
