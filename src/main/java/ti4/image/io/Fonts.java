package ti4.image.io;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

public final class Fonts {

    private static final String TI_FONT_FILE = "/font/Slider_TI4.ttf";
    private static final String EMOJI_FONT_FILE = "/font/NotoEmoji-Regular.ttf";
    private static final float EMOJI_FONT_SIZE = 40.0f;
    private static final Map<Integer, Font> TI_FONTS = new ConcurrentHashMap<>();
    private static volatile Font emojiFont;

    private Fonts() {}

    @Nullable
    public static Font ti(int size) {
        Font font = TI_FONTS.get(size);
        if (font != null) return font;
        font = load(TI_FONT_FILE, size);
        if (font != null) TI_FONTS.put(size, font);
        return font;
    }

    @Nullable
    public static Font emoji() {
        Font font = emojiFont;
        if (font != null) return font;
        font = load(EMOJI_FONT_FILE, EMOJI_FONT_SIZE);
        emojiFont = font;
        return font;
    }

    @Nullable
    private static Font load(String fontFile, float size) {
        String resource = Storage.getResourcePath();
        if (resource == null) return null;
        try (InputStream inputStream = new FileInputStream(new File(resource + fontFile))) {
            return Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont(size);
        } catch (Exception e) {
            BotLogger.error("Could not load font", e);
            return null;
        }
    }
}
