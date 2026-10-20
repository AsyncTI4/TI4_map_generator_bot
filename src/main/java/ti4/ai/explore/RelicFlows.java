package ti4.ai.explore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.Stall;

@UtilityClass
class RelicFlows {

    static final String DONE = "done";
    static final double MIN_NET = 0.1;
    private static final String MENU = "menu";
    private static final String RELIC_BUTTON = "componentActionRes_relic_";
    private static final List<RelicFlow> FLOWS = List.of(
            new DynamisCoreFlow(),
            new NanoforgeFlow(),
            new CodexFlow(),
            new CircletFlow(),
            new JuniorFlow(),
            new StellarConverterFlow(),
            new LatviniaFlow());

    static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (ComponentFlow.taken(context)) return Optional.empty();
        Optional<Candidate> best = best(context);
        if (best.isEmpty()
                || ComponentFlow.tacticalActionBeats(context, best.get().net() + Stall.ACTION_VALUE)) {
            return Optional.empty();
        }
        return open(
                context,
                thisTurn,
                best.get().flow(),
                "use the " + best.get().flow().name() + " instead of a weak tactical action");
    }

    static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (ComponentFlow.taken(context)) return Optional.empty();
        return best(context)
                .flatMap(candidate -> open(
                        context,
                        thisTurn,
                        candidate.flow(),
                        "use the " + candidate.flow().name() + " instead of passing"));
    }

    static Optional<AiDecision> next(AiTurnContext context) {
        for (RelicFlow flow : FLOWS) {
            Optional<List<String>> fields = ComponentFlow.fields(context, flow.name());
            if (fields.isEmpty()) continue;
            List<AiPrompt> visible = Prompts.newestFirst(context.prompts());
            if (MENU.equals(fields.get().get(1))) return pressRelic(context, visible, flow);
            return flow.step(context, visible, fields.get());
        }
        return Optional.empty();
    }

    static void advance(AiTurnContext context, RelicFlow flow, String stage, String... extra) {
        String[] fields = new String[extra.length + 2];
        fields[0] = flow.name();
        fields[1] = stage;
        System.arraycopy(extra, 0, fields, 2, extra.length);
        ComponentFlow.put(context, fields);
    }

    private record Candidate(RelicFlow flow, double net) {}

    private static Optional<Candidate> best(AiTurnContext context) {
        return FLOWS.stream()
                .filter(flow -> context.seat().hasRelicReady(flow.relicId()))
                .map(flow -> new Candidate(flow, flow.netValue(context)))
                .filter(candidate -> candidate.net() >= MIN_NET)
                .max(Comparator.comparingDouble(Candidate::net));
    }

    private static Optional<AiDecision> open(
            AiTurnContext context, List<AiPrompt> thisTurn, RelicFlow flow, String reason) {
        Optional<Match> menu = ComponentFlow.menu(context, thisTurn);
        menu.ifPresent(match -> ComponentFlow.put(context, flow.name(), MENU));
        return menu.map(match -> match.press(reason));
    }

    private static Optional<AiDecision> pressRelic(AiTurnContext context, List<AiPrompt> visible, RelicFlow flow) {
        String handler = RELIC_BUTTON + flow.relicId();
        Optional<Match> relic = Prompts.owned(visible, context.faction(), handler::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        relic.ifPresent(match -> advance(context, flow, flow.firstStage()));
        return relic.map(match -> match.press("use the " + flow.name()));
    }
}
