package ti4.ai.actioncards;

import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardStats.CardStats;
import ti4.game.Game;
import ti4.helpers.ActionCardHelper;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.model.Mapper;

@UtilityClass
public class ActionCardPrior {

    public static final double AVERAGE_CARD = 1.2;
    static final double NEUTRAL_STRENGTH = 1.0;
    static final double MIN_STRENGTH = 0.5;
    static final double MAX_STRENGTH = 2.0;
    static final double CANCEL_APPETITE_CAP = 4.0;
    static final double CANCEL_SHRINK_PLAYS = 50.0;
    static final double ONE_OF_RETRIEVAL = 0.065;
    private static final double FALLBACK_IMPACT = 30.0;
    private static final double FALLBACK_CANCEL_RATE = 0.0442;
    private static final double THIN_PULL = 0.5;
    private static final double STRENGTH_SHARE = 0.5;

    public static String nameOf(String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        return model == null ? alias : model.getName();
    }

    public static Optional<String> aliasOfTitle(Game game, String title) {
        DeckModel deck = Mapper.getDeck(game.getAcDeckID());
        Stream<String> deckCards = deck == null ? Stream.empty() : deck.getCardIDs().stream();
        return Stream.concat(deckCards, Mapper.getActionCards().keySet().stream())
                .filter(alias -> title.equals(nameOf(alias)))
                .findFirst();
    }

    public static double strength(String alias) {
        Optional<Double> expert = ExpertTiers.strengthOfName(nameOf(alias));
        Optional<Double> human = stats(alias).map(ActionCardPrior::strength);
        if (expert.isEmpty()) return human.orElse(NEUTRAL_STRENGTH);
        double blended = human.map(strength -> (strength + expert.get()) / 2).orElse(expert.get());
        return Math.clamp(blended, MIN_STRENGTH, MAX_STRENGTH);
    }

    public static double keepPrior(String alias) {
        return AVERAGE_CARD * (1 - STRENGTH_SHARE + STRENGTH_SHARE * strength(alias));
    }

    public static double cancelAppetite(String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        if (model != null && ActionCardHelper.cannotBeSabotaged(model)) return 0;
        double typical = typicalCancelRate();
        return stats(alias)
                .map(card -> Math.min(CANCEL_APPETITE_CAP, shrunkCancelRate(card, typical) / typical))
                .orElse(1.0);
    }

    public static double firstPlayRate(String alias) {
        return firstPlayRateOfName(nameOf(alias));
    }

    public static double firstPlayRateOfName(String name) {
        return ActionCardStats.byName(name).map(ActionCardPrior::firstPlayRate).orElse(0.0);
    }

    public static double retrieval(String alias) {
        return stats(alias).map(ActionCardPrior::retrieval).orElse(ONE_OF_RETRIEVAL);
    }

    private static Optional<CardStats> stats(String alias) {
        return ActionCardStats.byName(nameOf(alias));
    }

    private static double strength(CardStats card) {
        double reference = referenceImpact();
        double strength = Math.clamp(card.impact() / reference, MIN_STRENGTH, MAX_STRENGTH);
        return card.thin() ? strength + THIN_PULL * (NEUTRAL_STRENGTH - strength) : strength;
    }

    private static double referenceImpact() {
        double average = ActionCardStats.average().impact();
        return average > 0 ? average : FALLBACK_IMPACT;
    }

    private static double typicalCancelRate() {
        double weighted = ActionCardStats.average().cancelRateWeighted();
        return weighted > 0 ? weighted : FALLBACK_CANCEL_RATE;
    }

    private static double shrunkCancelRate(CardStats card, double typical) {
        return (card.cancels() + CANCEL_SHRINK_PLAYS * typical) / (card.plays() + CANCEL_SHRINK_PLAYS);
    }

    private static double firstPlayRate(CardStats card) {
        if (!card.isSingleCopy() || card.replays() == null) return card.playRate();
        int draws = ActionCardStats.snapshot().estimatedDrawsPerCopy();
        return draws > 0 ? (double) (card.plays() - card.replays()) / draws : card.playRate();
    }

    private static double retrieval(CardStats card) {
        if (!card.isSingleCopy() || card.replays() == null) return ONE_OF_RETRIEVAL;
        int firstPlays = card.plays() - card.replays();
        return firstPlays > 0 ? (double) card.replays() / firstPlays : ONE_OF_RETRIEVAL;
    }
}
