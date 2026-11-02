package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;

interface OffBoardHighlights {

    OffBoardHighlights NONE = (g2, game, player, miscTile) -> 0;

    int draw(Graphics2D g2, Game game, Player player, Point miscTile);

    static OffBoardHighlights forDisplayType(DisplayType displayType) {
        return switch (displayType) {
            case legendaries -> new LegendaryHighlights();
            case wormholes -> new WormholeHighlights();
            case anomalies -> new AnomalyHighlights();
            case traits -> new TraitHighlights();
            case techskips -> new TechSkipHighlights();
            case attachments -> new AttachmentHighlights();
            default -> NONE;
        };
    }
}
