package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class MiltyRandomSetupService {

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

        return randomSetupFromSpecs(event, specs);
    }

    public static String randomSetupFromSpecs(GenericInteractionCreateEvent event, MiltyDraftSpec specs) {
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
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (specs.numSlices > maxSlices) {
            String msg = "Random setup in this bot does not support " + specs.numSlices
                    + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles
                    + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return "Could not start random setup, fix the error and try again";
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "## Generating random setup\n - Also clearing out any tiles that may have already been on the map "
                        + "so setup can fill in tiles properly.");
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
        if (specs.presetSlices != null) {
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
        game.updateActivePlayer(null);
        return null;
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
