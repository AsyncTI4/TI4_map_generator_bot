package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;

class JuniorFlow implements RelicFlow {

    static final String CHOOSE_PREFIX = "jrResolution_";
    static final String STRUCTURE_PREFIX = "jrStructure_";
    private static final String RELIC = "titanprototype";
    private static final String CHOOSE = "choose";
    private static final String STRUCTURE = "structure";
    private static final String TRADE_GOOD = "jrStructure_tg";
    private static final double TRADE_GOODS = 1.0;

    @Override
    public String name() {
        return "JR-XS455-O";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        return TRADE_GOODS;
    }

    @Override
    public String firstStage() {
        return CHOOSE;
    }

    @Override
    public Optional<AiDecision> step(AiTurnContext context, List<AiPrompt> visible, List<String> fields) {
        return switch (fields.get(1)) {
            case CHOOSE -> chooseSelf(context, visible);
            case STRUCTURE -> takeTradeGood(context, visible);
            default -> Optional.empty();
        };
    }

    private Optional<AiDecision> chooseSelf(AiTurnContext context, List<AiPrompt> visible) {
        String faction = CHOOSE_PREFIX + context.faction();
        Optional<Match> self = Prompts.unowned(visible, handler -> handler.equals(faction))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        self.ifPresent(match -> RelicFlows.advance(context, this, STRUCTURE));
        return self.map(match -> match.press("use JR-XS455-O on itself"));
    }

    private Optional<AiDecision> takeTradeGood(AiTurnContext context, List<AiPrompt> visible) {
        Optional<Match> gain = Prompts.unowned(visible, TRADE_GOOD::equals)
                .filter(match -> Prompts.mentions(match.prompt(), context.seat()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        gain.ifPresent(match -> RelicFlows.advance(context, this, RelicFlows.DONE));
        return gain.map(match -> match.press("take the trade good from JR-XS455-O"));
    }
}
