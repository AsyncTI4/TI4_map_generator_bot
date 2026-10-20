package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Establish a Perimeter (4 PDS on the board): every Construction structure, follows included, goes to a PDS while the
// secret is held and short, except when an early dock is worth more or another secret is closer to scoring.
class PerimeterTest extends BaseTi4Test {

    private static final int CONSTRUCTION = 4;
    private static final String EAP = "eap";

    private AiTestGame test;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
        test.nekroHome();
        test.game.setVp(10);
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        test.place("26", "101");
        test.nekro.addPlanet("lodor");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private void pds(String planet, int count) {
        Tile tile = test.game.getTileFromPlanet(planet);
        test.units(tile, planet, test.nekro, UnitType.Pds, count);
        changed();
    }

    private void holdsPerimeter() {
        test.nekro.setSecret(EAP);
        changed();
    }

    private static AiPrompt constructionCard() {
        return prompt(
                "construction",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_4",
                "construction_pds",
                "construction_spacedock");
    }

    private void someoneElsePlayedConstruction() {
        test.sol.addSC(CONSTRUCTION);
        test.game.setSCPlayed(CONSTRUCTION, true);
        test.game.setActivePlayerID(test.sol.getUserID());
    }

    private CardPlan plan() {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), CONSTRUCTION);
    }

    private double objectiveValue() {
        return plan().benefit(CardUse.CONSTRUCTION_OBJECTIVE)
                .map(CardPlan.Benefit::value)
                .orElse(0.0);
    }

    // The follow used to go through next(), which prefers the forward dock on Lodor.
    @Test
    void followPlacesAPdsRatherThanADockWhileThePerimeterIsShort() {
        holdsPerimeter();
        pds("mordaiii", 1);
        someoneElsePlayedConstruction();

        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StrategyCardRules.follow(test.context(constructionCard())).map(AiTestGame::pressedId))
                .contains("construction_pds");
    }

    @Test
    void followKeepsPlacingTheDockWithoutThePerimeter() {
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
        pds("mordaiii", 1);
        someoneElsePlayedConstruction();

        assertThat(StrategyCardRules.follow(test.context(constructionCard())).map(AiTestGame::pressedId))
                .contains("construction_spacedock");
    }

    @Test
    void strategizeSecondaryPlacesAPdsWhileThePerimeterIsShort() {
        holdsPerimeter();
        pds("mordaiii", 2);

        assertThat(StructurePolicy.secondary(test.game, test.nekro)).contains(StructurePolicy.PDS);
    }

    @Test
    void followsConstructionThreeShortButNotFour() {
        holdsPerimeter();
        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isFalse();

        pds("mordaiii", 1);

        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isTrue();
    }

    @Test
    void primaryKeepsTheEarlyDockWhenTwoPdsDoNotCloseThePerimeter() {
        holdsPerimeter();
        pds("mordaiii", 1);

        assertThat(StructurePolicy.primaryFirst(test.game, test.nekro, 1)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StructurePolicy.pds(test.game, test.nekro)).contains(StructurePolicy.PDS);
    }

    @Test
    void primaryTakesTwoPdsWhenTheyClosethePerimeter() {
        holdsPerimeter();
        pds("mordaiii", 1);
        pds("lodor", 1);

        assertThat(StructurePolicy.primaryFirst(test.game, test.nekro, 1)).contains(StructurePolicy.PDS);
    }

    @Test
    void primaryTakesPdsOnceTheEarlyDockRoundsAreOver() {
        holdsPerimeter();
        pds("mordaiii", 1);

        assertThat(StructurePolicy.primaryFirst(test.game, test.nekro, CardValueScale.LAST_EARLY_DOCK_ROUND + 1))
                .contains(StructurePolicy.PDS);
    }

    @Test
    void respectsTheUnitLimit() {
        holdsPerimeter();
        pds("mordaiii", 1);
        test.nekro.setUnitCap("pds", 1);

        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isFalse();
        assertThat(StructurePolicy.secondary(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);
    }

    @Test
    void placesNoThirdPdsOnAPlanet() {
        holdsPerimeter();
        pds("mordaiii", 2);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii")))
                .isEmpty();
        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii", "lodor")))
                .contains("lodor");
    }

    // Starpoint's dock plus a dock on New Albion is Produce en Masse one dock away: with the perimeter two PDS short
    // the dock comes first, and once the perimeter is one short too the PDS does.
    @Test
    void breaksATieWithProduceEnMasseBySmallerShortfall() {
        Tile starpoint = test.place("27", "102");
        test.nekro.addPlanet("starpoint");
        test.nekro.addPlanet("newalbion");
        test.units(starpoint, "starpoint", test.nekro, UnitType.Spacedock, 1);
        test.nekro.setSecret("pem");
        holdsPerimeter();
        pds("mordaiii", 2);

        assertThat(StructurePolicy.perimeterShortfall(test.game, test.nekro)).isEqualTo(2);
        assertThat(StructurePolicy.secondary(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StructurePolicy.primaryFirst(test.game, test.nekro, 4)).contains(StructurePolicy.SPACE_DOCK);

        pds("lodor", 1);

        assertThat(StructurePolicy.perimeterShortfall(test.game, test.nekro)).isEqualTo(1);
        assertThat(StructurePolicy.secondary(test.game, test.nekro)).contains(StructurePolicy.PDS);
        assertThat(StructurePolicy.primaryFirst(test.game, test.nekro, 4)).contains(StructurePolicy.PDS);
    }

    @Test
    void valuesConstructionMoreWithThePerimeterHeld() {
        pds("mordaiii", 2);
        double without = StrategyCardRanking.value(test.game, test.nekro, CONSTRUCTION);
        assertThat(plan().has(CardUse.CONSTRUCTION_OBJECTIVE)).isFalse();

        holdsPerimeter();

        assertThat(StrategyCardRanking.value(test.game, test.nekro, CONSTRUCTION))
                .isGreaterThan(without);
        assertThat(plan().has(CardUse.CONSTRUCTION_OBJECTIVE)).isTrue();
    }

    @Test
    void valuesTheObjectiveInFullTwoPdsShort() {
        holdsPerimeter();
        pds("mordaiii", 2);

        assertThat(objectiveValue())
                .isCloseTo(CardValueScale.STRUCTURE_OBJECTIVE_SHARE * CardValueScale.VICTORY_POINT, within(1e-9));
    }

    @Test
    void scalesTheObjectiveValueDownThreeShort() {
        holdsPerimeter();
        pds("mordaiii", 1);

        assertThat(objectiveValue())
                .isCloseTo(
                        CardValueScale.STRUCTURE_OBJECTIVE_SHARE * CardValueScale.VICTORY_POINT * 2 / 3, within(1e-9));
    }
}
