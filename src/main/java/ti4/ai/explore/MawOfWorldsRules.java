package ti4.ai.explore;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class MawOfWorldsRules {

    static final double INFLUENCE_VOTE_VALUE = 0.25;
    static final double MIN_NET = 2.0;
    private static final String RELIC = "mawofworlds";
    private static final String PURGE = "resolveMaw";

    public static Optional<AiDecision> purge(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasRelic(RELIC)) return Optional.empty();
        Optional<Match> maw = Prompts.unowned(Prompts.newestFirst(context.prompts()), PURGE::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (maw.isEmpty() || net(context.game(), seat) < MIN_NET) return Optional.empty();
        StrategyCardRules.expectResearch(context, 0);
        EnigmaticDeviceRules.continueWithFreeTechnology(context);
        return maw.map(match -> match.press("purge the Maw of Worlds for a technology"));
    }

    static double net(Game game, Player seat) {
        double technology = ResearchPolicy.bestResearchable(game, seat)
                .map(alias -> ResearchPolicy.researchValue(game, seat, alias))
                .orElse(0.0);
        return technology - votesGivenUp(game, seat);
    }

    private static double votesGivenUp(Game game, Player seat) {
        Integer influence = Helper.getPlayerInfluenceAvailable(seat, game);
        return INFLUENCE_VOTE_VALUE * (influence == null ? 0 : influence);
    }
}
