package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
public class RelicActionRules {

    public static Optional<AiDecision> insteadOfTacticalAction(AiTurnContext context, List<AiPrompt> thisTurn) {
        return FragmentRules.insteadOfTacticalAction(context, thisTurn)
                .or(() -> EnigmaticDeviceRules.insteadOfTacticalAction(context, thisTurn));
    }

    public static Optional<AiDecision> beforePassing(AiTurnContext context, List<AiPrompt> thisTurn) {
        return FragmentRules.beforePassing(context, thisTurn)
                .or(() -> EnigmaticDeviceRules.beforePassing(context, thisTurn));
    }

    public static int stallsAvailable(Player seat) {
        int device = seat.hasRelicReady(Constants.ENIGMATIC_DEVICE) ? 1 : 0;
        return device + (FragmentRules.canMakeRelic(seat) ? 1 : 0);
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        return FragmentRules.next(context).or(() -> EnigmaticDeviceRules.next(context));
    }
}
