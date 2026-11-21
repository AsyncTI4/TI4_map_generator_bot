package ti4.ai.brain;

import java.util.Set;

public interface FactionBrain {

    String id();

    Set<String> publicWindowHandlerPrefixes();

    AiDecision decide(AiTurnContext context);
}
