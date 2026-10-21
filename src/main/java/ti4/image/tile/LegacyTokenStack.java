package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;

final class LegacyTokenStack {

    private static final float SCALE = 0.85f;
    private static final int STACK_LIFT = 35;

    private LegacyTokenStack() {}

    static void draw(Tile tile, Graphics2D g, UnitHolder unitHolder, List<String> tokenList) {
        Point centerPosition = TokenPlanetPlacement.holderCenter(unitHolder, tile);
        int x = centerPosition.x;
        int y = centerPosition.y - (tokenList.size() > 1 ? STACK_LIFT : 0);
        for (String tokenID : tokenList) {
            String tokenPath = ImageResources.tokenOrAttachment(tokenID);
            if (tokenPath == null) {
                BotLogger.warning("Could not parse token file for: " + tokenID);
                continue;
            }
            BufferedImage image = ImageHelper.readScaled(tokenPath, SCALE);
            if (image == null) continue;
            g.drawImage(
                    image, TILE_PADDING + x - (image.getWidth() / 2), TILE_PADDING + y - (image.getHeight() / 2), null);
            y += image.getHeight();
        }
    }
}
