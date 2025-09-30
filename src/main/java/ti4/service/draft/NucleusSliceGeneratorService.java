package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.DistanceTool;
import ti4.helpers.ListHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.milty.TierList;

// "borrowed heavily" from https://github.com/heisenbugged/ti4-lab/blob/main/app/draft/heisen/generateMap.ts

// WARNING: Don't change this file without making sure these pass pretty often:
// - src\test\java\ti4\service\draft\NucleusSliceGeneratorServiceTest.java

@UtilityClass
public class NucleusSliceGeneratorService {

    // TODO: These should be in a Settings object; they should adjust based on core
    // slice at least.
    private static final int CORE_SLICE_MIN_OPTIMAL = 4;
    private static final int CORE_SLICE_MAX_OPTIMAL = 8;

    private static final int ATTEMPTS = 50_000;

    // Major known issues:
    // - Because the Nucleus is made up of several slices which share some times (e.g. equidistants),
    //   it's usually the case that the last placement was actually already done as the first placmeent.
    //   This is not accounted for very well, and can result in bad tile counts even in otherwise good maps.
    // - The above issue primarily happens in 212, because that is the equidistant between first and last seats.
    //   It would be better if the issue was at least distributed randomly.
    // - For 7+ player maps, we're allowing the nucleus to have +/- 1 red-backed tile than it should.
    //   Due to insufficient tile options, this seems necessary.

    /**
     * Generates the 'nucleus' by placing map tiles according to MemePhilosopher's
     * work.
     * Simultaneously produce the draft slices that players will draft.
     * The nucleus is placed directly on the game map, so isn't returned here.
     * TODO: Get flexible on wormhole types, legendaries, etc.
     *
     * @return The slices that were generated for drafting.
     */
    public List<MiltyDraftSlice> generateNucleusAndSlices(GenericInteractionCreateEvent event, DraftSpec specs) {

        // TODO: Implement nucleus settings so we don't just have to flat out ignore the
        // specs...

        Game game = specs.getGame();
        MapTemplateModel mapTemplate = specs.getTemplate();
        if (!mapTemplate.isNucleusTemplate()) {
            String message = "Map template " + mapTemplate.getAlias()
                    + " is not a nucleus template, but nucleus generation was requested.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return null;
        }

        if (mapTemplate.getNucleusSliceCount() == 0) {
            String message = "Map template " + mapTemplate.getAlias()
                    + " has no nucleus slices defined, but nucleus generation was requested.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return null;
        }
        if (mapTemplate.getPlayerCount() == 0) {
            String message = "Map template " + mapTemplate.getAlias()
                    + " has no player count defined, but nucleus generation was requested.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return null;
        }

        // Under certain conditions, we should be flexible about how tiles are selected.
        // Drafts that use many available slices can fail very often if we don't allow some specific rules
        // to be flexible.
        boolean strictMode = useStrictMode(mapTemplate);

        for (int i = 0; i < ATTEMPTS; i++) {
            NucleusOutcome outcome = tryGenerateNucleusAndSlices(game, mapTemplate, specs.getNumSlices(), strictMode);
            if (outcome.slices != null) {
                return outcome.slices;
            }
        }

        return null;
    }

    private boolean useStrictMode(MapTemplateModel mapTemplate) {
        return mapTemplate.getPlayerCount() + mapTemplate.getNucleusSliceCount() < 14;
    }

    public void runAndPrintData(GenericInteractionCreateEvent event, DraftSpec specs, int numIterations) {
        if (numIterations > 10_000) {
            numIterations = 10_000;
        }

        Game game = specs.getGame();
        MapTemplateModel mapTemplate = specs.getTemplate();

        Map<String, Integer> failureReasonCount = new HashMap<>();
        List<Integer> successAfterAttempts = new ArrayList<>();

        DraftTileManager tileManager = game.getDraftTileManager();
        if (tileManager.getAll().isEmpty()) {
            tileManager.clear();
            tileManager.addAllDraftTiles(specs.getTileSources());
        }
        Map<String, Integer> tileOccurrenceInSuccessfulMaps = new HashMap<>();
        Map<String, Integer> tileOccurrenceInSuccessfulSlices = new HashMap<>();

        List<Double> failRuntimes = new ArrayList<>();
        List<Double> successRuntimes = new ArrayList<>();

        boolean strictMode = useStrictMode(mapTemplate);
        for (int i = 0; i < numIterations; ++i) {
            long startTime = System.nanoTime();
            NucleusOutcome outcome = tryGenerateNucleusAndSlices(game, mapTemplate, specs.getNumSlices(), strictMode);
            long endTime = System.nanoTime();
            if (outcome.slices != null) {
                successAfterAttempts.add(i + 1);
                successRuntimes.add((endTime - startTime) / 1_000_000.0);
                for (MiltyDraftSlice slice : outcome.slices) {
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
                        outcome.failureReason, failureReasonCount.getOrDefault(outcome.failureReason, 0) + 1);
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

    public record NucleusOutcome(List<MiltyDraftSlice> slices, String failureReason) {}

    public NucleusOutcome tryGenerateNucleusAndSlices(
            Game game, MapTemplateModel mapTemplate, int numSlices, boolean strictMode) {

        // Reset map and draft tiles
        DraftTileManager tileManager = game.getDraftTileManager();
        if (tileManager.getAll().isEmpty()) {
            return new NucleusOutcome(null, "No draft tiles available to generate nucleus and slices.");
        }

        Integer numPlayerSlices = Math.max(mapTemplate.getPlayerCount(), numSlices);
        Integer numNucleusSlices = mapTemplate.getNucleusSliceCount();
        List<Integer> nucleusWormholeOptions = ListHelper.listOfIntegers(0, numNucleusSlices / 2);
        Integer numNucleusAlphaWormholes = ListHelper.randomPick(nucleusWormholeOptions);
        Integer numNucleusBetaWormholes = ListHelper.randomPick(nucleusWormholeOptions);
        List<Integer> nucleusLegendaryOptions = ListHelper.listOfIntegers(0, numNucleusSlices / 3);
        Integer numNucleusLegendaries = ListHelper.randomPick(nucleusLegendaryOptions);
        List<Integer> mapWormholeOptions = ListHelper.listOfIntegers(2, Math.max(numPlayerSlices / 2, 2));
        Integer numMapAlphaWormholes = ListHelper.randomPick(mapWormholeOptions);
        Integer numMapBetaWormholes = ListHelper.randomPick(mapWormholeOptions);
        List<Integer> mapLegendaryOptions = List.of(1, 2, 2);
        Integer numMapLegendaries = ListHelper.randomPick(mapLegendaryOptions);

        List<MiltyDraftTile> alphaTiles = tileManager.filterAll(tile -> tile.isHasAlphaWH());
        List<MiltyDraftTile> betaTiles = tileManager.filterAll(tile -> tile.isHasBetaWH());
        List<MiltyDraftTile> legendaryTiles = tileManager.filterAll(tile -> tile.isLegendary());
        List<MapTemplateTile> nucleusTiles = new ArrayList<>(mapTemplate.getTemplateTiles().stream()
                .filter(tile -> tile.getNucleusNumbers() != null
                        && !tile.getNucleusNumbers().isEmpty())
                .toList());

        numNucleusAlphaWormholes = Math.min(numNucleusAlphaWormholes, alphaTiles.size());
        numNucleusBetaWormholes = Math.min(numNucleusBetaWormholes, betaTiles.size());
        numNucleusLegendaries = Math.min(numNucleusLegendaries, legendaryTiles.size());
        numMapAlphaWormholes = Math.min(numMapAlphaWormholes, alphaTiles.size());
        numMapBetaWormholes = Math.min(numMapBetaWormholes, betaTiles.size());
        numMapLegendaries = Math.min(numMapLegendaries, legendaryTiles.size());

        Collections.shuffle(alphaTiles);
        Collections.shuffle(betaTiles);
        Collections.shuffle(legendaryTiles);
        Collections.shuffle(nucleusTiles);

        DistanceTool distanceTool = new DistanceTool(game);

        List<PlacedTile> placedAlphaTiles =
                distributeByDistance(nucleusTiles, alphaTiles, numNucleusAlphaWormholes, distanceTool, null);
        List<PlacedTile> placedBetaTiles =
                distributeByDistance(nucleusTiles, betaTiles, numNucleusBetaWormholes, distanceTool, null);
        List<PlacedTile> placedLegendaryTiles =
                distributeByDistance(nucleusTiles, legendaryTiles, numNucleusLegendaries, distanceTool, null);

        Integer remainingAlphaWormholes = Math.max(numMapAlphaWormholes - placedAlphaTiles.size(), 0);
        Integer remainingBetaWormholes = Math.max(numMapBetaWormholes - placedBetaTiles.size(), 0);
        Integer remainingLegendaryWormholes = Math.max(numMapLegendaries - placedLegendaryTiles.size(), 0);

        List<PlacedTile> allPlacedTiles = new ArrayList<>();
        allPlacedTiles.addAll(placedAlphaTiles);
        allPlacedTiles.addAll(placedBetaTiles);
        allPlacedTiles.addAll(placedLegendaryTiles);

        List<MiltyDraftTile> availableTiles = new ArrayList<>(
                tileManager.filterAll(tile -> !allPlacedTiles.stream().anyMatch(pt -> pt.draftTile.equals(tile))));
        Collections.shuffle(availableTiles);
        List<MiltyDraftSlice> playerSlices = generatePlayerSlices(
                tileManager,
                numPlayerSlices,
                mapTemplate.getTilesPerPlayer(),
                availableTiles,
                remainingAlphaWormholes,
                remainingBetaWormholes,
                remainingLegendaryWormholes,
                strictMode);

        if (playerSlices == null) {
            return new NucleusOutcome(null, "Failed to generate player slices; could not place all required tiles.");
        }

        Collections.shuffle(availableTiles);
        Map<TierList, List<MiltyDraftTile>> tieredAvailableTiles =
                tierTiles(tileManager, new ArrayList<>(availableTiles));
        List<List<MapTemplateTile>> coreSliceLocations = new ArrayList<>();
        for (int i = 0; i < numNucleusSlices; i++) {
            coreSliceLocations.add(new ArrayList<>());
            int nucleusNumber = i + 1;
            coreSliceLocations
                    .get(i)
                    .addAll(mapTemplate.getTemplateTiles().stream()
                            .filter(t -> t.getNucleusNumbers() != null
                                    && t.getNucleusNumbers().contains(nucleusNumber))
                            .toList());
        }

        if (!fillNucleus(
                coreSliceLocations, tieredAvailableTiles, allPlacedTiles, tileManager, distanceTool, strictMode)) {
            return new NucleusOutcome(null, "Failed to fill Nucleus; could not find suitable tiles.");
        }

        // Make some attempts to rebalance traits. This will modify parameter objects.
        Predicate<MiltyDraftTile> isNotPlaced = dt -> allPlacedTiles.stream().noneMatch(pt -> pt.draftTile.equals(dt));
        availableTiles =
                new ArrayList<>(availableTiles.stream().filter(isNotPlaced).toList());
        rebalanceTraits(playerSlices, allPlacedTiles, availableTiles);

        // Validate map and nucleus balance
        String failureReason = validateMapAndSlices(allPlacedTiles, playerSlices, mapTemplate, distanceTool);
        if (failureReason != null) {
            return new NucleusOutcome(null, failureReason);
        }

        for (PlacedTile pt : allPlacedTiles) {
            game.setTile(new Tile(pt.draftTile.getTile().getTileID(), pt.mapTile.getPos()));
        }

        return new NucleusOutcome(playerSlices, null);
    }

    private String validateMapAndSlices(
            List<PlacedTile> placedTiles,
            List<MiltyDraftSlice> playerSlices,
            MapTemplateModel mapTemplate,
            DistanceTool distanceTool) {
        List<Integer> slicePlanetCounts = playerSlices.stream()
                .map(slice -> slice.getTiles().stream()
                        .map(t -> t.getTile().getPlanetUnitHolders().size())
                        .reduce(0, Integer::sum))
                .toList();
        Integer minSlicePlanets =
                slicePlanetCounts.stream().min(Integer::compareTo).orElse(0);
        Integer maxSlicePlanets =
                slicePlanetCounts.stream().max(Integer::compareTo).orElse(0);

        List<Integer> sliceSpends = playerSlices.stream()
                .map(slice -> slice.getOptimalRes() + slice.getOptimalInf() + slice.getOptimalFlex())
                .toList();
        Integer minSliceSpend = sliceSpends.stream().min(Integer::compareTo).orElse(0);
        Integer maxSliceSpend = sliceSpends.stream().max(Integer::compareTo).orElse(0);

        Map<Integer, Integer> coreSpends = new HashMap<>();
        for (PlacedTile pt : placedTiles) {
            MapTemplateTile mapTile = pt.mapTile;
            if (mapTile.getNucleusNumbers() == null) continue;

            MiltyDraftTile draftTile = pt.draftTile;
            Integer tileSpend = draftTile.getMiltyRes() + draftTile.getMiltyInf() + draftTile.getMiltyFlex();

            for (Integer nucleusNumber : mapTile.getNucleusNumbers()) {
                coreSpends.put(nucleusNumber, coreSpends.getOrDefault(nucleusNumber, 0) + tileSpend);
            }
        }
        Integer minCoreSpend =
                coreSpends.values().stream().min(Integer::compareTo).orElse(0);
        Integer maxCoreSpend =
                coreSpends.values().stream().max(Integer::compareTo).orElse(0);
        Integer coreSliceBalance = maxCoreSpend - minCoreSpend;

        Integer coreRedTiles = (int) placedTiles.stream()
                .filter(pt ->
                        pt.draftTile.getTierList() == TierList.red || pt.draftTile.getTierList() == TierList.anomaly)
                .count();
        Integer sliceRedTiles = (int) playerSlices.stream()
                .flatMap(slice -> slice.getTiles().stream())
                .filter(t -> t.getTierList() == TierList.red || t.getTierList() == TierList.anomaly)
                .count();
        Integer totalRedTiles = coreRedTiles + sliceRedTiles;
        Integer expectedRedTiles = Math.round(11 * coreSpends.size() / 6.0f);

        boolean anomaliesTouching = false;
        for (int i = 0; i < placedTiles.size(); ++i) {
            PlacedTile tileA = placedTiles.get(i);
            if (tileA.draftTile.getTile().isAnomaly() == false) continue;

            for (int j = i + 1; j < placedTiles.size(); ++j) {
                PlacedTile tileB = placedTiles.get(j);
                if (tileB.draftTile.getTile().isAnomaly() == false) continue;

                if (distanceTool.getNattyDistance(tileA.mapTile.getPos(), tileB.mapTile.getPos()) == 1) {
                    anomaliesTouching = true;
                    break;
                }
            }
            if (anomaliesTouching) break;
        }

        // TODO: Bake these numbers into the settings
        if (minSliceSpend < 4) {
            return "A player slice has less than 4 total optimal spend.";
        }
        if (maxSliceSpend > 9) {
            return "A player slice has more than 9 total optimal spend.";
        }
        if (minSlicePlanets < 2) {
            return "A player slice has less than 2 planets.";
        }
        if (maxSlicePlanets > 5) {
            return "A player slice has more than 5 planets.";
        }
        if (totalRedTiles < expectedRedTiles) {
            return "The map has less than the expected " + expectedRedTiles + " red/anomaly tiles total.";
        }
        if (minCoreSpend < 4) {
            return "A core slice has less than 4 total optimal spend.";
        }
        if (maxCoreSpend > 8) {
            return "A core slice has more than 8 total optimal spend.";
        }
        if (coreSliceBalance > 3) {
            return "The core slices are imbalanced by more than 3 total optimal spend.";
        }
        if (anomaliesTouching) {
            return "Two or more anomalies are touching.";
        }

        // Check for errors just in case
        // - No tile appears more than once
        // - No position has more than one tile placed on it
        // - Every position with a Nucleus number has a tile placed on it
        Set<String> seenTileIds = new HashSet<>();
        Set<String> seenPositions = new HashSet<>();
        for (PlacedTile pt : placedTiles) {
            if (seenTileIds.contains(pt.draftTile.getTile().getTileID())) {
                BotLogger.warning("Tile " + pt.draftTile.getTile().getTileID() + " appears more than once.");
                return "A tile (" + pt.draftTile.getTile().getTileID() + ") appears more than once on the map.";
            }
            seenTileIds.add(pt.draftTile.getTile().getTileID());
            if (seenPositions.contains(pt.mapTile.getPos())) {
                BotLogger.warning("Map position " + pt.mapTile.getPos() + " has more than one tile placed on it.");
                return "A map position (" + pt.mapTile.getPos() + ") has more than one tile placed on it.";
            }
            seenPositions.add(pt.mapTile.getPos());
        }
        for (MiltyDraftSlice slice : playerSlices) {
            for (MiltyDraftTile dt : slice.getTiles()) {
                if (seenTileIds.contains(dt.getTile().getTileID())) {
                    BotLogger.warning("Tile " + dt.getTile().getTileID()
                            + " appears more than once (maybe once in map, once in slice).");
                    return "A tile (" + dt.getTile().getTileID() + ") appears more than once on the map/slices.";
                }
                seenTileIds.add(dt.getTile().getTileID());
            }
        }
        for (MapTemplateTile mapTile : mapTemplate.getTemplateTiles()) {
            if (mapTile.getNucleusNumbers() == null
                    || mapTile.getNucleusNumbers().isEmpty()) continue;
            if (!seenPositions.contains(mapTile.getPos())) {
                BotLogger.warning(
                        "Map position " + mapTile.getPos() + " with a nucleus number has no tile placed on it.");
                return "A map position (" + mapTile.getPos() + ") with a nucleus number has no tile placed on it.";
            }
        }

        return null;
    }

    private void rebalanceTraits(
            List<MiltyDraftSlice> slices, List<PlacedTile> placedTiles, List<MiltyDraftTile> availableTiles) {

        // TODO: Is this number smart?
        for (int i = 0; i < 10; i++) {
            boolean changed = tryRebalanceTraits(slices, placedTiles, availableTiles);
            if (!changed) break;
        }
    }

    private boolean tryRebalanceTraits(
            List<MiltyDraftSlice> slices, List<PlacedTile> placedTiles, List<MiltyDraftTile> availableTiles) {

        PlanetTraits totalTraits = countAllTraits(placedTiles, slices);
        PlanetType maxTrait = totalTraits.max();
        PlanetType minTrait = totalTraits.min();
        int spread = totalTraits.spread();

        // TODO: Should this number be a setting?
        if (spread < 3) return false;

        MiltyDraftTile toRemove = null;
        MiltyDraftTile toAdd = null;
        Integer bestSpread = null;

        // Get all tiles in slices and in nucleus that could be removed to improve the
        // board
        List<MiltyDraftTile> removeCandidates = new ArrayList<>();
        placedTiles.stream()
                .map(pt -> pt.draftTile)
                .filter(dt -> isRemoveCandidate(dt, maxTrait, true))
                .forEach(removeCandidates::add);
        slices.stream()
                .flatMap(s -> s.getTiles().stream())
                .filter(dt -> isRemoveCandidate(dt, maxTrait, false))
                .forEach(removeCandidates::add);

        // Sort by who has the most of the max trait
        Comparator<MiltyDraftTile> descendingTraitComparator = Comparator.comparing(
                        (MiltyDraftTile draftTile) -> countTraits(draftTile).get(maxTrait))
                .reversed();
        removeCandidates.sort(descendingTraitComparator);

        // Check for good adds vs each individual remove candidate
        for (MiltyDraftTile removeCandidate : removeCandidates) {
            Predicate<MiltyDraftTile> planetCountsSimilar = dt -> Math.abs(dt.getTile()
                                    .getPlanetUnitHolders()
                                    .size()
                            - removeCandidate.getTile().getPlanetUnitHolders().size())
                    <= 1;
            List<MiltyDraftTile> addCandidates = availableTiles.stream()
                    .filter(dt -> countTraits(dt).get(minTrait) > 0)
                    .filter(planetCountsSimilar)
                    .toList();

            // For each add candidate, see if it would improve the board
            for (MiltyDraftTile addCandidate : addCandidates) {
                PlanetTraits newStats = countAllTraits(placedTiles, slices);
                newStats = newStats.add(countTraits(addCandidate));
                newStats = newStats.subtract(countTraits(removeCandidate));
                int newSpread = newStats.spread();
                if (bestSpread == null || newSpread < bestSpread) {
                    bestSpread = newSpread;
                    toRemove = removeCandidate;
                    toAdd = addCandidate;
                }
            }
        }

        if (bestSpread == null || toAdd == null || toRemove == null) return false;

        // Search for the remove tile on board and in slices, then swap when found
        for (PlacedTile pt : placedTiles) {
            if (pt.draftTile.equals(toRemove)) {
                placedTiles.remove(pt);
                placedTiles.add(new PlacedTile(pt.mapTile, toAdd));
                return true;
            }
        }
        for (MiltyDraftSlice slice : slices) {
            List<MiltyDraftTile> tiles = new ArrayList<>(slice.getTiles());
            for (int i = 0; i < tiles.size(); i++) {
                if (tiles.get(i).equals(toRemove)) {
                    tiles.set(i, toAdd);
                    slice.setTiles(tiles);
                    return true;
                }
            }
        }

        BotLogger.warning("Trying to rebalance traits, but couldn't find the tile to remove?");

        return false;
    }

    private boolean isRemoveCandidate(MiltyDraftTile draftTile, PlanetType maxTrait, boolean isOnBoard) {
        if (draftTile.getTile().getPlanetUnitHolders().isEmpty()) return false;
        List<Planet> planets = draftTile.getTile().getPlanetUnitHolders();
        boolean hasMax = planets.stream()
                .anyMatch(p -> p.getPlanetModel().getPlanetTypes().contains(maxTrait));
        boolean hasBoardWormholes = isOnBoard && draftTile.hasAnyWormhole();
        return hasMax && !hasBoardWormholes;
    }

    private record PlanetTraits(int cultural, int industrial, int hazardous) {
        public PlanetTraits add(PlanetTraits other) {
            return new PlanetTraits(
                    this.cultural + other.cultural,
                    this.industrial + other.industrial,
                    this.hazardous + other.hazardous);
        }

        public PlanetTraits subtract(PlanetTraits other) {
            return new PlanetTraits(
                    this.cultural - other.cultural,
                    this.industrial - other.industrial,
                    this.hazardous - other.hazardous);
        }

        public int get(PlanetType type) {
            return switch (type) {
                case CULTURAL -> cultural;
                case INDUSTRIAL -> industrial;
                case HAZARDOUS -> hazardous;
                default -> 0;
            };
        }

        public PlanetType max() {
            if (cultural >= industrial && cultural >= hazardous) {
                return PlanetType.CULTURAL;
            } else if (industrial >= hazardous) {
                return PlanetType.INDUSTRIAL;
            } else {
                return PlanetType.HAZARDOUS;
            }
        }

        public PlanetType min() {
            if (cultural <= industrial && cultural <= hazardous) {
                return PlanetType.CULTURAL;
            } else if (industrial <= hazardous) {
                return PlanetType.INDUSTRIAL;
            } else {
                return PlanetType.HAZARDOUS;
            }
        }

        public int spread() {
            return Math.max(cultural, Math.max(industrial, hazardous))
                    - Math.min(cultural, Math.min(industrial, hazardous));
        }
    }

    private PlanetTraits countTraits(MiltyDraftTile tile) {
        int cultural = 0;
        int industrial = 0;
        int hazardous = 0;
        for (Planet planet : tile.getTile().getPlanetUnitHolders()) {
            for (PlanetType planetType : planet.getPlanetModel().getPlanetTypes()) {
                switch (planetType) {
                    case CULTURAL -> cultural++;
                    case INDUSTRIAL -> industrial++;
                    case HAZARDOUS -> hazardous++;
                    default -> {}
                }
            }
        }
        return new PlanetTraits(cultural, industrial, hazardous);
    }

    private PlanetTraits countAllTraits(List<PlacedTile> placedTiles, List<MiltyDraftSlice> slices) {
        PlanetTraits total = new PlanetTraits(0, 0, 0);
        for (PlacedTile pt : placedTiles) {
            PlanetTraits traits = countTraits(pt.draftTile);
            total = total.add(traits);
        }
        for (MiltyDraftSlice slice : slices) {
            for (MiltyDraftTile tile : slice.getTiles()) {
                PlanetTraits traits = countTraits(tile);
                total = total.add(traits);
            }
        }
        return total;
    }

    private boolean fillNucleus(
            List<List<MapTemplateTile>> coreSliceLocations,
            Map<TierList, List<MiltyDraftTile>> tieredAvailableTiles,
            List<PlacedTile> placedTiles,
            DraftTileManager draftTileManager,
            DistanceTool distanceTool,
            boolean strictMode) {

        for (int i = 0; i < coreSliceLocations.size(); i++) {
            List<MapTemplateTile> sliceLocations = coreSliceLocations.get(i);
            // Generate tierlist picks for this nucleus slice
            List<TierList> sliceTiers = getRandomTierPicks(sliceLocations.size());

            if (sliceLocations.size() != sliceTiers.size()) {
                BotLogger.warning("Mismatched slice location and tier list sizes in fillNucleus");
                return false;
            }
            Collections.shuffle(sliceTiers);

            // First, iterate through each location to see what already has something placed
            List<MapTemplateTile> unplacedLocations = new ArrayList<>();
            for (int j = 0; j < sliceLocations.size(); j++) {
                MapTemplateTile location = sliceLocations.get(j);
                MiltyDraftTile placedTile = placedTiles.stream()
                        .filter(pt -> pt.mapTile.equals(location))
                        .map(pt -> pt.draftTile)
                        .findFirst()
                        .orElse(null);

                if (placedTile == null) {
                    unplacedLocations.add(location);
                } else {
                    // Try to remove a sliceTier entry that matches the placed tile, if possible.
                    // If not possible, just try to get as close as we can.
                    TierList placedTier = draftTileManager.getRelativeTier(placedTile);
                    if (!popSimilarTier(sliceTiers, placedTier, strictMode)) {
                        return false;
                    }
                }
            }

            // For each position in the slice
            for (MapTemplateTile location : unplacedLocations) {
                // Get our preferred tier for this tile
                TierList desiredTier = sliceTiers.removeFirst();

                // Get adjacent tiles
                List<PlacedTile> adjacentTiles = placedTiles.stream()
                        .filter(pt -> distanceTool.getNattyDistance(pt.mapTile.getPos(), location.getPos()) == 1)
                        .toList();

                // Check if any adjacent tiles are anomalies
                boolean adjacentAnomalies = adjacentTiles.stream()
                        .anyMatch(pt -> pt.draftTile.getTile().isAnomaly());

                // Predicate to filter based on anomaly adjacency
                Predicate<MiltyDraftTile> anomalyPredicate = tile -> {
                    if (adjacentAnomalies) {
                        return !tile.getTile().isAnomaly();
                    }
                    return true;
                };

                // Get candidate tiles of the desired tier, filtering out anomalies if adjacent
                // to one
                List<MiltyDraftTile> candidateTiles = tieredAvailableTiles.get(desiredTier).stream()
                        .filter(anomalyPredicate)
                        .toList();

                // If empty, try to find the highest tier that has candidates
                if (candidateTiles.isEmpty()) {
                    candidateTiles = tieredAvailableTiles.entrySet().stream()
                            // Sorting an enum in java puts it in the order it was declared
                            // so this will check high -> mid -> low -> red / anomaly
                            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                            .map(e -> e.getValue())
                            .map(tiles ->
                                    tiles.stream().filter(anomalyPredicate).toList())
                            .filter(tiles -> !tiles.isEmpty())
                            .findFirst()
                            .orElse(List.of());
                }

                // If STILL empty, get the desired tier list regardless of anomaly adjacency
                if (candidateTiles.isEmpty()) {
                    candidateTiles = tieredAvailableTiles.get(desiredTier);
                }

                // If STILL STILL empty, we've failed
                if (candidateTiles.isEmpty()) {
                    return false;
                }

                // Sort tiles by how well they'll balance the core slice
                if (candidateTiles.size() > 1) {
                    List<PlacedTile> inSliceTiles = placedTiles.stream()
                            .filter(pt -> sliceLocations.contains(pt.mapTile))
                            .toList();

                    candidateTiles = candidateTiles.stream()
                            .sorted((candidate1, candidate2) -> {
                                MiltyDraftSlice slice1 = new MiltyDraftSlice();
                                slice1.setTiles(new ArrayList<>());
                                slice1.getTiles().add(candidate1);
                                slice1.getTiles()
                                        .addAll(inSliceTiles.stream()
                                                .map(pt -> pt.draftTile)
                                                .toList());
                                MiltyDraftSlice slice2 = new MiltyDraftSlice();
                                slice2.setTiles(new ArrayList<>());
                                slice2.getTiles().add(candidate2);
                                slice2.getTiles()
                                        .addAll(inSliceTiles.stream()
                                                .map(pt -> pt.draftTile)
                                                .toList());
                                int total1 = slice1.getOptimalRes() + slice1.getOptimalInf() + slice1.getOptimalFlex();
                                int total2 = slice2.getOptimalRes() + slice2.getOptimalInf() + slice2.getOptimalFlex();

                                boolean inOptimalRange1 =
                                        total1 >= CORE_SLICE_MIN_OPTIMAL && total1 <= CORE_SLICE_MAX_OPTIMAL;
                                boolean inOptimalRange2 =
                                        total2 >= CORE_SLICE_MIN_OPTIMAL && total2 <= CORE_SLICE_MAX_OPTIMAL;

                                if (inOptimalRange1 && !inOptimalRange2) {
                                    return -1;
                                } else if (!inOptimalRange1 && inOptimalRange2) {
                                    return 1;
                                } else {
                                    int targetOptimal = (CORE_SLICE_MIN_OPTIMAL + CORE_SLICE_MAX_OPTIMAL) / 2;
                                    return Integer.compare(
                                            Math.abs(targetOptimal - total1), Math.abs(targetOptimal - total2));
                                }
                            })
                            .toList();
                }

                if (!candidateTiles.isEmpty()) {
                    MiltyDraftTile chosenTile = candidateTiles.getFirst();
                    placedTiles.add(new PlacedTile(location, chosenTile));
                    for (var tier : tieredAvailableTiles.keySet()) {
                        tieredAvailableTiles.get(tier).remove(chosenTile);
                    }
                }
            }
        }

        return true;
    }

    private List<MiltyDraftSlice> generatePlayerSlices(
            DraftTileManager tileManager,
            int numPlayerSlices,
            int sliceSize,
            List<MiltyDraftTile> availableSystems,
            int numAlphas,
            int numBetas,
            int numLegendaries,
            boolean strictMode) {

        // Generate a list of tiers to pick for each slice.
        // The generation should go [blue, red, blue] then repeat that pattern.
        List<List<TierList>> slicesAsTierLists = new ArrayList<>();
        for (int i = 0; i < numPlayerSlices; i++) {
            List<TierList> sliceTiers = getRandomTierPicks(sliceSize);
            Collections.shuffle(sliceTiers);
            slicesAsTierLists.add(sliceTiers);
        }

        // Get a random selection of tiles to satisfy our required counts
        Collections.shuffle(availableSystems);
        List<MiltyDraftTile> alphaTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isHasAlphaWH(), numAlphas);
        List<MiltyDraftTile> betaTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isHasBetaWH(), numBetas);
        List<MiltyDraftTile> legendaryTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isLegendary(), numLegendaries);

        // Prepare required tiles. Keeping similar tiles co-located is important;
        // it means when we distribute them across slices, the e.g. alphas will be
        // spread out. If 2+ required tiles land on one slice, they shouldn't be
        // from the same category.
        List<MiltyDraftTile> requiredTiles = new ArrayList<>();
        requiredTiles.addAll(alphaTiles);
        requiredTiles.addAll(betaTiles);
        requiredTiles.addAll(legendaryTiles);

        // Prepare filler tiers by organizing them into tiers for easy selection
        Collections.shuffle(availableSystems);
        Map<TierList, List<MiltyDraftTile>> tieredFillerTiles = tierTiles(tileManager, availableSystems);

        // Initialize slices
        List<MiltyDraftSlice> slices = new ArrayList<>();
        for (int i = 0; i < numPlayerSlices; ++i) {
            MiltyDraftSlice slice = new MiltyDraftSlice();
            slice.setTiles(new ArrayList<>());
            slices.add(slice);
        }

        // Breadth-first distribution across slices, so we can pick required tiles until
        // they're empty
        for (int i = 0; i < sliceSize; ++i) {
            for (int sliceIndex = 0; sliceIndex < numPlayerSlices; ++sliceIndex) {

                List<TierList> sliceTiers = slicesAsTierLists.get(sliceIndex);
                MiltyDraftTile tile = null;

                // If we have required tiles, they must be placed first.
                // We always place required tiles asasp because we want to ensure they
                // are distributed well across slices.
                if (!requiredTiles.isEmpty()) {
                    tile = requiredTiles.removeFirst();
                    TierList requiredTier = tileManager.getRelativeTier(tile);
                    // If this required tile can't be slotted into a similar tier, we fail
                    // Player slices should always adhere to the tierlist restriction on red tiles.
                    if (!popSimilarTier(sliceTiers, requiredTier, true)) {
                        return null;
                    }
                } else {
                    TierList tier = sliceTiers.removeFirst();
                    List<MiltyDraftTile> tierFillerTiles = tieredFillerTiles.get(tier);
                    if (tierFillerTiles != null && !tierFillerTiles.isEmpty()) {
                        tile = tierFillerTiles.remove(0);
                    }

                    if (tile == null) {
                        // This indicates we ran out of required tiles, AND ran
                        // out of tiles of our desired tier. For blue tiles, we
                        // can try to pick a similar tier.
                        List<TierList> blueTiers = List.of(TierList.high, TierList.mid, TierList.low);
                        if (blueTiers.contains(tier)) {
                            for (TierList altTier : blueTiers) {
                                if (altTier == tier) continue;
                                List<MiltyDraftTile> altTierFillerTiles = tieredFillerTiles.get(altTier);
                                if (altTierFillerTiles != null && !altTierFillerTiles.isEmpty()) {
                                    tile = altTierFillerTiles.removeFirst();
                                    break;
                                }
                            }
                        }
                    }
                }

                // At this point, not having a tile to place is a fail
                if (tile == null) {
                    return null;
                }

                slices.get(sliceIndex).getTiles().add(tile);
            }
        }

        // Finish removing all of the selected tiles from available ones
        availableSystems.removeIf(
                tile -> slices.stream().anyMatch(slice -> slice.getTiles().contains(tile)));

        // Our success criteria is that we've placed all required tiles at this point.
        if (requiredTiles.isEmpty()) {
            int i = 0;
            Collections.shuffle(slices);
            for (MiltyDraftSlice slice : slices) {
                slice.setName(String.valueOf((char) ('A' + i++)));
                Collections.shuffle(slice.getTiles());
            }

            return slices;
        }

        return null;
    }

    private boolean popSimilarTier(List<TierList> tiers, TierList desiredPopTier, boolean strict) {
        if (tiers.contains(desiredPopTier)) {
            tiers.remove(desiredPopTier);
            // high -> mid
        } else if (desiredPopTier == TierList.high && tiers.contains(TierList.mid)) {
            tiers.remove(TierList.mid);
            // mid -> high
        } else if (desiredPopTier == TierList.mid && tiers.contains(TierList.high)) {
            tiers.remove(TierList.high);
            // mid -> low
        } else if (desiredPopTier == TierList.mid && tiers.contains(TierList.low)) {
            tiers.remove(TierList.low);
            // low -> mid
        } else if (desiredPopTier == TierList.low && tiers.contains(TierList.mid)) {
            tiers.remove(TierList.mid);
            // low -> high
        } else if (desiredPopTier == TierList.low && tiers.contains(TierList.high)) {
            tiers.remove(TierList.high);
            // high -> low
        } else if (desiredPopTier == TierList.high && tiers.contains(TierList.low)) {
            tiers.remove(TierList.low);
        } else {
            // If we reach here, we'd be required to swap a blue for a red, which can
            // mess up tile counts. I keep seeing multiple matching wormholes in player slices,
            // and letting this happen is the likely cause.
            if (strict) {
                return false;
            } else {
                // Just pop the first tier and be done with it
                // Drafts that use many available slices can fail
                // very often if we don't allow some specific rules
                // to be flexible.
                tiers.removeFirst();
            }
        }

        return true;
    }

    private Map<TierList, List<MiltyDraftTile>> tierTiles(DraftTileManager tileManager, List<MiltyDraftTile> tiles) {
        Map<TierList, List<MiltyDraftTile>> result = tileManager.getTilesByTier(tiles);

        // For our purposes, combine red and anomaly tiers
        result.get(TierList.red).addAll(result.get(TierList.anomaly));
        result.remove(TierList.anomaly);

        // Other tiers go in shuffled, then come out shuffled. But since
        // we append red and anomaly tiers together, we need to shuffle them now.
        List<MiltyDraftTile> redTiles = new ArrayList<>(result.get(TierList.red));
        Collections.shuffle(redTiles);
        result.put(TierList.red, redTiles);

        return result;
    }

    /**
     * Get a random selection of tile tiers, balancing blue and red tiles.
     * The ratio of blue to red is roughly 2:1,
     * but leans red when a clean distribution isn't available.
     *
     * @param count
     * @return
     */
    private List<TierList> getRandomTierPicks(int count) {
        List<TierList> blueTiers = List.of(TierList.high, TierList.mid, TierList.low);
        List<TierList> tierPicks = new ArrayList<>();
        while (tierPicks.size() < count) {
            tierPicks.add(ListHelper.randomPick(blueTiers));
            if (tierPicks.size() < count) tierPicks.add(TierList.red);
            if (tierPicks.size() < count) tierPicks.add(ListHelper.randomPick(blueTiers));
        }
        return tierPicks;
    }

    private record PlacedTile(MapTemplateTile mapTile, MiltyDraftTile draftTile) {}

    /**
     * Distribute tiles to locations, trying to maximize the distance between all of
     * them.
     * Simply places them randomly amongst the available locations, then checks if
     * this placement
     * is better than the current best. Runs a fixed number of iterations...choose
     * iterations
     * to feel random but still often do a good job at spreading things out.
     *
     * @param availableLocations Candidate locations; will have chosen locations
     *                           removed.
     * @param availableSystems   Candidate systems; will have chosen systems
     *                           removed.
     * @param numPicks           Number of systems to place in available locations
     * @param distanceTool       Tool to measure distance between positions
     * @param iterations         Number of random placements to try; defaults to 20
     *                           if null.
     * @return
     */
    private List<PlacedTile> distributeByDistance(
            List<MapTemplateTile> availableLocations,
            List<MiltyDraftTile> availableSystems,
            int numPicks,
            DistanceTool distanceTool,
            Integer iterations) {
        if (numPicks == 0) return List.of();
        if (iterations == null) iterations = 20;

        if (numPicks > availableLocations.size()) {
            throw new IllegalStateException(
                    "Trying to build a Nucleus without enough locations; setup should prevent this!");
        }
        if (numPicks > availableSystems.size()) {
            throw new IllegalStateException(
                    "Trying to build a Nucleus without enough systems; setup should prevent this!");
        }

        List<PlacedTile> chosen = new ArrayList<>();
        Integer bestDistance = null;

        for (int iteration = 0; iteration < iterations; iteration++) {
            List<PlacedTile> candidate = new ArrayList<>();
            List<MapTemplateTile> locations = new LinkedList<>(availableLocations);
            List<MiltyDraftTile> systems = new LinkedList<>(availableSystems);
            Collections.shuffle(locations);
            Collections.shuffle(systems);

            for (int i = 0; i < numPicks; i++) {
                MapTemplateTile location = locations.removeLast();
                candidate.add(new PlacedTile(location, systems.removeLast()));
            }

            Integer minDistance = null;
            for (int i = 0; i < candidate.size(); i++) {
                PlacedTile pt1 = candidate.get(i);
                for (int j = i + 1; j < candidate.size(); j++) {
                    PlacedTile pt2 = candidate.get(j);
                    Integer distance = distanceTool.getNattyDistance(pt1.mapTile.getPos(), pt2.mapTile.getPos());
                    if (distance == null) continue; // sanity check
                    if (minDistance == null || distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }
            if (bestDistance == null || (minDistance != null && minDistance > bestDistance)) {
                bestDistance = minDistance;
                chosen = candidate;
            }
        }

        for (PlacedTile pt : chosen) {
            availableLocations.remove(pt.mapTile);
            availableSystems.remove(pt.draftTile);
        }

        return chosen;
    }
}
