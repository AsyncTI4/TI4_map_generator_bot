package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The follow is worth a strategy token when the best structure the secondary can place is worth more than the token
// plus a margin. The test game's cards are the Prophecy of Kings ones, so these also cover that variant.
class ConstructionFollowTest extends BaseTi4Test {

    private static final int CONSTRUCTION = 4;
    private static final int LATE_ROUND = 5;
    private static final String DOCK_BUTTON = "construction_spacedock";
    private static final String PDS_BUTTON = "construction_pds";

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
        test.sol.addSC(CONSTRUCTION);
        test.game.setSCPlayed(CONSTRUCTION, true);
        test.game.setActivePlayerID(test.sol.getUserID());
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private static AiPrompt constructionCard() {
        return prompt("construction", PromptSource.PUBLIC, NOW, "sc_no_follow_4", PDS_BUTTON, DOCK_BUTTON);
    }

    private Optional<String> follow() {
        changed();
        return StrategyCardRules.follow(test.context(constructionCard())).map(AiTestGame::pressedId);
    }

    private Tile forwardPlanet(String tileId, String position, String planet) {
        Tile tile = test.place(tileId, position);
        test.nekro.addPlanet(planet);
        return tile;
    }

    private void revealBuildDefenses() {
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
    }

    @Test
    void followsToPlaceAnEarlyDockOnAForwardPlanet() {
        forwardPlanet("26", "101", "lodor");

        assertThat(follow()).contains(DOCK_BUTTON);
    }

    @Test
    void doesNotFollowForALonePdsAtHome() {
        assertThat(follow()).isEmpty();
        assertThat(StructurePolicy.bestSecondary(test.game, test.nekro))
                .hasValueSatisfying(best -> assertThat(best.unit()).isEqualTo(StructurePolicy.PDS));
    }

    @Test
    void followsWithAPdsWhileThePerimeterIsThreeShort() {
        test.nekro.setSecret("eap");
        test.units(test.game.getTileFromPlanet("mordaiii"), "mordaiii", test.nekro, UnitType.Pds, 1);
        forwardPlanet("26", "101", "lodor");

        assertThat(follow()).contains(PDS_BUTTON);
    }

    @Test
    void doesNotFollowWithoutASpareStrategyToken() {
        forwardPlanet("26", "101", "lodor");
        test.nekro.setStrategicCC(0);

        assertThat(follow()).isEmpty();
    }

    @Test
    void stillFollowsTheFirstExtraDockLate() {
        test.game.setRound(LATE_ROUND);
        forwardPlanet("26", "101", "lodor");

        assertThat(follow()).contains(DOCK_BUTTON);
    }

    // The first dock past the home one is worth the full dock value and each further one splits it again, so with a
    // plentiful strategy pool a third dock late in the game no longer pays for its token.
    @Test
    void halvesTheDockValueForEachDockAlreadyPlacedBeyondTheFirst() {
        test.game.setRound(LATE_ROUND);
        test.nekro.setStrategicCC(3);
        Tile lodor = forwardPlanet("26", "101", "lodor");
        forwardPlanet("25", "102", "quann");
        double first = StructurePolicy.bestSecondary(test.game, test.nekro)
                .map(StructurePolicy.Placement::value)
                .orElseThrow();

        test.units(lodor, "lodor", test.nekro, UnitType.Spacedock, 1);
        double second = StructurePolicy.bestSecondary(test.game, test.nekro)
                .map(StructurePolicy.Placement::value)
                .orElseThrow();

        assertThat(first).isCloseTo(CardValueScale.DOCK_LATE, within(1e-9));
        assertThat(second).isLessThanOrEqualTo(CardValueScale.DOCK_LATE / 2 + 1e-9);
        assertThat(follow()).isEmpty();
    }

    @Test
    void secondaryValueIsTheValueOfTheBestPlacement() {
        forwardPlanet("26", "101", "lodor");

        assertThat(StrategyCardRules.secondaryValue(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .contains(StructurePolicy.bestSecondary(test.game, test.nekro)
                        .map(StructurePolicy.Placement::value)
                        .orElseThrow());
    }

    @Test
    void offersNothingWhenNoStructureCanBePlaced() {
        test.units(test.game.getTileFromPlanet("mordaiii"), "mordaiii", test.nekro, UnitType.Pds, 2);

        assertThat(StructurePolicy.bestSecondary(test.game, test.nekro)).isEmpty();
        assertThat(StrategyCardRules.secondaryValue(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isEmpty();
        assertThat(follow()).isEmpty();
    }

    @Test
    void wantsToFollowCheaplyForADockSiteButNotForAHomePds() {
        assertThat(StrategyCardRules.wantsToFollow(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isFalse();

        forwardPlanet("26", "101", "lodor");

        assertThat(StrategyCardRules.wantsToFollow(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isTrue();
    }

    @Test
    void closesAnObjectiveWithTheDockOnAForwardPlanet() {
        revealBuildDefenses();
        test.units(test.game.getTileFromPlanet("mordaiii"), "mordaiii", test.nekro, UnitType.Pds, 2);
        forwardPlanet("26", "101", "lodor");

        assertThat(follow()).contains(DOCK_BUTTON);
        assertThat(StructurePolicy.bestSecondary(test.game, test.nekro))
                .hasValueSatisfying(best -> assertThat(best.value())
                        .isGreaterThan(CardValueScale.DOCK_EARLY
                                + CardValueScale.STRUCTURE_OBJECTIVE_SHARE * CardValueScale.VICTORY_POINT
                                - 1e-9));
    }

    // In aiplay1 a 4-structure public was in play and the seat held only its home dock. The public could not be scored
    // that round, but a PDS from the follow leaves it 2 short, which one Construction closes next round. That progress
    // is worth the token.
    @Test
    void followsWithAPdsForAPublicThreeStructuresShort() {
        revealBuildDefenses();
        assertThat(StructurePolicy.structureShortfall(test.game, test.nekro)).isEqualTo(3);

        assertThat(StrategyCardRules.wantsToFollow(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isTrue();
        assertThat(follow()).contains(PDS_BUTTON);
    }

    // With the last strategy token the follow is still worth it, as long as no better card is waiting for it.
    @Test
    void spendsTheLastStrategyTokenOnThatProgress() {
        revealBuildDefenses();
        test.nekro.setStrategicCC(1);

        assertThat(follow()).contains(PDS_BUTTON);
    }

    // Four short is beyond what this follow and one Construction next round can close, so a lone home PDS still does
    // not pay for the token.
    @Test
    void doesNotFollowForAPublicFourStructuresShort() {
        test.game
                .getRevealedPublicObjectives()
                .put("massive_cities", test.game.getRevealedPublicObjectives().size() + 1);
        test.units(test.game.getTileFromPlanet("mordaiii"), "mordaiii", test.nekro, UnitType.Pds, 2);
        assertThat(StructurePolicy.structureShortfall(test.game, test.nekro)).isEqualTo(4);

        assertThat(StrategyCardRules.wantsToFollow(test.game, test.nekro, StrategyCard.CONSTRUCTION))
                .isFalse();
        assertThat(follow()).isEmpty();
    }

    // Saar's Floating Factory goes into the space area, so the planet-based follow never offers a dock for it.
    @Test
    void offersNoDockToTheSaar() {
        Player saar = test.addSeat("200000000000000009", "saar", "green");
        test.place("26", "101");
        saar.addPlanet("lodor");
        saar.setStrategicCC(2);

        assertThat(StructurePolicy.bestSecondary(test.game, saar))
                .hasValueSatisfying(best -> assertThat(best.unit()).isEqualTo(StructurePolicy.PDS));
    }
}
