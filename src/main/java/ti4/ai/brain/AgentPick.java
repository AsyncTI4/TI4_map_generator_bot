package ti4.ai.brain;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;

@UtilityClass
public class AgentPick {

    private static final String PICK_STEP = "agentPick_";
    private static final String YSSARIL_COPY_FLAG = "~cc";

    public static Optional<PromptButton> target(
            AiPrompt prompt, String seatFaction, String agent, List<String> targets) {
        return prompt.firstEnabled(button -> button.isOwnedBy(seatFaction)
                && targets.stream()
                        .map(target -> PICK_STEP + agent + "_" + target)
                        .anyMatch(StringUtils.removeEnd(button.handlerId(), YSSARIL_COPY_FLAG)::equals));
    }
}
