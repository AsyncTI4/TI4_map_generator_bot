package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.image.io.Fonts;
import ti4.image.playerarea.PlayerAreaColumns;
import ti4.service.option.FOWOptionService.FOWOption;

final class MapSideStatsSection {

    private MapSideStatsSection() {}

    static boolean drawsPlayerInfo(Game game, DisplayType displayType, FogView fog) {
        return displayType != DisplayType.stats
                && !PlayerAreaColumns.isMegaGame(game)
                && (!fog.isPrivate() || !game.getFowOption(FOWOption.HIDE_PLAYER_INFOS));
    }

    static boolean isDrawn(RenderContext ctx) {
        return drawsPlayerInfo(ctx.game(), ctx.displayType(), ctx.fog())
                && !ctx.board().isShowingSeparateFracture();
    }

    static void draw(RenderContext ctx, Graphics2D g2) {
        Game game = ctx.game();
        g2.setFont(Fonts.ti(32));
        g2.setColor(Color.WHITE);

        int ringCount = Math.clamp(game.getRingCount(), BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
        MapSideStatLayout.Layout layout = ctx.statLayout().get();
        HexTileStats hexTileStats = new HexTileStats(ctx, g2);
        AnchorStats anchorStats = new AnchorStats(ctx, g2);
        for (Player player : layout.statOrder()) {
            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }
            if (layout.useNewSystem()) {
                if (ctx.board().shownSegment() != null
                        && !ctx.board().framedStatFactions().contains(player.getFaction())) {
                    continue;
                }
                List<String> tiles = layout.playerStatTiles().get(player.getFaction());
                hexTileStats.paintPlayerInfo(player, tiles);
            } else {
                anchorStats.paintPlayerInfoOld(player, ringCount);
            }
        }
    }
}
