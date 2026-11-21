package ti4.ai.legendary;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The bot posts each legendary planet ability as a button in the end-of-turn or the passing menu and the choices
// that follow as new messages; the AI opens the ability that is worth most, then answers the choices.
class LegendaryRulesTest extends BaseTi4Test {

    private static final String ABILITY = "FFCC_nekro_planetAbilityExhaust_";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
    }

    private AiPrompt endMenu(String planet) {
        return prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", ABILITY + planet);
    }

    private AiPrompt passMenu(String planet) {
        return prompt("pass", PromptSource.PUBLIC, NOW, "FFCC_nekro_passForRound", ABILITY + planet);
    }

    private Optional<AiDecision> atTheEndOfTheTurn(AiPrompt menu) {
        return LegendaryRules.endOfTurn(test.context(menu), List.of(menu));
    }

    private Optional<AiDecision> whenPassing(AiPrompt menu) {
        return LegendaryRules.beforePassing(test.context(menu), List.of(menu));
    }

    @Test
    void knowsTheOfficialLegendaryPlanetsAndNotTheHomebrewOnes() {
        for (String planet : List.of(
                "mallice",
                "hopesend",
                "primor",
                "mirage",
                "illusion",
                "phantasm",
                "ordinianc4",
                "custodiavigilia",
                "avernus",
                "emelpar",
                "faunus",
                "garbozia",
                "industrex",
                "mrte",
                "ordinian",
                "styx",
                "tempesta",
                "thundersedge")) {
            assertThat(LegendaryPlanets.isOfficial(planet)).as(planet).isTrue();
        }
        for (String planet : List.of("alfheim", "hexmallice", "silence", "cineron", "ako40m", "ghoti", "lapidus")) {
            assertThat(LegendaryPlanets.isOfficial(planet)).as(planet).isFalse();
        }
    }

    // Thunder's Edge hands over a whole action each round; Mirage's fighters are a resource. The planner reads it.
    @Test
    void holdsOfficialLegendariesAtTheValueOfTheirAbility() {
        assertThat(LegendaryPlanets.holdingValue("thundersedge"))
                .isGreaterThan(LegendaryPlanets.holdingValue("mirage"));
        assertThat(LegendaryPlanets.holdingValue("ordinian")).isGreaterThan(LegendaryPlanets.holdingValue("primor"));
        assertThat(LegendaryPlanets.holdingValue("alfheim")).isEqualTo(BoardView.LEGENDARY_VALUE);
        assertThat(LegendaryPlanets.holdingValue(null)).isEqualTo(BoardView.LEGENDARY_VALUE);
    }

    @Test
    void dropsInfantryOnPrimorAtTheEndOfTheTurn() {
        test.nekro.addPlanet("primor");

        assertThat(pressedId(atTheEndOfTheTurn(endMenu("primor")).orElseThrow()))
                .isEqualTo(ABILITY + "primor");

        AiPrompt place =
                prompt("place", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_placeOneNDone_skipbuild_2gf_mordaiii");
        assertThat(pressedId(LegendaryRules.next(test.context(place)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_2gf_mordaiii");
        assertThat(LegendaryRules.next(test.context(place))).isEmpty();
    }

    // A card exhausts once a round: the menu still lists it until the bot refreshes, but the AI has used it.
    @Test
    void usesAnAbilityOnlyOncePerTurn() {
        test.nekro.addPlanet("primor");
        AiPrompt menu = endMenu("primor");

        assertThat(atTheEndOfTheTurn(menu)).isPresent();
        AiPrompt place =
                prompt("place", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_placeOneNDone_skipbuild_2gf_mordaiii");
        assertThat(LegendaryRules.next(test.context(place))).isPresent();

        assertThat(atTheEndOfTheTurn(menu)).isEmpty();
    }

    @Test
    void leavesAnExhaustedAbilityAlone() {
        test.nekro.addPlanet("primor");
        test.nekro.exhaustPlanetAbility("primor");

        assertThat(atTheEndOfTheTurn(endMenu("primor"))).isEmpty();
    }

    @Test
    void takesTwoTradeGoodsFromMalliceWithFewCommodities() {
        test.nekro.addPlanet("mallice");
        test.nekro.setCommodities(1);

        assertThat(pressedId(atTheEndOfTheTurn(endMenu("mallice")).orElseThrow()))
                .isEqualTo(ABILITY + "mallice");

        AiPrompt choice = prompt("choice", PromptSource.PUBLIC, NOW + 1, "mallice_2_tg", "mallice_convert_comm");
        assertThat(pressedId(LegendaryRules.next(test.context(choice)).orElseThrow()))
                .isEqualTo("mallice_2_tg");
    }

    @Test
    void convertsAFullSetOfCommoditiesWithMallice() {
        test.nekro.addPlanet("mallice");
        test.nekro.setCommodities(test.nekro.getCommoditiesTotal());
        assertThat(test.nekro.getCommodities()).isGreaterThanOrEqualTo(3);

        assertThat(atTheEndOfTheTurn(endMenu("mallice"))).isPresent();

        AiPrompt choice = prompt("choice", PromptSource.PUBLIC, NOW + 1, "mallice_2_tg", "mallice_convert_comm");
        assertThat(pressedId(LegendaryRules.next(test.context(choice)).orElseThrow()))
                .isEqualTo("mallice_convert_comm");
    }

    @Test
    void dropsAMechOnAPlanetWithHopesEnd() {
        test.nekro.addPlanet("hopesend");

        assertThat(pressedId(atTheEndOfTheTurn(endMenu("hopesend")).orElseThrow()))
                .isEqualTo(ABILITY + "hopesend");

        AiPrompt choice = prompt(
                "choice",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_nekro_placeOneNDone_skipbuild_mech_mordaiii",
                "draw_1_ACDelete");
        assertThat(pressedId(LegendaryRules.next(test.context(choice)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_mech_mordaiii");
    }

    // Mirage's two fighters need room: with no ship to carry them there is nowhere worth putting them.
    @Test
    void placesMirageFightersOnlyWhereThereIsCapacity() {
        test.nekro.addPlanet("mirage");
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        assertThat(atTheEndOfTheTurn(endMenu("mirage"))).isEmpty();

        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        assertThat(pressedId(atTheEndOfTheTurn(endMenu("mirage")).orElseThrow()))
                .isEqualTo(ABILITY + "mirage");

        AiPrompt place = prompt(
                "place", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_placeOneNDone_skipbuild_2ff_" + AiTestGame.HOME);
        assertThat(pressedId(LegendaryRules.next(test.context(place)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeOneNDone_skipbuild_2ff_" + AiTestGame.HOME);
    }

    @Test
    void drawsAnActionCardAndATokenWithOrdinianWhenPassing() {
        test.nekro.addPlanet("ordinian");

        assertThat(pressedId(whenPassing(passMenu("ordinian")).orElseThrow())).isEqualTo(ABILITY + "ordinian");

        AiPrompt gain = prompt(
                "gain",
                PromptSource.PUBLIC,
                NOW + 1,
                List.of(
                        "FFCC_nekro_increase_tactic_cc",
                        "FFCC_nekro_increase_fleet_cc",
                        "FFCC_nekro_increase_strategy_cc",
                        "FFCC_nekro_deleteButtons"),
                List.of(
                        "Gain 1 Tactic Token",
                        "Gain 1 Fleet Token",
                        "Gain 1 Strategy Token",
                        "Done Gaining Command Tokens"));
        assertThat(pressedId(LegendaryRules.next(test.context(gain)).orElseThrow()))
                .startsWith("FFCC_nekro_increase_");
        assertThat(pressedId(LegendaryRules.next(test.context(gain)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons");
        assertThat(LegendaryRules.next(test.context(gain))).isEmpty();
    }

    @Test
    void doesNotUsePassingAbilitiesAtTheEndOfTheTurn() {
        test.nekro.addPlanet("ordinian");

        assertThat(atTheEndOfTheTurn(passMenu("ordinian"))).isEmpty();
    }

    // Faunus takes a planet with no units and no attachments: the one worth most to the seat, Lodor (3/1) over Quann.
    @Test
    void takesTheBestFreePlanetWithFaunus() {
        test.nekro.addPlanet("faunus");
        String first = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", first);
        test.place(
                "25",
                AiTestGame.HOME.equals(AiTestGame.neighbourOf(first))
                        ? otherNeighbour(first)
                        : AiTestGame.neighbourOf(first));

        assertThat(pressedId(whenPassing(passMenu("faunus")).orElseThrow())).isEqualTo(ABILITY + "faunus");

        AiPrompt choice = prompt(
                "choice",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_nekro_faunusTake_quann",
                "FFCC_nekro_faunusTake_lodor",
                "deleteButtons");
        assertThat(pressedId(LegendaryRules.next(test.context(choice)).orElseThrow()))
                .isEqualTo("FFCC_nekro_faunusTake_lodor");
    }

    @Test
    void readiesAnExhaustedPlanetWithEmelpar() {
        test.nekro.addPlanet("emelpar");
        test.nekro.exhaustPlanet("mordaiii");

        assertThat(pressedId(atTheEndOfTheTurn(endMenu("emelpar")).orElseThrow()))
                .isEqualTo(ABILITY + "emelpar");
        AiPrompt choice = prompt(
                "choice",
                PromptSource.PUBLIC,
                NOW + 1,
                "emelparReady_planet_nekro_mordaiii",
                "getOtherFactionsEmelpar");
        assertThat(pressedId(LegendaryRules.next(test.context(choice)).orElseThrow()))
                .isEqualTo("emelparReady_planet_nekro_mordaiii");
    }

    @Test
    void takesAnotherActionWithThundersEdgeOnlyWhenOneIsWorthIt() {
        test.nekro.addPlanet("thundersedge");

        assertThat(atTheEndOfTheTurn(endMenu("thundersedge"))).isEmpty();
    }

    // After a tactical action, a second one that attacks is worth the card: Thunder's Edge opens it and the seat may
    // take another action this turn.
    @Test
    void takesAnotherActionWithThundersEdgeWhenAnAttackIsAvailable() {
        test.nekro.addPlanet("thundersedge");
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        Tile lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("lodor");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction");
        Set<String> pressed = Set.of(AiTurnContext.pressKey(turn, turn.buttons().getFirst()));
        AiPrompt menu = endMenu("thundersedge");

        assertThat(LegendaryRules.endOfTurn(test.context(Set.of(), turn, menu), List.of(turn, menu)))
                .as("no action taken yet")
                .isEmpty();

        AiTurnContext context = test.context(pressed, turn, menu);
        assertThat(pressedId(
                        LegendaryRules.endOfTurn(context, List.of(turn, menu)).orElseThrow()))
                .isEqualTo(ABILITY + "thundersedge");
        assertThat(TacticalRules.mayTakeAnotherAction(context)).isTrue();
    }

    @Test
    void addsTheTempestaMoveToWhatTheFleetCanReach() {
        int before = BoardView.moveValueWithBoosts(test.nekro, UnitType.Carrier);
        test.nekro.addPlanet("tempesta");

        assertThat(BoardView.moveValueWithBoosts(test.nekro, UnitType.Carrier)).isEqualTo(before + 1);

        test.nekro.exhaustPlanetAbility("tempesta");
        assertThat(BoardView.moveValueWithBoosts(test.nekro, UnitType.Carrier)).isEqualTo(before);
    }

    // The planner counts Tempesta's +1 among the moves a ship can make, so a move that needs it also needs the
    // button: the AI exhausts the card in the movement step, once.
    @Test
    void exhaustsTempestaForAMoveThatReachesOneSystemTooFar() {
        test.nekro.addPlanet("tempesta");
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        String middle = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", middle);
        String far = otherNeighbour(middle);
        test.place("25", far);
        int reach = BoardView.moveValueWithGravityDrive(test.nekro, UnitType.Carrier);
        assertThat(reach).isEqualTo(1);

        TacticalPlan plan = new TacticalPlan(
                Kind.EXPAND, far, List.of(new UnitMove(AiTestGame.HOME, "space", UnitType.Carrier, 1)), Map.of(), 5);
        AiPrompt movement = prompt(
                "move",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalMoveFrom_" + AiTestGame.HOME,
                "planetAbilityExhaust_tempesta");

        Optional<AiDecision> boost = TempestaRules.boost(test.context(movement), movement, Optional.of(plan));
        if (TempestaRules.needsBoost(test.game, test.nekro, plan)) {
            assertThat(pressedId(boost.orElseThrow())).isEqualTo("planetAbilityExhaust_tempesta");
            assertThat(TempestaRules.boost(test.context(movement), movement, Optional.of(plan)))
                    .isEmpty();
        } else {
            assertThat(boost).isEmpty();
        }
    }

    // The Nucleus is a component action: open the menu, exhaust the card, name the system with the war sun, then take
    // the destroyer. It needs a war sun to forge in.
    @Test
    void starForgesWithTheNucleusFromTheSystemOfItsWarSun() {
        test.nekro.addPlanet("avernus");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        assertThat(AvernusRules.beforePassing(test.context(turn), List.of(turn)))
                .isEmpty();

        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Warsun, 1);
        assertThat(pressedId(AvernusRules.beforePassing(test.context(turn), List.of(turn))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");

        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_planetAbilityExhaust_avernus");
        assertThat(pressedId(AvernusRules.next(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_planetAbilityExhaust_avernus");

        AiPrompt systems = prompt("systems", PromptSource.PUBLIC, NOW + 2, "starforgeTileFree_" + AiTestGame.HOME);
        assertThat(pressedId(AvernusRules.next(test.context(systems)).orElseThrow()))
                .isEqualTo("starforgeTileFree_" + AiTestGame.HOME);

        AiPrompt ships = prompt(
                "ships",
                PromptSource.PUBLIC,
                NOW + 3,
                "FFCC_nekro_starforgeFree_destroyer_" + AiTestGame.HOME,
                "FFCC_nekro_starforgeFree_fighters_" + AiTestGame.HOME);
        assertThat(pressedId(AvernusRules.next(test.context(ships)).orElseThrow()))
                .isEqualTo("FFCC_nekro_starforgeFree_destroyer_" + AiTestGame.HOME);
        assertThat(AvernusRules.next(test.context(ships))).isEmpty();
    }

    private static String otherNeighbour(String position) {
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
    }
}
