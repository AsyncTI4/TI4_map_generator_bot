package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.DistanceTool;
import ti4.helpers.ListHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.milty.TierList;

// "borrowed" from https://github.com/heisenbugged/ti4-lab/blob/main/app/draft/heisen/generateMap.ts

@UtilityClass
public class NucleusSliceGeneratorService {

    // TODO: These should be in a Settings object; they should adjust based on core slice at least.
    private static final int CORE_SLICE_MIN_OPTIMAL = 4;
    private static final int CORE_SLICE_MAX_OPTIMAL = 8;

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

        for (int i = 0; i < 10_000; i++) {
            List<MiltyDraftSlice> result = tryGenerateNucleusAndSlices(game, mapTemplate, specs.getNumSlices());
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private List<MiltyDraftSlice> tryGenerateNucleusAndSlices(Game game, MapTemplateModel mapTemplate, int numSlices) {

        // Reset map and draft tiles
        game.clearTileMap();
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.reset(game);

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
        List<MapTemplateTile> nucleusTiles = mapTemplate.getTemplateTiles().stream()
                .filter(tile -> tile.getNucleusNumbers() != null
                        && !tile.getNucleusNumbers().isEmpty())
                .toList();

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

        List<MiltyDraftTile> availableTiles =
                tileManager.filterAll(tile -> !placedAlphaTiles.stream().anyMatch(pt -> pt.draftTile.equals(tile))
                        && !placedBetaTiles.stream().anyMatch(pt -> pt.draftTile.equals(tile))
                        && !placedLegendaryTiles.stream().anyMatch(pt -> pt.draftTile.equals(tile)));
        Collections.shuffle(availableTiles);
        List<MiltyDraftSlice> playerSlices = generatePlayerSlices(
                numPlayerSlices,
                mapTemplate.getTilesPerPlayer(),
                availableTiles,
                remainingAlphaWormholes,
                remainingBetaWormholes,
                remainingLegendaryWormholes);

        if (playerSlices == null) {
            return null;
        }

        Collections.shuffle(availableTiles);
        Map<TierList, List<MiltyDraftTile>> tieredAvailableTiles = tierTiles(new ArrayList<>(availableTiles));
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
        List<PlacedTile> allPlacedTiles = new ArrayList<>();
        allPlacedTiles.addAll(placedAlphaTiles);
        allPlacedTiles.addAll(placedBetaTiles);
        allPlacedTiles.addAll(placedLegendaryTiles);

        if (!fillNucleus(coreSliceLocations, tieredAvailableTiles, allPlacedTiles, distanceTool)) {
            return null;
        }

        // TODO: Rebalance traits
        // TODO: Validate map and nucleus balance

        return playerSlices;
    }

    private boolean fillNucleus(
            List<List<MapTemplateTile>> coreSliceLocations,
            Map<TierList, List<MiltyDraftTile>> tieredAvailableTiles,
            List<PlacedTile> placedTiles,
            DistanceTool distanceTool) {
        List<List<TierList>> coreSliceTierLists = new ArrayList<>();
        for (int i = 0; i < coreSliceLocations.size(); i++) {
            List<TierList> sliceTiers =
                    getRandomTierPicks(coreSliceLocations.get(i).size());
            coreSliceTierLists.add(sliceTiers);
        }

        for (int i = 0; i < coreSliceLocations.size(); i++) {
            List<MapTemplateTile> sliceLocations = coreSliceLocations.get(i);
            List<TierList> sliceTiers = coreSliceTierLists.get(i);

            // For each position in the slice
            for (int j = 0; j < sliceLocations.size(); j++) {
                MapTemplateTile location = sliceLocations.get(j);

                // Skip if already occupied
                if (placedTiles.stream().anyMatch(pt -> pt.mapTile.equals(location))) {
                    continue;
                }

                TierList desiredTier = sliceTiers.get(j);

                // Get adjacent tiles
                List<PlacedTile> adjacentTiles = placedTiles.stream()
                        .filter(pt -> distanceTool.getNattyDistance(pt.mapTile.getPos(), location.getPos()) == 1)
                        .toList();

                // Check if any adjacent tiles are anomalies
                boolean adjacentAnomalies =
                        adjacentTiles.stream().anyMatch(pt -> pt.draftTile.getTierList() == TierList.anomaly);

                // Predicate to filter based on anomaly adjacency
                Predicate<MiltyDraftTile> anomalyPredicate = tile -> {
                    if (adjacentAnomalies) {
                        return tile.getTierList() != TierList.anomaly;
                    }
                    return true;
                };

                // Get candidate tiles of the desired tier, filtering out anomalies if adjacent to one
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
                                slice1.getTiles().add(candidate1);
                                slice1.getTiles()
                                        .addAll(inSliceTiles.stream()
                                                .map(pt -> pt.draftTile)
                                                .toList());
                                MiltyDraftSlice slice2 = new MiltyDraftSlice();
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
                    tieredAvailableTiles.get(chosenTile.getTierList()).remove(chosenTile);
                }
            }
        }

        return true;
    }

    private List<MiltyDraftSlice> generatePlayerSlices(
            int numPlayerSlices,
            int sliceSize,
            List<MiltyDraftTile> availableSystems,
            int numAlphas,
            int numBetas,
            int numLegendaries) {
        List<List<TierList>> slicesAsTierLists = new ArrayList<>();
        for (int i = 0; i < numPlayerSlices; i++) {
            List<TierList> sliceTiers = getRandomTierPicks(sliceSize);
            slicesAsTierLists.add(sliceTiers);
        }

        Collections.shuffle(availableSystems);
        List<MiltyDraftTile> alphaTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isHasAlphaWH(), numAlphas);
        List<MiltyDraftTile> betaTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isHasBetaWH(), numBetas);
        List<MiltyDraftTile> legendaryTiles =
                ListHelper.removeByPredicate(availableSystems, tile -> tile.isLegendary(), numLegendaries);

        List<MiltyDraftTile> requiredTiles = new ArrayList<>();
        requiredTiles.addAll(alphaTiles);
        requiredTiles.addAll(betaTiles);
        requiredTiles.addAll(legendaryTiles);

        Map<TierList, List<MiltyDraftTile>> tieredRequiredTiles = tierTiles(requiredTiles);
        Collections.shuffle(availableSystems);
        Map<TierList, List<MiltyDraftTile>> tieredFillerTiles = tierTiles(availableSystems);

        List<MiltyDraftSlice> slices = new ArrayList<>();
        for (int i = 0; i < numPlayerSlices; ++i) {
            MiltyDraftSlice slice = new MiltyDraftSlice();
            slice.setName(i + 'A' + "");
            slices.add(slice);
        }

        // Breadth-first distribution across slices, so we can pick required tiles until
        // they're empty
        for (int i = 0; i < sliceSize; ++i) {
            for (int sliceIndex = 0; sliceIndex < numPlayerSlices; ++sliceIndex) {
                TierList tier = slicesAsTierLists.get(sliceIndex).get(i);
                MiltyDraftTile tile = null;
                List<MiltyDraftTile> tierRequiredTiles = tieredRequiredTiles.get(tier);
                if (tierRequiredTiles != null && !tierRequiredTiles.isEmpty()) {
                    tile = tierRequiredTiles.remove(0);
                    if (tierRequiredTiles.isEmpty()) {
                        tieredRequiredTiles.remove(tier);
                    }
                } else {
                    List<MiltyDraftTile> tierFillerTiles = tieredFillerTiles.get(tier);
                    if (tierFillerTiles != null && !tierFillerTiles.isEmpty()) {
                        tile = tierFillerTiles.remove(0);
                    }
                }
                if (tile != null) {
                    slices.get(sliceIndex).getTiles().add(tile);
                } else {
                    BotLogger.warning("Couldn't find a tile for slice " + (sliceIndex + 'A') + " of tier " + tier);
                }
            }
        }

        availableSystems.removeIf(
                tile -> slices.stream().anyMatch(slice -> slice.getTiles().contains(tile)));

        if (tieredRequiredTiles.isEmpty()) {
            for (MiltyDraftSlice slice : slices) {
                Collections.shuffle(slice.getTiles());
            }
            Collections.shuffle(slices);

            return slices;
        }

        // TODO: Can we fix things up? Slice tiers are somewhat random. We could just
        // replace optional blues with required ones...

        return null;
    }

    private Map<TierList, List<MiltyDraftTile>> tierTiles(List<MiltyDraftTile> tiles) {
        List<MiltyDraftTile> tileList = new ArrayList<>(tiles);

        return Map.of(
                TierList.high,
                ListHelper.removeByPredicate(tileList, tile -> tile.getTierList() == TierList.high, Integer.MAX_VALUE),
                TierList.mid,
                ListHelper.removeByPredicate(tileList, tile -> tile.getTierList() == TierList.mid, Integer.MAX_VALUE),
                TierList.low,
                ListHelper.removeByPredicate(tileList, tile -> tile.getTierList() == TierList.low, Integer.MAX_VALUE),
                TierList.red,
                ListHelper.removeByPredicate(
                        tileList,
                        tile -> tile.getTierList() == TierList.red || tile.getTierList() == TierList.anomaly,
                        Integer.MAX_VALUE));
    }

    /**
     * Get a random selection of tile tiers, balancing blue and red tiles.
     * The ratio of blue to red is roughly 2:1,
     * but leans red when a clean distribution isn't available.
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
            List<MapTemplateTile> locations = new ArrayList<>(availableLocations);
            List<MiltyDraftTile> systems = new ArrayList<>(availableSystems);
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
