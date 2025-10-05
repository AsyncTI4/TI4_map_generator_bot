package ti4.commands.developer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftSpec;
import ti4.service.draft.DraftTileManager;
import ti4.service.draft.NucleusSliceGeneratorService;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusOutcome;
import ti4.service.draft.NucleusSpecs;
import ti4.service.draft.PartialMapService;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

class ProduceNucleusGenStats extends GameStateSubcommand {

    ProduceNucleusGenStats() {
        super("nucleus_gen_stats", "Generate statistics for nucleus generation for the in-channel game.", false, false);
        addOptions(new OptionData(
                OptionType.INTEGER, "num_iterations", "Number of iterations to run - default 1,000", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int numIterations = event.getOption("num_iterations", 1000, OptionMapping::getAsInt);
        Game game = getGame();
        DraftSpec draftSpec = new DraftSpec(game);
        if (game.getMiltySettingsUnsafe() != null) {
            draftSpec = DraftSpec.CreateFromMiltySettings(game.getMiltySettingsUnsafe());
        } else {
            String mapTemplateId = game.getMapTemplateID();
            MapTemplateModel mapTemplate = mapTemplateId != null ? Mapper.getMapTemplate(mapTemplateId) : null;
            if (mapTemplate == null) {
                mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(
                        game.getPlayers().size());
            }
            draftSpec.setTemplate(mapTemplate);
            draftSpec.numSlices = mapTemplate.getPlayerCount() + 1;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Generating nucleus generation stats for " + numIterations + " iterations to make "
                        + draftSpec.numSlices + " slices for "
                        + draftSpec.getTemplate().getAlias() + ".");
        runAndPrintData(event, draftSpec, numIterations);
    }

    private void runAndPrintData(GenericInteractionCreateEvent event, DraftSpec draftSpecs, int numIterations) {
        if (numIterations > 10_000) {
            numIterations = 10_000;
        }

        Game game = draftSpecs.getGame();
        MapTemplateModel mapTemplate = draftSpecs.getTemplate();

        Map<String, Integer> failureReasonCount = new HashMap<>();
        List<Integer> successAfterAttempts = new ArrayList<>();

        DraftTileManager tileManager = game.getDraftTileManager();
        if (tileManager.getAll().isEmpty()) {
            tileManager.clear();
            tileManager.addAllDraftTiles(draftSpecs.getTileSources());
        }
        Map<String, Integer> tileOccurrenceInSuccessfulMaps = new HashMap<>();
        Map<String, Integer> tileOccurrenceInSuccessfulSlices = new HashMap<>();

        List<Double> failRuntimes = new ArrayList<>();
        List<Double> successRuntimes = new ArrayList<>();

        NucleusSpecs nucleusSpecs =
                new NucleusSpecs(draftSpecs.getTemplate().getPlayerCount(), draftSpecs.getNumSlices());

        boolean strictMode = NucleusSliceGeneratorService.useStrictMode(mapTemplate);
        for (int i = 0; i < numIterations; ++i) {
            long startTime = System.nanoTime();
            NucleusOutcome outcome = NucleusSliceGeneratorService.tryGenerateNucleusAndSlices(
                    game, mapTemplate, nucleusSpecs, strictMode);
            long endTime = System.nanoTime();
            if (outcome.slices() != null) {
                successAfterAttempts.add(i + 1);
                successRuntimes.add((endTime - startTime) / 1_000_000.0);
                for (MiltyDraftSlice slice : outcome.slices()) {
                    for (MiltyDraftTile tile : slice.getTiles()) {
                        tileOccurrenceInSuccessfulSlices.put(
                                tile.getTile().getTileID(),
                                tileOccurrenceInSuccessfulSlices.getOrDefault(
                                                tile.getTile().getTileID(), 0)
                                        + 1);
                    }
                }
                for (Tile placedTile : game.getTileMap().values()) {

                    if (tileManager
                            .filterAll(t -> t.getTile().getTileID().equals(placedTile.getTileID()))
                            .isEmpty()) continue;

                    tileOccurrenceInSuccessfulMaps.put(
                            placedTile.getTileID(),
                            tileOccurrenceInSuccessfulMaps.getOrDefault(placedTile.getTileID(), 0) + 1);
                }

                // Reset map tiles for next attempt, but ensure draft tiles are out for the
                // distance tool
                game.clearTileMap();
                PartialMapService.tryUpdateMap(game.getDraftManager(), event, false);
            } else {
                failRuntimes.add((endTime - startTime) / 1_000_000.0);
                failureReasonCount.put(
                        outcome.failureReason(), failureReasonCount.getOrDefault(outcome.failureReason(), 0) + 1);
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("Out of ").append(numIterations).append(" attempts:\n");
        result.append("- Successes: ").append(successAfterAttempts.size()).append("\n");
        if (!successRuntimes.isEmpty()) {
            result.append("- Average Success Runtime: ")
                    .append(successRuntimes.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0))
                    .append(" ms\n");
        }
        if (!failRuntimes.isEmpty()) {
            result.append("- Average Failure Runtime: ")
                    .append(failRuntimes.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0))
                    .append(" ms\n");
        }
        result.append("- Failures: ").append(failureReasonCount.size()).append(" distinct:\n");
        List<Map.Entry<String, Integer>> failureReasonCounts = new ArrayList<>(failureReasonCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList());
        Collections.reverse(failureReasonCounts);
        failureReasonCounts.forEach((entry) -> result.append("  - ")
                .append(entry.getKey())
                .append(": ")
                .append(entry.getValue())
                .append("\n"));

        var tilesByOccurrence = tileManager.getAll().stream()
                .sorted(Comparator.comparing(t -> {
                    Integer occurrenceInMaps = tileOccurrenceInSuccessfulMaps.getOrDefault(
                            t.getTile().getTileID(), 0);
                    Integer occurrenceInSlices = tileOccurrenceInSuccessfulSlices.getOrDefault(
                            t.getTile().getTileID(), 0);
                    return occurrenceInMaps + occurrenceInSlices;
                }))
                .toList();
        tilesByOccurrence = new ArrayList<>(tilesByOccurrence);
        Collections.reverse(tilesByOccurrence);

        result.append("Tile usage stats breakdown:\n");
        for (MiltyDraftTile draftTile : tilesByOccurrence) {
            String tileId = draftTile.getTile().getTileID();
            int inMaps = tileOccurrenceInSuccessfulMaps.getOrDefault(tileId, 0);
            int inSlices = tileOccurrenceInSuccessfulSlices.getOrDefault(tileId, 0);
            int inTotal = inMaps + inSlices;
            result.append("- (")
                    .append(draftTile.getTile().getTileID())
                    .append(") ")
                    .append(draftTile.getTile().getRepresentation());
            float inSlicesPercent = (inSlices * 100.0f) / Math.max(1, successAfterAttempts.size());
            float inMapsPercent = (inMaps * 100.0f) / Math.max(1, successAfterAttempts.size());
            float inTotalPercent = (inTotal * 100.0f) / Math.max(1, successAfterAttempts.size());
            result.append(": ")
                    .append(inTotal)
                    .append(" (")
                    .append(String.format("%.1f", inTotalPercent))
                    .append("%)");
            result.append("\n  - in nucleus=")
                    .append(inMaps)
                    .append(" (")
                    .append(String.format("%.1f", inMapsPercent))
                    .append("%)");
            result.append("\n  - in slices=")
                    .append(inSlices)
                    .append(" (")
                    .append(String.format("%.1f", inSlicesPercent))
                    .append("%)");
            result.append("\n");
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result.toString());
    }
}
