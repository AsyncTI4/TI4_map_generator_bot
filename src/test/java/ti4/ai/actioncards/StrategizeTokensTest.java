package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.StrategyCard;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.strategy.TokenPlan;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Strategize spends a strategy token on a secondary, so a seat holding it should keep one more strategy token than
// the follows it expects.
class StrategizeTokensTest extends BaseTi4Test {

    private static final int HAND_INDEX = 412;
    private static final String GROW_TACTIC = "increase_tactic_cc";
    private static final String GROW_STRATEGY = "increase_strategy_cc";

    private CardTable table;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setTacticalCC(2);
        table.nekro.setFleetCC(3);
        table.nekro.setStrategicCC(2);
    }

    private void holdStrategize() {
        table.nekro.setActionCard("strategize3", HAND_INDEX);
    }

    private String nextToken() {
        return CommandTokenPolicy.poolToGrow(table.game, table.nekro);
    }

    private int strategyTarget() {
        return TokenPlan.of(table.game, table.nekro).strategy();
    }

    private void onlyThese(StrategyCard... cards) {
        List<StrategyCard> kept = List.of(cards);
        for (int initiative : table.game.getSCList()) {
            if (!kept.contains(StrategyCard.of(table.game, initiative))) table.game.setSCPlayed(initiative, true);
        }
    }

    @Test
    void statusPhaseKeepsATokenBeyondTheTwoForFollowsWhileHoldingStrategize() {
        table.game.setPhaseOfGame("status");
        assertThat(nextToken()).isEqualTo(GROW_TACTIC);

        holdStrategize();

        assertThat(nextToken()).isEqualTo(GROW_STRATEGY);
        table.nekro.setStrategicCC(3);
        assertThat(nextToken()).isEqualTo(GROW_TACTIC);
    }

    @Test
    void statusPhaseIgnoresAStrategizeThatWasPlayedOrDiscarded() {
        table.game.setPhaseOfGame("status");
        holdStrategize();
        table.nekro.getActionCards().remove("strategize3");

        assertThat(nextToken()).isEqualTo(GROW_TACTIC);
    }

    @Test
    void statusPhaseIgnoresStrategizeInAFogOfWarGame() {
        table.game.setPhaseOfGame("status");
        table.game.setFowMode(true);
        holdStrategize();

        assertThat(nextToken()).isEqualTo(GROW_TACTIC);
    }

    @Test
    void theTokenNeverComesBeforeTheTwoTacticTokensTheExistingPrioritiesKeep() {
        table.game.setPhaseOfGame("status");
        table.nekro.setTacticalCC(1);
        table.nekro.setStrategicCC(0);
        holdStrategize();

        assertThat(nextToken()).isEqualTo(GROW_TACTIC);
    }

    @Test
    void theTokenNeverComesBeforeTheFleetTokensTheExistingPrioritiesKeep() {
        table.game.setPhaseOfGame("status");
        table.nekro.setFleetCC(1);
        table.nekro.setStrategicCC(0);
        holdStrategize();
        table.ships(table.nekro, table.test.nekroHome(), UnitType.Carrier, 3);

        assertThat(nextToken()).isEqualTo("increase_fleet_cc");
    }

    @Test
    void actionPhaseCountsOneMoreStrategyTokenWhenAWorthwhileSecondaryIsOpen() {
        int without = strategyTarget();

        holdStrategize();

        assertThat(strategyTarget()).isEqualTo(without + 1);
    }

    @Test
    void actionPhaseCountsNothingWhenNoOpenSecondaryIsWorthAToken() {
        onlyThese(StrategyCard.LEADERSHIP, StrategyCard.TECHNOLOGY);
        int without = strategyTarget();

        holdStrategize();

        assertThat(strategyTarget()).isEqualTo(without);
    }

    @Test
    void actionPhaseCountsNothingOnceStrategizeIsPlayed() {
        int without = strategyTarget();
        holdStrategize();
        table.nekro.getActionCards().remove("strategize3");

        assertThat(strategyTarget()).isEqualTo(without);
    }

    @Test
    void actionPhaseGrowsTheStrategyPoolForTheSecondaryOnlyAfterTheEarlierPriorities() {
        table.nekro.setTacticalCC(1);
        table.nekro.setStrategicCC(0);
        holdStrategize();
        assertThat(nextToken()).isEqualTo(GROW_STRATEGY);

        table.nekro.setStrategicCC(strategyTarget());
        assertThat(nextToken()).isNotEqualTo(GROW_STRATEGY);
    }
}
