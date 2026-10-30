package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class DefenseOutlookTest extends BaseTi4Test {

    private static final double TOLERANCE = 0.01;
    private static final int POK_WARFARE = 6;
    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
    }

    private DefenseOutlook outlook() {
        return new DefenseOutlook(board.test.game, board.test.nekro);
    }

    private double fear() {
        return outlook().fear(board.sardakk, board.home, Horizon.IMMEDIATE, board.defenders());
    }

    private double cost() {
        return outlook().cost(board.home);
    }

    private void garrison(int infantry) {
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, infantry);
    }

    // No enemy ship in reach of home: nothing to defend against, whatever else is going on.
    @Test
    void costsNothingWhenNoEnemyShipCanReachHome() {
        Tile far = board.test.place("47", board.beyondStaging());
        board.test.units(far, "space", board.sardakk, UnitType.Carrier, 1);
        board.test.units(far, "space", board.sardakk, UnitType.Infantry, 4);
        garrison(1);

        assertThat(outlook().isGuarded(board.home)).isTrue();
        assertThat(cost()).isZero();
    }

    // The ground odds behind the planner: four Sardakk infantry against n Nekro infantry on Mordai II, no ships on
    // either side, so the whole invasion is decided on the ground.
    @Test
    void takeFollowsTheGroundOdds() {
        board.raidingFleet(1, 2, 4);
        double[] expected = {1.00, 0.986, 0.889, 0.662, 0.386};
        for (int infantry = 1; infantry <= expected.length; infantry++) {
            Defenders defended = board.defenders().plusGround(HOME_PLANET, UnitType.Infantry, infantry);

            double take = outlook()
                    .chances(board.sardakk, board.home, Horizon.IMMEDIATE, defended)
                    .take();

            assertThat(take)
                    .as("infantry %d", infantry)
                    .isCloseTo(expected[infantry - 1], org.assertj.core.data.Offset.offset(TOLERANCE));
        }
    }

    // Test 13: a PDS at home shoots the arriving ships and the landing infantry.
    @Test
    void ourPdsLowersTheTake() {
        board.raidingFleet(1, 2, 4);
        garrison(3);
        double without = outlook()
                .chances(board.sardakk, board.home, Horizon.IMMEDIATE, board.defenders())
                .take();

        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Pds, 1);
        double with = outlook()
                .chances(board.sardakk, board.home, Horizon.IMMEDIATE, board.defenders())
                .take();

        assertThat(with).isLessThan(without);
    }

    // Test 2: in round 1 a passive neighbour with a lone carrier is no reason to hold anything back.
    @Test
    void anEarlyPassiveNeighbourIsNoThreat() {
        board.test.game.setRound(1);
        Player hacan = board.test.addSeat("200000000000000003", "hacan", "green");
        board.test.units(board.staging, "space", hacan, UnitType.Carrier, 1);
        board.test.units(board.staging, "space", hacan, UnitType.Infantry, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 1);
        garrison(1);

        double fear = outlook().fear(hacan, board.home, Horizon.IMMEDIATE, board.defenders());

        assertThat(fear).isLessThan(0.1);
        assertThat(cost()).isZero();
    }

    // Test 5: a neighbour that has passed cannot attack this round, only next, which weighs half as much.
    @Test
    void aPassedNeighbourCostsLess() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        garrison(1);
        double active = cost();

        board.sardakk.setPassed(true);

        assertThat(cost()).isLessThan(active);
    }

    // Test 10: Achieve Supremacy rewards a flagship in another player's home system, so the same flagship next door is
    // a likelier visitor once it is revealed.
    @Test
    void aPublicObjectiveThatRewardsTheAttackRaisesFear() {
        board.test.game.setRound(3);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Flagship, 1);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Infantry, 2);
        garrison(2);
        double without = fear();

        board.reveal("supremacy");

        assertThat(fear()).isGreaterThan(without);
    }

    // Test 11: with nothing public to gain, being the leader or one step from winning is what makes home worth taking.
    @Test
    void leadingOrNearingTheWinMakesHomeALikelierTarget() {
        board.raidingFleet(1, 2, 4);
        garrison(4);
        double trailing = fear();

        board.test.game.scorePublicObjective(board.test.nekro.getUserID(), board.test.game.addCustomPO("Leading", 1));
        double leading = fear();
        board.test.game.scorePublicObjective(
                board.test.nekro.getUserID(), board.test.game.addCustomPO("Nearly there", 6));
        double nearlyWon = fear();

        assertThat(leading).isGreaterThan(trailing);
        assertThat(nearlyWon).isGreaterThan(leading);
    }

    // Test 18: free planets within the carrier's reach are an easier way to spend the same action, so home is less
    // appetizing.
    @Test
    void anEasierPlanetNextToTheFleetLowersFear() {
        board.raidingFleet(1, 2, 4);
        garrison(4);
        double before = fear();

        board.test.place("35", board.beyondStaging());

        assertThat(fear()).isLessThan(before);
    }

    // Test 22: a Ceasefire in the seat's own hand all but rules the holder's owner out.
    @Test
    void holdingTheEnemysCeasefireLowersFear() {
        board.raidingFleet(1, 2, 4);
        garrison(4);
        double before = fear();

        board.test.nekro.setPromissoryNote(board.sardakk.getColor() + "_cf", 51);

        assertThat(fear()).isLessThan(before);
    }

    @Test
    void guardsHomeDocksAndMecatolButNotAnOrdinaryPlanet() {
        DefenseOutlook outlook = outlook();
        Tile outpost = board.test.place("26", board.stagingPosition);
        board.test.nekro.addPlanet("lodor");
        Tile rex = board.test.place("18", "000");
        board.test.nekro.addPlanet("mr");

        assertThat(outlook.isGuarded(board.home)).isTrue();
        assertThat(outlook().isGuarded(rex)).isTrue();
        assertThat(outlook().isGuarded(outpost)).isFalse();

        board.test.units(outpost, "lodor", board.test.nekro, UnitType.Spacedock, 1);

        assertThat(outlook().isGuarded(outpost)).isTrue();
    }

    // A system where the seat holds no dock is still worth guarding when losing its ships there would cost the seat
    // an objective point (five destroyers for Raise a Fleet).
    @Test
    void guardsASystemWhoseShipsHoldAnObjective() {
        Tile staging = board.test.place("47", board.otherNeighbourOfHome());
        board.test.units(staging, "space", board.test.nekro, UnitType.Destroyer, 5);
        assertThat(outlook().isGuarded(staging)).isFalse();

        board.reveal("raise_fleet");

        assertThat(outlook().isGuarded(staging)).isTrue();
    }

    // A planet in a third player's home system is nobody's to keep: it is worth guarding as soon as a fleet that can
    // land on it would complete Conquer the Weak.
    @Test
    void guardsAPlanetAnInvasionWouldScoreFor() {
        board.raidingFleet(1, 2, 4);
        Tile solHome = board.test.place("01", board.beyondStaging());
        board.test.nekro.addPlanet("jord");
        assertThat(outlook().isGuarded(solHome)).isFalse();

        board.reveal("conquer");

        assertThat(outlook().isGuarded(solHome)).isTrue();
    }

    // Against a round-4 Sardakk landing party, home with six infantry needs four of them: the last two are free to go.
    // The home posture, weighed by how much Sardakk wants home, asks for no more.
    @Test
    void keepsOnlyTheGarrisonTheThreatAndThePostureNeed() {
        board.raidingFleet(1, 2, 4);
        garrison(6);
        Planet mordai = (Planet) board.home.getUnitHolders().get(HOME_PLANET);

        int keep = outlook().groundToKeep(board.home, mordai, List.of());

        assertThat(keep).isEqualTo(4);
    }

    // Round 4, but no enemy ship can reach home: there is no standing garrison to keep, not even one infantry.
    @Test
    void anUnthreatenedHomeKeepsNoGround() {
        garrison(6);
        Planet mordai = (Planet) board.home.getUnitHolders().get(HOME_PLANET);

        assertThat(outlook().homeDefenseLevel()).isZero();
        assertThat(outlook().groundToKeep(board.home, mordai, List.of())).isZero();
    }

    @Test
    void leavingCostsNothingWhenNothingThreatensHome() {
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        garrison(1);

        double leaving = outlook()
                .leaveCost(board.home, List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2)));

        assertThat(leaving).isZero();
    }

    // With three infantry at home and no ship, the two dreadnoughts are what keeps the landing party out. Holding one
    // back is enough to bring the exposure to within the tolerance.
    @Test
    void holdsBackJustEnoughOfAFleetThatTheThreatNeeds() {
        board.raidingFleet(1, 1, 4);
        board.reveal("conquer");
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        garrison(3);
        DefenseOutlook outlook = outlook();
        List<UnitMove> dreadnoughts = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
        double leavingBoth = outlook.leaveCost(board.home, dreadnoughts);

        List<UnitMove> mayLeave = outlook.holdBack(board.home, List.of(), dreadnoughts);

        assertThat(leavingBoth).isGreaterThan(DefenseOutlook.GARRISON_TOLERANCE);
        assertThat(mayLeave).isNotEmpty();
        assertThat(mayLeave.stream().mapToInt(UnitMove::count).sum()).isLessThan(2);
        assertThat(outlook.leaveCost(board.home, mayLeave)).isLessThanOrEqualTo(DefenseOutlook.GARRISON_TOLERANCE);
    }

    // Test 6: an unplayed Warfare card means the fleet may be unlocked and used before the seat's next turn, so its
    // next
    // round threat is as urgent as an immediate one.
    @Test
    void anUnplayedWarfareCardMakesNextRoundAsUrgentAsNow() {
        board.raidingFleet(1, 2, 4);
        board.test.game.setStrategyCardSet("pok");
        DefenseOutlook outlook = outlook();

        assertThat(outlook.weight(board.sardakk, Horizon.IMMEDIATE)).isEqualTo(1.0);
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(0.5);

        board.sardakk.addSC(POK_WARFARE);
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(1.0);

        board.test.game.setSCPlayed(POK_WARFARE, true);
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(0.5);
    }

    @Test
    void theSeatPassingOrRunningOutOfTokensMakesNextRoundUrgent() {
        board.raidingFleet(1, 2, 4);
        DefenseOutlook outlook = outlook();
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(0.5);

        board.test.nekro.setTacticalCC(1);
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(1.0);

        board.test.nekro.setTacticalCC(3);
        board.test.nekro.setPassed(true);
        assertThat(outlook.weight(board.sardakk, Horizon.NEXT_ROUND)).isEqualTo(1.0);
    }

    // The target is the highest take the seat tolerates: even-odds against the most frightening enemy, none at all when
    // there is nothing to fear.
    @Test
    void theTargetRelaxesAsFearFalls() {
        DefenseOutlook outlook = outlook();

        assertThat(outlook.target(1.0)).isEqualTo(0.5);
        assertThat(outlook.target(0.5)).isEqualTo(0.625);
        assertThat(outlook.target(0.0)).isEqualTo(1.0);
    }

    // Test 15: weighing a neighbour's threat uses only the table's public information.
    @Test
    void neverLooksAtTheEnemysHiddenCards() {
        board.raidingFleet(1, 2, 4);
        board.reveal("conquer");
        garrison(1);
        board.sardakk.setSecret("sar");
        board.sardakk.setActionCard("sabo1");
        board.sardakk.setPromissoryNote("black_ta", 17);
        Player spy = Mockito.spy(board.sardakk);
        board.test.game.getPlayers().put(spy.getUserID(), spy);

        DefenseOutlook outlook = outlook();
        outlook.cost(board.home);
        outlook.fear(spy, board.home, Horizon.NEXT_ROUND, board.defenders());
        outlook.holdBack(
                board.home,
                java.util.List.of(),
                java.util.List.of(
                        new TacticalPlan.UnitMove(board.home.getPosition(), "space", UnitType.Dreadnought, 1)));

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
