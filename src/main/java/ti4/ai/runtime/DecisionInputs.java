package ti4.ai.runtime;

import java.util.Objects;
import java.util.Set;
import ti4.ai.brain.AiTurnContext;

record DecisionInputs(long modified, int inputs) {

    static final String FIRST = "first";
    static final String UNCHANGED = "unchanged";
    static final String SAME_SAVE = "same save";
    static final String SAVED = "saved";

    static DecisionInputs of(AiTurnContext context) {
        return new DecisionInputs(
                context.game().getLastModifiedDate(),
                Objects.hash(
                        context.prompts(),
                        Set.copyOf(context.pressedKeys()),
                        context.memory().snapshot()));
    }

    String changeSince(DecisionInputs previous) {
        if (previous == null) return FIRST;
        if (modified != previous.modified) return SAVED;
        return inputs == previous.inputs ? UNCHANGED : SAME_SAVE;
    }
}
