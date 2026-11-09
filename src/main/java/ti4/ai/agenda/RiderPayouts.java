package ti4.ai.agenda;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.nekro.NekroRules;
import ti4.ai.perception.AiPerception;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class RiderPayouts {

    private static final String GET_A_TECHNOLOGY = "acquireATech";
    private static final String GAIN_TOKENS_TEXT = "Use buttons to gain command tokens";
    private static final String LEADERSHIP_KEY = "leadershipRiderGain|";
    private static final int LEADERSHIP_TOKENS = 3;
    private static final long RECENT_MILLIS = 30 * 60_000L;
    private static final long ANNOUNCEMENT_TOLERANCE_MILLIS = 10_000L;
    private static final String SEPARATOR = "|";

    public static Optional<AiDecision> technology(AiTurnContext context) {
        Player seat = context.seat();
        if (StrategyCardRules.cannotResearch(seat)) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!isRecentPublicMessageNaming(context, prompt, RiderPayoff.TECHNOLOGY)) continue;
            Optional<PromptButton> research =
                    prompt.firstEnabled(button -> button.isUnowned() && GET_A_TECHNOLOGY.equals(button.handlerId()));
            if (research.isEmpty() || context.alreadyPressed(prompt, research.get())) continue;
            StrategyCardRules.expectResearch(context, 0);
            return Optional.of(AiDecision.press(prompt, research.get(), "research the technology its rider won"));
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> leadership(AiTurnContext context) {
        List<AiPrompt> prompts = Prompts.newestFirst(context.prompts());
        for (AiPrompt prompt : prompts) {
            if (prompt.isHidden() || !Prompts.mentions(prompt, context.seat())) continue;
            Optional<LeadershipGain> gain = leadershipGain(context, prompt, prompts);
            if (gain.isEmpty()) continue;
            Optional<AiDecision> press = NekroRules.gainTokens(
                    context,
                    prompt,
                    gain.get().gained(context.seat()),
                    gain.get().target(),
                    LEADERSHIP_KEY + prompt.messageId(),
                    "its Leadership Rider");
            if (press.isPresent()) return press;
        }
        return Optional.empty();
    }

    private record LeadershipGain(int target, int baseline) {

        int gained(Player seat) {
            return totalTokens(seat) - baseline;
        }

        String encode() {
            return target + SEPARATOR + baseline;
        }

        static Optional<LeadershipGain> decode(String stored) {
            String[] parts = stored.split(Pattern.quote(SEPARATOR));
            if (parts.length != 2 || !StringUtils.isNumeric(parts[0]) || !StringUtils.isNumeric(parts[1])) {
                return Optional.empty();
            }
            return Optional.of(new LeadershipGain(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        }
    }

    private static Optional<LeadershipGain> leadershipGain(
            AiTurnContext context, AiPrompt prompt, List<AiPrompt> prompts) {
        String key = LEADERSHIP_KEY + prompt.messageId();
        Optional<LeadershipGain> remembered = context.memory().get(key).flatMap(LeadershipGain::decode);
        if (remembered.isPresent()) return remembered;
        if (!prompt.content().contains(GAIN_TOKENS_TEXT) || !announcedBefore(context, prompt, prompts)) {
            return Optional.empty();
        }
        Player seat = context.seat();
        int room = Math.max(0, seat.getCommandTokenLimit() - Helper.getCCCount(context.game(), seat.getColor()));
        LeadershipGain gain = new LeadershipGain(Math.min(LEADERSHIP_TOKENS, room), totalTokens(seat));
        context.memory().put(key, gain.encode());
        return Optional.of(gain);
    }

    private static boolean announcedBefore(AiTurnContext context, AiPrompt gainPrompt, List<AiPrompt> prompts) {
        return prompts.stream()
                .filter(prompt -> !prompt.isHidden())
                .filter(prompt -> prompt.content().contains(AiPerception.LEADERSHIP_RIDER_PAYOUT))
                .filter(prompt -> Prompts.mentions(prompt, context.seat()))
                .anyMatch(prompt -> gainPrompt.createdAtMillis() >= prompt.createdAtMillis()
                        && gainPrompt.createdAtMillis() - prompt.createdAtMillis() <= ANNOUNCEMENT_TOLERANCE_MILLIS);
    }

    private static int totalTokens(Player seat) {
        return seat.getTacticalCC() + seat.getFleetCC() + seat.getStrategicCC();
    }

    private static boolean isRecentPublicMessageNaming(AiTurnContext context, AiPrompt prompt, String rider) {
        return !prompt.isHidden()
                && context.now() - prompt.createdAtMillis() <= RECENT_MILLIS
                && prompt.content().contains(rider)
                && Prompts.mentions(prompt, context.seat());
    }
}
