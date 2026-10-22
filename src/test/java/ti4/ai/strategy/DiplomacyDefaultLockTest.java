package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// aiwatch9, round 3: Xxcha played Diplomacy to claim a planet with Peace Accords and picked no system, so no other
// player placed a token. The primary always has a system to choose; with nothing threatened it goes to the system an
// attack would cost the seat most, which is decided by who can reach it and what the seat stands to lose there.
class DiplomacyDefaultLockTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
    }

    private java.util.Optional<String> lockTarget() {
        return DiplomacyUses.bestLockTarget(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION));
    }

    @Test
    void locksTheOnlySystemTheSeatHolds() {
        assertThat(lockTarget()).contains(home.getPosition());
    }

    @Test
    void locksTheSystemAnEnemyFleetCanReachOverAMoreValuableOneItCannot() {
        Tile party = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(party, "space", test.sol, UnitType.Carrier, 2);
        test.units(party, "space", test.sol, UnitType.Infantry, 6);
        Tile remote = test.place("28", remoteFrom(party));
        test.nekro.addPlanet("lodor");
        test.units(remote, "lodor", test.nekro, UnitType.Infantry, 1);
        PlanInputs in = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);

        assertThat(in.lockExposure(home)).isPositive();
        assertThat(in.lockExposure(remote)).isZero();
        assertThat(lockTarget()).contains(home.getPosition());
    }

    private String remoteFrom(Tile party) {
        java.util.List<String> nearHome = ti4.image.PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        java.util.List<String> nearParty = ti4.image.PositionMapper.getAdjacentTilePositions(party.getPosition());
        return nearHome.stream()
                .filter(position -> !"x".equals(position) && test.game.getTileByPosition(position) == null)
                .flatMap(position -> ti4.image.PositionMapper.getAdjacentTilePositions(position).stream())
                .filter(position -> !"x".equals(position) && test.game.getTileByPosition(position) == null)
                .filter(position -> !nearHome.contains(position) && !nearParty.contains(position))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void neverLocksMecatolRex() {
        Tile rex = test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.game.removeTile(home.getPosition());

        assertThat(rex).isNotNull();
        assertThat(lockTarget()).isEmpty();
    }
}
