package ti4.image;

import java.awt.Graphics;
import ti4.game.Game;
import ti4.helpers.Units.UnitKey;

public final class EidolonDecal {

    private EidolonDecal() {}

    public static void drawIfMaximum(UnitKey unitKey, int imageX, int imageY, Graphics graphics, Game game) {
        UnitRenderGenerator.optionallyDrawEidolonMaximumDecal(unitKey, imageX, imageY, graphics, game);
    }
}
