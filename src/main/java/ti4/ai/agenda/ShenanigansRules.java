package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;

@UtilityClass
public class ShenanigansRules {

    private static final String END_PHASE = "agendaEnd";
    private static final Set<String> HANDLERS =
            Set.of("generic_button_id_1", "generic_button_id_2", "generic_button_id_3", "genericReact4");
    private static final List<String> LABELS =
            List.of("No Deadly Plot", "No Bribery", "Refuse Confounding Legal Text", "Refuse Confusing Legal Text");
    private static final long MIN_DELAY_SECONDS = 5;
    private static final int DELAY_SPREAD_SECONDS = 20;

    private record Pending(AiPrompt prompt, PromptButton button, long readyAt) {}

    public static Optional<AiDecision> react(AiTurnContext context) {
        Game game = context.game();
        if (game.isFowMode() || !END_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        Optional<Pending> next = pending(context).stream().min(Comparator.comparingLong(Pending::readyAt));
        if (next.isEmpty()) return Optional.empty();
        if (context.now() < next.get().readyAt()) {
            return Optional.of(new AiDecision.Wait(next.get().readyAt(), "the agenda shenanigans"));
        }
        return Optional.of(AiDecision.press(
                next.get().prompt(),
                next.get().button(),
                "confirm it plays none of the agenda shenanigans: "
                        + next.get().button().label()));
    }

    public static boolean isShenanigansButton(PromptButton button) {
        return button.isUnowned() && HANDLERS.contains(button.handlerId()) && LABELS.contains(button.label());
    }

    private static List<Pending> pending(AiTurnContext context) {
        List<Pending> pending = new ArrayList<>();
        for (AiPrompt prompt : context.prompts()) {
            if (prompt.isHidden() || !AgendaClock.isInThisAgenda(context, prompt)) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (!isShenanigansButton(button) || context.alreadyPressed(prompt, button)) continue;
                pending.add(
                        new Pending(prompt, button, prompt.createdAtMillis() + delayMillis(context, prompt, button)));
            }
        }
        return pending;
    }

    private static long delayMillis(AiTurnContext context, AiPrompt prompt, PromptButton button) {
        int seed = (prompt.messageId() + button.handlerId()).hashCode()
                ^ (int) context.profile().seed();
        return TimeUnit.SECONDS.toMillis(MIN_DELAY_SECONDS + Math.floorMod(seed, DELAY_SPREAD_SECONDS));
    }
}
