package ti4.service.milty;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.rules.ThundersEdgeRulesService;

@UtilityClass
public class MiltyService {
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

    public static void offerKeleresSetupButtons(MiltyDraftManager manager, Player player) {
        List<String> flavors = List.of("mentak", "xxcha", "argent");
        List<Button> keleresPresets = new ArrayList<>();
        boolean warn = false;
        for (String f : flavors) {
            if (manager.isFactionTaken(f)) continue;

            FactionModel model = Mapper.getFaction(f);
            String id = "draftPresetKeleres_" + f;
            String label = StringUtils.capitalize(f);
            if (manager.getFactionDraft().contains(f)) {
                keleresPresets.add(Buttons.gray(id, label + " 🛑", model.getFactionEmoji()));
                warn = true;
            } else {
                keleresPresets.add(Buttons.green(id, label, model.getFactionEmoji()));
            }
        }

        String message = player.getPing()
                + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
        message += " You can change your decision later by clicking a different button.";
        if (warn)
            message +=
                    "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be canceled.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, keleresPresets);
    }

    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        MiltyDraftSpec specs = MiltyDraftSpec.fromSettings(settings);

        return startFromSpecs(event, specs);
    }

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
        initDraftOrder(draftManager, specs.playerIDs, false);

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
        List<String> factionDraft = createFactionDraft(
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

    private static List<List<Boolean>> getAdjMatrix(ti4.model.MapTemplateModel template) {
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
                        f -> logicalFactionSource(Mapper.getFaction(f).getSource()), Collectors.counting()));
        long unconstrained = unbannedFactions.stream()
                .filter(f -> Mapper.getFaction(f) != null)
                .filter(f -> !specs.sourceConstraints.containsKey(
                        logicalFactionSource(Mapper.getFaction(f).getSource())))
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

    public static String startFromSpecs(GenericInteractionCreateEvent event, MiltyDraftSpec specs) {
        Game game = specs.game;

        if (specs.presetSlices != null) {
            if (specs.presetSlices.size() < specs.playerIDs.size())
                return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
        }

        // Milty Draft Manager Setup --------------------------------------------------------------
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
        List<String> players = new ArrayList<>(specs.playerIDs);
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            players = new ArrayList<>(specs.playerDraftOrder)
                    .stream().filter(p -> specs.playerIDs.contains(p)).toList();
        }
        initDraftOrder(draftManager, players, staticOrder);

        // initialize factions
        List<String> unbannedFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> specs.factionSources.contains(f.getSource()))
                .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("neutral"))
                .filter(f -> !f.getAlias().contains("keleres")
                        || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres flavor
                .map(FactionModel::getAlias)
                .toList());
        if (specs.sourceConstraints != null && !specs.sourceConstraints.isEmpty()) {
            Map<ComponentSource, Long> actualBySource = unbannedFactions.stream()
                    .filter(f -> Mapper.getFaction(f) != null)
                    .collect(Collectors.groupingBy(
                            f -> logicalFactionSource(Mapper.getFaction(f).getSource()), Collectors.counting()));
            long unconstrained = unbannedFactions.stream()
                    .filter(f -> Mapper.getFaction(f) != null)
                    .filter(f -> !specs.sourceConstraints.containsKey(
                            logicalFactionSource(Mapper.getFaction(f).getSource())))
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
                        "Faction source minimums sum to **" + sumMins + "**, which exceeds the draft pool size of **"
                                + specs.numFactions + "**. Reduce the minimums before starting.");
                return "Could not start milty draft, fix the error and try again";
            }
            if (sumMaxes < specs.numFactions) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Faction source maximums sum to **" + sumMaxes
                                + "**, which is less than the draft pool size of **" + specs.numFactions
                                + "**. Increase the maximums before starting.");
                return "Could not start milty draft, fix the error and try again";
            }
        }

        List<String> factionDraft = createFactionDraft(
                specs.numFactions, unbannedFactions, specs.priorityFactions, specs.sourceConstraints);
        draftManager.setFactionDraft(factionDraft);

        // validate slice count + sources
        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (specs.numSlices > maxSlices) {
            String msg = "Milty draft in this bot does not support " + specs.numSlices
                    + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles
                    + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return "Could not start milty draft, fix the error and try again";
        }

        String startMsg = "## Generating the milty draft!!";
        startMsg +=
                "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
        if (specs.numSlices == maxSlices) {
            startMsg += "\n - *You asked for the max number of slices, so this may take several seconds*";
        }

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

        if (specs.presetSlices != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "### You are using preset slices!! Starting the draft right away!");
            specs.presetSlices.forEach(draftManager::addSlice);
            MiltyDraftDisplayService.repostDraftInformation(draftManager, game);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), startMsg);
            boolean slicesCreated = GenerateSlicesService.generateSlices(event, draftManager, specs);
            if (!slicesCreated) {
                String msg = "Generating slices was too hard so I gave up.... Please try again.";
                if (specs.numSlices == maxSlices) {
                    msg += "\n*...and maybe consider asking for fewer slices*";
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            } else {
                MiltyDraftDisplayService.repostDraftInformation(draftManager, game);

                game.setPhaseOfGame("miltydraft");
                for (String player : draftManager.getPlayers()) {
                    Player p = game.getPlayer(player);
                    game.setStoredValue(p.getUserID() + "queuedMiltyPick", "");
                }
                // SPOOKY SAVE
                GameManager.save(game, "Milty");
                if (game.isThundersEdge()) {
                    ThundersEdgeRulesService.alertTabletalkWithRulesAtStartOfDraft(game);
                }
                for (String player : draftManager.getPlayers()) {
                    Player p = game.getPlayer(player);
                    if (p != draftManager.getCurrentDraftPlayer(game) && p.getCardsInfoThread() != null) {
                        MessageHelper.sendMessageToChannel(
                                p.getCardsInfoThread(),
                                p.getRepresentation() + " You can queue your choices with these buttons",
                                draftManager.getQueueButtons(p, game));
                    }
                }
            }
        }
        return null;
    }

    private static void initDraftOrder(MiltyDraftManager draftManager, List<String> playerIDs, boolean staticOrder) {
        List<String> players = new ArrayList<>(playerIDs);
        if (!staticOrder) {
            Collections.shuffle(players);
        }

        List<String> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);

        List<String> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);

        draftManager.setDraftOrder(draftOrder);
        draftManager.setPlayers(players);
    }

    /**
     * Builds the faction draft pool.
     * - Banned factions are excluded before this is called (not in {@code factions}).
     * - Priority factions ({@code firstFactions}) bypass source constraints and are added first.
     * - Per-source constraints cap/guarantee the remaining slots per expansion.
     * - If constraints is null/empty, falls back to the original unconstrained shuffle.
     */
    private static List<String> createFactionDraft(
            int factionCount,
            List<String> factions,
            List<String> firstFactions,
            Map<ComponentSource, int[]> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            List<String> randomOrder = new ArrayList<>(firstFactions);
            Collections.shuffle(randomOrder);
            Collections.shuffle(factions);
            randomOrder.addAll(factions);
            List<String> output = new ArrayList<>();
            int i = 0;
            while (output.size() < factionCount) {
                if (i >= randomOrder.size()) return output;
                String f = randomOrder.get(i);
                i++;
                if (!output.contains(f) && factions.contains(f)) output.add(f);
            }
            return output;
        }

        // Priority factions bypass source constraints; add all eligible ones first
        List<String> output = new ArrayList<>();
        for (String pf : firstFactions) {
            if (factions.contains(pf) && !output.contains(pf)) output.add(pf);
        }

        // Count priority factions per logical source so effective min/max accounts for them
        Map<ComponentSource, Integer> priorityCountBySource = new HashMap<>();
        for (String pf : output) {
            FactionModel m = Mapper.getFaction(pf);
            if (m == null) continue;
            priorityCountBySource.merge(logicalFactionSource(m.getSource()), 1, Integer::sum);
        }

        // Group remaining factions by logical source
        Map<ComponentSource, List<String>> bySource = new HashMap<>();
        List<String> unconstrained = new ArrayList<>();
        for (String f : factions) {
            if (output.contains(f)) continue;
            FactionModel m = Mapper.getFaction(f);
            if (m == null) continue;
            ComponentSource logical = logicalFactionSource(m.getSource());
            if (constraints.containsKey(logical)) {
                bySource.computeIfAbsent(logical, _ -> new ArrayList<>()).add(f);
            } else {
                unconstrained.add(f);
            }
        }
        for (List<String> pool : bySource.values()) Collections.shuffle(pool);

        // Apply effective min/max per source
        List<String> guaranteed = new ArrayList<>();
        List<String> available = new ArrayList<>();
        for (Map.Entry<ComponentSource, List<String>> e : bySource.entrySet()) {
            int[] range = constraints.get(e.getKey());
            int priorityCount = priorityCountBySource.getOrDefault(e.getKey(), 0);
            int effectiveMin = Math.max(0, range[0] - priorityCount);
            int effectiveMax = Math.max(0, range[1] - priorityCount);
            List<String> pool = e.getValue();
            for (int i = 0; i < pool.size(); i++) {
                if (i < effectiveMin) guaranteed.add(pool.get(i));
                else if (i < effectiveMax) available.add(pool.get(i));
                // beyond effectiveMax: excluded from pool
            }
        }
        Collections.shuffle(unconstrained);
        available.addAll(unconstrained);
        Collections.shuffle(available);

        // Fill: priority already in output, then guaranteed, then available up to factionCount
        for (String g : guaranteed) {
            if (!output.contains(g)) output.add(g);
        }
        for (String a : available) {
            if (output.size() >= factionCount) break;
            if (!output.contains(a)) output.add(a);
        }

        return output.subList(0, Math.min(factionCount, output.size()));
    }

    private static ComponentSource logicalFactionSource(ComponentSource source) {
        return switch (source) {
            case codex1, codex2, codex3, codex4 -> ComponentSource.pok;
            default -> source;
        };
    }

    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltySettings menu = game.initializeMiltySettings();
        menu.setRandomSetup(false);
        // TODO: Settings should use a flag for nucleus generation.
        // But for now, trying to keep our settings changes minimal.
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            if (buttonEvent.getButton().getCustomId().endsWith("_nucleus")) {
                menu.getDraftMode().setChosenKey("nucleus");
            }
        }
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }

    public static void setupExtraFactionTiles(Game game, Player player, String faction, String positionHS, Tile tile) {
        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
            if (!game.isBaseGameMode()) {
                tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            }
            if (game.isTwilightsFallMode()) {
                player.addAbility("echo_of_sacrifice");
            }

            // Add the new tile
            String pos = addAnotherCornerTile(game, "51", positionHS);
            player.setHomeSystemPosition(pos);
        }

        // HANDLE CRIMSON'S HOME SYSTEM LOCATION
        if ("crimson".equals(faction)) {
            if (!game.isBaseGameMode()) {
                tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            }
            if (!game.isTwilightsFallMode()) {
                tile.addToken(Constants.TOKEN_BREACH_INACTIVE, Constants.SPACE);
            } else {
                player.addAbility("echo_of_divergence");
            }

            // Add the new tile
            String pos = addAnotherCornerTile(game, "118", positionHS);
            player.setHomeSystemPosition(pos);
        }
    }

    private static String addAnotherCornerTile(Game game, String tileID, String anchorPos) {
        record TileAndAnchor(Tile tile, String pos) {}
        List<TileAndAnchor> tilesWithAnchors = Stream.of("tl", "tr", "bl", "br")
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .map(t -> new TileAndAnchor(t, getAnchorPositionForTile(game, t)))
                .collect(Collectors.toCollection(ArrayList::new));
        tilesWithAnchors.add(new TileAndAnchor(new Tile(tileID, "tl"), anchorPos));
        if (tilesWithAnchors.size() > 4) {
            String err = "# " + game.getPing() + " UNABLE TO ADD ANOTHER CORNER TILE. PLEASE RESOLVE.";
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), err);
            return null;
        }
        String pos = "tr";
        for (List<String> positions : CollectionUtils.permutations(List.of("tl", "tr", "bl", "br"))) {
            boolean acceptable = true;
            Point center = PositionMapper.getTilePosition("000");
            for (int x = 0; x < tilesWithAnchors.size(); x++) {
                String xAnchorPos = tilesWithAnchors.get(x).pos();
                if (xAnchorPos != null) {
                    Point anchor = PositionMapper.getTilePosition(xAnchorPos);
                    String corner = positions.get(x);
                    acceptable &= switch (corner) {
                        case "tl" -> anchor.x <= center.x && anchor.y <= center.y;
                        case "tr" -> anchor.x >= center.x && anchor.y <= center.y;
                        case "bl" -> anchor.x <= center.x && anchor.y >= center.y;
                        case "br" -> anchor.x >= center.x && anchor.y >= center.y;
                        default -> false;
                    };
                }
            }
            if (acceptable) {
                for (int x = 0; x < positions.size(); x++) {
                    String corner = positions.get(x);
                    if (x >= tilesWithAnchors.size()) {
                        game.getTileMap().remove(corner);
                    } else {
                        Tile tile = tilesWithAnchors.get(x).tile();
                        if (tile.getTileID().equals(tileID)) pos = corner;
                        tile.setPosition(corner);
                        game.setTile(tile);
                    }
                }
            }
        }
        return pos;
    }

    private static String getAnchorPositionForTile(Game game, Tile t) {
        return switch (t.getTileID()) {
            case "51" -> game.getTile("17").getPosition();
            case "118" -> game.getTile("94").getPosition();
            default -> null;
        };
    }
}
