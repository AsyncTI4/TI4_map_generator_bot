package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Letnev's whole fleet sits in one system with the hero in play, the breakthrough unlocked and Styx three systems
// away. The destroyer moves 2, so only Gravity Drive on the destroyer (3) carries the dreadnoughts and the carrier
// that far: Gravleash gives every ship in the system the destroyer's boosted move. The carrier and the dreadnoughts
// carry six infantry (four fighters would fill the carrier and leave nothing to land on Styx). The system is Mecatol
// Rex, already taken, with an ingress token, which is three moves from Styx.
class GravleashStyxTest extends BaseTi4Test {

    private FractureScenario fracture;
    private Tile launch;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
        fracture.board.test.aiIsActive("action");
        launch = fracture.board.test.place("18", "000");
        launch.addToken(Constants.TOKEN_INGRESS, "space");
        fracture.nekro().addPlanet("mr");
        fracture.ships(launch, UnitType.Dreadnought, 2);
        fracture.ships(launch, UnitType.Carrier, 1);
        fracture.ships(launch, UnitType.Destroyer, 1);
        fracture.ships(launch, UnitType.Infantry, 6);
        fracture.nekro().addBreakthrough("letnevbt");
        fracture.nekro().setBreakthroughUnlocked("letnevbt", true);
        fracture.nekro().addLeader("letnevhero");
        fracture.nekro().getLeader("letnevhero").orElseThrow().setActive(true);
    }

    private List<TacticalPlan> ranked() {
        return TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro());
    }

    private boolean aimsAtStyx(TacticalPlan plan) {
        return FractureScenario.STYX.equals(plan.target());
    }

    @Test
    void theFleetCannotReachStyxWithoutGravityDrive() {
        assertThat(ranked()).noneMatch(this::aimsAtStyx);
    }

    @Test
    void gravityDriveOnTheDestroyerTakesTheWholeFleetToStyx() {
        fracture.nekro().addTech("gd");

        TacticalPlan strike =
                ranked().stream().filter(this::aimsAtStyx).findFirst().orElseThrow();

        assertThat(strike.moves().stream()
                        .filter(move -> move.type() == UnitType.Dreadnought)
                        .mapToInt(TacticalPlan.UnitMove::count)
                        .sum())
                .isEqualTo(2);
        assertThat(strike.moves()).anyMatch(move -> move.type() == UnitType.Destroyer);
        assertThat(strike.moves()).anyMatch(move -> move.type() == UnitType.Carrier);
    }

    @Test
    void gravityDriveMakesStyxTheFleetsBestTarget() {
        fracture.nekro().addTech("gd");

        assertThat(ranked().getFirst().target()).isEqualTo(FractureScenario.STYX);
    }
}
