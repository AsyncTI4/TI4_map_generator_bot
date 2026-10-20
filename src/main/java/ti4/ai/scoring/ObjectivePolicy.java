package ti4.ai.scoring;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class ObjectivePolicy {

    public record ScoringChoice(PromptButton button, String objectiveId, Optional<Wallet.Payment> payment) {

        public boolean isSpend() {
            return payment.isPresent();
        }
    }

    public static Optional<ScoringChoice> bestScorablePublic(
            Game game, Player seat, List<PromptButton> scoringButtons) {
        if (!Helper.canPlayerScorePOs(game, seat)) return Optional.empty();
        Map<String, Integer> revealed = game.getRevealedPublicObjectives();
        Wallet wallet = Wallet.of(game, seat);
        boolean statusPhase = !"action".equalsIgnoreCase(game.getPhaseOfGame());
        return scoringButtons.stream()
                .map(button -> choice(game, seat, wallet, button, objectiveFor(revealed, button)))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt((ScoringChoice choice) -> victoryPoints(choice.objectiveId()))
                        .thenComparingInt(choice -> -realCost(choice))
                        .thenComparing(choice -> statusPhase == choice.isSpend()));
    }

    public static List<String> scorablePublics(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return List.of();
        Wallet wallet = Wallet.of(game, seat);
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> qualifies(game, seat, wallet, id))
                .toList();
    }

    public static boolean qualifies(Game game, Player seat, Wallet wallet, @Nullable String objectiveId) {
        if (objectiveId == null || hasScored(game, seat, objectiveId)) return false;
        if (Mapper.getPublicObjective(objectiveId) == null) return false;
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objectiveId, game);
        if (threshold <= 0 || ListPlayerInfoService.getPlayerProgressOnObjective(objectiveId, game, seat) < threshold) {
            return false;
        }
        return ObjectiveCatalog.spendCost(objectiveId).map(wallet::canPay).orElse(true);
    }

    public static boolean hasScored(Game game, Player seat, String objectiveId) {
        return game.getScoredPublicObjectives()
                .getOrDefault(objectiveId, List.of())
                .contains(seat.getUserID());
    }

    public static int victoryPoints(@Nullable String objectiveId) {
        PublicObjectiveModel model = objectiveId == null ? null : Mapper.getPublicObjective(objectiveId);
        return model == null ? 0 : model.getPoints();
    }

    @Nullable
    public static String objectiveFor(Map<String, Integer> revealed, PromptButton button) {
        String value = StringUtils.substringAfterLast(button.handlerId(), "_");
        if (!StringUtils.isNumeric(value)) return null;
        int id = Integer.parseInt(value);
        return revealed.entrySet().stream()
                .filter(entry -> entry.getValue() == id)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Optional<ScoringChoice> choice(
            Game game, Player seat, Wallet wallet, PromptButton button, @Nullable String objectiveId) {
        if (!qualifies(game, seat, wallet, objectiveId)) return Optional.empty();
        Optional<Wallet.Payment> payment =
                ObjectiveCatalog.spendCost(objectiveId).flatMap(wallet::plan);
        return Optional.of(new ScoringChoice(button, objectiveId, payment));
    }

    private static int realCost(ScoringChoice choice) {
        return choice.payment()
                .map(payment -> payment.tradeGoods() + payment.tokens())
                .orElse(0);
    }
}
