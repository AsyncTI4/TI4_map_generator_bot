package ti4.image.tile;

import java.awt.Graphics2D;
import ti4.game.Game;
import ti4.helpers.Storage;

final class TileText {

    private TileText() {}

    static void applyTextSize(Graphics2D g, Game game) {
        switch (game.getTextSize()) {
            case "large" -> g.setFont(Storage.getFont40());
            case "medium" -> g.setFont(Storage.getFont30());
            case "tiny" -> g.setFont(Storage.getFont12());
            case null, default -> g.setFont(Storage.getFont20());
        }
    }
}
