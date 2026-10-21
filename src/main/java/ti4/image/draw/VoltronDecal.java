package ti4.image.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.RenderRandom;
import ti4.helpers.Units.UnitKey;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.UnitModel;
import ti4.settings.users.UserSettingsManager;

public final class VoltronDecal {

    private static final Set<String> WARM_COLORS = Set.of(
            "red",
            "splitred",
            "mgm",
            "rby",
            "rst",
            "bld",
            "splitbld",
            "pld",
            "cnb",
            "tan",
            "splittan",
            "cpr",
            "chk",
            "splitchk",
            "bwn",
            "pch",
            "org",
            "splitorg",
            "wsp",
            "gld",
            "splitgld",
            "spr",
            "ylw",
            "splitylw",
            "beg");
    private static final Set<String> ORANGE_COLORS =
            Set.of("tan", "splittan", "cpr", "chk", "splitchk", "bwn", "pch", "org", "splitorg");
    private static final Set<String> BLUE_COLORS =
            Set.of("tea", "splittea", "gcr", "eth", "nvy", "splitnvy", "blu", "splitblu", "ptr", "splitptr", "azr");
    private static final int ARMS_POSE_ODDS = 20;
    private static final int OVERHANG = 22;

    private VoltronDecal() {}

    public static void drawIfVoltron(UnitKey unitKey, int imageX, int imageY, Graphics2D graphics, Game game) {
        UnitModel model = game.getUnitFromUnitKey(unitKey);
        if (model == null || !"naaz_voltron".equals(model.getAlias())) return;
        String style = "eyes";
        Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
        if (player != null) {
            style = UserSettingsManager.get(player.getUserID()).getVoltronStyle();
        }

        if ("minis".equals(style)) {
            drawMinis(unitKey, imageX, imageY, graphics, game);
            return;
        }
        BufferedImage voltron = ImageHelper.read(ImageResources.decal(decalFile(style, unitKey)));
        graphics.drawImage(voltron, imageX - OVERHANG, imageY - OVERHANG, null);
    }

    private static void drawMinis(UnitKey unitKey, int imageX, int imageY, Graphics2D graphics, Game game) {
        BufferedImage unitImage = ImageHelper.readScaled(ImageResources.unitPath(unitKey, game), 0.5f);
        double angle = 2 * Math.PI * RenderRandom.nextDouble("unit.voltronMinis.angle");
        for (int i = 0; i < 3; i++) {
            double radius = 12 * RenderRandom.nextDouble("unit.voltronMinis.radius") + 22;
            graphics.drawImage(
                    unitImage,
                    (int) (imageX + radius * Math.cos(angle) + 11.5),
                    (int) (imageY + radius * Math.sin(angle) + 11.5),
                    null);
            angle += (4 * Math.PI / 9)
                    * (RenderRandom.nextDouble("unit.voltronMinis.step")
                            + RenderRandom.nextDouble("unit.voltronMinis.step")
                            + RenderRandom.nextDouble("unit.voltronMinis.step"));
        }
    }

    private static String decalFile(String style, UnitKey unitKey) {
        String color = unitKey.getColor();
        return switch (style) {
            case "arms" ->
                RenderRandom.oneIn("unit.voltronArmsPose", ARMS_POSE_ODDS)
                        ? "voltron_arms_pose.png"
                        : "voltron_arms.png";
            case "link" -> "voltron_link.png";
            case "saiyan" -> WARM_COLORS.contains(color) ? "voltron_saiyan_cyan.png" : "voltron_saiyan.png";
            case "at_field" -> ORANGE_COLORS.contains(color) ? "voltron_at_field_magenta.png" : "voltron_at_field.png";
            case "nyan" -> "voltron_nyan.png";
            case "royal" -> "voltron_royal.png";
            case "fancy" -> BLUE_COLORS.contains(color) ? "voltron_fancy_red.png" : "voltron_fancy.png";
            case "baba" -> "voltron_baba.png";
            case "lightning" -> BLUE_COLORS.contains(color) ? "voltron_lightning_yellow.png" : "voltron_lightning.png";
            case "epaulettes" -> "voltron_epaulettes.png";
            case "lion" -> "voltron_lion.png";
            case "panther" -> "voltron_panther.png";
            default -> WARM_COLORS.contains(color) ? "voltron_eyes_blue.png" : "voltron_eyes.png";
        };
    }
}
