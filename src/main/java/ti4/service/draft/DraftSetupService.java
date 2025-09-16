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
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

@UtilityClass
public class DraftSetupService {
    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success)
            return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        DraftSpec specs = DraftSpec.CreateFromMiltySettings(settings);

        return startFromSpecs(event, specs);
    }

    public static String startFromSpecs(GenericInteractionCreateEvent event, DraftSpec specs) {
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
        // initDraftOrder(draftManager, players, staticOrder);
        orchestrator.initialize(draftManager, players);
        draftManager.setOrchestrator(orchestrator);

        // initialize factions
        // List<String> unbannedFactions = new
        // ArrayList<>(Mapper.getFactionsValues().stream()
        // .filter(f -> specs.factionSources.contains(f.getSource()))
        // .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
        // .filter(f -> !f.getAlias().contains("keleres")
        // || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres
        // flavor
        // .map(FactionModel::getAlias)
        // .toList());
        // List<String> factionDraft = createFactionDraft(specs.numFactions,
        // unbannedFactions, specs.priorityFactions);
        // draftManager.setFactionDraft(factionDraft);

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
        startMsg += "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
        if (specs.numSlices == maxSlices) {
            startMsg += "\n - *You asked for the max number of slices, so this may take several seconds*";
        }

        game.clearTileMap();
        try {
            PartialMapService.tryUpdateMap(event, draftManager);
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
}
