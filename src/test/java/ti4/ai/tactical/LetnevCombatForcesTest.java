package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CombatOdds.Force;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Letnev abilities that change space combat are all counted in the odds the AI plans with, so it sees that the
// Barony wins fights that the plain dice call even.
class LetnevCombatForcesTest extends BaseTi4Test {

    private static final String SPACE = "space";

    private AiTestGame test;
    private Player letnev;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        test.isActive(letnev, "action");
        tile = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.game.setActiveSystem(tile.getPosition());
        test.units(tile, SPACE, letnev, UnitType.Dreadnought, 2);
        test.units(tile, SPACE, test.sol, UnitType.Dreadnought, 2);
    }

    private double letnevWins() {
        return CombatForces.spaceWinChance(test.game, tile, letnev, test.sol);
    }

    private Force letnevForce() {
        return CombatForces.current(test.game, tile, tile.getUnitHolders().get(SPACE), letnev, test.sol, false);
    }

    // Sustain damage cancels two hits for the owner of Non-Euclidean Shielding.
    @Test
    void nonEuclideanShieldingMakesAnEvenFightFavourTheBarony() {
        double plain = letnevWins();

        letnev.addTech("nes");

        assertThat(letnevWins()).isGreaterThan(plain + 0.1);
    }

    @Test
    void anEvenFightIsEvenWithoutAnyLetnevEdge() {
        letnev.setTg(0);

        assertThat(letnevWins()).isGreaterThan(0.4).isLessThan(0.6);
        assertThat(letnevForce().rerollRounds()).isZero();
    }

    // Munitions Reserves: 2 trade goods per round, up to three rounds are planned for.
    @Test
    void munitionsReservesAreCountedForTheTradeGoodsTheSeatCanSpend() {
        letnev.setTg(0);
        double broke = letnevWins();

        letnev.setTg(2);
        assertThat(letnevForce().rerollRounds()).isEqualTo(1);
        double oneRound = letnevWins();
        letnev.setTg(5);
        assertThat(letnevForce().rerollRounds()).isEqualTo(2);
        letnev.setTg(40);
        assertThat(letnevForce().rerollRounds()).isEqualTo(3);
        double rich = letnevWins();

        assertThat(oneRound).isGreaterThan(broke + 0.05);
        assertThat(rich).isGreaterThan(oneRound);
    }

    @Test
    void theOpponentsMunitionsReservesCountAgainstTheSeat() {
        letnev.setTg(0);
        test.isActive(test.sol, "action");
        double alone = CombatForces.spaceWinChance(test.game, tile, test.sol, letnev);

        letnev.setTg(6);

        assertThat(CombatForces.spaceWinChance(test.game, tile, test.sol, letnev))
                .isLessThan(alone - 0.05);
    }

    @Test
    void nobodyElseGetsMunitionsReserves() {
        test.sol.setTg(20);

        assertThat(CombatForces.current(test.game, tile, tile.getUnitHolders().get(SPACE), test.sol, letnev, false)
                        .rerollRounds())
                .isZero();
    }

    // Arc Secundus repairs itself at the start of every combat round, so a damaged flagship is worth more than a
    // damaged dreadnought.
    @Test
    void theFlagshipRepairsItselfEachRound() {
        test.units(tile, SPACE, letnev, UnitType.Flagship, 1);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Flagship, letnev.getColor()), 1);
        Force force = letnevForce();

        assertThat(force.repairs()).isTrue();
    }

    @Test
    void aFleetWithoutTheFlagshipDoesNotRepair() {
        assertThat(letnevForce().repairs()).isFalse();
    }

    // Gravleash Maneuvers adds the number of ship types in the fight to the rolls of one ship.
    @Test
    void gravleashManeuversBoostsOneShip() {
        test.units(tile, SPACE, letnev, UnitType.Cruiser, 1);
        test.units(tile, SPACE, letnev, UnitType.Destroyer, 1);
        double plain = letnevWins();
        long boostedBefore = letnevForce().units().stream()
                .filter(unit -> unit.hitsOn() <= 2)
                .count();

        letnev.addBreakthrough("letnevbt");
        letnev.setBreakthroughUnlocked("letnevbt", true);

        assertThat(boostedBefore).isZero();
        assertThat(letnevForce().units()).anyMatch(unit -> unit.hitsOn() == 5 - 3);
        assertThat(letnevWins()).isGreaterThan(plain);
    }

    @Test
    void aLockedBreakthroughBoostsNothing() {
        letnev.addBreakthrough("letnevbt");

        assertThat(letnevForce().units()).noneMatch(unit -> unit.hitsOn() < 5);
    }
}
