package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The offensive planner on whole boards: which strikes it plans, and what each term of the strike's value says.
// Boards are clear-cut and the assertions are relations (more, less, present, absent) or single terms, never exact
// odds.
class OffenseTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;
    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board = new DefenseScenario();

    private AiTestGame test() {
        return board.test;
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private Player sardakk() {
        return board.sardakk;
    }

    private int scored;

    private void levelOnTwo() {
        score(sardakk(), 2);
        score(nekro(), 2);
        score(board.test.sol, 2);
    }

    private void score(Player player, int points) {
        scored++;
        board.test.game.scorePublicObjective(
                player.getUserID(), board.test.game.addCustomPO("Test points " + scored, points));
    }

    private Optional<TacticalPlan> attackOn(String position) {
        return TacticalPlanner.forTarget(board.test.game, board.test.nekro, position)
                .filter(plan -> plan.kind() == Kind.ATTACK);
    }

    private StrikeValue breakdown(String position) {
        return TacticalPlanner.explainAttack(board.test.game, board.test.nekro, position)
                .orElseThrow();
    }

    private static List<String> neighbours(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate))
                .toList();
    }

    private String nextTo(String position, String... excluded) {
        List<String> skip = List.of(excluded);
        return neighbours(position).stream()
                .filter(candidate -> !skip.contains(candidate) && board.test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }

    private void fleetAtHome(int carriers, int dreadnoughts, int infantry) {
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, carriers);
        board.test.units(board.home, "space", nekro(), UnitType.Dreadnought, dreadnoughts);
        board.test.units(board.home, HOME_PLANET, nekro(), UnitType.Infantry, infantry);
    }

    // Test 1: a lone destroyer and nothing else at stake is not worth an action.
    @Test
    void doesNotChaseALoneDestroyerWithNothingAtStake() {
        fleetAtHome(1, 1, 3);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Destroyer, 1);

        assertThat(attackOn(target)).isEmpty();
    }

    // Test 2: the same destroyer belongs to the sole leader, three points ahead: hurting it is worth more.
    @Test
    void killsCountMoreAgainstTheSoleLeader() {
        double level = killsAgainstALoneDestroyer(0);
        double leader = killsAgainstALoneDestroyer(3);

        assertThat(leader).isGreaterThan(level);
    }

    private double killsAgainstALoneDestroyer(int leaderPoints) {
        board = new DefenseScenario();
        fleetAtHome(1, 1, 3);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Destroyer, 1);
        if (leaderPoints > 0) score(sardakk(), leaderPoints);
        return breakdown(target).kills();
    }

    private String launchNextToSardakkHome(int carriers, int dreadnoughts, int infantry) {
        String launch = nextTo(DefenseScenario.SARDAKK_HOME_POSITION, AiTestGame.HOME);
        Tile tile = board.test.place("47", launch);
        board.test.units(tile, "space", nekro(), UnitType.Carrier, carriers);
        board.test.units(tile, "space", nekro(), UnitType.Dreadnought, dreadnoughts);
        board.test.units(tile, "space", nekro(), UnitType.Infantry, infantry);
        return launch;
    }

    private void defendSardakkHome(int infantry) {
        Tile sardakkHome = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);
        board.test.units(sardakkHome, "quinarra", sardakk(), UnitType.Infantry, infantry);
    }

    private boolean landsOnSardakkHome() {
        return attackOn(DefenseScenario.SARDAKK_HOME_POSITION)
                .map(plan -> !plan.landings().isEmpty())
                .orElse(false);
    }

    // Test 3: the neighbour is one point from winning with a public objective ready to score, so taking a planet of
    // its home system (about a 60% fight) is planned. Level on two points with the seat, the same raid is not: the
    // block is worth less than half a point of our own, and ordinary home raids on a player who threatens nothing
    // stay shut.
    @Test
    void raidsTheHomeOfAPlayerNearWinningButNotOfAnAverageNeighbour() {
        launchNextToSardakkHome(1, 1, 5);
        defendSardakkHome(4);
        board.reveal("sway_council");
        sardakk().setTg(8);
        score(sardakk(), 9);

        assertThat(landsOnSardakkHome()).isTrue();

        board = new DefenseScenario();
        launchNextToSardakkHome(1, 1, 5);
        defendSardakkHome(4);
        board.reveal("sway_council");
        sardakk().setTg(8);
        levelOnTwo();

        assertThat(landsOnSardakkHome()).isFalse();
    }

    // Test 19: a player without a fixed home cannot be blocked by taking it.
    @Test
    void takingANomadsHomeBlocksNothing() {
        launchNextToSardakkHome(1, 1, 5);
        defendSardakkHome(4);
        board.reveal("sway_council");
        sardakk().setTg(8);
        score(sardakk(), 9);
        assertThat(breakdown(DefenseScenario.SARDAKK_HOME_POSITION).homeBlock()).isPositive();

        sardakk().addAbility("nomadic");

        assertThat(breakdown(DefenseScenario.SARDAKK_HOME_POSITION).homeBlock()).isZero();
    }

    // Test 18: an average neighbour two points up in round three with a thinly held home: the gate stays shut, so
    // the strike is a ship-only one at most.
    @Test
    void anAverageNeighbourWithAWeakHomeIsNotRaided() {
        board.test.game.setRound(3);
        launchNextToSardakkHome(1, 1, 5);
        board.reveal("sway_council");
        sardakk().setTg(8);
        levelOnTwo();

        assertThat(landsOnSardakkHome()).isFalse();
    }

    private void denialBoard(int sardakkPoints) {
        board = new DefenseScenario();
        board.test.place("18", "000");
        sardakk().addPlanet("mr");
        sardakk().addPlanet("lodor");
        sardakk().addSC(IMPERIAL);
        board.reveal("monument");
        board.reveal("sway_council");
        board.reveal("deep_space");
        score(sardakk(), sardakkPoints);
        String target = board.otherNeighbourOfHome();
        Tile lodor = board.test.place("26", target);
        board.test.units(lodor, "lodor", sardakk(), UnitType.Infantry, 1);
        board.test.units(lodor, "space", sardakk(), UnitType.Cruiser, 1);
        fleetAtHome(1, 1, 4);
        Tile quiet = board.test.place("48", nextTo(AiTestGame.HOME, target, board.stagingPosition));
        board.test.units(board.staging, "space", nekro(), UnitType.Destroyer, 1);
        board.test.units(quiet, "space", nekro(), UnitType.Destroyer, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Destroyer, 1);
    }

    private String thirdEmptySystem() {
        return nextTo(AiTestGame.HOME, board.otherNeighbourOfHome(), board.stagingPosition, "000");
    }

    private double positionScore() {
        String third = thirdEmptySystem();
        board.test.place("49", third);
        return TacticalPlanner.forTarget(board.test.game, nekro(), third)
                .filter(plan -> plan.kind() == Kind.POSITION)
                .map(TacticalPlan::score)
                .orElse(0.0);
    }

    // Test 7: completing a point of our own beats denying a non-winning sole leader two points and a planet, because
    // what stopping a player that is not about to win can add is capped below one point of our own, and the neighbour
    // keeps a fleet at home to answer with. With no fleet left to retaliate the two are about level (7.79 vs 7.75).
    @Test
    void ourOwnPointOutranksDenyingAPlayerWhoIsNotWinning() {
        denialBoard(4);
        Tile sardakkHome = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);
        board.test.units(sardakkHome, "space", sardakk(), UnitType.Cruiser, 2);
        sardakk().setTacticalCC(3);
        String target = board.otherNeighbourOfHome();

        StrikeValue attack = breakdown(target);
        double position = positionScore();

        assertThat(position).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE - 1);
        assertThat(attack.objectiveDenial()).isPositive();
        assertThat(position).as(attack.toString()).isGreaterThan(attack.score());
    }

    // Test 8: with the same denial but the neighbour on the brink of winning, stopping it outranks our own point.
    @Test
    void denyingAPlayerAboutToWinOutranksOurOwnPoint() {
        denialBoard(8);
        String target = board.otherNeighbourOfHome();

        StrikeValue attack = breakdown(target);
        double position = positionScore();

        assertThat(attack.score()).as(attack.toString()).isGreaterThan(position);
    }

    // Test 11: a ready Nullification Field with a strategy token can cancel the activation; once it is exhausted it
    // cannot.
    @Test
    void anAvailableNullificationFieldMayCancelTheAction() {
        Player xxcha = board.test.addSeat("200000000000000004", "xxcha", "green");
        xxcha.setStrategicCC(2);
        xxcha.addTech("nf");
        fleetAtHome(1, 1, 3);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", xxcha, UnitType.Cruiser, 1);

        assertThat(breakdown(target).cancel()).isEqualTo(0.8);

        xxcha.exhaustTech("nf");

        assertThat(breakdown(target).cancel()).isZero();
    }

    private String lodorTargetHeldByTheOpponent() {
        String target = board.otherNeighbourOfHome();
        Tile lodor = board.test.place("26", target);
        sardakk().addPlanet("lodor");
        board.test.units(lodor, "space", sardakk(), UnitType.Destroyer, 1);
        nekro().setSecret("sar");
        return target;
    }

    // Test 9: one enemy PDS shoots a ship out of the fleet before the combat, which is priced in and does not stop an
    // attack that is otherwise worth it (here a full point for a lone destroyer of the leader, with Spark a
    // Rebellion). Four PDS II covering the system destroy the whole small fleet, so there is no attack.
    @Test
    void pricesAnEnemyPdsInsteadOfAvoidingTheSystem() {
        String target = lodorTargetHeldByTheOpponent();
        fleetAtHome(1, 1, 0);
        board.test.units(board.test.game.getTileByPosition(target), "lodor", sardakk(), UnitType.Pds, 1);
        double withPds = breakdown(target).losses();
        Optional<TacticalPlan> plan = attackOn(target);

        board = new DefenseScenario();
        lodorTargetHeldByTheOpponent();
        fleetAtHome(1, 1, 0);
        double withoutPds = breakdown(target).losses();

        assertThat(plan).isPresent();
        assertThat(withPds).isGreaterThan(withoutPds);
    }

    @Test
    void doesNotFlyASmallFleetIntoFourPdsII() {
        String target = lodorTargetHeldByTheOpponent();
        sardakk().removeOwnedUnitByID("pds");
        sardakk().addOwnedUnitByID("pds2");
        board.test.units(board.test.game.getTileByPosition(target), "lodor", sardakk(), UnitType.Pds, 4);
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Cruiser, 1);

        assertThat(attackOn(target)).isEmpty();
    }

    // An enemy PDS alone is not a reason to stay away: a strong fleet with a landing party takes the planet it guards,
    // and the space cannon is only a cost in the strike.
    @Test
    void attacksAPdsPlanetWithAStrongFleet() {
        String target = lodorTargetHeldByTheOpponent();
        board.test.units(board.test.game.getTileByPosition(target), "lodor", sardakk(), UnitType.Pds, 1);
        fleetAtHome(2, 2, 4);

        Optional<TacticalPlan> plan = attackOn(target);

        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsKey("lodor");
    }

    // The same weak fleet attacks the planet when nothing covers it, and stays home when four PDS II would destroy it.
    @Test
    void theSameWeakFleetIsKeptAwayByTheSpaceCannonAlone() {
        String target = lodorTargetHeldByTheOpponent();
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Cruiser, 1);
        assertThat(attackOn(target)).isPresent();

        sardakk().removeOwnedUnitByID("pds");
        sardakk().addOwnedUnitByID("pds2");
        board.test.units(board.test.game.getTileByPosition(target), "lodor", sardakk(), UnitType.Pds, 4);

        assertThat(attackOn(target)).isEmpty();
    }

    private Player naaluAt(String target, UnitType type, int ships) {
        Player naalu = board.test.addSeat("200000000000000005", "naalu", "yellow");
        naalu.setStrategicCC(2);
        naalu.setFleetCC(5);
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", naalu, type, ships);
        board.test.place("48", nextTo(target));
        return naalu;
    }

    // Test 12: against a clearly weaker Naalu stack that has a strategy token and an empty system beside it, Foresight
    // moves the stack away before the combat: nothing is killed.
    @Test
    void aNaaluStackThatWouldLoseEscapesBeforeTheCombat() {
        fleetAtHome(1, 2, 0);
        String target = board.otherNeighbourOfHome();
        Player naalu = naaluAt(target, UnitType.Cruiser, 3);

        assertThat(breakdown(target).kills()).isZero();

        naalu.setStrategicCC(0);

        assertThat(breakdown(target).kills()).isPositive();
    }

    // Test 12b: a stack that would win the fight stays and fights, so a weak fleet does not attack it.
    @Test
    void aNaaluStackThatWouldWinStaysAndFights() {
        board.test.units(board.home, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(board.home, "space", nekro(), UnitType.Destroyer, 1);
        String target = board.otherNeighbourOfHome();
        naaluAt(target, UnitType.Dreadnought, 3);

        assertThat(attackOn(target)).isEmpty();
    }

    // Test 13: Nekro values the technologies the opponent could lose, and fears giving another Nekro the seat's.
    @Test
    void nekroCountsTheTechnologyItMayCopy() {
        fleetAtHome(1, 2, 0);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", board.test.sol, UnitType.Cruiser, 1);
        board.test.sol.addTech("ff2");
        board.test.sol.addTech("dd2");

        double with = breakdown(target).nekroGain();
        nekro().removeAbility("technological_singularity");
        double without = breakdown(target).nekroGain();

        assertThat(with).isPositive();
        assertThat(without).isZero();
    }

    @Test
    void attackingNekroRisksFeedingItsTechnology() {
        Tile solFleet = board.test.place("47", nextTo(board.stagingPosition));
        board.test.units(solFleet, "space", board.test.sol, UnitType.Carrier, 1);
        board.test.units(solFleet, "space", board.test.sol, UnitType.Cruiser, 1);
        String target = board.stagingPosition;
        board.test.units(board.staging, "space", nekro(), UnitType.Cruiser, 1);
        board.test.units(board.staging, "space", nekro(), UnitType.Destroyer, 1);
        board.test.sol.setFleetCC(5);
        board.test.sol.setTacticalCC(3);

        Optional<StrikeValue> feed = TacticalPlanner.explainAttack(board.test.game, board.test.sol, target);

        assertThat(feed).isPresent();
        assertThat(feed.get().nekroFeed()).isPositive();
    }

    // Test 14: what the opponent could strike back with. A target nowhere near the opponent's other ships draws no
    // counterstrike; the same target next to its big fleet does.
    @Test
    void counterstrikeIsThePriceOfStrikingNextToTheBigFleet() {
        board.test.units(board.home, "space", nekro(), UnitType.Cruiser, 3);
        String target = board.beyondStaging();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Destroyer, 1);
        double alone = breakdown(target).counter();

        Tile bigFleet = board.test.place("49", nextTo(target));
        board.test.units(bigFleet, "space", sardakk(), UnitType.Carrier, 1);
        board.test.units(bigFleet, "space", sardakk(), UnitType.Cruiser, 2);
        board.test.units(bigFleet, "space", sardakk(), UnitType.Infantry, 4);
        double nextToTheFleet = breakdown(target).counter();

        assertThat(alone).isZero();
        assertThat(nextToTheFleet).isPositive();
    }

    private StrikeValue relievingBoard(boolean withGround) {
        board = new DefenseScenario();
        board.test.game.setRound(4);
        board.test.units(board.home, "space", nekro(), UnitType.Cruiser, 4);
        String target = board.beyondStaging();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Carrier, 1);
        board.test.units(system, "space", sardakk(), UnitType.Dreadnought, 1);
        if (withGround) board.test.units(system, "space", sardakk(), UnitType.Infantry, 3);
        String dockPosition = neighbours(target).stream()
                .filter(position -> !position.equals(board.stagingPosition) && !AiTestGame.HOME.equals(position))
                .filter(position -> !neighbours(AiTestGame.HOME).contains(position))
                .filter(position -> board.test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
        Tile dockSystem = board.test.place("26", dockPosition);
        nekro().addPlanet("lodor");
        board.test.units(dockSystem, "lodor", nekro(), UnitType.Spacedock, 1);
        board.test.units(dockSystem, "lodor", nekro(), UnitType.Infantry, 1);
        return breakdown(target);
    }

    // Test 15: the opponent's fleet threatens a weakly held dock system the strike does not leave from. Destroying the
    // fleet relieves that system, and the strike scores higher than the same strike when nothing threatens the dock.
    @Test
    void relievingAThreatenedDockSystemIsWorthSomething() {
        StrikeValue threatened = relievingBoard(true);
        StrikeValue calm = relievingBoard(false);

        assertThat(threatened.defense()).isPositive();
        assertThat(calm.defense()).isZero();
        assertThat(threatened.score()).isGreaterThan(calm.score());
    }

    // Test 16: with about the same chance to win, the fleet that expects to lose less scores higher.
    @Test
    void lightLossesBeatHeavyLossesAtTheSameOdds() {
        StrikeValue heavy = fightValue(UnitType.Cruiser, 5);
        StrikeValue light = fightValue(UnitType.Dreadnought, 3);

        assertThat(heavy.losses()).isGreaterThan(3 * light.losses());
        assertThat(light.score()).isGreaterThan(heavy.score());
    }

    private StrikeValue fightValue(UnitType attacker, int ships) {
        board = new DefenseScenario();
        nekro().setFleetCC(10);
        board.test.units(board.home, "space", nekro(), attacker, ships);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", board.test.sol, UnitType.Cruiser, 3);
        return breakdown(target);
    }

    private double imperialTermOfAnAttackOnMecatol(boolean imperialPlayed) {
        board = new DefenseScenario();
        Tile rex = board.test.place("18", "000");
        sardakk().addPlanet("mr");
        board.test.units(rex, "mr", sardakk(), UnitType.Infantry, 1);
        sardakk().addSC(IMPERIAL);
        if (imperialPlayed) board.test.game.setSCPlayed(IMPERIAL, true);
        Tile launch = board.test.place("47", nextTo("000"));
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 4);
        return breakdown("000").imperial();
    }

    // Test 5: the neighbour holds Mecatol Rex and the unplayed Imperial card, so taking the planet stops it scoring the
    // Imperial point. The point is not counted twice (the neighbour is only denied the one point it holds), and once
    // the card has been played there is nothing left to deny.
    @Test
    void takingMecatolFromTheImperialHolderIsWorthItsPoint() {
        double before = imperialTermOfAnAttackOnMecatol(false);
        double oneDenied =
                new DefenseOutlook(board.test.game, nekro()).standings().denialWeight(sardakk())
                        * ObjectiveValue.VICTORY_POINT_VALUE;

        double after = imperialTermOfAnAttackOnMecatol(true);

        assertThat(before).isPositive().isLessThanOrEqualTo(oneDenied + 1e-9);
        assertThat(after).isZero();
    }

    private double expansionToMecatol(boolean someoneHoldsImperial) {
        board = new DefenseScenario();
        Tile rex = board.test.place("18", "000");
        assertThat(rex).isNotNull();
        sardakk().setTacticalCC(3);
        if (someoneHoldsImperial) sardakk().addSC(IMPERIAL);
        Tile rivals = board.test.place("49", nextTo("000"));
        board.test.units(rivals, "space", sardakk(), UnitType.Carrier, 1);
        board.test.units(rivals, "space", sardakk(), UnitType.Infantry, 4);
        Tile launch = board.test.place("47", nextTo("000"));
        board.test.units(launch, "space", nekro(), UnitType.Carrier, 1);
        board.test.units(launch, "space", nekro(), UnitType.Infantry, 2);
        return TacticalPlanner.forTarget(board.test.game, nekro(), "000")
                .filter(plan -> plan.kind() == Kind.EXPAND)
                .map(TacticalPlan::score)
                .orElseThrow();
    }

    // Test 6: another player holds the unplayed Imperial card and could reach an empty Mecatol Rex; taking it first
    // denies them that point, so the expansion is worth more than when nobody holds the card.
    @Test
    void expandingOntoMecatolBeatsTheImperialHolderToIt() {
        double withTheCard = expansionToMecatol(true);
        double withoutTheCard = expansionToMecatol(false);

        assertThat(withTheCard).isGreaterThan(withoutTheCard);
    }

    private String twoSmallFleetsAgainstOneBig() {
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Cruiser, 5);
        board.test.units(board.home, "space", nekro(), UnitType.Dreadnought, 2);
        Tile second = board.test.place("48", nextTo(target));
        board.test.units(second, "space", nekro(), UnitType.Dreadnought, 2);
        nekro().setSecret("sar");
        return target;
    }

    // Test 17: two systems each hold two dreadnoughts that would lose to the opponent's five cruisers alone (a win
    // chance under one fifth) and win together. The strike moves ships out of both, within the fleet supply.
    @Test
    void combinesTwoFleetsThatAreTooWeakAlone() {
        nekro().setFleetCC(4);
        String target = twoSmallFleetsAgainstOneBig();

        TacticalPlan plan = attackOn(target).orElseThrow();

        assertThat(plan.moves().stream().map(TacticalPlan.UnitMove::origin).distinct())
                .hasSize(2);
        assertThat(plan.moves().stream()
                        .filter(move -> move.type() == UnitType.Dreadnought)
                        .mapToInt(TacticalPlan.UnitMove::count)
                        .sum())
                .isEqualTo(4);
    }

    @Test
    void aCombinedStrikeStaysWithinTheFleetSupply() {
        nekro().setFleetCC(3);
        String target = twoSmallFleetsAgainstOneBig();

        assertThat(attackOn(target)).isEmpty();
    }

    private StrikeValue naaluThreatensTheDockSystem(int strategyTokens) {
        board = new DefenseScenario();
        // Round 3, not the scenario's round 4: from round 4 the home posture holds cruisers back against this stack,
        // and an attack by fewer cruisers is no longer one the stack flees from, which is not what this test is about.
        board.test.game.setRound(3);
        board.test.units(board.home, "space", nekro(), UnitType.Cruiser, 4);
        nekro().setFleetCC(6);
        String target = board.beyondStaging();
        Tile system = board.test.place("47", target);
        Player naalu = board.test.addSeat("200000000000000005", "naalu", "yellow");
        naalu.setStrategicCC(strategyTokens);
        naalu.setFleetCC(5);
        board.test.units(system, "space", naalu, UnitType.Carrier, 1);
        board.test.units(system, "space", naalu, UnitType.Cruiser, 2);
        board.test.units(system, "space", naalu, UnitType.Infantry, 3);
        String dockPosition = neighbours(target).stream()
                .filter(position -> !position.equals(board.stagingPosition) && !AiTestGame.HOME.equals(position))
                .filter(position -> !neighbours(AiTestGame.HOME).contains(position))
                .filter(position -> board.test.game.getTileByPosition(position) == null)
                .toList()
                .getFirst();
        Tile dockSystem = board.test.place("26", dockPosition);
        nekro().addPlanet("lodor");
        board.test.units(dockSystem, "lodor", nekro(), UnitType.Spacedock, 1);
        board.test.units(dockSystem, "lodor", nekro(), UnitType.Infantry, 1);
        board.test.place(
                "48",
                neighbours(target).stream()
                        .filter(position -> board.test.game.getTileByPosition(position) == null)
                        .toList()
                        .getLast());
        return breakdown(target);
    }

    // Test 12, second half: a stack that slips away is still there afterwards, so destroying it relieves no other
    // system
    // of its threat; the same stack that stays and loses does.
    @Test
    void anEscapedStackRelievesNothing() {
        StrikeValue escaping = naaluThreatensTheDockSystem(2);
        StrikeValue staying = naaluThreatensTheDockSystem(0);

        assertThat(escaping.kills()).isZero();
        assertThat(escaping.defense()).isZero();
        assertThat(staying.defense()).isPositive();
    }

    // Test 22: planning attacks only reads what the table can see of the opponent.
    @Test
    void neverLooksAtTheOpponentsHiddenCards() {
        fleetAtHome(1, 1, 3);
        String target = board.otherNeighbourOfHome();
        Tile system = board.test.place("47", target);
        board.test.units(system, "space", sardakk(), UnitType.Cruiser, 1);
        board.reveal("sway_council");
        sardakk().setSecret("sar");
        sardakk().setActionCard("sabo1");
        sardakk().setPromissoryNote("black_ta", 17);
        Player spy = Mockito.spy(sardakk());
        board.test.game.getPlayers().put(spy.getUserID(), spy);

        TacticalPlanner.ranked(board.test.game, nekro());
        TacticalPlanner.explainAttack(board.test.game, nekro(), target);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
