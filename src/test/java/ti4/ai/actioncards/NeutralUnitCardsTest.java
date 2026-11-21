package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class NeutralUnitCardsTest extends BaseTi4Test {

    private static final String NEBULA = "42";
    private static final String EMPTY = "46";

    private CardTable table;
    private Tile home;
    private String next;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setTacticalCC(3);
        table.nekro.setStrategicCC(2);
        home = table.game.getTileByPosition(AiTestGame.HOME);
        next = AiTestGame.neighbourOf(AiTestGame.HOME);
        table.ships(table.nekro, home, UnitType.Dreadnought, 2);
    }

    private Optional<Plan> plan(String alias) {
        return AiActionCards.of(alias).orElseThrow().planFor(table.game, table.nekro);
    }

    private Optional<Plan> plan(String alias, PlayMoment moment) {
        return AiActionCards.of(alias).orElseThrow().planFor(table.game, table.nekro, moment);
    }

    @Test
    void aSeatHoldingBraveTheVoidPostsThePirateInAnAnomalyItCanAttackNextTurn() {
        table.system(NEBULA, next);
        table.nekro.setSecret("btv");

        Plan plan = plan("piratecontract1").orElseThrow();

        assertThat(plan.target()).isEqualTo(next);
        assertThat(plan.value()).isPositive();
    }

    @Test
    void withoutACombatSecretThePirateIsNotPostedInAnAnomalyForASetup() {
        table.system(NEBULA, next);

        assertThat(plan("piratecontract1")).isEmpty();
    }

    @Test
    void aSetupIsNotPlayedJustBeforePassingBecauseThereIsNoLaterAttack() {
        table.system(NEBULA, next);
        table.nekro.setSecret("btv");

        assertThat(plan("piratecontract1", PlayMoment.BEFORE_PASSING)).isEmpty();
    }

    @Test
    void theSetupSystemMayBeNextToTheSeatsOwnFleet() {
        table.system(EMPTY, next);
        table.nekro.setSecret("dyp");
        table.ships(table.nekro, home, UnitType.Cruiser, 1);

        assertThat(plan("piratecontract2").orElseThrow().target()).isEqualTo(next);
    }

    @Test
    void theHomebrewTheodisiContractIsNotATeCard() {
        assertThat(AiActionCards.of("piratecontractll")).isEmpty();
        assertThat(AiActionCards.of("piratecontract4")).isPresent();
    }
}
