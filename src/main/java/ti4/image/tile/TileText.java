package ti4.image.tile;

import java.awt.Graphics2D;
import ti4.game.Game;
import ti4.image.io.Fonts;

final class TileText {

    private TileText() {}

    static void applyTextSize(Graphics2D g, Game game) {
        switch (game.getTextSize()) {
            case "large" -> g.setFont(Fonts.ti(40));
            case "medium" -> g.setFont(Fonts.ti(30));
            case "tiny" -> g.setFont(Fonts.ti(12));
            case null, default -> g.setFont(Fonts.ti(20));
        }
    }
}
