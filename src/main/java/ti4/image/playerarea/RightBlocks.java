package ti4.image.playerarea;

import java.util.List;
import ti4.game.Player;

final class RightBlocks {

    private static final int EDGE_BUFFER = 10;

    private static final List<RightBlock> FIRST_ROW = List.of(UnitValues::draw, Nombox::draw, PlotCards::draw);

    private static final List<RightBlock> SECOND_ROW = List.of(
            Reinforcements::draw,
            MonumentSlot::draw,
            StasisCards::drawGenSynthesis,
            StasisCards::drawCorsairsCove,
            SpeakerAndTyrant::draw,
            HonorAndPathCounters::draw,
            WinnuVault::draw,
            CrimsonRebellionTokens::draw,
            GalvanizeTokens::draw,
            TheodisiTokenSupplies::draw,
            DreamNexusTokens::draw,
            SleeperTokens::draw,
            CreussWormholeTokens::draw,
            ValefarZTokens::draw);

    private RightBlocks() {}

    static void draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int firstRowY, int secondRowY) {
        drawRow(FIRST_ROW, ctx, slot, player, firstRowY);
        drawRow(SECOND_ROW, ctx, slot, player, secondRowY);
    }

    private static void drawRow(List<RightBlock> blocks, PlayerAreaContext ctx, AreaSlot slot, Player player, int y) {
        int xDeltaFromRight = EDGE_BUFFER;
        for (RightBlock block : blocks) {
            xDeltaFromRight = block.draw(ctx, slot, player, xDeltaFromRight, y);
        }
    }
}
