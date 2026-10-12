package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.strategy.CardFacade;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class ExtraActionCard {

    static final String MASTER_PLAN = "master_plan";

    static final String COUNTERSTROKE = "counterstroke";
    private static final String RESOLVE_PREFIX = "resolveCounterStroke_";

    static AiCard counterstroke() {
        return AiActionCards.reactionWithPlayButton(
                COUNTERSTROKE,
                alias -> alias.startsWith(COUNTERSTROKE),
                (game, seat, activated) -> activated.hasPlayerCC(seat),
                ExtraActionCard::counterstrokePlan,
                List.of(RESOLVE_PREFIX),
                CardChains.chain(CardChains.last(
                        RESOLVE_PREFIX,
                        Source.MENTIONED,
                        (context, position) ->
                                holdsOwnToken(context.game(), context.seat(), position) ? 0 : Double.NEGATIVE_INFINITY,
                        "take the command token back in")),
                game -> "_counterstroke_" + game.getActiveSystem());
    }

    static Optional<Plan> counterstrokePlan(Game game, Player seat) {
        String position = game.getActiveSystem();
        double unlocked = Math.max(0, CardFacade.tokenUnlockGain(game, seat, position));
        return Optional.of(
                Plan.aimedAt(CardFacade.returnedTacticToken(game, seat) + unlocked, position, "in " + position + "."));
    }

    private static boolean holdsOwnToken(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && tile.hasPlayerCC(seat);
    }

    static AiCard masterPlan() {
        return AiActionCards.afterAction(MASTER_PLAN, alias -> alias.startsWith(MASTER_PLAN), ExtraActionCard::allow);
    }

    private static Optional<AiDecision> allow(AiTurnContext context, String key, CardPlay play) {
        TacticalRules.allowAnotherAction(context);
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return Optional.empty();
    }
}
