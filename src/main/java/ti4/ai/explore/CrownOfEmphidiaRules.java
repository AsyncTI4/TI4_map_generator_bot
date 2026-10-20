package ti4.ai.explore;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;

@UtilityClass
public class CrownOfEmphidiaRules {

    private static final String RELIC = "emphidia";
    private static final String PURGE = "resolveCrownOfE";

    public static Optional<AiDecision> scorePoint(AiTurnContext context) {
        if (!context.seat().hasRelic(RELIC)) return Optional.empty();
        Optional<Match> purge = Prompts.unowned(Prompts.newestFirst(context.prompts()), PURGE::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        return purge.map(match -> match.press("purge the Crown of Emphidia for its victory point"));
    }
}
