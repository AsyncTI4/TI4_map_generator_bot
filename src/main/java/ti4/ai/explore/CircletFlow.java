package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.helpers.ButtonHelperActionCards;

class CircletFlow implements RelicFlow {

    static final String TARGET_PREFIX = "probeStep2_";
    private static final String RELIC = "circletofthevoid";
    private static final String TARGET = "target";

    @Override
    public String name() {
        return "Circlet of the Void";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        if (!ButtonHelperActionCards.hasCircletTargets(context.game(), context.seat())) return 0;
        return ExploreValues.frontierCard(context.game(), context.seat());
    }

    @Override
    public String firstStage() {
        return TARGET;
    }

    @Override
    public Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        Optional<Match> target = Prompts.unowned(visible, handler -> handler.startsWith(TARGET_PREFIX))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        target.ifPresent(match -> RelicFlows.advance(context, this, RelicFlows.DONE));
        return target.map(match -> match.press("explore a frontier token with the Circlet of the Void"));
    }
}
