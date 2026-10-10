package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class EnigmaticDeviceRules {

    static final int RESEARCH_COST = 6;
    private static final String FLOW = "device";
    private static final String MENU = "menu";
    private static final String RESEARCH = "research";
    private static final String TYPE = "type";
    private static final String DONE = "done";
    private static final String PURGE_DEVICE = "componentActionRes_relic_" + Constants.ENIGMATIC_DEVICE;
    private static final String GET_TECHNOLOGY = "acquireATech";
    private static final String TYPE_PREFIX = "getAllTechOfType_";

    static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!worthUsing(context.game(), context.seat())) return Optional.empty();
        Game game = context.game();
        double net =
                researchValue(game, context.seat()) - ProductionPlanner.fillerValuePerResource(game) * RESEARCH_COST;
        if (ComponentFlow.tacticalActionBeats(context, net + ComponentFlow.STALL_ACTION_VALUE)) {
            return Optional.empty();
        }
        return open(context, thisTurn, "research with the Enigmatic Device instead of a weak tactical action");
    }

    static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!worthUsing(context.game(), context.seat())) return Optional.empty();
        return open(context, thisTurn, "research with the Enigmatic Device instead of passing");
    }

    private static Optional<AiDecision> open(AiTurnContext context, List<AiPrompt> thisTurn, String reason) {
        if (ComponentFlow.taken(context)) return Optional.empty();
        Optional<Match> menu = ComponentFlow.menu(context, thisTurn);
        menu.ifPresent(match -> ComponentFlow.put(context, FLOW, MENU));
        return menu.map(match -> match.press(reason));
    }

    static Optional<AiDecision> next(AiTurnContext context) {
        Optional<List<String>> state = ComponentFlow.fields(context, FLOW);
        if (state.isEmpty()) return Optional.empty();
        return switch (state.get().get(1)) {
            case MENU -> purgeDevice(context);
            case RESEARCH -> getTechnology(context);
            case TYPE -> chooseType(context);
            default -> Optional.empty();
        };
    }

    private static Optional<AiDecision> purgeDevice(AiTurnContext context) {
        Optional<Match> purge = Prompts.owned(visible(context), context.faction(), PURGE_DEVICE::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        purge.ifPresent(match -> {
            StrategyCardRules.expectResearch(context, RESEARCH_COST);
            ComponentFlow.put(context, FLOW, RESEARCH);
        });
        return purge.map(match -> match.press("purge the Enigmatic Device to research a technology"));
    }

    private static Optional<AiDecision> getTechnology(AiTurnContext context) {
        Optional<Match> get = Prompts.unowned(visible(context), GET_TECHNOLOGY::equals)
                .filter(match -> Prompts.mentions(match.prompt(), context.seat()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        get.ifPresent(match -> ComponentFlow.put(context, FLOW, TYPE));
        return get.map(match -> match.press("get the technology from the Enigmatic Device"));
    }

    private static Optional<AiDecision> chooseType(AiTurnContext context) {
        Game game = context.game();
        Optional<String> wanted = ResearchPolicy.bestResearchable(game, context.seat())
                .map(Mapper::getTech)
                .map(TechnologyModel::getFirstType)
                .map(Object::toString);
        List<AiPrompt> hidden = Prompts.newestFirst(context.prompts()).stream()
                .filter(AiPrompt::isHidden)
                .toList();
        Optional<Match> offered = Prompts.owned(hidden, context.faction(), id -> id.startsWith(TYPE_PREFIX))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        Optional<PromptButton> type = offered.flatMap(match -> match.prompt()
                .firstEnabled(button -> button.isOwnedBy(context.faction())
                        && wanted.map(name -> button.handlerId().equals(TYPE_PREFIX + name))
                                .orElse(false)));
        Optional<Match> chosen =
                type.map(button -> new Match(offered.get().prompt(), button)).or(() -> offered);
        chosen.ifPresent(match -> ComponentFlow.put(context, FLOW, DONE));
        return chosen.map(match -> match.press("choose the kind of technology to research"));
    }

    static boolean worthUsing(Game game, Player seat) {
        return seat.getRelics().contains(Constants.ENIGMATIC_DEVICE)
                && !StrategyCardRules.cannotResearch(seat)
                && StrategyCardRules.worthPayingForResearch(
                        game, seat, RESEARCH_COST, ResearchPolicy.WORTH_PAYING_FOR, Wallet.of(game, seat));
    }

    private static double researchValue(Game game, Player seat) {
        return ResearchPolicy.bestResearchable(game, seat)
                .map(alias -> ResearchPolicy.researchValue(game, seat, alias))
                .orElse(0.0);
    }

    private static List<AiPrompt> visible(AiTurnContext context) {
        return Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> !prompt.isHidden())
                .toList();
    }
}
