package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class MiltyRandomSetupService {
    private static final int AGE_OF_EXPLORATION_RED_ROLL_MAX = 4;
    private static final int TRUE_RANDOM_GALAXY_MAX_ATTEMPTS = 200;

    private record RandomGalacticEvent(String alias, String name, Consumer<Game> enable) {}

    private static final List<RandomGalacticEvent> RANDOM_GALACTIC_EVENTS = List.of(
            new RandomGalacticEvent("age_commerce", "Age of Commerce", game -> game.setAgeOfCommerceMode(true)),
            new RandomGalacticEvent("call_of_the_void", "Call of the Void", game -> game.setCallOfTheVoidMode(true)),
            new RandomGalacticEvent("hidden_agenda", "Hidden Agenda", game -> game.setHiddenAgendaMode(true)),
            new RandomGalacticEvent(
                    "age_exploration", "Age of Exploration", game -> game.setAgeOfExplorationMode(true)),
            new RandomGalacticEvent(
                    "civilized_society", "Civilized Society", game -> game.setCivilizedSocietyMode(true)),
            new RandomGalacticEvent("total_war", "Total War", game -> game.setTotalWarMode(true)),
            new RandomGalacticEvent("wild_wild_galaxy", "Wild, Wild Galaxy", game -> game.setWildWildGalaxyMode(true)),
            new RandomGalacticEvent("weird_wormholes", "Weird Wormholes", game -> game.setWeirdWormholesMode(true)),
            new RandomGalacticEvent(
                    "cosmic_phenomenae", "Cosmic Phenomenae", game -> game.setCosmicPhenomenaeMode(true)),
            new RandomGalacticEvent(
                    "zealous_orthodoxy", "Zealous Orthodoxy", game -> game.setZealousOrthodoxyMode(true)),
            new RandomGalacticEvent(
                    "advent_war_sun", "Advent of the War Sun", game -> game.setAdventOfTheWarsunMode(true)),
            new RandomGalacticEvent(
                    "cowabunga", "Conventions of War Abandoned", game -> game.setConventionsOfWarAbandonedMode(true)));

    public static String randomSetupFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        MiltyDraftSpec specs = MiltyDraftSpec.fromSettings(settings);
        int playerCount = specs.playerIDs.size();
        specs.setNumSlices(playerCount);
        specs.setNumFactions(playerCount);
        specs.setPlayerDraftOrder(null);

        return randomSetupFromSpecs(
                event,
                specs,
                settings.getRandomGalacticEvents().getVal(),
                settings.getTrueRandomGalaxy().isVal());
    }

    public static String randomSetupFromSpecs(GenericInteractionCreateEvent event, MiltyDraftSpec specs) {
        return randomSetupFromSpecs(event, specs, 0, false);
    }

    private static String randomSetupFromSpecs(
            GenericInteractionCreateEvent event, MiltyDraftSpec specs, int randomEventCount, boolean trueRandomGalaxy) {
        Game game = specs.game;

        if (specs.presetSlices != null && specs.presetSlices.size() < specs.playerIDs.size()) {
            return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
        }

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        List<ComponentSource> sources = new ArrayList<>(specs.tileSources);
        if (game.isDiscordantStarsMode()) {
            sources.add(ComponentSource.ds);
        }
        if (game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.uncharted_space);
        }
        if ((!game.isBaseGameMode() && game.getStoredValue("useOldPok").isEmpty()) || game.isTwilightsFallMode()) {
            sources.add(ComponentSource.thunders_edge);
        }

        draftManager.init(sources);
        draftManager.setMapTemplate(specs.template.getAlias());
        game.setMapTemplateID(specs.template.getAlias());
        MiltyService.initDraftOrder(draftManager, specs.playerIDs, false);

        List<String> unbannedFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> specs.factionSources.contains(f.getSource()))
                .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("neutral"))
                .filter(f -> !f.getAlias().contains("keleres") || "keleresm".equals(f.getAlias()))
                .map(FactionModel::getAlias)
                .toList());
        String sourceConstraintError = validateFactionSourceConstraints(event, specs, unbannedFactions);
        if (sourceConstraintError != null) {
            return sourceConstraintError;
        }
        List<String> factionDraft = MiltyService.createFactionDraft(
                specs.numFactions, unbannedFactions, specs.priorityFactions, specs.sourceConstraints);
        if (factionDraft.size() < specs.playerIDs.size()) {
            return "Not enough factions are available for the number of players. "
                    + "Please reduce faction bans or enable more faction sources.";
        }
        draftManager.setFactionDraft(factionDraft);

        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        if (trueRandomGalaxy) {
            int neededTiles = specs.numSlices * (specs.template.bluePerPlayer() + specs.template.redPerPlayer());
            if (redTiles + blueTiles < neededTiles) {
                String msg = "True random galaxy setup needs **" + neededTiles + "** tiles, but only **"
                        + (redTiles + blueTiles) + "** are available with the selected sources.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                return "Could not start random setup, fix the error and try again";
            }
        } else {
            int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
            if (specs.numSlices > maxSlices) {
                String msg = "Random setup in this bot does not support " + specs.numSlices
                        + " slices. You can enable DS to allow building additional slices";
                msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles
                        + "blue/" + redTiles + "red]";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                return "Could not start random setup, fix the error and try again";
            }
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "## Generating random setup\n - Also clearing out any tiles that may have already been on the map "
                        + "so setup can fill in tiles properly."
                        + (trueRandomGalaxy
                                ? "\n - True Random Galaxy is enabled, so tile slots will roll red/blue independently."
                                : ""));
        game.clearTileMap();
        try {
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            // Ignore
        }
        for (String player : draftManager.getPlayers()) {
            Player p = game.getPlayer(player);
            p.getCardsInfoThread();
        }

        boolean slicesCreated = true;
        if (trueRandomGalaxy) {
            slicesCreated = generateTrueRandomGalaxySlices(draftManager, specs);
        } else if (specs.presetSlices != null) {
            specs.presetSlices.forEach(draftManager::addSlice);
        } else {
            slicesCreated = GenerateSlicesService.generateSlices(event, draftManager, specs);
        }
        if (!slicesCreated) {
            return "Generating slices was too hard so I gave up.... Please try again.";
        }

        assignRandomPicks(draftManager, specs);
        presetRandomKeleresFlavorIfNeeded(draftManager, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                game.getPing() + " random setup generated. Applying factions, speaker order, and map now.");
        FinishDraftService.finishDraft(event, draftManager, game);
        applyRandomGalacticEvents(event, game, randomEventCount);
        game.updateActivePlayer(null);
        return null;
    }

    private static boolean generateTrueRandomGalaxySlices(MiltyDraftManager draftManager, MiltyDraftSpec specs) {
        for (int attempt = 0; attempt < TRUE_RANDOM_GALAXY_MAX_ATTEMPTS; attempt++) {
            List<MiltyDraftTile> blue = new ArrayList<>(draftManager.getBlue());
            List<MiltyDraftTile> red = new ArrayList<>(draftManager.getRed());
            Collections.shuffle(blue);
            Collections.shuffle(red);
            draftManager.clearSlices();

            boolean success = true;
            for (int sliceIndex = 0; sliceIndex < specs.numSlices; sliceIndex++) {
                MiltyDraftSlice slice = new MiltyDraftSlice();
                slice.setName(Character.toString('A' + sliceIndex));
                List<MiltyDraftTile> tiles = new ArrayList<>();
                int tilesInSlice = specs.template.bluePerPlayer() + specs.template.redPerPlayer();
                for (int tileIndex = 0; tileIndex < tilesInSlice; tileIndex++) {
                    MiltyDraftTile tile = drawAgeOfExplorationTile(blue, red);
                    if (tile == null) {
                        success = false;
                        break;
                    }
                    tiles.add(tile);
                }
                if (!success) {
                    break;
                }
                slice.setTiles(tiles);
                if (!trueRandomSliceIsValid(specs, slice)) {
                    success = false;
                    break;
                }
                draftManager.addSlice(slice);
            }
            if (success && draftManager.getSlices().size() == specs.numSlices) {
                return true;
            }
        }
        draftManager.clearSlices();
        return false;
    }

    private static MiltyDraftTile drawAgeOfExplorationTile(List<MiltyDraftTile> blue, List<MiltyDraftTile> red) {
        boolean rollRed = ThreadLocalRandom.current().nextInt(1, 11) <= AGE_OF_EXPLORATION_RED_ROLL_MAX;
        List<MiltyDraftTile> preferred = rollRed ? red : blue;
        List<MiltyDraftTile> fallback = rollRed ? blue : red;
        if (!preferred.isEmpty()) {
            return preferred.removeLast();
        }
        if (!fallback.isEmpty()) {
            return fallback.removeLast();
        }
        return null;
    }

    private static boolean trueRandomSliceIsValid(MiltyDraftSpec specs, MiltyDraftSlice slice) {
        if (specs.anomaliesCanTouch) {
            return true;
        }

        List<List<Boolean>> adjMatrix = getAdjMatrix(specs.template);
        List<Integer> anomalyIndexes = new ArrayList<>();
        for (int i = 0; i < slice.getTiles().size(); i++) {
            if (slice.getTiles().get(i).getTierList() == TierList.anomaly) {
                anomalyIndexes.add(i + 1);
            }
        }
        for (int x : anomalyIndexes) {
            for (int y : anomalyIndexes) {
                if (x != y
                        && x < adjMatrix.size()
                        && y < adjMatrix.get(x).size()
                        && adjMatrix.get(x).get(y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<List<Boolean>> getAdjMatrix(MapTemplateModel template) {
        List<List<Boolean>> adjMatrix = new ArrayList<>();
        List<String> tilePositions = template.emulatedTiles();
        for (String pos1 : tilePositions) {
            List<Boolean> row = new ArrayList<>();
            List<String> adj = PositionMapper.getAdjacentTilePositions(pos1);
            for (String pos2 : tilePositions) {
                row.add(adj.contains(pos2));
            }
            adjMatrix.add(row);
        }
        return adjMatrix;
    }

    private static void applyRandomGalacticEvents(
            GenericInteractionCreateEvent event, Game game, int randomEventCount) {
        if (randomEventCount <= 0) {
            return;
        }

        List<RandomGalacticEvent> events = new ArrayList<>(RANDOM_GALACTIC_EVENTS);
        Collections.shuffle(events);
        List<RandomGalacticEvent> selected =
                events.stream().limit(Math.min(randomEventCount, events.size())).toList();
        for (RandomGalacticEvent selectedEvent : selected) {
            selectedEvent.enable().accept(game);
        }

        String appliedEvents = selected.stream().map(RandomGalacticEvent::name).collect(Collectors.joining(", "));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "## Random Galactic Events Applied\n" + appliedEvents
                        + "\n-# Events that require substantial manual setup are excluded from this random pool.");
    }

    private static void assignRandomPicks(MiltyDraftManager draftManager, MiltyDraftSpec specs) {
        int playerCount = specs.playerIDs.size();
        List<String> players = new ArrayList<>(draftManager.getPlayers());
        List<MiltyDraftSlice> slices = new ArrayList<>(draftManager.getSlices());
        List<String> factions = new ArrayList<>(draftManager.getFactionDraft());
        List<Integer> positions = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            positions.add(i);
        }

        List<MiltyDraftSlice> bestSlices = new ArrayList<>();
        List<String> bestFactions = new ArrayList<>();
        List<Integer> bestPositions = new ArrayList<>();
        int fewestCatastrophes = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < 200; attempt++) {
            Collections.shuffle(slices);
            Collections.shuffle(factions);
            Collections.shuffle(positions);

            int catastrophes = countCatastrophes(specs, slices, factions, positions);
            if (catastrophes < fewestCatastrophes) {
                fewestCatastrophes = catastrophes;
                bestSlices = new ArrayList<>(slices);
                bestFactions = new ArrayList<>(factions);
                bestPositions = new ArrayList<>(positions);
            }
            if (catastrophes == 0) {
                break;
            }
        }

        for (int i = 0; i < playerCount; i++) {
            MiltyDraftManager.PlayerDraft picks = draftManager.getPlayerDraft(players.get(i));
            picks.setSlice(bestSlices.get(i));
            picks.setFaction(bestFactions.get(i));
            picks.setPosition(bestPositions.get(i));
        }
    }

    private static int countCatastrophes(
            MiltyDraftSpec specs, List<MiltyDraftSlice> slices, List<String> factions, List<Integer> positions) {
        int catastrophes = 0;
        for (int i = 0; i < specs.playerIDs.size(); i++) {
            if (isTerribleSlice(specs, slices.get(i))
                    && isBadSpeakerPosition(specs.playerIDs.size(), positions.get(i))
                    && isYikesFaction(factions.get(i))) {
                catastrophes++;
            }
        }
        return catastrophes;
    }

    private static boolean isTerribleSlice(MiltyDraftSpec specs, MiltyDraftSlice slice) {
        int optRes = slice.getOptimalRes();
        int optInf = slice.getOptimalInf();
        int totalOptimal = slice.getOptimalTotalValue();
        if (slice.getTiles().stream().anyMatch(MiltyDraftTile::isHasScar)) {
            optRes += 2;
            totalOptimal += 2;
        }
        return totalOptimal <= specs.minTot
                || (totalOptimal <= specs.minTot + 1 && optRes <= specs.minRes && optInf <= specs.minInf);
    }

    private static boolean isBadSpeakerPosition(int playerCount, int position) {
        return position >= Math.max(1, playerCount - 1);
    }

    private static boolean isYikesFaction(String faction) {
        return List.of("arborec", "sardakk", "winnu").contains(faction);
    }

    private static void presetRandomKeleresFlavorIfNeeded(MiltyDraftManager draftManager, Game game) {
        boolean keleresWasPicked = draftManager.getDraft().values().stream()
                .anyMatch(picks ->
                        picks.getFaction() != null && picks.getFaction().startsWith("keleres"));
        if (!keleresWasPicked) {
            return;
        }

        List<String> valid = new ArrayList<>(List.of("mentak", "xxcha", "argent"));
        valid.removeIf(draftManager::isFactionTaken);
        if (valid.isEmpty()) {
            valid.addAll(List.of("mentak", "xxcha", "argent"));
        }
        Collections.shuffle(valid);
        game.setStoredValue("keleresFlavorPreset", valid.getFirst());
    }

    private static String validateFactionSourceConstraints(
            GenericInteractionCreateEvent event, MiltyDraftSpec specs, List<String> unbannedFactions) {
        if (specs.sourceConstraints == null || specs.sourceConstraints.isEmpty()) {
            return null;
        }
        Map<ComponentSource, Long> actualBySource = unbannedFactions.stream()
                .filter(f -> Mapper.getFaction(f) != null)
                .collect(Collectors.groupingBy(
                        f -> MiltyService.logicalFactionSource(
                                Mapper.getFaction(f).getSource()),
                        Collectors.counting()));
        long unconstrained = unbannedFactions.stream()
                .filter(f -> Mapper.getFaction(f) != null)
                .filter(f -> !specs.sourceConstraints.containsKey(
                        MiltyService.logicalFactionSource(Mapper.getFaction(f).getSource())))
                .count();
        int sumMins = 0, sumMaxes = (int) unconstrained;
        for (Map.Entry<ComponentSource, int[]> e : specs.sourceConstraints.entrySet()) {
            int actual = actualBySource.getOrDefault(e.getKey(), 0L).intValue();
            sumMins += Math.min(e.getValue()[0], actual);
            sumMaxes += Math.min(e.getValue()[1], actual);
        }
        if (sumMins > specs.numFactions) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Faction source minimums sum to **" + sumMins
                            + "**, which exceeds the random setup faction count of **" + specs.numFactions
                            + "**. Reduce the minimums before starting.");
            return "Could not start random setup, fix the error and try again";
        }
        if (sumMaxes < specs.numFactions) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Faction source maximums sum to **" + sumMaxes
                            + "**, which is less than the random setup faction count of **" + specs.numFactions
                            + "**. Increase the maximums before starting.");
            return "Could not start random setup, fix the error and try again";
        }
        return null;
    }
}
