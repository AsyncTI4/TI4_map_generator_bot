package ti4.image;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;

final class AnomalyHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        if (!(player.ownsUnitSubstring("cabal_spacedock") || player.hasTech("tf-dimensionaltear"))) {
            return 0;
        }
        int offBoardHighlighting = 0;
        UnitKey unitKey = Mapper.getUnitKey("sd", player.getColor());
        int unitNum = player.getUnitCap("sd") + player.getUnitCap("csd");
        unitNum = (unitNum == 0 ? PositionMapper.getReinforcementsPosition("sd").getPositionCount("sd") : unitNum);
        for (Tile tile2 : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                unitNum -= unitHolder.getUnitCount(unitKey);
            }
        }
        if (unitNum > 0) {
            int x = miscTile.x + (BoardLayout.TILE_WIDTH - 95) / 2;
            x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
            int y = miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 95) / 2 + (player.isSpeaker() ? 30 : 0);
            String tokenFile = Mapper.getTokenPath("token_gravityrift.png");
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
