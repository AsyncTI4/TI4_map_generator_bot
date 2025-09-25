package ti4.service.draft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

public class DraftSaveServiceTest extends BaseTi4Test {
    @Test
    public void testSaveAndLoad() {
        beforeAll();
        Game game = createTestGame(6);
        DraftManager draftManager = new DraftManager(game);
        draftManager.setPlayers(game.getPlayerIDs().stream().toList());

        // Just add one of every draftable
        for (String draftableClassName : DraftComponentFactory.getKnownDraftableTypes()) {
            Draftable draftable = getDraftableWithTestData(draftableClassName);
            assertNotNull(draftable, "No test data set up for draftable type: " + draftableClassName);
            draftManager.addDraftable(draftable);

            // Make some random picks
            List<DraftChoice> choices = draftable.getAllDraftChoices();
            int i = 0;
            for (PlayerDraftState state : draftManager.getPlayerStates().values()) {
                if (i >= choices.size()) {
                    break;
                }
                Map<DraftableType, List<DraftChoice>> picks = state.getPicks();
                picks.putIfAbsent(draftable.getType(), new java.util.ArrayList<>());
                picks.get(draftable.getType()).add(choices.get(i));
                ++i;
            }
        }

        // Try save and load with each orchestrator type
        for (String orchestratorClassName : DraftComponentFactory.getKnownOrchestratorTypes()) {
            DraftOrchestrator orchestrator = getOrchestratorWithTestData(orchestratorClassName);
            assertNotNull(orchestrator, "No test data set up for orchestrator class: " + orchestratorClassName);
            draftManager.setOrchestrator(orchestrator);
            TestData.setOrchestratorPlayerState(orchestrator, draftManager);

            // Initial state should be valid
            assertNull(orchestrator.validateState(draftManager));
            for (Draftable draftable : draftManager.getDraftables()) {
                assertNull(draftable.validateState(draftManager));
            }

            String savedData = DraftSaveService.saveDraftManager(draftManager);
            DraftManager loadedManager = DraftLoadService.loadDraftManager(game, savedData);

            // Loaded state should be valid
            assertNull(orchestrator.validateState(loadedManager));
            for (Draftable draftable : loadedManager.getDraftables()) {
                assertNull(draftable.validateState(loadedManager));
            }

            // Check that save data matches is the same for original and loaded managers
            assertEquals(
                    savedData,
                    DraftSaveService.saveDraftManager(loadedManager),
                    "Mismatch in save data when you save, then load, then save again a draft manager using orchestrator "
                            + orchestratorClassName);

            // Check for choice key consistency
            for (Draftable draftable : draftManager.getDraftables()) {
                List<DraftChoice> originalChoices = draftable.getAllDraftChoices();
                List<DraftChoice> loadedDraftable =
                        loadedManager.getDraftableByType(draftable.getType()).getAllDraftChoices();
                assertArrayEquals(
                        originalChoices.stream().map(DraftChoice::getChoiceKey).toArray(),
                        loadedDraftable.stream().map(DraftChoice::getChoiceKey).toArray(),
                        "Mismatch in choice keys for draftable type: "
                                + draftable.getClass().getSimpleName());

                for (String userId : draftManager.getPlayerStates().keySet()) {
                    List<DraftChoice> originalPlayerChoices = draftManager.getPlayerPicks(userId, draftable.getType());
                    List<DraftChoice> loadedPlayerChoices = loadedManager.getPlayerPicks(userId, draftable.getType());
                    assertArrayEquals(
                            originalPlayerChoices.stream()
                                    .map(DraftChoice::getChoiceKey)
                                    .toArray(),
                            loadedPlayerChoices.stream()
                                    .map(DraftChoice::getChoiceKey)
                                    .toArray(),
                            "Mismatch in player " + userId + " choice keys for draftable type: "
                                    + draftable.getClass().getSimpleName());
                }
            }
        }
    }

    @Test
    public void testSaveFormatUnchanged() {
        beforeAll();
        String draftSave = TestData.getTestFile(TestData.FINISHED_6P_DRAFT_FILE);
        assertNotNull(draftSave, "Test data for finished 6p draft is missing or empty");
        Game game = createTestGame(6);
        DraftManager draftManager = DraftLoadService.loadDraftManager(game, draftSave);

        // Loaded state should be valid
        assertNull(draftManager.getOrchestrator().validateState(draftManager));
        for (Draftable draftable : draftManager.getDraftables()) {
            assertNull(draftable.validateState(draftManager));
        }

        // Check that save data matches is the same for original and loaded managers
        assertEquals(
                draftSave,
                DraftSaveService.saveDraftManager(draftManager),
                "Mismatch in save data when you save, then load, then save again a draft manager");
    }

    private Draftable getDraftableWithTestData(String className) {
        switch (className) {
            case "FactionDraftable":
                return TestData.createFactionDraftable();
            case "SeatDraftable":
                return TestData.createSeatDraftable();
            case "SliceDraftable":
                return TestData.createSliceDraftable();
            case "SpeakerOrderDraftable":
                return TestData.createSpeakerOrderDraftable();
            default:
                return null;
        }
    }

    private DraftOrchestrator getOrchestratorWithTestData(String className) {
        switch (className) {
            case "PublicSnakeDraftOrchestrator":
                return TestData.createPublicSnakeDraftOrchestrator();
            default:
                return null;
        }
    }

    private Game createTestGame(int playerCount) {
        Game game = new Game();
        game.setName("testGame");
        for (int i = 0; i < playerCount; i++) {
            game.addPlayer("p" + (i + 1), "blue");
        }
        game.setMapTemplateID(
                Mapper.getDefaultMapTemplateForPlayerCount(playerCount).getID());
        return game;
    }
}
