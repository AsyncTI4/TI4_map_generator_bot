package ti4.image;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.ColorModel;
import ti4.model.StrategyCardModel;

@UtilityClass
public class ColorUtil {

    public static final Color EliminatedColor = new Color(150, 0, 24); // Carmine
    public static final Color ActiveColor = new Color(80, 200, 120); // Emerald
    public static final Color PassedColor = new Color(220, 20, 60); // Crimson
    public static final Color Stage1RevealedColor = new Color(230, 126, 34);
    public static final Color LawColor = new Color(228, 255, 0);
    public static final Color TradeGoodColor = new Color(241, 176, 0);

    public static final Color PropulsionTech = Color.decode("#509dce");
    public static final Color CyberneticTech = Color.decode("#e2da6a");
    public static final Color BioticTech = Color.decode("#7cba6b");
    public static final Color WarfareTech = Color.decode("#dc6569");

    public Color getPlayerMainColor(Player p) {
        if (p == null) return getColor(null);
        return getColor(p.getColor());
    }

    public Color getPlayerAccentColor(Player p) {
        if (p == null) return getColor(null);
        ColorModel colorModel = Mapper.getColor(p.getColor());
        return (colorModel != null && colorModel.getSecondaryColor() != null)
                ? colorModel.getSecondaryColor()
                : getPlayerMainColor(p);
    }

    public Color getColor(String color) {
        color = Mapper.getColorName(color);
        if (color == null) return Color.WHITE;
        if (color.equals("orca")) return getColor("gray");
        ColorModel model = Mapper.getColor(color);
        return model.getPrimaryColor();
    }

    public Paint gradient(Color main, Color accent, Rectangle rect) {
        if (accent == null) return main;
        if (main == null) return accent;
        Point p1 = new Point(rect.getLocation());
        Point p2 = new Point(rect.getLocation());
        p2.translate(rect.width, rect.height);
        return new GradientPaint(p1, main, p2, accent);
    }

    public Color getSCColor(Integer sc, Game game) {
        return getSCColor(sc, game, false);
    }

    public Color getSCColor(Integer sc, Game game, boolean ignorePlayed) {
        Map<Integer, Boolean> scPlayed = game.getScPlayed();
        if (!ignorePlayed && scPlayed.get(sc) != null && scPlayed.get(sc)) {
            return Color.GRAY;
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel != null) {
            return scModel.getColour();
        }
        String scString = sc.toString();
        int scGroup = Integer.parseInt(StringUtils.left(scString, 1));
        return switch (scGroup) {
            case 1 -> new Color(255, 38, 38);
            case 2 -> new Color(253, 168, 24);
            case 3 -> new Color(247, 237, 28);
            case 4 -> new Color(46, 204, 113);
            case 5 -> new Color(26, 188, 156);
            case 6 -> new Color(52, 152, 171);
            case 7 -> new Color(155, 89, 182);
            case 8 -> new Color(124, 0, 192);
            case 9 -> new Color(251, 96, 213);
            case 10 -> new Color(165, 211, 34);
            default -> Color.WHITE;
        };
    }
}
