package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.brain.AiDecision;
import ti4.ai.strategy.OverruleChoice;
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
        TargetStep pick = CardChains.chain(CardChains.last(
                PICK_PREFIX,
                Source.ANYONE,
                (context, suffix) -> value(context.game(), context.seat(), initiativeOf(suffix))
                        .orElse(Double.NEGATIVE_INFINITY),
                "overrule"));
        return AiActionCards.component(
                OVERRULE, OverruleCard::plan, "overruleStart", List.of(PICK_PREFIX), (context, key, play) -> {
                    Optional<AiDecision> decision = pick.next(context, key, play);
                    decision.filter(AiDecision.Press.class::isInstance)
                            .map(AiDecision.Press.class::cast)
                            .map(press -> initiativeOf(
                                    StringUtils.removeStart(press.button().handlerId(), PICK_PREFIX)))
                            .filter(initiative -> initiative > 0)
                            .ifPresent(initiative -> StrategyCardRules.beginOverruledPrimary(context, initiative));
                    return decision;
                });
    }

    static Optional<Plan> plan(Game game, Player seat) {
        if (game.isFowMode()) return Optional.empty();
        return game.getSCList().stream()
                .filter(initiative -> OverruleChoice.canPlay(game, initiative))
                .map(initiative -> Plan.aimedAt(
                        value(game, seat, initiative).orElse(Double.NEGATIVE_INFINITY),
                        initiative + SUFFIX,
                        "on " + Helper.getSCName(initiative, game) + "."))
                .filter(plan -> plan.value() > Double.NEGATIVE_INFINITY)
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target));
    }

    private static Optional<Double> value(Game game, Player seat, int initiative) {
        return OverruleChoice.value(game, seat, initiative);
    }

    private static int initiativeOf(String suffix) {
        String number = suffix.endsWith(SUFFIX) ? suffix.substring(0, suffix.length() - SUFFIX.length()) : suffix;
        return number.chars().allMatch(Character::isDigit) && !number.isEmpty() ? Integer.parseInt(number) : -1;
    }
}
