package ti4.ai.agenda;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;

@UtilityClass
public class AgendaClock {

    private static final String AGENDA_SEEN_KEY = "agendaSeen|";
    private static final String REACT_TIME = "lastAgendaReactTime";
    private static final long FUTURE_SKEW_MILLIS = 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    public static boolean isInThisAgenda(AiTurnContext context, AiPrompt prompt) {
        return prompt.createdAtMillis() >= seenAt(context) - CLOCK_SKEW_MILLIS && revealedBefore(context, prompt);
    }

    public static long seenAt(AiTurnContext context) {
        String key = AGENDA_SEEN_KEY + context.game().getCurrentAgendaInfo();
        Optional<String> seen = context.memory().get(key).filter(StringUtils::isNumeric);
        if (seen.isPresent()) return Long.parseLong(seen.get());
        context.memory().put(key, String.valueOf(context.now()));
        return context.now();
    }

    private static boolean revealedBefore(AiTurnContext context, AiPrompt prompt) {
        String revealed = context.game().getStoredValue(REACT_TIME);
        if (!StringUtils.isNumeric(revealed) || revealed.isEmpty()) return true;
        long at = Long.parseLong(revealed);
        return at > context.now() + FUTURE_SKEW_MILLIS || prompt.createdAtMillis() >= at - CLOCK_SKEW_MILLIS;
    }
}
