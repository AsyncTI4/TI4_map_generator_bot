package ti4.image.playerarea;

import java.util.List;
import ti4.game.Player;

final class CardStrip {

    private static final int GAP_BEFORE_ABILITIES = 20;

    private static final List<StripBlock> BLOCKS = List.of(
            HomebrewStripCards::drawBentorBlueprints,
            LeaderCards::draw,
            DebtPools::draw,
            RelicCards::draw,
            HomebrewStripCards::drawOmenDice,
            PromissoryNotesInPlay::draw,
            BreakthroughCards::draw,
            TechCards::draw,
            FactionTechCards::draw,
            (ctx, player, x, y) -> x + GAP_BEFORE_ABILITIES,
            AbilityCards::draw,
            OwnedPromissoryNotes::draw);

    private CardStrip() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        for (StripBlock block : BLOCKS) {
            x = block.draw(ctx, player, x, y);
        }
        return x;
    }
}
