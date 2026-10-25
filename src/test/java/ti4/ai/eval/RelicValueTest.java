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
        test.game.setRelics(new ArrayList<>(List.of("shard", "circlet")));
    }

    // Drawing from an empty relic deck gives nothing (LRR 73.2a).
    @Test
    void anEmptyDeckIsWorthNothing() {
        test.game.setRelics(new ArrayList<>());

        assertThat(RelicValue.draw(test.game)).isZero();
    }

    @Test
    void aRelicIsWorthItsBaseWithRoundsToUseIt() {
        test.game.setRound(1);

        assertThat(RelicValue.draw(test.game)).isCloseTo(RelicValue.BASE, within(EXACT));
    }

    // A leader one point from winning has little time to use a relic.
    @Test
    void aRelicShrinksWhenTheGameIsAboutToEnd() {
        test.points(test.nekro, 9);

        assertThat(RelicValue.draw(test.game)).isCloseTo(RelicValue.BASE * 0.4, within(EXACT));
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
