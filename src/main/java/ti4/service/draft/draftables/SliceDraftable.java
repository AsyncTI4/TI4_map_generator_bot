package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.NucleusSliceDraftableSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SliceDraftableSettings;
import ti4.helpers.settingsFramework.menus.SliceDraftableSettings.MapGenerationMode;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSliceHelper;
import ti4.service.draft.DraftSpec;
import ti4.service.draft.DraftableType;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusOutcome;
import ti4.service.draft.NucleusSpecs;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.draft.SliceGenerationPipeline;
import ti4.service.draft.SliceImageGeneratorService;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.milty.MiltyDraftSlice;

public class SliceDraftable extends SinglePickDraftable {

    @Getter
    private List<MiltyDraftSlice> slices = new ArrayList<>();

    public void initialize(List<MiltyDraftSlice> slices) {
        this.slices = slices;
    }

    public MiltyDraftSlice getSliceByName(String name) {
        for (MiltyDraftSlice slice : slices) {
            if (slice.getName().equals(name)) {
                return slice;
            }
        }
        return null;
    }

    public List<MiltyDraftSlice> getDraftSlices() {
        return slices;
    }

    public static final DraftableType TYPE = DraftableType.of("Slice");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (MiltyDraftSlice slice : slices) {
            String choiceKey = slice.getName();
            String buttonEmoji = MiltyDraftEmojis.getMiltyDraftEmoji(choiceKey).toString();
            String unformattedName = "Slice " + slice.getName();
            String displayName = "Slice " + slice.getName();
            choices.add(new DraftChoice(
                    getType(),
                    choiceKey,
                    makeChoiceButton(choiceKey, null, buttonEmoji),
                    displayName,
                    unformattedName,
                    buttonEmoji));
        }
        return choices;
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {

        return "Unknown command: " + commandKey;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No slice picked",
                "No slice picked",
                MiltyDraftEmojis.getMiltyDraftEmoji(null).toString());
    }

    @Override
    public FileUpload generateSummaryImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {
        return SliceImageGeneratorService.tryGenerateImage(draftManager, uniqueKey, restrictChoiceKeys);
    }

    @Override
    public String save() {
        if (slices == null) {
            return "";
        }
        return String.join(";", slices.stream().map(MiltyDraftSlice::ttsString).toList());
    }

    @Override
    public void load(String data) {
        slices = new ArrayList<>(DraftSliceHelper.parseSlicesFromString(data));
    }

    @Override
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (slices.size() < numPlayers) {
            return "Number of slices (" + slices.size() + ") is less than number of players (" + numPlayers
                    + "). Add more slices with `/draft slice add`.";
        }

        return super.validateState(draftManager);
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        // Map is built as a side effect of slice drafting.
        return null;
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        if (menu == null || !(menu instanceof DraftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
        DraftSystemSettings draftSystemSettings = (DraftSystemSettings) menu;
        Game game = draftSystemSettings.getGame();
        if (game == null) {
            return "Error: Could not find game instance.";
        }
        SliceDraftableSettings sliceSettings = draftSystemSettings.getSliceSettings();

        if (MapGenerationMode.Nucleus.equals(
                sliceSettings.getMapGenerationMode().getValue())) {
            return doNucleusGeneration(
                    event,
                    game,
                    sliceSettings,
                    draftSystemSettings.getPlayerUserIds().size());
        } else if (MapGenerationMode.Milty.equals(
                sliceSettings.getMapGenerationMode().getValue())) {
            return doMiltyGeneration(event, game, draftSystemSettings);
        } else {
            return "Error: Unknown map generation mode: "
                    + sliceSettings.getMapGenerationMode().getValue();
        }
    }

    private String doNucleusGeneration(
            GenericInteractionCreateEvent event, Game game, SliceDraftableSettings sliceSettings, int playerCount) {
        NucleusSliceDraftableSettings nucleusSettings = sliceSettings.getNucleusSettings();
        MapTemplateModel mapTemplate = sliceSettings.getMapTemplate().getValue();
        if (mapTemplate == null || mapTemplate.getPlayerCount() != playerCount) {
            return "The selected map template " + mapTemplate.getAlias() + " is for a different number of players than "
                    + playerCount;
        }

        NucleusSpecs specs = new NucleusSpecs(
                sliceSettings.getNumSlices().getVal(),
                nucleusSettings.getNucleusWormholes().getValLow(), // min nucleus wormholes
                nucleusSettings.getNucleusWormholes().getValHigh(), // max nucleus wormholes
                nucleusSettings.getNucleusLegendaries().getValLow(), // min nucleus legendaries
                nucleusSettings.getNucleusLegendaries().getValHigh(), // max nucleus legendaries
                nucleusSettings.getTotalWormholes().getValLow(), // min map wormholes
                nucleusSettings.getTotalWormholes().getValHigh(), // max map wormholes
                nucleusSettings.getTotalLegendaries().getValLow(), // min map legendaries
                nucleusSettings.getTotalLegendaries().getValHigh(), // max map legendaries
                nucleusSettings.getSliceValue().getValLow(), // min slice value
                nucleusSettings.getSliceValue().getValHigh(), // max slice value
                nucleusSettings.getNucleusValue().getValLow(), // min nucleus value
                nucleusSettings.getNucleusValue().getValHigh(), // max nucleus value
                nucleusSettings.getSlicePlanetCount().getValLow(), // min slice planets
                nucleusSettings.getSlicePlanetCount().getValHigh(), // max slice planets
                nucleusSettings.getMinimumSliceRes().getVal(), // min slice resources
                nucleusSettings.getMinimumSliceInf().getVal(), // min slice influence
                nucleusSettings.getMaxNucleusQualityDifference().getVal(), // max nucleus quality difference
                nucleusSettings.getMinimumRedTiles().getVal() // expected red tiles
                );

        game.clearTileMap();
        // Very important...the distance tool needs hyperlane tiles placed to calculate adjacencies
        PartialMapService.placeFromTemplate(mapTemplate, game);
        // Nucleus generation expects the map template to be set on the game
        game.setMapTemplateID(mapTemplate.getAlias());

        String specError = NucleusSpecs.validateSpecsForGame(specs, game);
        if (specError != null) {
            return "Error in nucleus/slice settings: " + specError;
        }

        // Ensure we can't start yet
        slices = null;

        // Create a draftable, AND setup the nucleus directly onto the map!
        SliceGenerationPipeline.queue(event, game, specs, (NucleusOutcome outcome) -> {
            if (outcome.failureReason() != null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "We couldn't generate an acceptable map+slices. You can adjust and try again. The most common problem with the maps was: "
                                + outcome.failureReason());
                BotLogger.info(new LogOrigin(game), "Nucleus generation failed: " + outcome.failureReason());
                return;
            }
            if (outcome.slices() == null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "We couldn't generate an acceptable map+slices. You can adjust and try again.");
                BotLogger.error(new LogOrigin(game), "Nucleus generation failed without giving a reason.");
                return;
            }
            initialize(outcome.slices());
            game.getDraftManager().tryStartDraft();
            GameManager.save(game, "Nucleus generation");
        });

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "## Generating nucleus and slices!\nThis may take a couple moments\n-# if it fails, you can try again, maybe adjusting settings to be more lax, or ping a bothelper.");

        return null;
    }

    private String doMiltyGeneration(
            GenericInteractionCreateEvent event, Game game, DraftSystemSettings draftSystemSettings) {
        DraftSpec specs = DraftSpec.SliceSpecsFromDraftSystemSettings(draftSystemSettings);

        // Ensure we can't start yet
        slices = null;

        SliceGenerationPipeline.queue(event, this, game.getDraftTileManager(), specs, (Boolean success) -> {
            if (success) {
                game.getDraftManager().tryStartDraft();
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Could not generate slices with the current settings. Try adjusting the settings and generating again.");
            }
        });

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "## Generating slices!\nThis may take a couple moments\n-# if it fails, you can try again, maybe adjusting settings to be more lax, or ping a bothelper.");
        return null;
    }

    @Override
    public String whatsStoppingDraftStart(DraftManager draftManager) {
        if (slices == null || slices.isEmpty()) {
            return "No slices have been defined. Use `/draft slice add` to add slices (or wait for generation to finish).";
        }
        return super.whatsStoppingDraftStart(draftManager);
    }
}
