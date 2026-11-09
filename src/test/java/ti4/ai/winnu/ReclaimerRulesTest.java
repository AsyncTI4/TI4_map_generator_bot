package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Reclaimer (Winnu mech): after a tactical action in which the Winnu took the mech's planet, the bot offers
// unowned buttons to place 1 space dock or 1 PDS there, plus a Done button that closes the offer.
class ReclaimerRulesTest extends BaseTi4Test {

    private static final String POSITION = "101";
    private static final String DOCK = "winnuStructure_sd_lodor";
    private static final String PDS = "winnuStructure_pds_lodor";
    private static final String DONE = "deleteButtons";

    private WinnuTestBoard board;
    private Tile lodor;
    private AiPrompt offer;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        lodor = board.test.place("26", POSITION);
        board.winnu.addPlanet("lodor");
        board.test.units(lodor, "lodor", board.winnu, UnitType.Mech, 1);
        board.winnuActive("action");
        offer = prompt("reclaimer", PromptSource.PUBLIC, NOW, DOCK, PDS, DONE);
    }

    // A space dock is the bigger prize, so it goes down when the system has none of the seat's docks yet.
    @Test
    void placesASpaceDockWhereTheSystemHasNone() {
        assertThat(ReclaimerRules.next(context(Set.of(), offer)).map(AiTestGame::pressedId))
                .contains(DOCK);
    }

    @Test
    void placesAPdsWhereTheSystemAlreadyHasADock() {
        board.test.units(lodor, "lodor", board.winnu, UnitType.Spacedock, 1);

        assertThat(ReclaimerRules.next(context(Set.of(), offer)).map(AiTestGame::pressedId))
                .contains(PDS);
    }

    // The bot leaves the offer up after a structure is placed, so the seat closes it.
    @Test
    void closesTheOfferAfterPlacingAStructure() {
        Set<String> pressed =
                Set.of(AiTurnContext.pressKey(offer, offer.buttons().getFirst()));

        assertThat(ReclaimerRules.next(context(pressed, offer)).map(AiTestGame::pressedId))
                .contains(DONE);
    }

    // Reclaimer ruling III: with two Reclaimers on the planet the seat places a structure for each, here a space dock
    // and then a PDS, and closes the offer only after the second.
    @Test
    void placesAStructureForEachReclaimer() {
        board.test.units(lodor, "lodor", board.winnu, UnitType.Mech, 1);
        Set<String> dockPlaced =
                Set.of(AiTurnContext.pressKey(offer, offer.buttons().getFirst()));
        board.test.units(lodor, "lodor", board.winnu, UnitType.Spacedock, 1);

        assertThat(ReclaimerRules.next(context(dockPlaced, offer)).map(AiTestGame::pressedId))
                .contains(PDS);

        Set<String> bothPlaced = Set.of(
                AiTurnContext.pressKey(offer, offer.buttons().getFirst()),
                AiTurnContext.pressKey(offer, offer.buttons().get(1)));
        assertThat(ReclaimerRules.next(context(bothPlaced, offer)).map(AiTestGame::pressedId))
                .contains(DONE);
    }

    @Test
    void closesTheOfferWhenNoStructureIsLeft() {
        board.winnu.setUnitCap(Reclamation.SPACE_DOCK, 1);
        board.winnu.setUnitCap(Reclamation.PDS, 1);
        board.test.units(board.test.place("46", "102"), "space", board.winnu, UnitType.Spacedock, 1);
        board.test.units(lodor, "lodor", board.winnu, UnitType.Pds, 1);

        assertThat(ReclaimerRules.next(context(Set.of(), offer)).map(AiTestGame::pressedId))
                .contains(DONE);
    }

    // Another player's Reclaimer offer is not the seat's to answer.
    @Test
    void ignoresAnOfferForAPlanetItDoesNotHold() {
        board.winnu.removePlanet("lodor");

        assertThat(ReclaimerRules.next(context(Set.of(), offer))).isEmpty();
    }

    @Test
    void waitsForItsOwnTurn() {
        board.test.isActive(board.test.sol, "action");

        assertThat(ReclaimerRules.next(context(Set.of(), offer))).isEmpty();
    }

    private AiTurnContext context(Set<String> pressed, AiPrompt... prompts) {
        return board.test.contextFor(board.winnu, pressed, NOW, prompts);
    }
}
