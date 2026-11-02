package ti4.service.fow;

import java.awt.Color;
import java.util.Arrays;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.model.ColorModel;
import ti4.model.Mapper;

@UtilityClass
class CommsColorMarker {

    static final String RAINBOW = "🌈";
    private static final Set<String> RAINBOW_COLORS = Set.of("rainbow", "sherbet", "paintball");

    enum Hue {
        RED("🔴", "❤️"),
        ORANGE("🟠", "🧡"),
        YELLOW("🟡", "💛"),
        GREEN("🟢", "💚"),
        BLUE("🔵", "💙"),
        PURPLE("🟣", "💜"),
        PINK("🌸", "🩷"),
        BROWN("🟤", "🤎"),
        BLACK("⚫", "🖤"),
        WHITE("⚪", "🤍");

        final String circle;
        final String heart;

        Hue(String circle, String heart) {
            this.circle = circle;
            this.heart = heart;
        }
    }

    static String of(String color) {
        ColorModel model = Mapper.getColor(color);
        if (model == null) return Hue.WHITE.circle;
        Hue minor = hueOf(model.getSecondaryColor());
        if (RAINBOW_COLORS.contains(model.getName())) return RAINBOW;
        String primaryRef = model.getPrimaryColorRef();
        if (primaryRef != null && RAINBOW_COLORS.contains(primaryRef)) return RAINBOW + minor.heart;
        Hue main = mainHue(model);
        return main == minor ? main.circle : main.circle + minor.heart;
    }

    private static Hue mainHue(ColorModel model) {
        Set<String> categories = categoriesOf(model);
        if (isGradient(model) || categories.size() != 1) return hueOf(model.getPrimaryColor());
        String category = categories.iterator().next();
        return Arrays.stream(Hue.values())
                .filter(hue -> hue.name().equals(category))
                .findFirst()
                .orElseGet(() -> hueOf(model.getPrimaryColor()));
    }

    private static boolean isGradient(ColorModel model) {
        return model.getSecondaryColorRef() == null && !model.getPrimaryColor().equals(model.getSecondaryColor());
    }

    private static Set<String> categoriesOf(ColorModel model) {
        if (!model.getColorCategories().isEmpty() || model.getPrimaryColorRef() == null) {
            return model.getColorCategories();
        }
        ColorModel primary = Mapper.getColor(model.getPrimaryColorRef());
        return primary == null ? Set.of() : primary.getColorCategories();
    }

    static Hue hueOf(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0] * 360;
        float saturation = hsb[1];
        float brightness = hsb[2];
        if (brightness < 0.3f) return Hue.BLACK;
        if (saturation < 0.2f) return brightness > 0.75f ? Hue.WHITE : Hue.BLACK;
        if (isBrown(hue, saturation, brightness)) return Hue.BROWN;
        if (hue < 15 || hue >= 345) return Hue.RED;
        if (hue < 45) return Hue.ORANGE;
        if (hue < 70) return Hue.YELLOW;
        if (hue < 165) return Hue.GREEN;
        if (hue < 255) return Hue.BLUE;
        if (hue < 290) return Hue.PURPLE;
        return Hue.PINK;
    }

    private static boolean isBrown(float hue, float saturation, float brightness) {
        boolean darkWarm = hue < 45 && brightness < 0.6f;
        boolean mutedWarm = hue >= 15 && hue < 60 && saturation < 0.45f && brightness < 0.75f;
        return darkWarm || mutedWarm;
    }
}
