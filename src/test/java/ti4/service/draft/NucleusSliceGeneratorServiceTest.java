package ti4.service.draft;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusOutcome;
import ti4.testUtils.BaseTi4Test;

@Disabled("Inherently flaky tests; DEFINITELY run locally before changing nucleus generation.")
public class NucleusSliceGeneratorServiceTest extends BaseTi4Test {
    // Note this is currently half what is actually used in game.
    private static final int REASONABLE_MAX_ATTEMPTS = 2500;
    // Min is -1.
    // NOTE: These tests get flakey at FEWER slices.
    private static final int EXTRA_SLICES = 0;

    // If running this locally, try testing with this set to 10+
    // to really exercise the generator.
    private static final int MIN_SUCCESS_COUNT = 1;

    private static boolean useStrictMode(int totalSlices) {
        return totalSlices < 14;
    }

    @Test
    void testStandard6p() {
        beforeAll();
        Game game = createTestGame(6);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testStandard5p() {
        beforeAll();
        Game game = createTestGame(5);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testStandard4p() {
        beforeAll();
        Game game = createTestGame(4);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testStandard3p() {
        beforeAll();
        Game game = createTestGame(3);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testStandard7p() {
        beforeAll();
        Game game = createTestGame(7);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testStandard8p() {
        beforeAll();
        Game game = createTestGame(8);

        testSuccessfulNucleusForGame(game);
    }

    @Test
    void testAllSources6p() {
        beforeAll();

        Game game = createTestGame(6);
        game.setUnchartedSpaceStuff(true);
        game.getDraftTileManager().reset(game);

        // Sanity checks
        assert game.getDraftTileManager().getAll().stream()
                .anyMatch(t ->
                        t.getTile().getTileModel().getSource() == ti4.model.Source.ComponentSource.uncharted_space);

        MiltySettings settings = game.initializeMiltySettings();
        settings.getDraftMode().setChosenKey("nucleus");
        settings.getSourceSettings().getDiscoStars().setVal(true);
        settings.getSourceSettings().getUnchartedSpace().setVal(true);
        settings.getSourceSettings().getEronous().setVal(true);

        testSuccessfulNucleusForMiltySettings(game, settings);
    }

    @Test
    void testAllSources3p() {
        beforeAll();

        Game game = createTestGame(3);
        game.setUnchartedSpaceStuff(true);
        game.getDraftTileManager().reset(game);

        // Sanity checks
        assert game.getDraftTileManager().getAll().stream()
                .anyMatch(t ->
                        t.getTile().getTileModel().getSource() == ti4.model.Source.ComponentSource.uncharted_space);

        MiltySettings settings = game.initializeMiltySettings();
        settings.getDraftMode().setChosenKey("nucleus");
        settings.getSourceSettings().getDiscoStars().setVal(true);
        settings.getSourceSettings().getUnchartedSpace().setVal(true);
        settings.getSourceSettings().getEronous().setVal(true);

        testSuccessfulNucleusForMiltySettings(game, settings);
    }

    @Test
    void testAllSources8p() {
        beforeAll();

        Game game = createTestGame(8);
        game.setUnchartedSpaceStuff(true);
        game.getDraftTileManager().reset(game);

        // Sanity checks
        assert game.getDraftTileManager().getAll().stream()
                .anyMatch(t ->
                        t.getTile().getTileModel().getSource() == ti4.model.Source.ComponentSource.uncharted_space);

        MiltySettings settings = game.initializeMiltySettings();
        settings.getDraftMode().setChosenKey("nucleus");
        settings.getSourceSettings().getDiscoStars().setVal(true);
        settings.getSourceSettings().getUnchartedSpace().setVal(true);
        settings.getSourceSettings().getEronous().setVal(true);

        testSuccessfulNucleusForMiltySettings(game, settings);
    }

    private void testSuccessfulNucleusForGame(Game game) {
        MiltySettings settings = game.initializeMiltySettings();
        settings.getDraftMode().setChosenKey("nucleus");
        game.getDraftTileManager().reset(game);
        testSuccessfulNucleusForMiltySettings(game, settings);
    }

    private void testSuccessfulNucleusForMiltySettings(Game game, MiltySettings settings) {
        DraftSpec specs = DraftSpec.CreateFromMiltySettings(settings);

        MapTemplateModel normalTemplate =
                Mapper.getDefaultMapTemplateForPlayerCount(game.getPlayers().size());
        String nucleusTemplateName = normalTemplate.getAlias() + "Nucleus";
        MapTemplateModel nucleusTemplate = Mapper.getMapTemplate(nucleusTemplateName);
        game.setMapTemplateID(nucleusTemplateName);
        specs.setTemplate(nucleusTemplate);
        specs.setNumSlices(specs.getNumSlices() + EXTRA_SLICES);
        boolean strictMode = useStrictMode(specs.getNumSlices() + nucleusTemplate.getPlayerCount());

        game.clearTileMap();
        PartialMapService.tryUpdateMap(null, game.getDraftManager(), false);

        for (int i = 0; i < MIN_SUCCESS_COUNT; ++i) {
            runTest(game, nucleusTemplate, specs, strictMode);
        }
    }

    private void runTest(Game game, MapTemplateModel mapTemplate, DraftSpec specs, boolean strictMode) {
        // Test that generator can succeed with these settings (often enough to be a unit test)
        NucleusOutcome outcome = null;
        Map<String, Integer> failureReasons = new HashMap<>();
        for (int i = 0; i < REASONABLE_MAX_ATTEMPTS; ++i) {
            outcome = NucleusSliceGeneratorService.tryGenerateNucleusAndSlices(
                    game, mapTemplate, specs.getNumSlices(), strictMode);
            if (outcome.slices() != null) {
                assert outcome.slices().size() == game.getPlayers().size() + 1 + EXTRA_SLICES;
                return;
            }
            failureReasons.put(outcome.failureReason(), failureReasons.getOrDefault(outcome.failureReason(), 0) + 1);
        }

        String mostCommonFailure = failureReasons.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry<String, Integer>::getKey)
                .orElse("none");

        assertNull(mostCommonFailure);
    }

    private Game createTestGame(int playerCount) {
        Game game = new Game();
        game.setName("testGame");
        for (int i = 0; i < playerCount; i++) {
            game.addPlayer("p" + (i + 1), "blue");
        }
        return game;
    }
}
