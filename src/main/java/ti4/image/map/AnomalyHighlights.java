package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.TileGeometry;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

final class AnomalyHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        if (!(player.ownsUnitSubstring("cabal_spacedock") || player.hasTech("tf-dimensionaltear"))) {
            return 0;
        }
        int offBoardHighlighting = 0;
        UnitKey unitKey = Mapper.getUnitKey("sd", player.getColor());
        int unitNum = player.getUnitCap("sd") + player.getUnitCap("csd");
        unitNum = (unitNum == 0
                ? MapPositionMapper.getReinforcementsPosition("sd").getPositionCount("sd")
                : unitNum);
        for (Tile tile2 : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                unitNum -= unitHolder.getUnitCount(unitKey);
            }
        }
        if (unitNum > 0) {
            int x = miscTile.x + (TileGeometry.TILE_WIDTH - 95) / 2;
            x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
            int y = miscTile.y + (TileGeometry.TILE_HEIGHT - 95) / 2 + (player.isSpeaker() ? 30 : 0);
            String tokenFile = ImageResources.tokenOrAttachment("token_gravityrift.png");
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            for (int i = 0; i < unitNum; i++) {
                g2.drawImage(bufferedImage, x, y, null);
                x -= (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 60 : 0);
            }
            offBoardHighlighting += unitNum;
        }
        return offBoardHighlighting;
    }
}
