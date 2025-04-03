package ti4.model;

import java.awt.*;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.image.Mapper;
import ti4.service.emoji.ColorEmojis;

@Data
public class ColorModel implements ModelInterface {

    private String alias;
    private String name;
    private String displayName;
    private List<String> aliases;
    private String textColor;
    private String hue;

    private Color primaryColor;
    private Color secondaryColor;

    private String primaryColorRef;
    private String secondaryColorRef;

    public boolean isValid() {
        if (primaryColorRef != null && primaryColorRef.equals(name)) return false;
        if (secondaryColorRef != null && secondaryColorRef.equals(name)) return false;
        return alias != null && name != null && textColor != null;
    }

    public String getDisplayName() {
        return displayName == null ? name : displayName;
    }

    public Color getPrimaryColor() {
        return primaryColor();
    }

    public Color getSecondaryColor() {
        return secondaryColor();
    }

    public String getHue() {
        return (hue == null ? "null" : hue);
    }

    public Color primaryColor() {
        if (primaryColor != null)
            return new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue());
        if (primaryColorRef != null)
            return Mapper.getColor(primaryColorRef).primaryColor();
        return new Color(255, 255, 255);
    }

    public Color secondaryColor() {
        if (secondaryColor != null)
            return new Color(secondaryColor.getRed(), secondaryColor.getGreen(), secondaryColor.getBlue());
        if (secondaryColorRef != null)
            return Mapper.getColor(secondaryColorRef).primaryColor();
        return primaryColor();
    }

    @JsonIgnore
    public String getRepresentation(boolean includeName) {
        if (includeName)
            return ColorEmojis.getColorEmojiWithName(name);
        return ColorEmojis.getColorEmoji(name).toString();
    }

    @JsonIgnore
    public Emoji getEmoji() {
        String emoji = getRepresentation(false);
        if (emoji != null)
            return Emoji.fromFormatted(emoji);
        return null;
    }

    public double contrastWith(ColorModel c2) {
        double primary1 = primaryLuminance();
        double primary2 = c2.primaryLuminance();
        double secondary1 = secondaryLuminance();
        double secondary2 = c2.secondaryLuminance();

        return Math.max(contrastRatio(primary1, primary2), contrastRatio(secondary1, secondary2));
    }

    private double primaryLuminance() {
        return relativeLuminance(primaryColor());
    }

    private double secondaryLuminance() {
        return relativeLuminance(secondaryColor());
    }

    // For the sRGB colorspace, the relative luminance of a color is defined as
    //    L = 0.2126 * R + 0.7152 * G + 0.0722 * B
    // where R, G and B are defined as:
    //    if XsRGB <= 0.03928 then  X = XsRGB/12.92 
    //    else                      X = ((XsRGB+0.055)/1.055) ^ 2.4
    private static double relativeLuminance(Color color) {
        if (color == null) return 0;
        double RsRGB = ((double) color.getRed()) / 255.0;
        double GsRGB = ((double) color.getGreen()) / 255.0;
        double BsRGB = ((double) color.getBlue()) / 255.0;
        double r = color.getRed() <= 10 ? RsRGB / 12.92 : Math.pow((RsRGB + 0.055) / 1.055, 2.4);
        double g = color.getGreen() <= 10 ? GsRGB / 12.92 : Math.pow((GsRGB + 0.055) / 1.055, 2.4);
        double b = color.getBlue() <= 10 ? BsRGB / 12.92 : Math.pow((BsRGB + 0.055) / 1.055, 2.4);
        return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
    }

    // This results in a value ranging from 1:1 (no contrast at all) to 21:1 (the highest possible contrast).
    private static double contrastRatio(double color1, double color2) {
        if (color1 < color2)
            return contrastRatio(color2, color1);
        return (color1 + 0.05) / (color2 + 0.05);
    }
}
