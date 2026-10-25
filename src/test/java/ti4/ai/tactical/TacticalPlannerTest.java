package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.strategy.MecatolCash;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class TacticalPlannerTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    // Self-play: seats passed holding exactly the 6 tokens of Galvanize the People while another seat was sure to reach
    // 10 first on public information. A plain expansion does not outweigh the 2-point reserve, so the seat keeps its
    // tokens; once Sol (Leadership, 3 tokens, 8 points) is projected to win before Nekro can score, the reserve is
    // dropped and the expansion goes ahead.
    @Test
    void dropsTheTokenReserveWhenAnotherSeatWinsFirst() {
        test.game.setVp(10);
        test.aiIsActive("action");
        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(3);
        test.nekro.addSC(5);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        test.solHomeBeside(neighbour);
        test.sol.addSC(1);
        test.sol.setTacticalCC(2);
        test.sol.setStrategicCC(1);

        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();

        test.points(test.sol, 8);

        assertThat(TacticalPlanner.best(test.game, test.nekro)).isPresent();
    }

    // A carrier already sits in the system with its infantry aboard: activating that system lands them without
    // moving anything in.
    @Test
    void landsGroundForcesAlreadyInTheSystem() {
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.nekro, UnitType.Carrier, 1);
        test.units(lodor, "space", test.nekro, UnitType.Infantry, 1);

        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.moves()).isEmpty();
        assertThat(plan.landings()).containsEntry("lodor", 1);
    }

    // The same carrier under Nekro's own token: only freeing that token makes the landing possible.
    @Test
    void landsInPlaceOnlyOnceTheSystemIsFreed() {
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.nekro, UnitType.Carrier, 1);
        test.units(lodor, "space", test.nekro, UnitType.Infantry, 1);
        lodor.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(TacticalPlanner.ranked(test.game, test.nekro))
                .noneMatch(plan -> plan.target().equals(neighbour));
        assertThat(TacticalPlanner.rankedFreeing(test.game, test.nekro, neighbour))
                .anyMatch(plan ->
                        plan.target().equals(neighbour) && plan.landings().containsKey("lodor"));
    }

    // The classic opening: a carrier takes infantry to an empty neighbouring planet, leaving the dreadnought at home.
    // Nothing can reach home, so no infantry has to stay: the second rides along to move on from Lodor.
    @Test
    void expandsToAFreeNeighbouringPlanet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.get().target()).isEqualTo(neighbour);
        assertThat(plan.get().moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 2));
        assertThat(plan.get().landings()).containsKey("lodor");
    }

    // The slice continues past the neighbour, so the idle second carrier rides along to be one step closer to it next
    // round instead of waiting at home. The destroyer cannot claim planets and has no fight ahead, so it stays.
    @Test
    void bringsAnIdleShipForwardWhenTheSliceContinues() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        test.place("26", neighbour);
        test.place("27", twoSystemsFromHome());

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(movedShips(plan, UnitType.Carrier)).isEqualTo(2);
        assertThat(movedShips(plan, UnitType.Destroyer)).isZero();
    }

    // A second carrier that another free system next to home still needs stays behind for that expansion.
    @Test
    void keepsACarrierHomeThatAnotherExpansionNeeds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        test.place("26", neighbour);
        test.place("27", twoSystemsFromHome());
        String otherNeighbour = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(neighbour))
                .findFirst()
                .orElseThrow();
        test.place("25", otherNeighbour);

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(movedShips(plan, UnitType.Carrier)).isEqualTo(1);
    }

    // Round 1, and only one system with planets is in reach. Sol holds Thunder's Edge Warfare, which almost always
    // comes out on its first turn; following it builds infantry at home before the carrier leaves, so the carrier
    // waits and the destroyer steps into the empty system next to home instead. Without a rival Warfare the carrier
    // goes at once.
    @Test
    void holdsTheCarrierHomeForARivalsWarfareBuild() {
        test.game.setRound(1);
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(2);
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("27", neighbour);
        test.place("28", twoSystemsFromHome());
        String empty = otherNeighbourOfHome();
        test.place("47", empty);

        TacticalPlan rushing = TacticalPlanner.best(test.game, test.nekro).orElseThrow();
        assertThat(movedShips(rushing, UnitType.Carrier)).isEqualTo(1);

        test.sol.addSC(6);

        TacticalPlan holding = TacticalPlanner.best(test.game, test.nekro).orElseThrow();
        assertThat(movedShips(holding, UnitType.Carrier)).isZero();
        assertThat(movedShips(holding, UnitType.Infantry)).isZero();
        assertThat(holding.target()).isEqualTo(empty);
        assertThat(movedShips(holding, UnitType.Destroyer)).isEqualTo(1);
    }

    private static int movedShips(TacticalPlan plan, UnitType type) {
        return plan.moves().stream()
                .filter(move -> move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
    }

    // aiwatch6, round 1: Hacan's four infantry sat one or two to a home planet, and the AI kept one on every home
    // planet,
    // so only one could go and it took one of the two planets next door. The garrison is one infantry for the home
    // system, not one per planet, so two go and both planets are taken.
    @Test
    void keepsOneInfantryForTheWholeHomeSystemNotOnePerPlanet() {
        AiTestGame table = new AiTestGame();
        Tile hacanHome = table.place("16", AiTestGame.HOME);
        for (String planet : List.of("arretze", "hercant", "kamdorn")) table.nekro.addPlanet(planet);
        table.units(hacanHome, "arretze", table.nekro, UnitType.Spacedock, 1);
        table.units(hacanHome, "arretze", table.nekro, UnitType.Infantry, 2);
        table.units(hacanHome, "hercant", table.nekro, UnitType.Infantry, 1);
        table.units(hacanHome, "kamdorn", table.nekro, UnitType.Infantry, 1);
        table.units(hacanHome, "space", table.nekro, UnitType.Carrier, 1);
        table.place("27", neighbour);

        TacticalPlan plan = TacticalPlanner.forTarget(table.game, table.nekro, neighbour)
                .filter(found -> found.kind() == Kind.EXPAND)
                .orElseThrow();

        assertThat(plan.landings()).containsOnlyKeys("newalbion", "starpoint");
    }

    // Nothing claimable is in reach this turn; the free planets are two systems out. With tokens to spare the carrier
    // and
    // its infantry move to the empty system between, to take them next time, rather than wait at home. The move is too
    // small to count as a planned activation.
    @Test
    void advancesACarrierTowardPlanetsOutOfReachWhenTokensAreSpare() {
        test.game.setRound(1);
        test.nekro.setTacticalCC(3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.place("47", neighbour);
        test.place("27", twoSystemsFromHome());

        TacticalPlan plan = TacticalPlanner.best(test.game, test.nekro).orElseThrow();

        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.target()).isEqualTo(neighbour);
        assertThat(movedShips(plan, UnitType.Infantry)).isPositive();
        assertThat(TacticalPlanner.ranked(test.game, test.nekro)).isEmpty();
    }

    // With no enemy ship able to reach home there is no standing garrison: the last infantry may go to expand.
    @Test
    void takesTheLastInfantryWhenNothingCanReachHome() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1));
    }

    // A Sol landing party next to home changes that: with the carrier gone Mordai II would be empty against two
    // infantry, so the last infantry stays and there is nothing to expand with.
    @Test
    void keepsTheLastInfantryWhenALandingPartyCanReachHome() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.place("26", neighbour);
        solLandingPartyNextToHome();

        assertThat(expansionTo(neighbour)).isEmpty();
    }

    private void solLandingPartyNextToHome() {
        Tile staging = test.place("46", otherNeighbourOfHome());
        test.units(staging, "space", test.sol, UnitType.Carrier, 1);
        test.units(staging, "space", test.sol, UnitType.Cruiser, 2);
        test.units(staging, "space", test.sol, UnitType.Infantry, 2);
    }

    // Quann is two systems away and a carrier moves one. Gravity Drive gives one ship a move of +1 on each tactical
    // action, which is enough for the carrier and its infantry.
    @Test
    void gravityDriveTakesACarrierOneSystemFarther() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        String farther = twoSystemsFromHome();
        test.place("25", farther);
        assertThat(expansionTo(farther)).isEmpty();

        test.nekro.addTech("gd");

        assertThat(expansionTo(farther)).isPresent();
    }

    // Only one ship gets the extra move: of the two carriers at home, one goes to take Sol's undefended New Albion
    // and Starpoint. The dreadnought that cannot reach them stays home as the guard, so the carrier need not.
    @Test
    void gravityDriveSpeedsUpOnlyOneShipOfAnAttack() {
        String farther = twoSystemsFromHome();
        test.place("26", neighbour);
        test.place("27", farther);
        test.sol.addPlanet("newalbion");
        test.sol.addPlanet("starpoint");
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.nekro.addTech("gd");

        TacticalPlan plan = attackOn(farther).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1));
        assertThat(plan.moves()).noneMatch(move -> move.type() == UnitType.Dreadnought);
    }

    // A mech is the better garrison: when home needs one against a landing party, the mech stays and the last
    // infantry is free to take Lodor.
    @Test
    void aMechGarrisonFreesTheLastInfantry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 1);
        test.place("26", neighbour);
        solLandingPartyNextToHome();

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 1));
    }

    // A lone carrier is a coin flip against Sol's destroyer, but the fighters at home can ride along and make the
    // attack safe. Two of the carrier's four slots still carry infantry to land on Lodor. Nothing threatens home, so
    // the carrier needs no guard ship to stay behind (the old rule that kept one ship home is gone).
    @Test
    void bringsFightersToWinASpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(attackOn(neighbour)).isEmpty();

        test.units(home, "space", test.nekro, UnitType.Fighter, 2);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();
        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2));
        assertThat(plan.landings()).containsKey("lodor");
    }

    @Test
    void doesNotExpandIntoAnotherPlayersUnits() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.sol, UnitType.Fighter, 1);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);

        assertThat(plan.filter(found -> found.kind() == Kind.EXPAND)).isEmpty();
    }

    // With nothing to take, it builds at its home dock: a second carrier first, then infantry.
    @Test
    void producesAtHomeWhenThereIsNothingToTake() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.nekro.setTg(2);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.PRODUCE);
        assertThat(plan.get().target()).isEqualTo(AiTestGame.HOME);
        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units(UnitType.Carrier))
                .isEqualTo(1);
    }

    // Mordai II's 4 resources buy a carrier and two infantry: three units are not worth activating the system, so
    // the seat keeps its token and its resources for a bigger build.
    @Test
    void doesNotActivateADockToBuildFewerThanFourUnits() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units()).isEqualTo(3);
        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
    }

    // A flagship for Engineer a Marvel scores a point on its own, so that one unit is worth the token.
    @Test
    void buildsALoneFlagshipThatScoresAnObjective() {
        test.game.getRevealedPublicObjectives().put("engineer_marvel", 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(4);

        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units()).isEqualTo(1);
        assertThat(productionScoreAtHome()).isGreaterThan(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    @Test
    void doesNothingWithoutTacticTokens() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        test.nekro.setTacticalCC(0);

        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
    }

    // A lone infantry cannot be expected to beat three defenders, so the attack gate refuses.
    @Test
    void refusesAttacksWithPoorOdds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 3);

        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);

        assertThat(plan.filter(found -> found.kind() == Kind.ATTACK)).isEmpty();
    }

    // Sol holds Lodor with nothing but a War Sun. Ignoring the War Sun would make this a free planet plus a
    // technology copy; a carrier and a dreadnought cannot beat it, so there must be no attack.
    @Test
    void doesNotTreatALoneWarSunAsAnEmptyDefence() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.sol.addOwnedUnitByID("warsun");
        test.units(lodor, "space", test.sol, UnitType.Warsun, 1);
        assertThat(BoardView.hasEnemyShips(test.game, test.nekro, lodor)).isTrue();

        assertThat(attackOn(neighbour)).isEmpty();

        // A fleet that can beat the War Sun does attack, so it is the War Sun that holds the first fleet back.
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 6);
        test.nekro.setFleetCC(10);
        assertThat(attackOn(neighbour)).isPresent();
    }

    // Twelve infantry wait at home, but the infantry taken along must fit in the ships that leave: two carriers carry
    // eight. (The dreadnought that used to stay behind as the home guard is gone; a threatened home that holds ships
    // back is covered in the defence tests below.)
    @Test
    void neverPlansMoreInfantryThanTheDepartingShipsCanCarry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 12);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 2);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();

        int capacity = plan.moves().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> move.count() * BoardView.capacity(test.nekro, move.type()))
                .sum();
        int infantry = plan.moves().stream()
                .filter(move -> move.type() == UnitType.Infantry)
                .mapToInt(UnitMove::count)
                .sum();
        assertThat(infantry).isPositive().isLessThanOrEqualTo(capacity);
        assertThat(plan.landings().values().stream().mapToInt(Integer::intValue).sum())
                .isLessThanOrEqualTo(infantry);
    }

    // Fleet supply counts the AI's ships already waiting in the target system.
    @Test
    void dropsPlansThatWouldExceedFleetSupplyAtTheTarget() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "space", test.nekro, UnitType.Destroyer, 1);

        test.nekro.setFleetCC(2);
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                        .filter(found -> found.kind() == Kind.EXPAND))
                .isPresent();

        test.nekro.setFleetCC(1);
        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)
                        .filter(found -> found.kind() == Kind.EXPAND))
                .isEmpty();
    }

    // Four infantry against four on Lodor are a clear underdog (about 30% to take the planet), below even the relaxed
    // landing bar. X-89 Bacterial Weapon ΩΩ doubles their ground combat hits, which makes the same invasion worth it.
    @Test
    void x89MakesAnInvasionWorthIt() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 4);

        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("x89c4");
        assertThat(attackOn(neighbour).orElseThrow().landings()).containsEntry("lodor", 4);
    }

    // Nekro copies one of the defender's technologies after the first kill. Against Sol's four cruisers, copying
    // Fighter II upgrades its six fighters for the rest of the fight, so the attack scores better when Sol owns
    // Fighter II than when Sol only owns Antimass Deflectors, which would not change the fight. The fleet is two
    // carriers and the fighters: a dreadnought beside them would make the fight a walkover, and nothing threatens home
    // enough to keep it back.
    @Test
    void countsACombatTechnologyItWouldCopyMidFight() {
        double withAntimass = attackScoreAgainstFourCruisers("amd");
        double withFighterTwo = attackScoreAgainstFourCruisers("ff2");

        assertThat(withFighterTwo).isGreaterThan(withAntimass);
    }

    private double attackScoreAgainstFourCruisers(String solTechnology) {
        AiTestGame fresh = new AiTestGame();
        Tile freshHome = fresh.nekroHome();
        fresh.units(freshHome, "space", fresh.nekro, UnitType.Carrier, 2);
        fresh.units(freshHome, "space", fresh.nekro, UnitType.Fighter, 6);
        fresh.units(freshHome, "mordaiii", fresh.nekro, UnitType.Infantry, 4);
        fresh.nekro.setFleetCC(8);
        Tile lodor = fresh.place("26", neighbour);
        fresh.sol.addPlanet("lodor");
        fresh.units(lodor, "space", fresh.sol, UnitType.Cruiser, 4);
        fresh.sol.addTech(solTechnology);
        return TacticalPlanner.forTarget(fresh.game, fresh.nekro, neighbour)
                .filter(found -> found.kind() == Kind.ATTACK)
                .orElseThrow()
                .score();
    }

    // Dark Energy Tap explores a frontier token when a ship ends a tactical action in its system, so a spare destroyer
    // is worth sending to an empty neighbour holding one. Without the technology there is nothing to gain there.
    @Test
    void sendsAShipToExploreAFrontierWithDarkEnergyTap() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        Tile empty = test.place("46", neighbour);
        empty.getSpaceUnitHolder().addToken(Mapper.getTokenID(Constants.FRONTIER));

        assertThat(TacticalPlanner.forTarget(test.game, test.nekro, neighbour)).isEmpty();

        test.nekro.addTech("det");
        TacticalPlan plan =
                TacticalPlanner.forTarget(test.game, test.nekro, neighbour).orElseThrow();
        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.moves()).containsExactly(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 1));
    }

    // Copying a technology after a combat is Nekro's Technological Singularity. A seat without that ability must
    // not count it, so a marginal attack (three defenders on Lodor, on the border with Sol's area so that the territory
    // bonus is small) that only the copy made worthwhile is dropped.
    @Test
    void onlyASeatWithTechnologicalSingularityValuesCopyingATechnology() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 3);
        test.solHomeBeside(neighbour);

        assertThat(attackOn(neighbour)).isPresent();

        test.nekro.removeAbility("technological_singularity");

        assertThat(attackOn(neighbour)).isEmpty();
    }

    // Xanhact (0/1) is the only free planet left once Cealdri is held. Early in the game any free planet is worth a
    // tactical action, so the expansion clears the bar (0.6 + 0.8 tempo - 0.2 distance); by round 8 the tempo has
    // faded and the same planet (0.4) is no longer worth a token on its own. The explore decks are empty, so that
    // exploring the planet adds nothing to the numbers. Sol's home is placed so that the planet lies on the border,
    // where the territory bonus is only 0.3 a planet.
    @Test
    void takesALoneSmallPlanetEarlyButNotLate() {
        test.game.setExploreDeck(new ArrayList<>());
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("73", neighbour);
        test.nekro.addPlanet("cealdri");
        test.solHomeBeside(neighbour);

        test.game.setRound(2);
        TacticalPlan early = expansionTo(neighbour).orElseThrow();
        assertThat(early.landings()).containsOnlyKeys("xanhact");
        assertThat(early.score()).isGreaterThanOrEqualTo(TacticalPlanner.MIN_SCORE);

        test.game.setRound(8);
        assertThat(expansionTo(neighbour).orElseThrow().score()).isLessThan(TacticalPlanner.MIN_SCORE);
    }

    // With both carriers on the board and a carrier load of infantry already waiting at home, this build is only
    // filler: two mechs, two dreadnoughts and two fighters (13 resources at 0.2). Early in the game that is still
    // worth a spare tactic token when nothing else is, but the last token goes to Wellon next door (2.5 + 0.8 tempo
    // - 0.2 distance) instead.
    @Test
    void prefersAFreePlanetToDreadnoughtFillerWithItsLastToken() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.nekro.setFleetCC(5);
        test.nekro.setTg(10);
        test.nekro.setTacticalCC(1);

        assertThat(TacticalPlanner.best(test.game, test.nekro).map(TacticalPlan::kind))
                .contains(Kind.PRODUCE);

        test.place("19", neighbour);
        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, test.nekro);

        assertThat(plan.map(TacticalPlan::kind)).contains(Kind.EXPAND);
        assertThat(plan.map(TacticalPlan::target)).contains(neighbour);
    }

    // The same coin flip as above (a lone carrier, no guard ship needed), but a PDS II at home covers Lodor (Deep
    // Space Cannon): its expected hit removes the destroyer before the space combat, so the attack is safe without any
    // fighters.
    @Test
    void countsItsOwnSpaceCannonWhenAttacking() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 4);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.removeOwnedUnitByID("pds");
        test.nekro.addOwnedUnitByID("pds2");
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);

        assertThat(attackOn(neighbour)).isPresent();
    }

    // Destroyer II's barrage (3 dice on 6 each) is expected to shoot down all five of Sol's fighters before the
    // combat, leaving a lone carrier. Plain destroyers (2 dice on 9) only get one, and the rest of the swarm wins.
    @Test
    void countsAntiFighterBarrageBeforeTheSpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Carrier, 1);
        test.units(lodor, "space", test.sol, UnitType.Fighter, 5);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.removeOwnedUnitByID("destroyer");
        test.nekro.addOwnedUnitByID("destroyer2");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A carrier and two cruisers attack Sol's two cruisers: too even a fight. With Assault Cannon those three ships
    // make Sol destroy a cruiser before the combat, and the attack is on. (Nothing threatens home, so no cruiser is
    // kept back as a guard.)
    @Test
    void countsAssaultCannonBeforeTheSpaceCombat() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, 2);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("asc");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A carrier and two dreadnoughts attack Sol's four cruisers: they would win more often than not, but the expected
    // losses (about five resources) eat the prize. Non-Euclidean Shielding lets each dreadnought's sustain cancel two
    // hits, which cuts those losses and the attack is on. (Nothing threatens home, so no dreadnought is kept back as
    // a guard.)
    @Test
    void countsNonEuclideanShieldingInTheOdds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, 4);
        test.nekro.setFleetCC(5);
        assertThat(attackOn(neighbour)).isEmpty();

        test.nekro.addTech("nes");

        assertThat(attackOn(neighbour)).isPresent();
    }

    // A damaged dreadnought can no longer sustain damage, but it still fights: both dreadnoughts are damaged, and they
    // join the attack instead of waiting for repairs.
    @Test
    void bringsDamagedShipsIntoAnAttack() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        home.addUnitDamage("space", ti4.helpers.Units.getUnitKey(UnitType.Dreadnought, test.nekro.getColor()), 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.nekro.setFleetCC(5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);

        TacticalPlan plan = attackOn(neighbour).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
    }

    // Infantry are worth building while the dock is short of a carrier load; beyond that they are filler, so the same
    // four infantry score less once the home system already holds spares.
    @Test
    void valuesInfantryOnlyWhileTheDockIsShortOfACarrierLoad() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        double shortOfInfantry = productionScoreAtHome();

        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        double stocked = productionScoreAtHome();

        assertThat(shortOfInfantry).isGreaterThan(stocked);
    }

    // Destroyers built as pawns for a revealed presence objective are not filler: building them at home moves no
    // objective yet, so builtGain does not value them. Four fighters (2 resources at 0.2) plus two pawns (2
    // resources at 0.5) score 1.4, so the build is still worth a spare token early; discounting the pawns as filler
    // too would score 0.8.
    @Test
    void keepsTheValueOfDestroyerPawnsForAPresenceObjectiveEarly() {
        test.game.setPhaseOfGame("action");
        test.game.getRevealedPublicObjectives().put("deep_space", 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.nekro.setFleetCC(7);
        assertThat(ProductionPlanner.plan(test.game, test.nekro, home).units(UnitType.Destroyer))
                .isEqualTo(2);

        assertThat(productionScoreAtHome()).isGreaterThanOrEqualTo(TacticalPlanner.MIN_SCORE);
    }

    // The last carrier to leave home fills up with spare infantry beyond the one it lands. The rest land on the planet
    // too, ready to move on next round. The home garrison stays.
    @Test
    void fillsTheLastCarrierLeavingHomeWithSpareInfantry() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 4));
        assertThat(plan.landings()).containsOnlyKeys("lodor").containsEntry("lodor", 4);
    }

    // Capacity is used, but with forethought: the first carrier takes infantry for Lodor and what lies beyond it, and
    // leaves behind what the second carrier at home needs for the best system it can reach this round.
    @Test
    void leavesSpareInfantryHomeWhileAnotherCarrierIsThere() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.place("26", neighbour);
        test.place("25", otherNeighbourOfHome());

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .containsExactlyInAnyOrder(
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1),
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 3));
    }

    // Mecatol Rex is worth an Imperial point every round it is held, so its last infantry stays: an empty Mecatol
    // is the easiest planet on the board for a neighbour to take.
    @Test
    void keepsAnInfantryOnMecatolRex() {
        Tile rex = test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.units(rex, "space", test.nekro, UnitType.Carrier, 1);
        test.units(rex, "mr", test.nekro, UnitType.Infantry, 1);
        test.place("26", "101");

        assertThat(expansionTo("101")).isEmpty();

        // A second infantry on Mecatol is free to leave and take Lodor.
        test.units(rex, "mr", test.nekro, UnitType.Infantry, 1);
        assertThat(expansionTo("101")).isPresent();
    }

    private String twoSystemsFromHome() {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }

    // Taking a planet nobody holds explores it, so the planet is worth the average of what is left in its deck on
    // top of its own value. A Dyson Sphere alone (2 resources and 1 influence, 2.6) raises the plan by exactly that.
    @Test
    void valuesExploringAPlanetNobodyHolds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);

        test.game.setExploreDeck(new ArrayList<>());
        double unexplored = expansionTo(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double explored = expansionTo(neighbour).orElseThrow().score();

        assertThat(explored - unexplored).isCloseTo(2.6, org.assertj.core.data.Offset.offset(1e-9));
    }

    // A planet that someone holds is not explored when it is invaded, so the same deck changes nothing.
    @Test
    void doesNotValueExploringAPlanetAnotherPlayerHolds() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        test.game.setExploreDeck(new ArrayList<>());
        double without = attackOn(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double with = attackOn(neighbour).orElseThrow().score();

        assertThat(with).isEqualTo(without);
    }

    // The same attack on a planet nobody holds (but that has defenders) does explore it once it is taken.
    @Test
    void valuesExploringAPlanetTakenFromGroundForcesThatDoNotHoldIt() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", neighbour);
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        test.game.setExploreDeck(new ArrayList<>());
        double without = attackOn(neighbour).orElseThrow().score();
        test.game.setExploreDeck(new ArrayList<>(List.of("ds")));
        double with = attackOn(neighbour).orElseThrow().score();

        assertThat(with).isGreaterThan(without);
    }

    // A mech on a hazardous planet makes Volatile Fuel Source, Core Mine and Expedition free, so the carrier takes the
    // mech along and lands it there, with the infantry that come with it.
    @Test
    void landsAMechOnAHazardousPlanet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Mech, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("24", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Mech, 1));
        assertThat(plan.landings()).containsEntry("meharxull", 3);
        assertThat(plan.landings()).hasSize(1);
    }

    // With a second carrier at home to need the rest, the first still brings a second infantry for a hazardous planet:
    // the cards that remove an infantry then cost almost nothing, and one stays to move on from there.
    @Test
    void landsTwoInfantryOnAHazardousPlanet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 6);
        test.place("24", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.landings().get("meharxull")).isGreaterThanOrEqualTo(2);
    }

    // Nekro's opening into Abyz and Fria, two hazardous planets: two dreadnoughts carry one infantry each, and both
    // move so that every planet in the activated system is taken.
    @Test
    void bringsEnoughTransportsToTakeEveryPlanet() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        test.place("38", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.landings()).containsOnlyKeys("abyz", "fria");
        assertThat(plan.moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 2));
    }

    // Fighters ride along in the carrier's spare capacity: they move forward with it and guard it.
    @Test
    void fillsSpareCapacityWithFighters() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);

        TacticalPlan plan = expansionTo(neighbour).orElseThrow();

        assertThat(plan.moves())
                .contains(
                        new UnitMove(AiTestGame.HOME, "mordaiii", UnitType.Infantry, 2),
                        new UnitMove(AiTestGame.HOME, "space", UnitType.Fighter, 2));
    }

    // Home holds Nekro's command token, so its carrier is stuck there: no plan moves out of home. Freeing home (what
    // PoK Warfare does when it takes the token back) unlocks the expansion to Lodor again.
    @Test
    void rankedFreeingHomeMovesOutOfAHomeThatHoldsTheToken() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        home.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(TacticalPlanner.ranked(test.game, test.nekro)).noneMatch(TacticalPlannerTest::movesOutOfHome);
        assertThat(TacticalPlanner.rankedFreeing(test.game, test.nekro, AiTestGame.HOME))
                .anyMatch(TacticalPlannerTest::movesOutOfHome);
    }

    // ranked lists every plan worth an action, best first, ignoring how many tactic tokens are left.
    @Test
    void rankedIgnoresTheTokenGateAndSortsBestFirst() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", neighbour);
        test.nekro.setTacticalCC(0);

        List<TacticalPlan> ranked = TacticalPlanner.ranked(test.game, test.nekro);

        assertThat(TacticalPlanner.best(test.game, test.nekro)).isEmpty();
        assertThat(ranked).isNotEmpty();
        assertThat(TacticalPlanner.bestScore(ranked))
                .isEqualTo(ranked.getFirst().score());
        assertThat(ranked).isSortedAccordingTo((a, b) -> Double.compare(b.score(), a.score()));
        assertThat(TacticalPlanner.bestScore(List.of())).isZero();
    }

    private double secretBonusForThreeDreadnoughtsAttackingTheNeighbour(Tile target) {
        List<UnitMove> ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Dreadnought, 3));
        return TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);
    }

    private Tile neighbourWithSolDestroyerAndThreeDreadnoughtsAtHome() {
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        return target;
    }

    // Three dreadnoughts attacking Sol's destroyer meet Demonstrate Your Power. In round 2 with nobody scoring there
    // are rounds to meet it later (1 - 0.7^4 = 0.76), so the seat is patient: the fight is only worth a quarter of a
    // point of secret bonus. With an opponent on 6 of 10 the end is in sight and it is worth a full point.
    @Test
    void leansOnlySlightlyTowardsASecretItIsPatientWith() {
        Tile target = neighbourWithSolDestroyerAndThreeDreadnoughtsAtHome();
        test.nekro.setSecret("dyp");

        double early = secretBonusForThreeDreadnoughtsAttackingTheNeighbour(target);
        test.points(test.sol, 6);
        double late = secretBonusForThreeDreadnoughtsAttackingTheNeighbour(target);

        assertThat(early).isEqualTo(0.25 * ObjectiveValue.VICTORY_POINT_VALUE);
        assertThat(late).isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // The fight meets two secrets but is one space combat, so only the more valuable one counts: Destroy Their
    // Greatest Ship (rare, pursued, a full point) rather than the patient Demonstrate Your Power (0.25 of a point;
    // with Destroy Their Greatest Ship in hand too its chance is 0.3 * 0.85 a round, 1 - 0.745^4 = 0.69, still
    // patient in round 2).
    @Test
    void countsTheHighestValuedSecretsUpToTheNumberOfCombats() {
        Tile target = neighbourWithSolDestroyerAndThreeDreadnoughtsAtHome();
        test.units(target, "space", test.sol, UnitType.Flagship, 1);
        test.nekro.setSecret("dyp");
        test.nekro.setSecret("dtgs");

        assertThat(secretBonusForThreeDreadnoughtsAttackingTheNeighbour(target))
                .isEqualTo(ObjectiveValue.VICTORY_POINT_VALUE);
    }

    // Three destroyers (six barrage dice at 9) clear one fighter with probability 1 - 0.8^6. The secret is rare enough
    // that the seat pursues it at once (a chance of 0.13 a round does not promise a later chance), so the bonus is a
    // full point times that chance. It is nothing without the secret or when the target holds no fighters.
    @Test
    void valuesAnAttackWhoseBarrageClearsTheFightersForFightWithPrecision() {
        Tile target = test.place("46", neighbour);
        test.units(target, "space", test.sol, UnitType.Fighter, 1);
        test.units(target, "space", test.sol, UnitType.Destroyer, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 3);
        List<UnitMove> ships = List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 3));
        double chance = 1 - Math.pow(0.8, 6);

        assertThat(barrageBonus(target, ships)).isZero();
        test.nekro.setSecret("fwp");
        assertThat(barrageBonus(target, ships)).isCloseTo(VP * chance, within(EXACT));
        target.removeUnit("space", Units.getUnitKey(UnitType.Fighter, test.sol.getColor()), 1);
        assertThat(barrageBonus(target, ships)).isZero();
    }

    private double barrageBonus(Tile target, List<UnitMove> ships) {
        return TacticalPlanner.actionSecretBonus(test.game, test.nekro, target, test.sol, ships, true, false);
    }

    // Holding Fight with Precision, three destroyers at home are worth moving next door to a system two steps from
    // fighters they could not reach from home: a barrage ship that can reach the fighters next turn.
    @Test
    void positionsBarrageShipsWithinReachOfFighters() {
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        String middle = stepAwayFrom(near, AiTestGame.HOME);
        String far = stepAwayFrom(middle, near);
        test.place("26", near);
        test.place("27", middle);
        test.place("46", far);
        test.units(test.game.getTileByPosition(far), "space", test.sol, UnitType.Fighter, 1);
        test.units(home, "space", test.nekro, UnitType.Destroyer, 3);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);

        assertThat(positionAt(middle)).isEmpty();

        test.nekro.setSecret("fwp");
        Optional<TacticalPlan> plan = positionAt(middle);

        assertThat(plan).isPresent();
        assertThat(plan.get().moves()).contains(new UnitMove(AiTestGame.HOME, "space", UnitType.Destroyer, 3));
    }

    private Optional<TacticalPlan> positionAt(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position)
                .filter(found -> found.kind() == Kind.POSITION);
    }

    private static String stepAwayFrom(String position, String behind) {
        List<String> behindRing = new ArrayList<>(MapPositionMapper.getAdjacentTilePositions(behind));
        behindRing.add(behind);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !behindRing.contains(candidate))
                .findFirst()
                .orElseThrow();
    }

    private static boolean movesOutOfHome(TacticalPlan plan) {
        return plan.moves().stream().anyMatch(move -> AiTestGame.HOME.equals(move.origin()));
    }

    private String otherNeighbourOfHome() {
        return MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate) && !neighbour.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private Optional<TacticalPlan> attackOn(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(found -> found.kind() == Kind.ATTACK);
    }

    private Optional<TacticalPlan> expansionTo(String position) {
        return TacticalPlanner.forTarget(test.game, test.nekro, position).filter(found -> found.kind() == Kind.EXPAND);
    }

    private static final int IMPERIAL = 8;
    private static final double EXACT = 1e-9;
    private static final double VP = ObjectiveValue.VICTORY_POINT_VALUE;

    // Sol at 6 of 10: the late game, when a held Overrule is played for points.
    private void lateGame() {
        test.game.scorePublicObjective(test.sol.getUserID(), test.game.addCustomPO("Test points", 6));
    }

    private void imperialIsInTheGame() {
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
    }

    private void carrierWithInfantryNextTo(String position, int infantry) {
        Tile staging = test.place("26", AiTestGame.neighbourOf(position));
        test.units(staging, "space", test.nekro, UnitType.Carrier, 1);
        test.units(staging, "space", test.nekro, UnitType.Infantry, infantry);
    }

    // Taking Mecatol Rex lets the Imperial point be cashed: the point Imperial gives for holding it, once with its own
    // unplayed Imperial and once more through Overrule, which performs Imperial's primary again. Each point counts as
    // the offense outlook's own-Imperial share of a victory point (0.8 of 8).
    @Test
    void takingMecatolRexIsWorthTheImperialPointsItCanCash() {
        lateGame();
        imperialIsInTheGame();
        test.place("18", "000");
        carrierWithInfantryNextTo("000", 2);
        double plain = expansionTo("000").orElseThrow().score();
        double point = 0.8 * ObjectiveValue.VICTORY_POINT_VALUE;

        test.nekro.setActionCard("overrule", 1);
        double withOverrule = expansionTo("000").orElseThrow().score();
        assertThat(withOverrule - plain).isCloseTo(point, within(EXACT));

        test.nekro.addSC(IMPERIAL);
        double withBoth = expansionTo("000").orElseThrow().score();
        assertThat(withBoth - plain).isCloseTo(2 * point, within(EXACT));

        test.nekro.getActionCards().clear();
        double withImperial = expansionTo("000").orElseThrow().score();
        assertThat(withImperial - plain).isCloseTo(point, within(EXACT));
    }

    // Early in the game the Overrule is being saved, so it cashes no point yet.
    @Test
    void anOverruleSavedForTheLateGameIsNotWorthTakingMecatolRexForEarly() {
        imperialIsInTheGame();
        test.place("18", "000");
        carrierWithInfantryNextTo("000", 2);
        double plain = expansionTo("000").orElseThrow().score();

        test.nekro.setActionCard("overrule", 1);

        assertThat(expansionTo("000").orElseThrow().score()).isCloseTo(plain, within(EXACT));
    }

    @Test
    void anOverruleItCannotPlayIsNotWorthTakingMecatolRexFor() {
        imperialIsInTheGame();
        test.place("18", "000");
        carrierWithInfantryNextTo("000", 2);
        double plain = expansionTo("000").orElseThrow().score();

        test.nekro.setActionCard("overrule", 1);
        test.game.setSCPlayed(IMPERIAL, true);

        assertThat(expansionTo("000").orElseThrow().score()).isCloseTo(plain, within(EXACT));
    }

    @Test
    void takingMecatolRexFromAnotherPlayerIsWorthTheSameImperialPoints() {
        imperialIsInTheGame();
        Tile rex = test.place("18", "000");
        test.sol.addPlanet("mr");
        test.units(rex, "mr", test.sol, UnitType.Infantry, 1);
        Tile staging = test.place("26", AiTestGame.neighbourOf("000"));
        test.units(staging, "space", test.nekro, UnitType.Carrier, 1);
        test.units(staging, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(staging, "space", test.nekro, UnitType.Infantry, 4);
        double plain = attackOn("000").orElseThrow().score();

        test.nekro.addSC(IMPERIAL);

        assertThat(attackOn("000").orElseThrow().score()).isGreaterThan(plain);
    }

    @Test
    void holdingMecatolRexAlreadyIsNotTakenAgain() {
        imperialIsInTheGame();
        test.nekro.addSC(IMPERIAL);

        assertThat(MecatolCash.points(test.game, test.nekro)).isEqualTo(1);

        test.place("18", "000");
        test.nekro.addPlanet("mr");

        assertThat(MecatolCash.points(test.game, test.nekro)).isZero();
    }

    private double productionScoreAtHome() {
        return TacticalPlanner.forTarget(test.game, test.nekro, AiTestGame.HOME)
                .filter(found -> found.kind() == Kind.PRODUCE)
                .orElseThrow()
                .score();
    }
}
