package ti4.ai.winnu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Player;
import ti4.model.FactionModel;

@UtilityClass
public class StartingTechRules {

    private static final String GET_TECH = "getTech_";
    private static final String FREE_SUFFIX = "__noPay__comp";
    private static final int SETUP_ROUND = 1;

    public static Optional<AiDecision> choose(AiTurnContext context) {
        Player seat = context.seat();
        List<String> options = openOptions(context);
        if (options.isEmpty()) return Optional.empty();
        Map<String, Match> offered = new LinkedHashMap<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            for (PromptButton button : prompt.enabledButtons()) {
                String alias = startingTech(button);
                if (!button.isOwnedBy(context.faction()) || !options.contains(alias)) continue;
                if (context.alreadyPressed(prompt, button)) continue;
                offered.putIfAbsent(alias, new Match(prompt, button));
            }
        }
        if (offered.isEmpty()) return Optional.empty();
        return ResearchPolicy.best(context.game(), seat, offered.keySet())
                .map(best -> offered.get(best).press("take " + best + " as the starting technology"));
    }

    static List<String> openOptions(AiTurnContext context) {
        Player seat = context.seat();
        FactionModel model = seat.getFactionModel();
        if (context.game().getRound() > SETUP_ROUND
                || model == null
                || model.getStartingTechOptions() == null
                || model.getStartingTechAmount() == null) {
            return List.of();
        }
        List<String> options = model.getStartingTechOptions();
        long taken = options.stream().filter(seat::hasTech).count();
        if (taken >= model.getStartingTechAmount()) return List.of();
        return options.stream().filter(alias -> !seat.hasTech(alias)).toList();
    }

    private static String startingTech(PromptButton button) {
        String handler = button.handlerId();
        if (!handler.startsWith(GET_TECH) || !handler.endsWith(FREE_SUFFIX)) return "";
        return StringUtils.substringBefore(StringUtils.removeStart(handler, GET_TECH), "__");
    }
}
