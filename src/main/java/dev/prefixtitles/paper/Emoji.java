package dev.prefixtitles.paper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Символы, которые стандартный шрифт Minecraft (с Unifont) отображает без ресурспака.
 * Цветные эмодзи вроде 😀 ванильный клиент не рисует, поэтому их здесь нет.
 */
public final class Emoji {

    public static final List<String> LIST = List.of(
            // сердца, звёзды, цветы
            "❤", "♥", "★", "☆", "✦", "✧", "✪", "✫", "✬", "✯", "❂", "☀", "☁", "☂", "❄", "☘", "✿", "❀", "❁", "✾",
            // оружие, символы силы
            "⚔", "☠", "♛", "♚", "♝", "♞", "⚜", "✞", "✝", "☥", "☯", "☮", "☢", "☣", "⚠", "⚡", "☄", "☽", "☾", "✺",
            // фигуры и стрелки
            "◆", "◇", "■", "□", "▲", "△", "▼", "▽", "●", "○", "◉", "➤", "➔", "➜", "➥", "➧", "»", "«", "›", "‹",
            // галочки, музыка, прочее
            "✔", "✖", "✘", "✓", "♪", "♫", "♬", "☎", "✉", "✎", "✂", "✈", "⚓", "⚙", "⚑", "☺", "☻", "☹", "♠", "♣",
            "♦", "✹", "✸", "❖", "❈", "❉", "❊", "❋", "⌂", "⌘", "☑", "☒", "✱", "✲", "✳", "✴", "✵", "✶", "✷", "※"
    );

    private static final Set<Integer> CODE_POINTS = new LinkedHashSet<>();

    static {
        for (String symbol : LIST) {
            CODE_POINTS.add(symbol.codePointAt(0));
        }
    }

    private Emoji() {
    }

    public static boolean isAllowed(int codePoint) {
        return CODE_POINTS.contains(codePoint);
    }
}
