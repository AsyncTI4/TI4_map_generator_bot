package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class HomeRaidSecretsTest extends BaseTi4Test {

    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.sol.removeTech("amd");
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private Tile solHomeNextDoorHeldByTwoInfantry() {
        Tile solHome = test.place("01", neighbour);
        test.sol.addPlanet("jord");
        test.units(solHome, "jord", test.sol, UnitType.Infantry, 2);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        return solHome;
    }

    private Optional<TacticalPlan> landingOnJord() {
        return TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                .filter(plan -> plan.kind() == Kind.ATTACK && plan.landings().containsKey("jord"));
    }

    private void spaceCannonCoversTheNeighbour() {
        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
    }

    // Four infantry against two on Sol's home planet is a clear ground win, but raiding another player's home needs a
    // reason. A held Darken the Skies is that reason; without it the seat leaves Sol's home alone.
    @Test
    void landsOnAnEnemyHomeOnlyWhileHoldingDarkenTheSkies() {
        solHomeNextDoorHeldByTwoInfantry();

        assertThat(landingOnJord()).isEmpty();

        test.nekro.setSecret("dts");

        assertThat(landingOnJord()).isPresent();
    }

    // Become a Martyr scores when the seat loses a planet in a home system, so a raid that lands troops on Sol's
    // home is worth making for it: the ground combat is what puts a planet in play that can be lost.
    @Test
    void landsOnAnEnemyHomeWhileHoldingBecomeAMartyr() {
        solHomeNextDoorHeldByTwoInfantry();

        assertThat(landingOnJord()).isEmpty();

        test.nekro.setSecret("bam");

        assertThat(landingOnJord()).isPresent();
    }

    // The secret is only worth something at another player's home, and there it is worth the whole point: the bonus
    // already depends on the odds of winning, so it is not discounted as a patient secret any more.
    @Test
    void valuesDarkenTheSkiesInFullAtAnotherPlayersHomeOnly() {
        Tile solHome = test.place("01", neighbour);
        test.units(solHome, "space", test.sol, UnitType.Destroyer, 1);
        Tile elsewhere = test.place("46", otherNeighbour());
        test.units(elsewhere, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("dts");
        List<UnitMove> ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 1));

        assertThat(bonus(solHome, ships)).isEqualTo(VP);
        assertThat(bonus(elsewhere, ships)).isZero();
    }

    private String otherNeighbour() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate) && !candidate.equals(neighbour))
                .findFirst()
                .orElseThrow();
    }

    private double bonus(Tile target, List<UnitMove> ships) {
        return TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);
    }

    private Tile loneSolDestroyerNextDoor() {
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        return target;
    }

    private List<UnitMove> oneDestroyerFromHome() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        return List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
    }

    // PDS II at home reaches next door with one die on 5, so it kills Sol's lone destroyer 60% of the time and the
    // seat scores Turn Their Fleets to Dust without any combat. Without the secret, or without cover, there is nothing.
    @Test
    void valuesACoveredLoneDestroyerByTheChanceTheCannonKillsIt() {
        Tile target = loneSolDestroyerNextDoor();
        List<UnitMove> ships = oneDestroyerFromHome();

        assertThat(bonus(target, ships)).isZero();
        test.nekro.setSecret("ttfd");
        assertThat(bonus(target, ships)).isZero();

        spaceCannonCoversTheNeighbour();

        assertThat(bonus(target, ships)).isCloseTo(VP * 0.6, within(EXACT));
    }

    // Two dreadnoughts take four hits (two sustains and two kills) and a single die can never deliver them.
    @Test
    void valuesNothingWhenTheCannonCannotKillEveryShip() {
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Dreadnought, 2);
        List<UnitMove> ships = oneDestroyerFromHome();
        test.nekro.setSecret("ttfd");
        spaceCannonCoversTheNeighbour();

        assertThat(bonus(target, ships)).isZero();
    }

    // Sol starts with Antimass Deflectors, which push the cannon from 5 to 6: the chance falls from 0.6 to 0.5.
    // The other tests remove it to see the bare cannon.
    @Test
    void countsTheDefendersAntimassDeflectors() {
        Tile target = loneSolDestroyerNextDoor();
        List<UnitMove> ships = oneDestroyerFromHome();
        test.nekro.setSecret("ttfd");
        spaceCannonCoversTheNeighbour();
        test.sol.addTech("amd");
        assertThat(bonus(target, ships)).isCloseTo(VP * 0.5, within(EXACT));
    }

    // The enemy's fighters take hits first, so a destroyer behind two fighters needs three hits.
    @Test
    void countsFightersAsHitsTheDefenderCanSpend() {
        Tile target = loneSolDestroyerNextDoor();
        test.units(target, "space", test.sol, UnitType.Fighter, 2);
        List<UnitMove> ships = oneDestroyerFromHome();
        test.nekro.setSecret("ttfd");
        spaceCannonCoversTheNeighbour();

        assertThat(bonus(target, ships)).isZero();
    }

    // On Sol's home system a cannon that wipes the defenders leaves no combat to win, so Darken the Skies and Turn
    // Their Fleets to Dust are alternatives: the better one counts, never both.
    @Test
    void countsTheBetterOfDarkenTheSkiesAndDustOnOneTile() {
        Tile solHome = test.place("01", neighbour);
        test.units(solHome, "space", test.sol, UnitType.Destroyer, 1);
        List<UnitMove> ships = oneDestroyerFromHome();
        spaceCannonCoversTheNeighbour();
        test.nekro.setSecret("dts");
        test.nekro.setSecret("ttfd");

        assertThat(bonus(solHome, ships)).isCloseTo(VP, within(EXACT));
    }

    // With no ship to send, activating the covered system still fires the cannon (LRR 77.4), and a lone destroyer
    // dies 60% of the time: worth 4.8 for the token plus the destroyer's 1 resource at that chance. It is a RAID, not
    // an
    // ATTACK, so that a long shot like this never counts as urgent (see DustRaidTest).
    @Test
    void activatesACoveredSystemWithNothingToMoveForADustKill() {
        loneSolDestroyerNextDoor();
        test.nekro.setSecret("ttfd");
        spaceCannonCoversTheNeighbour();

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.RAID);
        assertThat(plan.moves()).isEmpty();
        assertThat(plan.score()).isCloseTo(VP * 0.6 + 0.6, within(EXACT));
    }

    // The system picker lists systems from the seat's unlocked unit systems. With every one of them holding the seat's
    // own token the covered system cannot be picked, so the seat must not plan the activation.
    @Test
    void doesNotActivateForADustKillWhenNoUnlockedSystemHoldsUnits() {
        loneSolDestroyerNextDoor();
        test.nekro.setSecret("ttfd");
        spaceCannonCoversTheNeighbour();
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isPresent();

        home.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isEmpty();
    }

    @Test
    void doesNotActivateForADustKillWithoutTheSecretOrCover() {
        loneSolDestroyerNextDoor();

        test.nekro.setSecret("ttfd");
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isEmpty();

        test.nekro.removeSecret(test.nekro.getSecretsUnscored().get("ttfd"));
        spaceCannonCoversTheNeighbour();
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isEmpty();
    }
}
