package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.tactical.CombatRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
class AiStallDetector {

    private static final long TURN_START_TOLERANCE_MILLIS = 5_000L;

    record WaitReason(String description, Predicate<AiPrompt> related) {}

    static List<WaitReason> waitReasons(Game game, Player seat, List<AiPrompt> prompts) {
        List<WaitReason> reasons = new ArrayList<>();
        String faction = seat.getFaction();
        String phase = game.getPhaseOfGame();
        int round = game.getRound();
        if (seat.getUserID().equals(game.getActivePlayerID())) {
            long turnStart = game.getLastActivePlayerChange().getTime() - TURN_START_TOLERANCE_MILLIS;
            reasons.add(new WaitReason(
                    "its turn",
                    prompt -> !prompt.isHidden()
                            && prompt.createdAtMillis() >= turnStart
                            && prompt.buttons().stream().anyMatch(button -> button.isOwnedBy(faction))));
        }
        if ("statusScoring".equalsIgnoreCase(phase)) {
            if (game.getStoredValue(faction + "round" + round + "PO").isEmpty()) {
                reasons.add(new WaitReason(
                        "public objective scoring",
                        prompt -> !prompt.isHidden() && prompt.hasHandlerPrefix(Constants.PO_NO_SCORING)));
            }
            if (game.getStoredValue(faction + "round" + round + "SO").isEmpty()) {
                reasons.add(new WaitReason(
                        "secret objective scoring",
                        prompt -> !prompt.isHidden() && prompt.hasHandlerPrefix(Constants.SO_NO_SCORING)));
            }
        }
        if ("statusHomework".equalsIgnoreCase(phase)
                && game.getStoredValue("statusHomeworkReactionFor" + faction + "Round" + round)
                        .isEmpty()) {
            reasons.add(new WaitReason(
                    "status phase homework",
                    prompt -> !prompt.isHidden() && prompt.hasHandlerPrefix("redistributeCCButtons")));
        }
        for (Integer initiative : game.getPlayedSCs()) {
            if (!seat.hasFollowedSC(initiative) && !seat.getSCs().contains(initiative)) {
                reasons.add(new WaitReason(
                        "following strategy card " + initiative,
                        prompt -> !prompt.isHidden() && prompt.hasHandlerPrefix("sc_no_follow_" + initiative)));
            }
        }
        Predicate<AiPrompt> combatAwaitsSeat = prompt -> CombatRules.awaitsSeat(game, seat, prompts, prompt);
        if (prompts.stream().anyMatch(combatAwaitsSeat)) reasons.add(new WaitReason("a combat", combatAwaitsSeat));
        if (round <= 1
                && !game.isExtraSecretMode()
                && seat.getSecretsUnscored().size() > 1
                && NekroBrain.noRealObjectiveRevealed(game)) {
            reasons.add(new WaitReason(
                    "discarding a starting secret objective",
                    prompt -> prompt.isHidden() && prompt.hasHandlerPrefix("discardSecret_")));
        }
        Predicate<AiPrompt> agendaQueue = prompt -> prompt.isHidden()
                && (prompt.hasHandlerPrefix("declineToQueueAWhen") || prompt.hasHandlerPrefix("declineToQueueAnAfter"));
        if (prompts.stream().anyMatch(agendaQueue))
            reasons.add(new WaitReason("a \"when\"/\"after\" decision", agendaQueue));
        return reasons;
    }

    static String describe(List<WaitReason> reasons) {
        return String.join(", ", reasons.stream().map(WaitReason::description).toList());
    }
}
