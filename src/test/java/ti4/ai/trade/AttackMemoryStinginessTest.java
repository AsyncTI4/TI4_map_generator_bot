package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.scoring.ObjectiveValue;
import ti4.testUtils.BaseTi4Test;

// ATTACK_MEMORY: a partner who attacked the seat gets stingier deals. Each VP's worth of damage still remembered in
// the attack ledger adds the same tenth of rivalry a point of lead does, inside the rivalry cap.
class AttackMemoryStinginessTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final String LEDGER_KEY = "aiAttackLedger";

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
    }

    private void solAttackedNekro(double damage) {
        table.game.setStoredValue(LEDGER_KEY, table.game.getRound() + "/sol/nekro=" + damage);
    }

    private double rivalry() {
        return Stinginess.rivalry(table.game, table.nekro, table.sol);
    }

    @Test
    void aRecentAttackerIsTreatedAsAStrongerRival() {
        double peer = rivalry();
        solAttackedNekro(2 * VP);

        assertThat(peer).isCloseTo(0.1, within(EXACT));
        assertThat(rivalry()).isCloseTo(0.3, within(EXACT));
    }

    @Test
    void anAttackerFarBehindIsStillMinded() {
        table.points(table.nekro, 3);
        solAttackedNekro(VP);

        assertThat(rivalry()).isCloseTo(0.1, within(EXACT));
    }

    @Test
    void aControlSeatIgnoresTheLedger() {
        AiExperiments.disable(table.nekro, EnumSet.of(Experiment.ATTACK_MEMORY));
        solAttackedNekro(2 * VP);

        assertThat(rivalry()).isCloseTo(0.1, within(EXACT));
    }
}
