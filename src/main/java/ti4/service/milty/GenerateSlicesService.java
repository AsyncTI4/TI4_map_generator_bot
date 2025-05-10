package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.ListUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.message.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.service.milty.MiltyService.DraftSpec;
import ti4.settings.GlobalSettings;

public class GenerateSlicesService {

    public static boolean generateSlices(GenericInteractionCreateEvent event, MiltyDraftManager draftManager, DraftSpec specs) {
        int sliceCount = specs.numSlices;
        boolean anomaliesCanTouch = specs.anomaliesCanTouch;

        MapTemplateModel mapTemplate = specs.template;
        int bluePerPlayer = mapTemplate.bluePerPlayer();
        int redPerPlayer = mapTemplate.redPerPlayer();

        List<List<Boolean>> adjMatrix = new ArrayList<>();
        List<String> tilePositions = mapTemplate.emulatedTiles();
        for (String pos1 : tilePositions) {
            List<Boolean> row = new ArrayList<>();
            List<String> adj = PositionMapper.getAdjacentTilePositions(pos1);
            for (String pos2 : tilePositions)
                row.add(adj.contains(pos2));
            adjMatrix.add(row);
        }

        boolean slicesCreated = false;
        int i = 0;
        Map<String, Integer> reasons = new HashMap<>();
        Function<String, Integer> addReason = reason -> reasons.put(reason, reasons.getOrDefault(reason, 0) + 1);

        List<MiltyDraftTile> allTiles = draftManager.getBlue();
        allTiles.addAll(draftManager.getRed());
        int totalWHs = allTiles.stream().filter(tile -> tile.isHasAlphaWH() || tile.isHasBetaWH() || tile.isHasOtherWH()).toList().size();
        int extraWHs = Math.min(totalWHs - 1, (int) (sliceCount * 1.5));
        if (specs.playerIDs.size() == 1) extraWHs = 0; //disable the behavior if there's only 1 player
        if (specs.playerIDs.size() == 2) extraWHs = 3; //lessen the behavior if there's 2 players
        if (!specs.extraWHs) extraWHs = 0;

        List<List<MiltyDraftTile>> partitionedTiles = new ArrayList<>();

        // Partition blue tiles to split them up into "tiers" so that slices get 1 good tile, 1 medium tile, and 1 meh tile
        List<MiltyDraftTile> blue = draftManager.getBlue();
        blue.sort(Comparator.comparingDouble(MiltyDraftTile::abstractValue));
        int bluePerPartition = Math.ceilDiv(blue.size(), bluePerPlayer);
        partitionedTiles.addAll(ListUtils.partition(blue, bluePerPartition));

        // Partition RED tiles into "tiers" so that slices don't get dumb stuff like 2 supernovae, 2 rifts, etc
        List<MiltyDraftTile> red = draftManager.getRed();
        red.sort(Comparator.comparingDouble(MiltyDraftTile::abstractValue));
        int redPerPartition = Math.ceilDiv(red.size(), redPerPlayer);
        partitionedTiles.addAll(ListUtils.partition(red, redPerPartition));

        long quitDiff = 60L * 1000L * 1000L * 1000L;
        long minAttempts = 1000000L;
        long startTime = System.nanoTime();
        int possibleSlices = partitionedTiles.stream().map(List::size).min(Integer::compare).orElse(sliceCount);
        while (!slicesCreated) {
            long elapTime = System.nanoTime() - startTime;
            if (i % 1000 == 0) {
                // check if the bot is shutting down
                if (!AsyncTI4DiscordBot.isReadyToReceiveCommands())
                    break;
            }
            if (elapTime > quitDiff && i > minAttempts) {
                break;
            }

            // Reset the draft, shuffle the tiers
            draftManager.clearSlices();
            for (List<MiltyDraftTile> tier : partitionedTiles)
                Collections.shuffle(tier);

            String nextSliceName = "A";
            for (int sliceIndex = 0; sliceIndex < possibleSlices; sliceIndex++) {
                MiltyDraftSlice slice = assembleOneSlice(adjMatrix, partitionedTiles, sliceIndex, nextSliceName, anomaliesCanTouch);
                if (!checkIfSliceIsGood(specs, slice, reasons)) {
                    if (draftManager.getSlices().size() == sliceCount)
                        break;
                    if ((draftManager.getSlices().size() + possibleSlices - sliceIndex) <= sliceCount)
                        break;
                    continue;
                }
                draftManager.addSlice(slice);
                nextSliceName = Character.toString('A' + draftManager.getSlices().size());
            }

            if (draftManager.getSlices().size() == sliceCount) {
                long legends = draftManager.getSlices().stream().flatMap(s -> s.getTiles().stream())
                    .filter(MiltyDraftTile::isLegendary).count();
                long whs = draftManager.getSlices().stream().flatMap(s -> s.getTiles().stream())
                    .filter(MiltyDraftTile::hasAnyWormhole).count();
                if (legends > specs.maxLegend || legends < specs.minLegend) {
                    addReason.apply("legendTot");
                } else if (whs < extraWHs) {
                    addReason.apply("extrawh");
                } else {
                    slicesCreated = true;
                }
            }
            i++;
        }
        if (!slicesCreated) {
            draftManager.clear();
        }

        long elapsed = System.nanoTime() - startTime;
        boolean debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
        if (!slicesCreated || elapsed >= 10000000000L || debug) {
            StringBuilder sb = new StringBuilder();
            sb.append("Milty draft took a while... jazz, take a look:\n");
            sb.append("`        Elapsed time:` ").append(DateTimeHelper.getTimeRepresentationNanoSeconds(elapsed)).append("\n");
            sb.append("`           Quit time:` ").append(DateTimeHelper.getTimeRepresentationNanoSeconds(quitDiff)).append("\n");
            sb.append("`    Number of cycles:` ").append(i).append("\n");
            for (Map.Entry<String, Integer> reason : reasons.entrySet()) {
                sb.append("`").append(Helper.leftpad(reason.getKey(), 15)).append(" fail:` ").append(reason.getValue()).append("\n");
            }
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), sb.toString());
        }
        return slicesCreated;
    }

    private static MiltyDraftSlice assembleOneSlice(List<List<Boolean>> adjMatrix, List<List<MiltyDraftTile>> partition, int sliceNum, String sliceName, boolean anomaliesCanTouch) {

        List<MiltyDraftTile> tiles = new ArrayList<>();
        for (List<MiltyDraftTile> tier : partition)
            tiles.add(tier.get(sliceNum));
        Collections.shuffle(tiles);

        List<Integer> ints = new ArrayList<>();
        for (int k = 0; k < tiles.size(); k++)
            if (tiles.get(k).getTierList() == TierList.anomaly)
                ints.add(k + 1);
        if (!anomaliesCanTouch && ints.size() == 2) { // just skip this if there's more than 2 anomalies tbh
            int turns = -4;
            boolean tryagain = true;
            while (tryagain && turns < tiles.size()) {
                tryagain = false;
                for (int x : ints)
                    for (int y : ints)
                        if (x != y && adjMatrix.get(x).get(y)) {
                            tryagain = true;
                            break;
                        }
                if (tryagain) {
                    Collections.rotate(tiles, 1);
                    if (turns == 0) Collections.shuffle(tiles);
                    ints.clear();
                    for (int k = 0; k < tiles.size(); k++)
                        if (tiles.get(k).getTierList() == TierList.anomaly)
                            ints.add(k + 1);
                }
                turns++;
            }
        }
        MiltyDraftSlice slice = new MiltyDraftSlice();
        slice.setName(sliceName);
        slice.setTiles(tiles);
        return slice;
    }

    private static boolean checkIfSliceIsGood(DraftSpec spec, MiltyDraftSlice slice, Map<String, Integer> failReasons) {
        Function<String, Integer> addReason = reason -> failReasons.put(reason, failReasons.getOrDefault(reason, 0) + 1);

        int optInf = slice.getOptimalInf();
        int optRes = slice.getOptimalRes();
        int totalOptimal = slice.getOptimalTotalValue();
        if (optInf < spec.getMinInf() || optRes < spec.getMinRes() || totalOptimal < spec.getMinTot() || totalOptimal > spec.getMaxTot()) {
            addReason.apply("value");
            System.out.println(slice.ttsString() + " = " + optInf + "/" + optRes + " (" + totalOptimal + ")");
            return false;
        }

        // if the slice has 2 alphas, or 2 betas, throw it out
        if (slice.getTiles().stream().filter(MiltyDraftTile::isHasAlphaWH).count() > 1) {
            addReason.apply("alpha");
            return false;
        }
        if (slice.getTiles().stream().filter(MiltyDraftTile::isHasBetaWH).count() > 1) {
            addReason.apply("beta");
            return false;
        }
        // if the spec says to load it up, don't fail here lol
        if (slice.getTiles().stream().filter(MiltyDraftTile::isLegendary).count() > 1 && spec.getMaxLegend() < spec.getNumSlices()) {
            addReason.apply("legend");
            return false;
        }

        return true;
    }
}