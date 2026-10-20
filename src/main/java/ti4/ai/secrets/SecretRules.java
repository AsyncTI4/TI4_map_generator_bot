package ti4.ai.secrets;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

@UtilityClass
public class SecretRules {

    private static final int LAWS_FOR_DICTATE_POLICY = 3;

    public static Optional<AiDecision> scoreStatusSecret(AiTurnContext context) {
        Game game = context.game();
        if (!"statusScoring".equalsIgnoreCase(game.getPhaseOfGame())) return Optional.empty();
        if (!game.getStoredValue(context.faction() + "round" + game.getRound() + "SO")
                .isEmpty()) {
            return Optional.empty();
        }
        Player seat = context.seat();
        Optional<String> best = SecretValue.bestScorableStatusSecret(game, seat);
        if (best.isPresent()) {
            Optional<AiDecision> score = scoreWithCost(context, best.get(), "score a secret");
            if (score.isPresent()) return score;
        }
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            Optional<PromptButton> noScoring = prompt.enabledHandler(Constants.SO_NO_SCORING);
            if (noScoring.isPresent()) {
                return Optional.of(AiDecision.press(prompt, noScoring.get(), "no secret objective to score"));
            }
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> scoreAgendaSecret(AiTurnContext context) {
        Game game = context.game();
        if (!game.getPhaseOfGame().toLowerCase().startsWith("agenda")) return Optional.empty();
        Player seat = context.seat();
        for (String secret : seat.getSecretsUnscored().keySet()) {
            if (SecretPhase.of(secret) != SecretPhase.AGENDA || !agendaSecretMet(game, seat, secret)) continue;
            Optional<AiDecision> score = SecretScoring.score(context, secret, "score an agenda-phase secret");
            if (score.isPresent()) return score;
        }
        return Optional.empty();
    }

    static Optional<AiDecision> scoreWithCost(AiTurnContext context, String secretId, String reason) {
        Optional<AiDecision> score = SecretScoring.score(context, secretId, reason);
        if (score.filter(SecretRules::isScorePress).isPresent()) SecretCostRules.expect(context, secretId);
        return score;
    }

    private static boolean isScorePress(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                && press.button().handlerId().startsWith(Constants.SO_SCORE_FROM_HAND);
    }

    private static boolean agendaSecretMet(Game game, Player seat, String secretId) {
        if ("dp".equals(secretId)) return game.getLaws().size() >= LAWS_FOR_DICTATE_POLICY;
        if ("dtd".equals(secretId)) {
            if (!"agendaEnd".equalsIgnoreCase(game.getPhaseOfGame())) return false;
            String elected = game.getStoredValue("resolvedAgendaOutcome");
            if (elected.isBlank()) return false;
            return elected.equalsIgnoreCase(seat.getFaction())
                    || elected.equalsIgnoreCase(seat.getColor())
                    || seat.getPlanets().contains(elected);
        }
        return false;
    }
}
