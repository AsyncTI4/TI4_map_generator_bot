package ti4.ai.letnev;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.tactical.CombatMoment;

@UtilityClass
public class LetnevCombatRules {

    public static Optional<AiDecision> atRoundStart(AiTurnContext context, CombatMoment moment) {
        return ViscountUnlennRules.atRoundStart(context, moment)
                .or(() -> MunitionsReservesRules.atRoundStart(context, moment))
                .or(() -> DunlainReaperRules.atRoundStart(context, moment));
    }
}
