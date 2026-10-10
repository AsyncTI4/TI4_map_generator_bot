package ti4.ai.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class StrategyCardRanking {

    private static final Set<String> TECH_OBJECTIVES =
            Set.of("develop", "diversify", "master_science", "revolutionize");
    private static final int WANTED_TACTIC_TOKENS = 3;
    private static final double TACTIC_SHORTFALL_VALUE = 1.0;
    private static final Set<String> TRADE_GOOD_OBJECTIVES =
            Set.of("trade_routes", "centralize_trade", "amass_wealth", "vast_reserves");
    private static final double VICTORY_POINT_VALUE = 5.0;
    private static final double IMPERIAL_BASE = 2.0;
    private static final double SECRET_DRAW_VALUE = 1.0;
    private static final double ONE_STEP_SHORT_CHANCE = 0.4;
    private static final double CUSTODIANS_THIS_ROUND_CHANCE = 0.5;
    private static final int STATUS_PHASE_PUBLIC_SCORES = 1;
    private static final int IMPERIAL_PUBLIC_SCORES = 1;
    private static final int RESERVED_SPEND_SCORES = 1;
    private static final int CUSTODIANS_COST = 6;

    public static Optional<PromptButton> best(Game game, Player seat, List<PromptButton> pickButtons) {
        return pickButtons.stream()
                .filter(button -> initiative(button) > 0)
                .max(Comparator.comparingDouble((PromptButton button) -> value(game, seat, initiative(button)))
                        .thenComparing(StrategyCardRanking::initiative, Comparator.reverseOrder()));
    }

    public static double value(Game game, Player seat, int initiative) {
        return baseValue(game, seat, initiative) + game.getScTradeGoods().getOrDefault(initiative, 0);
    }

    public static double baseValue(Game game, Player seat, int initiative) {
        int round = game.getRound();
        double value =
                switch (StrategyCard.of(game, initiative)) {
                    case LEADERSHIP -> (round <= 2 ? 4.5 : 3.0) + TACTIC_SHORTFALL_VALUE * tacticShortfall(seat);
                    case DIPLOMACY -> 2.0;
                    case POLITICS -> 2.5;
                    case CONSTRUCTION -> StructurePolicy.wantsStructures(game, seat) ? 4.0 : 2.5;
                    case TRADE -> 3.5 + (anyUnscored(game, seat, TRADE_GOOD_OBJECTIVES) ? 1.5 : 0);
                    case WARFARE -> 3.5;
                    case TECHNOLOGY -> technologyValue(game, seat, round);
                    case IMPERIAL -> imperialValue(game, seat);
                    case OTHER -> 3.0;
                };
        return value;
    }

    public static int initiative(PromptButton button) {
        String number = StringUtils.substringAfterLast(button.handlerId(), "_");
        return StringUtils.isNumeric(number) ? Integer.parseInt(number) : -1;
    }

    private static int tacticShortfall(Player seat) {
        return Math.max(0, WANTED_TACTIC_TOKENS - seat.getTacticalCC());
    }

    private static double technologyValue(Game game, Player seat, int round) {
        if (seat.hasAbility("propagation")) return 1.0;
        double value = round <= 3 ? 5.0 : 3.5;
        boolean chasesTechObjective = game.getRevealedPublicObjectives().keySet().stream()
                .anyMatch(id -> TECH_OBJECTIVES.contains(id) && ResearchPolicy.withinReach(game, seat, id));
        return value + (chasesTechObjective ? 1.5 : 0);
    }

    private static double imperialValue(Game game, Player seat) {
        double value = IMPERIAL_BASE + VICTORY_POINT_VALUE * extraPublicScore(game, seat);
        if (seat.controlsMecatol(true)) return value + VICTORY_POINT_VALUE;
        if (takesCustodiansThisRound(game, seat)) value += CUSTODIANS_THIS_ROUND_CHANCE * VICTORY_POINT_VALUE;
        return hasRoomForSecret(seat) ? value + SECRET_DRAW_VALUE : value;
    }

    public static boolean imperialScoresNow(Game game, Player seat) {
        return seat.controlsMecatol(true)
                || !ObjectivePolicy.scorablePublics(game, seat).isEmpty();
    }

    private static double extraPublicScore(Game game, Player seat) {
        List<String> scorable = ObjectivePolicy.scorablePublics(game, seat);
        long free =
                scorable.stream().filter(id -> !ObjectiveCatalog.isSpend(id)).count();
        long spend = Math.min(RESERVED_SPEND_SCORES, scorable.size() - free);
        double expected = free + spend + ONE_STEP_SHORT_CHANCE * oneStepShortPublics(game, seat);
        return Math.clamp(expected - STATUS_PHASE_PUBLIC_SCORES, 0, IMPERIAL_PUBLIC_SCORES);
    }

    private static long oneStepShortPublics(Game game, Player seat) {
        if (!Helper.canPlayerScorePOs(game, seat)) return 0;
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> Mapper.getPublicObjective(id) != null && !ObjectiveCatalog.isSpend(id))
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .filter(id -> isOneStepShort(game, seat, id))
                .count();
    }

    private static boolean isOneStepShort(Game game, Player seat, String objectiveId) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objectiveId, game);
        return threshold > 1
                && ListPlayerInfoService.getPlayerProgressOnObjective(objectiveId, game, seat) == threshold - 1;
    }

    private static boolean takesCustodiansThisRound(Game game, Player seat) {
        return TokenPurchase.custodiansWithinReach(game, seat)
                && Wallet.of(game, seat).canPay(SpendCost.influence(CUSTODIANS_COST));
    }

    private static boolean hasRoomForSecret(Player seat) {
        return seat.getSoScored() + seat.getSecretsUnscored().size() < seat.getMaxSOCount();
    }

    private static boolean anyUnscored(Game game, Player seat, Set<String> objectives) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .anyMatch(id -> objectives.contains(id) && !ObjectivePolicy.hasScored(game, seat, id));
    }
}
