package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.IconStake;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

class TraitRaidTest extends BaseTi4Test {

    private static final String THIBAH = "thibah";

    private AiTestGame test;
    private Tile home;
    private List<String> ring;
    private String target;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.sol.removeTech("amd");
        ring = new ArrayList<>(MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .toList());
        target = ring.get(0);
    }

    private void ownsIndustrial(int planets) {
        List<String> tiles = List.of("36", "19");
        List<String> names = List.of("arnor", "lor", "wellon");
        int placed = 0;
        for (int index = 0; index < tiles.size() && placed < planets; index++) {
            test.place(tiles.get(index), ring.get(index + 1));
            for (int planet = 0; planet < (index == 0 ? 2 : 1) && placed < planets; planet++) {
                test.nekro.addPlanet(names.get(placed++));
            }
        }
    }

    private Tile solHoldsThibah(int infantry) {
        Tile tile = test.place("21", target);
        test.sol.addPlanet(THIBAH);
        test.units(tile, THIBAH, test.sol, UnitType.Infantry, infantry);
        return tile;
    }

    private void raidingFleet() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 3);
    }

    private Optional<TacticalPlan> landingOnThibah() {
        return TacticalPlanner.forTarget(test.game, test.nekro, target)
                .filter(plan -> plan.kind() == Kind.ATTACK && plan.landings().containsKey(THIBAH));
    }

    private double secretShareOfTheRaid() {
        return TacticalPlanner.explainAttack(test.game, test.nekro, target)
                .orElseThrow()
                .ownObjective();
    }

    private String beyondTarget() {
        return MapPositionMapper.getAdjacentTilePositions(target).stream()
                .filter(position -> !"x".equals(position) && !ring.contains(position))
                .filter(position -> !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
    }

    private double raidScore() {
        return landingOnThibah().orElseThrow().score();
    }

    // With Monopolize Production held, the industrial planet outranks a richer cultural one; without it the richer
    // one still comes first.
    @Test
    void claimsAHeldTraitPlanetAheadOfARicherOne() {
        Tile cultural = test.place("26", ring.get(1));
        Tile industrial = test.place("19", ring.get(2));
        List<Planet> both = new ArrayList<>(cultural.getPlanetUnitHolders());
        both.addAll(industrial.getPlanetUnitHolders());

        List<Planet> without = both.stream()
                .sorted(IconStake.claimOrder(test.game, test.nekro))
                .toList();
        test.nekro.setSecret("mp");
        List<Planet> with = both.stream()
                .sorted(IconStake.claimOrder(test.game, test.nekro))
                .toList();

        assertThat(without.getFirst().getName()).isEqualTo("lodor");
        assertThat(with.getFirst().getName()).isEqualTo("wellon");
    }

    @Test
    void stopsPullingOnceTheSecretHasItsFourPlanets() {
        Tile cultural = test.place("26", ring.get(1));
        Tile industrial = test.place("19", ring.get(2));
        test.nekro.setSecret("mp");
        test.place("36", ring.get(3));
        test.place("21", ring.get(4));
        test.place("22", ring.get(5));
        for (String planet : List.of("arnor", "lor", "thibah", "tarmann")) test.nekro.addPlanet(planet);
        List<Planet> both = new ArrayList<>(cultural.getPlanetUnitHolders());
        both.addAll(industrial.getPlanetUnitHolders());

        List<Planet> order = both.stream()
                .sorted(IconStake.claimOrder(test.game, test.nekro))
                .toList();

        assertThat(order.getFirst().getName()).isEqualTo("lodor");
    }

    // A rival planet held by one infantry is already a fair raid for its resources. The secret adds the completing
    // point
    // on top, in full only when the planet is the fourth.
    @Test
    void raidsALightlyHeldRivalPlanetThatCompletesTheCount() {
        ownsIndustrial(3);
        solHoldsThibah(1);
        raidingFleet();
        double without = raidScore();

        test.nekro.setSecret("mp");

        assertThat(raidScore()).isGreaterThan(without + 6);
    }

    @Test
    void liftsARaidLessWhileTwoPlanetsShort() {
        ownsIndustrial(2);
        solHoldsThibah(1);
        raidingFleet();
        double without = raidScore();

        test.nekro.setSecret("mp");

        double lift = raidScore() - without;
        assertThat(lift).isPositive().isLessThan(4);
    }

    @Test
    void doesNotRaidAHeavilyDefendedPlanet() {
        ownsIndustrial(3);
        solHoldsThibah(6);
        raidingFleet();
        test.nekro.setSecret("mp");

        assertThat(landingOnThibah()).isEmpty();
    }

    @Test
    void landsOnARivalPlanetWithAPdsOnlyForTheSecret() {
        ownsIndustrial(3);
        Tile tile = solHoldsThibah(1);
        test.units(tile, THIBAH, test.sol, UnitType.Pds, 1);
        raidingFleet();

        assertThat(landingOnThibah()).isEmpty();

        test.nekro.setSecret("mp");

        assertThat(landingOnThibah()).isPresent();
    }

    @Test
    void valuesARaidMoreOnceTheOwnerHasPassed() {
        ownsIndustrial(3);
        solHoldsThibah(1);
        raidingFleet();
        test.nekro.setSecret("mp");
        Tile solBase = test.place("20", beyondTarget());
        test.units(solBase, "space", test.sol, UnitType.Carrier, 1);
        test.units(solBase, "space", test.sol, UnitType.Infantry, 3);

        double whileActing = secretShareOfTheRaid();
        double scoreWhileActing = raidScore();
        test.sol.setPassed(true);
        double afterPassing = secretShareOfTheRaid();

        assertThat(afterPassing).isGreaterThan(whileActing);
        assertThat(raidScore()).isGreaterThan(scoreWhileActing);
    }

    @Test
    void guardsTheTilesOfAPlanetWhoseLossBreaksANearCompleteCount() {
        ownsIndustrial(3);
        Tile wellon = test.game.getTileFromPlanet("wellon");

        assertThat(new DefenseOutlook(test.game, test.nekro).isGuarded(wellon)).isFalse();

        test.nekro.setSecret("mp");

        assertThat(new DefenseOutlook(test.game, test.nekro).isGuarded(wellon)).isTrue();
    }

    @Test
    void leavesTilesUnguardedWhileTheCountIsStillFarOff() {
        test.place("36", ring.get(1));
        test.place("19", ring.get(2));
        test.nekro.addPlanet("arnor");
        test.nekro.addPlanet("wellon");
        test.nekro.setSecret("mp");

        Tile tile = test.game.getTileFromPlanet("wellon");

        assertThat(new DefenseOutlook(test.game, test.nekro).isGuarded(tile)).isFalse();
    }

    @Test
    void buildsInfantryForARaidOnlyWhenOneIsWorthMaking() {
        ownsIndustrial(3);
        solHoldsThibah(1);
        DefenseOutlook defense = new DefenseOutlook(test.game, test.nekro);

        assertThat(TraitRaid.infantryToBuild(test.game, test.nekro, home, 0, defense))
                .isZero();

        test.nekro.setSecret("mp");

        assertThat(TraitRaid.infantryToBuild(test.game, test.nekro, home, 0, defense))
                .isEqualTo(3);
        assertThat(TraitRaid.infantryToBuild(test.game, test.nekro, home, 3, defense))
                .isZero();
    }

    @Test
    void buildsNoRaidInfantryAgainstTheSoleLeader() {
        ownsIndustrial(3);
        solHoldsThibah(1);
        test.nekro.setSecret("mp");
        test.points(test.sol, 5);
        DefenseOutlook defense = new DefenseOutlook(test.game, test.nekro);

        assertThat(TraitRaid.infantryToBuild(test.game, test.nekro, home, 0, defense))
                .isZero();
    }

    @Test
    void namesTheGateThatBlocksARaid() {
        ownsIndustrial(3);
        solHoldsThibah(1);
        test.nekro.setSecret("mp");

        assertThat(TraitRaid.firstBlockingGate(test.game, test.nekro)).isEqualTo("NO_FLEET");

        raidingFleet();

        assertThat(TraitRaid.firstBlockingGate(test.game, test.nekro)).isEqualTo("OPEN");
    }

    @Test
    void namesNoGateWithoutATraitSecret() {
        ownsIndustrial(3);
        solHoldsThibah(1);

        assertThat(TraitRaid.firstBlockingGate(test.game, test.nekro)).isEmpty();
        assertThat(TraitRaid.firstUnclaimedGate(test.game, test.nekro)).isEmpty();
    }
}
