package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.brain.AiDecision;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.strategy.OverruleChoice;
import ti4.ai.strategy.OverruleChoice.Option;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
class OverruleCard {

    static final String OVERRULE = "overrule";
    private static final String PICK_PREFIX = "winnuHero_";
    private static final String SUFFIX = "_overrule";

    static AiCard overrule() {
        return AiActionCards.component(
                        OVERRULE,
                        Evaluator.atMoment(OverruleCard::plan),
                        "overruleStart",
                        List.of(PICK_PREFIX),
                        (context, key, play) -> {
                            Optional<AiDecision> decision =
                                    pick(initiativeOf(play.extra())).next(context, key, play);
                            decision.filter(AiDecision.Press.class::isInstance)
                                    .map(AiDecision.Press.class::cast)
                                    .map(press -> initiativeOf(StringUtils.removeStart(
                                            press.button().handlerId(), PICK_PREFIX)))
                                    .filter(initiative -> initiative > 0)
                                    .ifPresent(
                                            initiative -> StrategyCardRules.beginOverruledPrimary(context, initiative));
                            return decision;
                        })
                .withKeepFloor(OverruleReserve::keepFloor);
    }

    private static TargetStep pick(int planned) {
        return CardChains.chain(CardChains.last(
                PICK_PREFIX,
                Source.ANYONE,
                (context, suffix) -> pickValue(context.game(), context.seat(), initiativeOf(suffix), planned),
                "overrule"));
    }

    static Optional<Plan> plan(Game game, Player seat, PlayMoment moment) {
        if (game.isFowMode()) return Optional.empty();
        return keptOptions(game, seat, moment).stream()
                .map(option -> planOf(game, option))
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target));
    }

    private static Plan planOf(Game game, Option option) {
        Plan plan = Plan.aimedAt(
                option.value(),
                option.initiative() + SUFFIX,
                "on " + Helper.getSCName(option.initiative(), game) + ".");
        return option.pointsPlay() ? plan.urgently() : plan;
    }

    private static List<Option> keptOptions(Game game, Player seat, PlayMoment moment) {
        List<Option> options = game.getSCList().stream()
                .map(initiative -> OverruleChoice.option(game, seat, initiative))
                .flatMap(Optional::stream)
                .toList();
        if (moment == PlayMoment.IN_TURN && OverruleChoice.takesMecatolFirst(game, seat)) {
            return options.stream().filter(Option::wins).toList();
        }
        boolean reserved = OverruleReserve.active(game, seat, moment);
        if (reserved && !ScoringStakes.lateGame(game)) {
            return options.stream().filter(Option::wins).toList();
        }
        return options.stream()
                .filter(option -> option.pointsPlay() || !reserved || option.value() >= OverruleReserve.VALUE)
                .toList();
    }

    private static double pickValue(Game game, Player seat, int initiative, int planned) {
        Optional<Option> option = OverruleChoice.option(game, seat, initiative);
        if (option.isEmpty()) return Double.NEGATIVE_INFINITY;
        List<Option> kept = keptOptions(game, seat, PlayMoment.IN_TURN);
        boolean allowed = initiative == planned
                || kept.isEmpty()
                || kept.stream().anyMatch(other -> other.initiative() == initiative);
        return allowed ? option.get().value() : Double.NEGATIVE_INFINITY;
    }

    private static int initiativeOf(String suffix) {
        String number = suffix.endsWith(SUFFIX) ? suffix.substring(0, suffix.length() - SUFFIX.length()) : suffix;
        return number.chars().allMatch(Character::isDigit) && !number.isEmpty() ? Integer.parseInt(number) : -1;
    }
}
