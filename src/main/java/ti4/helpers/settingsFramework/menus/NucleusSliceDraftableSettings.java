package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
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

    private static final Map<Integer, Integer> SEAT_COUNT_TO_MINIMUM_RED_TILES = Map.of(
            3, 6,
            4, 7,
            5, 9,
            6, 11,
            7, 13,
            8, 15);

    private static final String MENU_ID = "nucleusSlice";

    public NucleusSliceDraftableSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(
                MENU_ID,
                "Nucleus & Slice value settings",
                "Advanced settings for map features and slice quality.",
                parent);
        this.description.add("Big changes here can cause the nucleus generation to fail.");

        // Initialize settings
        int players = game != null ? game.getPlayers().size() : 6;
        int suggestWormholesMax = Math.round(players / 2.0f);
        nucleusWormholes =
                new IntegerRangeSetting("NucleusWH", "Nucleus Wormholes", 0, 0, 3, suggestWormholesMax, 0, 3, 1);
        int suggestWormholesMin = 2;
        totalWormholes = new IntegerRangeSetting(
                "TotalWH", "Max Wormholes", suggestWormholesMin, 0, 3, suggestWormholesMax, 0, 3, 1);
        int suggestLegendariesMax = Math.round(players / 3.0f);
        nucleusLegendaries =
                new IntegerRangeSetting("NucleusLeg", "Nucleus Legendaries", 0, 0, 3, suggestLegendariesMax, 0, 3, 1);
        totalLegendaries =
                new IntegerRangeSetting("TotalLeg", "Total Legendaries", 1, 0, 5, suggestLegendariesMax, 0, 5, 1);
        minimumSliceRes = new IntegerSetting("MinSliceRes", "Min Slice Resources", 0, 0, 5, 1);
        minimumSliceInf = new IntegerSetting("MinSliceInf", "Min Slice Influence", 0, 0, 5, 1);
        sliceValue = new IntegerRangeSetting("SliceVal", "Slice Optimal Value", 4, 0, 8, 9, 3, 12, 1);
        slicePlanetCount = new IntegerRangeSetting("SlicePlanets", "Slice Planet Count", 2, 0, 7, 5, 0, 7, 1);
        nucleusValue = new IntegerRangeSetting("NucleusVal", "Nucleus Optimal Value", 4, 0, 8, 8, 3, 12, 1);
        maxNucleusQualityDifference = new IntegerSetting("MaxNucDiff", "Max Nucleus Quality Diff", 3, 0, 10, 1);
        minimumRedTiles = new IntegerSetting(
                "MinRed", "Minimum Red Tiles", SEAT_COUNT_TO_MINIMUM_RED_TILES.get(players), 0, 20, 1);

        // Add extra info
        minimumSliceRes.setExtraInfo(
                "Defaults to 0. A low res slice can be combined with a specific seat to balance out.");
        minimumSliceInf.setExtraInfo(
                "Defaults to 0. A low inf slice can be combined with a specific seat to balance out.");
        nucleusWormholes.setExtraInfo("Applies to each type of wormhole separately.");
        totalWormholes.setExtraInfo("Applies to each type of wormhole separately.");

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
        if (json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText(""))) {
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
        showAdvanced =
                json.get("showAdvanced") != null && json.get("showAdvanced").asBoolean(false);
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(nucleusWormholes);
        ls.add(totalWormholes);
        ls.add(nucleusLegendaries);
        ls.add(totalLegendaries);
        if (showAdvanced) {
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
        // TODO: Presets
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

        if (showAdvanced) {
            String msg =
                    "Be careful with these settings; turn a couple of these knobs, and a valid map may not be possible.";
            MessageHelper.sendMessageToEventChannel(event, msg);
        }

        return "success";
    }

    public void setDefaultsForTemplate(StringSelectInteractionEvent event, MapTemplateModel mapTemplateModel) {
        if (mapTemplateModel == null) {
            return;
        }
        int players = mapTemplateModel.getPlayerCount();
        int suggestWormholesMax = players;
        nucleusWormholes.setValLow(1);
        nucleusWormholes.setValHigh(suggestWormholesMax);
        int suggestWormholesMin = players - 1;
        totalWormholes.setValLow(suggestWormholesMin);
        totalWormholes.setValHigh(suggestWormholesMax);
        int suggestLegendariesMax = Math.round(players / 3.0f);
        nucleusLegendaries.setValLow(0);
        nucleusLegendaries.setValHigh(suggestLegendariesMax);
        totalLegendaries.setValLow(1);
        totalLegendaries.setValHigh(suggestLegendariesMax);
        minimumRedTiles.setVal(SEAT_COUNT_TO_MINIMUM_RED_TILES.get(players));
        minimumSliceRes.setVal(0);
        minimumSliceInf.setVal(0);
        int bpp = mapTemplateModel.bluePerPlayer();

        // Scale slice values from 2bpp using default values
        sliceValue.setValLow(bpp * 2); // 2 * 2 = 4
        sliceValue.setValHigh(Math.round(bpp * 4.5f)); // 2 * 4.5 = 9
        slicePlanetCount.setValLow(bpp); // 2 * 1 = 2
        slicePlanetCount.setValHigh(Math.round(bpp * 2.5f)); // 2 * 2.5 = 5
        nucleusValue.setValLow(bpp * 2); // 2 * 2 = 4
        nucleusValue.setValHigh(bpp * 4); // 2 * 4 = 8
        maxNucleusQualityDifference.setVal(3);
    }
}
