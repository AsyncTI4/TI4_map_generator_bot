package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class ActionCardPriorTest extends BaseTi4Test {

    private static final String DEFAULT_DECK = "action_cards_te";

    // The statistics are keyed by card name; a card of the default deck without an entry would be valued as an
    // unknown card, which is how a deck change goes unnoticed.
    @Test
    void theSnapshotCoversEveryCardOfTheDefaultDeck() {
        List<String> missing = Mapper.getDeck(DEFAULT_DECK).getCardIDs().stream()
                .filter(id -> ActionCardStats.byName(ActionCardPrior.nameOf(id)).isEmpty())
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void theSnapshotKnowsHowManyCopiesTheDeckHolds() {
        List<String> ids = Mapper.getDeck(DEFAULT_DECK).getCardIDs();

        for (ActionCardStats.CardStats card : ActionCardStats.all()) {
            long copies = ids.stream()
                    .filter(id -> card.name().equals(ActionCardPrior.nameOf(id)))
                    .count();
            assertThat(card.copies()).as(card.name()).isEqualTo((int) copies);
        }
    }

    @Test
    void strongerCardsAreWorthKeepingLonger() {
        assertThat(ActionCardPrior.keepPrior("overrule"))
                .isGreaterThan(ActionCardPrior.keepPrior("sabo1"))
                .isGreaterThan(ActionCardPrior.keepPrior("mining_initiative"));
        assertThat(ActionCardPrior.keepPrior("sabo1"))
                .isGreaterThan(ActionCardPrior.keepPrior("mining_initiative"))
                .isGreaterThan(ActionCardPrior.keepPrior("mb1"))
                .isGreaterThan(ActionCardPrior.keepPrior("mjets1"));
    }

    // Cards the experts have not ranked (Parley, Uprising, Sabotage) keep the strength the statistics give them.
    @Test
    void keepPriorsOfUnrankedCardsFollowTheImpactScore() {
        assertThat(ActionCardPrior.keepPrior("parley")).isCloseTo(1.57, within(0.005));
        assertThat(ActionCardPrior.keepPrior("uprising")).isCloseTo(1.52, within(0.005));
        assertThat(ActionCardPrior.keepPrior("sabo1")).isCloseTo(1.41, within(0.005));
    }

    // Impact 33.6 gives Lie in Wait a statistics strength of 1.68; the experts put it third of six in A (1.7 to 1.4).
    @Test
    void aRankedCardIsWorthTheMeanOfWhatTheStatisticsAndTheExpertsSay() {
        double expert = 1.7 - 0.4 * 0.3;

        assertThat(ActionCardPrior.strength("lieinwait")).isCloseTo((1.683 + expert) / 2, within(0.005));
        assertThat(ActionCardPrior.keepPrior("lieinwait"))
                .isCloseTo(1.2 * (0.5 + 0.5 * (1.683 + expert) / 2), within(0.005));
    }

    // The statistics rate Impersonation, Leadership Rider and Morale Boost differently from the experts.
    @Test
    void theExpertsMoveCardsTheStatisticsMisjudged() {
        assertThat(ActionCardPrior.keepPrior("mb1")).isLessThan(1.10);
        assertThat(ActionCardPrior.strength("mb1")).isLessThan(0.9);
        assertThat(ActionCardPrior.keepPrior("mining_initiative")).isCloseTo(1.32, within(0.005));
        assertThat(ActionCardPrior.keepPrior("mjets1")).isCloseTo(0.9, within(0.02));
    }

    @Test
    void theBlendStaysWithinTheStrengthBounds() {
        for (String alias : Mapper.getDeck(DEFAULT_DECK).getCardIDs()) {
            assertThat(ActionCardPrior.strength(alias)).as(alias).isBetween(0.5, 2.0);
        }
    }

    // A card with few recorded plays has a less certain impact, so it is pulled halfway towards an average card.
    @Test
    void aThinCardIsPulledTowardsTheAverage() {
        double impactStrength = 17.9 / 30;
        double pulled = impactStrength + 0.5 * (1 - impactStrength);

        assertThat(ActionCardPrior.strength("salvage")).isCloseTo(pulled, within(0.001));
    }

    @Test
    void aCardWithoutStatisticsIsAnAverageCard() {
        assertThat(ActionCardPrior.keepPrior("not_a_card")).isEqualTo(ActionCardPrior.AVERAGE_CARD);
        assertThat(ActionCardPrior.cancelAppetite("not_a_card")).isEqualTo(1.0);
        assertThat(ActionCardPrior.firstPlayRate("not_a_card")).isZero();
    }

    @Test
    void cancelAppetiteIsTheShrunkCancelRateOverTheTypicalOne() {
        assertThat(ActionCardPrior.cancelAppetite("overrule")).isEqualTo(ActionCardPrior.CANCEL_APPETITE_CAP);
        assertThat(ActionCardPrior.cancelAppetite("lieinwait")).isEqualTo(ActionCardPrior.CANCEL_APPETITE_CAP);
        assertThat(ActionCardPrior.cancelAppetite("rout")).isCloseTo(3.89, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("confounding")).isCloseTo(3.73, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("stability")).isCloseTo(3.45, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("parley")).isCloseTo(3.44, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("disgrace")).isCloseTo(3.26, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("puppetsonastring")).isCloseTo(2.95, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("coup")).isCloseTo(2.66, within(0.01));
        assertThat(ActionCardPrior.cancelAppetite("mining_initiative")).isLessThan(0.5);
    }

    @Test
    void cardsThatCannotBeCancelledHaveNoCancelAppetite() {
        assertThat(ActionCardPrior.cancelAppetite("sabo1")).isZero();
        assertThat(ActionCardPrior.cancelAppetite("blackmarketdealing")).isZero();
    }

    // A replay is a play after the first one, almost always after the card came back out of the discard pile.
    @Test
    void aSingleCopyCardCountsOnlyItsFirstPlay() {
        double draws = ActionCardStats.snapshot().estimatedDrawsPerCopy();

        assertThat(ActionCardPrior.firstPlayRate("overrule")).isCloseTo((923 - 225) / draws, within(1e-9));
        assertThat(ActionCardPrior.firstPlayRate("sabo1")).isCloseTo(0.686, within(1e-9));
    }

    @Test
    void retrievalIsReplaysPerFirstPlay() {
        assertThat(ActionCardPrior.retrieval("overrule")).isCloseTo(225.0 / (923 - 225), within(1e-9));
        assertThat(ActionCardPrior.retrieval("sabo1")).isEqualTo(ActionCardPrior.ONE_OF_RETRIEVAL);
    }
}
