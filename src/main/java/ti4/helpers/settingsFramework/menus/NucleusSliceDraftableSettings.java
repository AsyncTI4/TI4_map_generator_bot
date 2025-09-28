package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.helpers.StringHelper;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.NucleusSliceGeneratorService;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusOutcome;
import ti4.service.draft.NucleusSliceGeneratorService.NucleusSpecs;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TileEmojis;


@Getter
@JsonIgnoreProperties("messageId")
public class NucleusSliceDraftableSettings extends SettingsMenu {

    // Setting
    private final IntegerRangeSetting nucleusWormholes;
    private final IntegerRangeSetting totalWormholes;
    private final IntegerRangeSetting nucleusLegendaries;
    private final IntegerRangeSetting totalLegendaries;

    // Fine tuning
    private final IntegerSetting minimumSliceRes;
    private final IntegerSetting minimumSliceInf;
    private final IntegerRangeSetting sliceValue;
    private final IntegerRangeSetting slicePlanetCount;
    private final IntegerRangeSetting nucleusValue;
    private final IntegerSetting maxNucleusQualityDifference;
    private final IntegerSetting minimumRedTiles;

    private boolean showAdvanced = false;

    private final static Map<Integer, Integer> SEAT_COUNT_TO_MINIMUM_RED_TILES = Map.of(
            3, 6,
            4, 7,
            5, 9,
            6, 11,
            7, 13,
            8, 15);

    private final static String MENU_ID = "nucleusSlice";

    public NucleusSliceDraftableSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Nucleus & Slice value settings", "Advanced settings for map features and slice quality.", parent);
        this.description.add("Big changes here can cause the nucleus generation to fail.");

        // Initialize settings
        int players = game != null ? game.getPlayers().size() : 6;
        int suggestWormholesMax = players;
        nucleusWormholes = new IntegerRangeSetting("NucleusWH", "Nucleus Wormholes", 1, 0, 4, suggestWormholesMax, 0, 4, 1);
        int suggestWormholesMin = players - 1;
        totalWormholes = new IntegerRangeSetting("TotalWH", "Total Wormholes", suggestWormholesMin, 0, 6, suggestWormholesMax, 0, 6, 1);
        int suggestLegendariesMax = Math.round(players / 3.0f);
        nucleusLegendaries = new IntegerRangeSetting("NucleusLeg", "Nucleus Legendaries", 0, 0, 3, suggestLegendariesMax, 0, 3, 1);
        totalLegendaries = new IntegerRangeSetting("TotalLeg", "Total Legendaries", 1, 0, 5, suggestLegendariesMax, 0, 5, 1);
        minimumSliceRes = new IntegerSetting("MinSliceRes", "Min Slice Resources", 0, 0, 5, 1);
        minimumSliceInf = new IntegerSetting("MinSliceInf", "Min Slice Influence", 0, 0, 5, 1);
        sliceValue = new IntegerRangeSetting("SliceVal", "Slice Optimal Value", 4, 0, 8, 9, 3, 12, 1);
        slicePlanetCount = new IntegerRangeSetting("SlicePlanets", "Slice Planet Count", 2, 0, 7, 5, 0, 7, 1);
        nucleusValue = new IntegerRangeSetting("NucleusVal", "Nucleus Optimal Value", 4, 0, 8, 8, 3, 12, 1);
        maxNucleusQualityDifference = new IntegerSetting("MaxNucDiff", "Max Nucleus Quality Diff", 3, 0, 10, 1);
        minimumRedTiles = new IntegerSetting("MinRed", "Minimum Red Tiles", SEAT_COUNT_TO_MINIMUM_RED_TILES.get(players), 0, 20, 1);

        // Add extra info
        minimumSliceRes.setExtraInfo("Defaults to 0. A low res slice can be combined with a specific seat to balance out.");
        minimumSliceInf.setExtraInfo("Defaults to 0. A low inf slice can be combined with a specific seat to balance out.");

        // Emojis
        nucleusWormholes.setEmoji(MiscEmojis.WHalpha);
        totalWormholes.setEmoji(MiscEmojis.WHbeta);
        nucleusLegendaries.setEmoji(PlanetEmojis.Primor);
        totalLegendaries.setEmoji(PlanetEmojis.HopesEnd);
        minimumSliceRes.setEmoji(MiscEmojis.resources);
        minimumSliceInf.setEmoji(MiscEmojis.influence);
        sliceValue.setEmoji(MiscEmojis.ResInf);
        slicePlanetCount.setEmoji(PlanetEmojis.RigelI);
        nucleusValue.setEmoji(MiscEmojis.ResInf);
        maxNucleusQualityDifference.setEmoji(MiscEmojis.Resources_0);
        minimumRedTiles.setEmoji(TileEmojis.GravityRift_41);
        
        // Load JSON if applicable
        if (json == null || !json.has("menuId") || !MENU_ID.equals(json.get("menuId").asText(""))) {
            return;
        }

        nucleusWormholes.initialize(json.get("nucleusWormholes"));
        totalWormholes.initialize(json.get("totalWormholes"));
        nucleusLegendaries.initialize(json.get("nucleusLegendaries"));
        totalLegendaries.initialize(json.get("totalLegendaries"));
        minimumSliceRes.initialize(json.get("minimumSliceRes"));
        minimumSliceInf.initialize(json.get("minimumSliceInf"));
        sliceValue.initialize(json.get("sliceValue"));
        slicePlanetCount.initialize(json.get("slicePlanetCount"));
        nucleusValue.initialize(json.get("nucleusValue"));
        maxNucleusQualityDifference.initialize(json.get("maxNucleusQualityDifference"));
        minimumRedTiles.initialize(json.get("minimumRedTiles"));
        showAdvanced = json.get("showAdvanced") != null && json.get("showAdvanced").asBoolean(false);
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(nucleusWormholes);
        ls.add(totalWormholes);
        ls.add(nucleusLegendaries);
        ls.add(totalLegendaries);
        if(showAdvanced) {
            ls.add(minimumSliceRes);
            ls.add(minimumSliceInf);
            ls.add(sliceValue);
            ls.add(slicePlanetCount);
            ls.add(nucleusValue);
            ls.add(maxNucleusQualityDifference);
            ls.add(minimumRedTiles);
        }
        return ls;
    }

    
    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        List<Button> ls = new ArrayList<>(super.specialButtons());
        String toggleText = showAdvanced ? "Hide Advanced" : "Show Advanced";
        ls.add(Button.primary(idPrefix + "toggleAdvanced", toggleText));
        // TODO: Preset map and slices
        // TODO: Rich galaxy (rich nucleus? rich slices?)
        // TODO: Poor galaxy (poor nucleus? poor slices?)
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "toggleAdvanced" -> toggleAdvanced(event);
                    default -> null;
                };

        return (error == null ? "success" : error);
    }

    private String toggleAdvanced(GenericInteractionCreateEvent event) {
        showAdvanced = !showAdvanced;

        if(showAdvanced) {      
            String msg = "Be careful with these settings; turn a couple of these knobs, and a valid map may not be possible.";
            MessageHelper.sendMessageToEventChannel(event, msg);
        }

        return "success";
    }

    public void setDefaultsForTemplate(StringSelectInteractionEvent event, MapTemplateModel mapTemplateModel) {
        if (mapTemplateModel == null) {
            return;
        }
        // TODO
        // int players = mapTemplateModel.getPlayerCount();
        // int suggestWormholesMax = players;
        // nucleusWormholes.setVal(1);
        // nucleusWormholes.setMax(suggestWormholesMax);
        // int suggestWormholesMin = players - 1;
        // totalWormholes.setVal(suggestWormholesMin);
        // totalWormholes.setMax(suggestWormholesMax);
        // int suggestLegendariesMax = Math.round(players / 3.0f);
        // nucleusLegendaries.setVal(0);
        // nucleusLegendaries.setMax(suggestLegendariesMax);
        // totalLegendaries.setVal(1);
        // totalLegendaries.setMax(suggestLegendariesMax);
        // minimumRedTiles.setVal(SEAT_COUNT_TO_MINIMUM_RED_TILES.get(players));
    }

    // public String applyToDraftable(Draftable draftable, GenericInteractionCreateEvent event) {
    //     if(parent == null || !(parent instanceof DraftSystemSettings)) {
    //         return "Error: Could not find parent draft system settings.";
    //     }
    //     if(draftable == null || !(draftable instanceof SliceDraftable)) {
    //         return "Error: Could not find slice draftable to apply settings to.";
    //     }
    //     DraftSystemSettings draftSystemSettings = (DraftSystemSettings) parent;
    //     SliceDraftable sliceDraftable = (SliceDraftable) draftable;
    //     Game game = draftSystemSettings.getGame();
    //     if(game == null) {
    //         return "Error: Could not find game instance.";
    //     }

    //     int playerCount = draftSystemSettings.getGamePlayers().getKeys().size();
    //     MapTemplateModel mapTemplate = this.mapTemplate.getValue();
    //     if(mapTemplate == null || mapTemplate.getPlayerCount() != playerCount) {
    //         return "The selected map template "+mapTemplate.getAlias()+" is for a different number of players than " + playerCount;
    //     }


    //     NucleusSpecs specs = new NucleusSpecs(
    //         numSlices.getVal(),
    //         nucleusWormholes.getValLow(), // min nucleus wormholes
    //         nucleusWormholes.getValHigh(), // max nucleus wormholes
    //         nucleusLegendaries.getValLow(), // min nucleus legendaries
    //         nucleusLegendaries.getValHigh(), // max nucleus legendaries
    //         totalWormholes.getValLow(), //min map wormholes
    //         totalWormholes.getValHigh(), // max map wormholes
    //         totalLegendaries.getValLow(), // min map legendaries
    //         totalLegendaries.getValHigh(), // max map legendaries
    //         sliceValue.getValLow(), // min slice value
    //         sliceValue.getValHigh(), // max slice value
    //         nucleusValue.getValLow(), // min nucleus value
    //         nucleusValue.getValHigh(), // max nucleus value
    //         slicePlanetCount.getValLow(), // min slice planets
    //         slicePlanetCount.getValHigh(), // max slice planets
    //         minimumSliceRes.getVal(), // min slice resources
    //         minimumSliceInf.getVal(), // min slice influence
    //         maxNucleusQualityDifference.getVal(), // max nucleus quality difference
    //         SEAT_COUNT_TO_MINIMUM_RED_TILES.get(mapTemplate.getPlayerCount()) // expected red tiles
    //     );

    //     game.clearTileMap();
    //     // Very important...the distance tool needs hyperlane tiles placed to calculate adjacencies
    //     PartialMapService.placeFromTemplate(mapTemplate, game);

    //     // Create a draftable, AND setup the nucleus directly onto the map!
    //     // TODO: Does slice generation need its own executor?
    //     NucleusOutcome outcome = NucleusSliceGeneratorService.generateNucleusAndSlices(event, game, specs);
    //     if(outcome.failureReason() != null) {
    //         return "Could not generate nucleus and slices: " + outcome.failureReason();
    //     }

    //     game.setMapTemplateID(mapTemplate.getAlias());
    //     sliceDraftable.initialize(outcome.slices());
    //     return null;
    // }
}