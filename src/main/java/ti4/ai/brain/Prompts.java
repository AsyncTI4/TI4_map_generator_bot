package ti4.ai.brain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

@UtilityClass
public class Prompts {

    private static final long SAME_TURN_TOLERANCE_MILLIS = 5_000L;

    public record Match(AiPrompt prompt, PromptButton button) {
        public AiDecision press(String reason) {
            return AiDecision.press(prompt, button, reason);
        }
    }

    public static List<AiPrompt> newestFirst(List<AiPrompt> prompts) {
        return prompts.stream()
                .sorted(Comparator.comparingLong(AiPrompt::createdAtMillis).reversed())
                .toList();
    }

    public static long turnStart(AiTurnContext context) {
        return context.game().getLastActivePlayerChange().getTime() - SAME_TURN_TOLERANCE_MILLIS;
    }

    public static List<AiPrompt> thisTurn(AiTurnContext context) {
        long turnStart = turnStart(context);
        return newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.createdAtMillis() >= turnStart)
                .toList();
    }

    public static Optional<Match> owned(List<AiPrompt> prompts, String faction, Predicate<String> handler) {
        return first(prompts, button -> button.isOwnedBy(faction) && handler.test(button.handlerId()));
    }

    public static Optional<Match> unowned(List<AiPrompt> prompts, Predicate<String> handler) {
        return first(prompts, button -> button.isUnowned() && handler.test(button.handlerId()));
    }

    public static Optional<Match> first(List<AiPrompt> prompts, Predicate<PromptButton> predicate) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> button = prompt.firstEnabled(predicate);
            if (button.isPresent()) return Optional.of(new Match(prompt, button.get()));
        }
        return Optional.empty();
    }

    public static boolean mentions(AiPrompt prompt, Player seat) {
        String content = prompt.content();
        return content.contains(seat.getRepresentation())
                || content.contains(seat.getRepresentationUnfogged())
                || content.contains(seat.getRepresentationNoPing());
    }

    public static boolean createdThisTurn(AiTurnContext context, AiPrompt prompt) {
        return prompt.createdAtMillis() >= turnStart(context);
    }

    public static Optional<PromptButton> in(AiPrompt prompt, Predicate<PromptButton> predicate) {
        return prompt.firstEnabled(predicate);
    }

    public static Optional<AiPrompt> newestWith(List<AiPrompt> prompts, Predicate<PromptButton> predicate) {
        return prompts.stream()
                .filter(prompt -> prompt.firstEnabled(predicate).isPresent())
                .findFirst();
    }
}
