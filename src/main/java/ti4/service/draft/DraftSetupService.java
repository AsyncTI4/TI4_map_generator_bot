package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftSlice;

@UtilityClass
public class DraftSetupService {
    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        DraftSpec specs = DraftSpec.CreateFromMiltySettings(settings);

        if (specs.getTemplate().isNucleusTemplate()) {
            return startNucleusFromSpecs(event, specs);
        } else {
            return startMiltyFromSpecs(event, specs);
        }
    }

    public static String startMiltyFromSpecs(GenericInteractionCreateEvent event, DraftSpec specs) {
        Game game = specs.game;

        if (specs.presetSlices != null) {
            if (specs.presetSlices.size() < specs.playerIDs.size())
                return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
        }

        // Draft Manager Setup
        // --------------------------------------------------------------

        // Setup managers and game state
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.addAllDraftTiles(specs.tileSources);
        DraftManager draftManager = game.getDraftManager();
        draftManager.resetForNewDraft();
        draftManager.setPlayers(specs.playerIDs);

        game.setMapTemplateID(specs.template.getAlias());

        FactionDraftable factionDraftable = new FactionDraftable();
        factionDraftable.initialize(
                specs.numFactions, specs.factionSources, specs.priorityFactions, specs.bannedFactions);
        draftManager.addDraftable(factionDraftable);

        SpeakerOrderDraftable speakerOrderDraftable = new SpeakerOrderDraftable();
        speakerOrderDraftable.initialize(draftManager.getPlayerStates().size());
        draftManager.addDraftable(speakerOrderDraftable);

        // Setup Public Snake Draft Orchestrator
        PublicSnakeDraftOrchestrator orchestrator = new PublicSnakeDraftOrchestrator();
        List<String> players = null;
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            players = new ArrayList<>(specs.playerDraftOrder)
                    .stream().filter(p -> specs.playerIDs.contains(p)).toList();
        }
        orchestrator.initialize(draftManager, players);
        draftManager.setOrchestrator(orchestrator);

        // validate slice count + sources
        int redTiles = tileManager.getRed().size();
        int blueTiles = tileManager.getBlue().size();
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
            PartialMapService.tryUpdateMap(event, draftManager, true);
        } catch (Exception e) {
            // Ignore
        }

        SliceDraftable sliceDraftable = new SliceDraftable();
        draftManager.addDraftable(sliceDraftable);

        if (specs.presetSlices != null) {
            sliceDraftable.initialize(specs.presetSlices);
            draftManager.tryStartDraft();
        } else {
            event.getMessageChannel().sendMessage(startMsg).queue((ignore) -> {
                boolean slicesCreated = SliceGeneratorService.generateSlices(event, sliceDraftable, tileManager, specs);
                if (!slicesCreated) {
                    String msg = "Generating slices was too hard so I gave up.... Please try again.";
                    if (specs.numSlices == maxSlices) {
                        msg += "\n*...and maybe consider asking for fewer slices*";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                } else {
                    draftManager.tryStartDraft();
                    game.setPhaseOfGame("miltydraft");
                    GameManager.save(game, "Milty"); // TODO: We should be locking since we're saving
                }
            });
        }
        return null;
    }

    public static String startNucleusFromSpecs(GenericInteractionCreateEvent event, DraftSpec specs) {
        Game game = specs.game;

        // Setup managers and game state
        DraftManager draftManager = game.getDraftManager();
        draftManager.resetForNewDraft();
        draftManager.setPlayers(specs.playerIDs);

        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.clear();
        tileManager.addAllDraftTiles(specs.getTileSources());

        game.setMapTemplateID(specs.template.getAlias());

        FactionDraftable factionDraftable = new FactionDraftable();
        factionDraftable.initialize(
                specs.numFactions, specs.factionSources, specs.priorityFactions, specs.bannedFactions);
        draftManager.addDraftable(factionDraftable);

        SpeakerOrderDraftable speakerOrderDraftable = new SpeakerOrderDraftable();
        speakerOrderDraftable.initialize(specs.playerIDs.size());
        draftManager.addDraftable(speakerOrderDraftable);

        SeatDraftable seatDraftable = new SeatDraftable();
        seatDraftable.initialize(specs.getTemplate().getPlayerCount());
        draftManager.addDraftable(seatDraftable);

        // Setup Public Snake Draft Orchestrator
        PublicSnakeDraftOrchestrator orchestrator = new PublicSnakeDraftOrchestrator();
        List<String> setPlayerOrder = null;
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            setPlayerOrder = new ArrayList<>(specs.playerDraftOrder)
                    .stream().filter(p -> specs.playerIDs.contains(p)).toList();
        }
        orchestrator.initialize(draftManager, setPlayerOrder);
        draftManager.setOrchestrator(orchestrator);

        game.clearTileMap();
        try {
            // Very important...the distance tool needs tiles placed to calculate adjacencies
            PartialMapService.tryUpdateMap(event, draftManager, false);
        } catch (Exception e) {
            // Ignore
        }

        // TODO: Support this in the Nucleus generator, by factoring in to the nucleus generation
        // if (specs.presetSlices != null) {
        //     SliceDraftable sliceDraftable = new SliceDraftable();
        //     draftManager.addDraftable(sliceDraftable);
        //     sliceDraftable.initialize(specs.presetSlices);
        //     draftManager.tryStartDraft();
        // }

        // TODO: Support presetting the Nucleus in the Settings object, maybe via modal w/ TTS string
        String startMsg = "## Generating the nucleus and slices!!";
        if (specs.getPlayerIDs().size() > 7 && specs.numSlices < 10) {
            startMsg +=
                    "\n -# This process can fail if valid configurations are hard to find. If you get stuck, try adding a slice.";
        }

        event.getMessageChannel().sendMessage(startMsg).queue((ignore) -> {
            List<MiltyDraftSlice> slices = NucleusSliceGeneratorService.generateNucleusAndSlices(event, specs);
            if (slices == null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Failed to generate nucleus and slices after many attempts! Ping bothelper to report this issue.");
                BotLogger.warning(new LogOrigin(event), "Failed to generate nucleus and slices after many attempts.");
            } else {
                SliceDraftable sliceDraftable = new SliceDraftable();
                List<MiltyDraftSlice> sortedSlcies = slices.stream()
                        .sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
                        .toList();
                sliceDraftable.initialize(sortedSlcies);
                draftManager.addDraftable(sliceDraftable);
                draftManager.tryStartDraft();
                game.setPhaseOfGame("miltydraft");
                GameManager.save(game, "Milty"); // TODO: We should be locking since we're saving
                try {
                    PartialMapService.tryUpdateMap(event, draftManager, true);
                } catch (Exception e) {
                    // Ignore
                }
            }
        });

        return null;
    }
}
