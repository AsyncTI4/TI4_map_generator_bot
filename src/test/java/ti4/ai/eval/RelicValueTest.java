package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.trade.FragmentValue;
import ti4.ai.trade.FragmentValue.Holdings;
import ti4.testUtils.BaseTi4Test;

class RelicValueTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setRound(1);
    }

    private void deck(String... cards) {
        test.game.setRelics(new ArrayList<>(List.of(cards)));
    }

    private double of(String relic) {
        return RelicValue.of(test.game, test.nekro, relic);
    }

    // Drawing from an empty relic deck gives nothing (LRR 73.2a).
    @Test
    void anEmptyDeckIsWorthNothing() {
        deck();

        assertThat(RelicValue.draw(test.game, test.nekro)).isZero();
    }

    @Test
    void aDrawIsWorthTheAverageOfTheRelicsLeftInTheDeck() {
        deck("codex", "obsidian", "silverflame");

        assertThat(RelicValue.draw(test.game, test.nekro)).isCloseTo((7 + 8 + 0) / 3.0, within(EXACT));
    }

    @Test
    void fixedRelicsAreWorthTheirListedValue() {
        assertThat(of("codex")).isEqualTo(7);
        assertThat(of("obsidian")).isEqualTo(8);
        assertThat(of("bookoflatvinia")).isEqualTo(8);
        assertThat(of("dominusorb")).isEqualTo(4);
        assertThat(of("stellarconverter")).isEqualTo(1);
        assertThat(of("thesilverflame")).isZero();
        assertThat(of("heartofixth")).isZero();
    }

    @Test
    void decayingRelicsLoseHalfAPointARound() {
        assertThat(of("dynamiscore")).isEqualTo(4.5);
        assertThat(of("neuraloop")).isEqualTo(1.5);

        test.game.setRound(5);

        assertThat(of("dynamiscore")).isEqualTo(2.5);
        assertThat(of("quantumcore")).isZero();
    }

    @Test
    void theScepterLosesAPointARoundAndNeverGoesBelowZero() {
        assertThat(of("emelpar")).isEqualTo(5);

        test.game.setRound(7);

        assertThat(of("emelpar")).isZero();
    }

    @Test
    void theMawIsWorthNothingFromRoundFive() {
        test.game.setRound(4);
        assertThat(of("mawofworlds")).isEqualTo(4);

        test.game.setRound(5);
        assertThat(of("mawofworlds")).isZero();
    }

    @Test
    void theNanoForgeIsWorthLessToTheHolderOfTheShard() {
        double alone = of("nanoforge");
        test.nekro.addRelic("shard");

        assertThat(of("nanoforge")).isCloseTo(Math.max(0, alone - 4), within(EXACT));
    }

    @Test
    void theCrownOfThalnosNeedsWarSunsOrNaazRokha() {
        assertThat(of("thalnos")).isZero();

        test.nekro.setFaction("naaz");

        assertThat(of("thalnos")).isEqualTo(1);
    }

    @Test
    void theTriadGrowsWithEachKindOfFragment() {
        double none = of("thetriad");
        test.nekro.setCrf(1);
        test.nekro.setHrf(2);

        assertThat(of("thetriad")).isCloseTo(none + 2, within(EXACT));
    }

    // Fragments get worth more the closer they bring a relic: three of a kind are worth exactly one relic.
    @Test
    void aSetOfThreeFragmentsIsWorthOneRelicAndEachCompletesMoreThanTheLast() {
        double one = FragmentValue.worth(new Holdings(1, 0, 0, 0), false);
        double two = FragmentValue.worth(new Holdings(2, 0, 0, 0), false);
        double three = FragmentValue.worth(new Holdings(3, 0, 0, 0), false);

        assertThat(three).isCloseTo(RelicValue.BASE, within(EXACT));
        assertThat(three - two).isGreaterThan(two - one);
        assertThat(two - one).isGreaterThan(one);
    }

    // An unknown fragment stands in for any kind, so it turns a lone fragment into a pair.
    @Test
    void anUnknownFragmentJoinsALoneOneIntoAPair() {
        double pair = FragmentValue.worth(new Holdings(1, 0, 0, 1), false);

        assertThat(pair).isCloseTo(FragmentValue.worth(new Holdings(2, 0, 0, 0), false), within(EXACT));
    }
}
