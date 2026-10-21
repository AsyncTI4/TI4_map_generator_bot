package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.image.io.ImageHelper;

final class ServerIcon {

    static final int SIZE = 96;

    private static final Map<String, String> ICON_BY_GUILD_ID = Map.ofEntries(
            Map.entry("943410040369479690", "asyncti_icon_hub"),
            Map.entry("1176104225932058694", "asyncti_icon_warsuntzu"),
            Map.entry("1145823841227112598", "asyncti_icon_dreadnot"),
            Map.entry("1250131684393881610", "asyncti_icon_tommerhawk"),
            Map.entry("1312882116597518416", "asyncti_icon_dudersdomain"),
            Map.entry("1090910555327434774", "asyncti_icon_stroterarea"),
            Map.entry("1209956332380229672", "asyncti_icon_fighterclub"),
            Map.entry("1378702133297414164", "asyncti_icon_whatsupdock"),
            Map.entry("1410728648817770526", "asyncti_icon_shipflag"),
            Map.entry("1434181175944941649", "asyncti_icon_pdstrians"),
            Map.entry("1434180793139204198", "asyncti_icon_greatcarrierreef"),
            Map.entry("1434632452097446040", "asyncti_icon_tournaments"),
            Map.entry("1458845770672377989", "asyncti_icon_planetaryducksystem"),
            Map.entry("1458844879709929532", "asyncti_icon_stroatymcstroatface"),
            Map.entry("1458845518393246032", "asyncti_icon_dannelscampground"));

    private ServerIcon() {}

    static void draw(Game game, Graphics2D g2, int x, int y) {
        String serverPath = ResourceHelper.getResourceFromFolder("server_icons/", iconName(game) + ".png");
        BufferedImage serverImage = ImageHelper.readScaled(serverPath, SIZE, SIZE);
        g2.drawImage(serverImage, x, y - 3 * SIZE / 4, null);
    }

    private static String iconName(Game game) {
        if (game.getGuild() == null) {
            return "asyncti_icon_nothing";
        }
        return ICON_BY_GUILD_ID.getOrDefault(game.getGuild().getId(), "asyncti_icon_unknown");
    }
}
