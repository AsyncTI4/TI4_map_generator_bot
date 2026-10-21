package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.game.Player;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;

final class FleetCommandCounters {

    private FleetCommandCounters() {}

    static void draw(Graphics2D g2, String ccID, int x, int y, Player player, boolean rightAlign) {
        String ccPath = ImageResources.commandToken(ccID);
        int ccCount = player.getFleetCC();
        boolean hasArmada = player.hasAbility("armada");
        List<String> mahactCC = player.getMahactCC();
        boolean hasMahactCCs =
                !player.getMahactCC().isEmpty() && (player.hasAbility("edict") || player.hasAbility("edict_y"));

        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(ImageResources.commandToken("command_blank.png"));
            int delta = rightAlign ? -20 : 20;

            if (hasArmada) {
                BufferedImage armadaLowerCCImage =
                        ImageHelper.read(ImageResources.commandToken(Mapper.getCCID(player.getColor())));
                BufferedImage armadaCCImage = ImageHelper.read(ImageResources.commandToken("fleet_armada.png"));
                for (int i = 0; i < 2; i++) {
                    g2.drawImage(armadaLowerCCImage, x, y, null);
                    g2.drawImage(armadaCCImage, x, y, null);
                    x += delta;
                }
                x += delta;
            }

            if (ccCount == 0 && !hasArmada && !hasMahactCCs) {
                ccCount = 1;
                ccImage = blankCC;
            }

            for (int i = 0; i < ccCount; i++) {
                g2.drawImage(ccImage, x, y, null);
                x += delta;
            }

            if (hasMahactCCs) {
                if (hasArmada || ccCount >= 1) {
                    x += delta;
                }
                for (String ccColor : mahactCC) {
                    String fleetCCID = ImageResources.commandToken(Mapper.getFleetCCID(ccColor));
                    BufferedImage ccImageExtra = ImageHelper.readScaled(fleetCCID, 1.0f);
                    g2.drawImage(ccImageExtra, x, y, null);
                    x += delta;
                }
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Ignored exception during map generation", e);
        }
    }
}
