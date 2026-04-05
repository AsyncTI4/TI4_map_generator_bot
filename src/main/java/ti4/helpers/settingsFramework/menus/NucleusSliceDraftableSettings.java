package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TileEmojis;
import ti4.service.map.MapStringMapper;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftSlice;
import tools.jackson.databind.JsonNode;

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

    private boolean showAdvanced;

    // Preset slices and map
    private String presetSlices;
    private String presetMapString;

    @JsonIgnore
    private List<MiltyDraftSlice> parsedSlices;

    @JsonIgnore
    private Map<String, String> parsedMapTiles;

    private static final String MENU_ID = "nucleusSlice";

    public NucleusSliceDraftableSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(
                MENU_ID,
                "Nucleus & Slice value settings",
                "Advanced settings for map features and slice quality.",
                parent);

        // Initialize settings
        int players = game != null ? game.getPlayers().size() : 6;
        int suggestWormholesMax = Math.round(players / 2.0f);
        nucleusWormholes =
                new IntegerRangeSetting("NucleusWH", "Nucleus Wormholes", 0, 0, 3, suggestWormholesMax, 0, 3, 1);
        int suggestWormholesMin = 2;
        totalWormholes = new IntegerRangeSetting(
                "TotalWH", "Max Wormholes", suggestWormholesMin, 0, 3, suggestWormholesMax, 0, 3, 1);
        int suggestLegendariesMax = Math.round(players / 2.0f);
        nucleusLegendaries =
                new IntegerRangeSetting("NucleusLeg", "Nucleus Legendaries", 1, 0, 3, suggestLegendariesMax, 0, 3, 1);
        int suggestLegendariesMin = Math.min(2, suggestLegendariesMax);
        totalLegendaries = new IntegerRangeSetting(
                "TotalLeg", "Total Legendaries", suggestLegendariesMin, 0, 5, suggestLegendariesMax, 0, 5, 1);
        minimumSliceRes = new IntegerSetting("MinSliceRes", "Min Slice Resources", 0, 0, 5, 1);
        minimumSliceInf = new IntegerSetting("MinSliceInf", "Min Slice Influence", 0, 0, 5, 1);
        sliceValue = new IntegerRangeSetting("SliceVal", "Slice Optimal Value", 4, 0, 8, 9, 3, 12, 1);
        slicePlanetCount = new IntegerRangeSetting("SlicePlanets", "Slice Planet Count", 2, 0, 7, 5, 0, 7, 1);
        nucleusValue = new IntegerRangeSetting("NucleusVal", "Nucleus Optimal Value", 4, 0, 8, 8, 3, 12, 1);
        maxNucleusQualityDifference = new IntegerSetting("MaxNucDiff", "Max Nucleus Quality Diff", 3, 0, 10, 1);
        int minRedTiles = Math.min(20, Math.max(Math.round((11.0f / 6.0f) * players), 0));
        minimumRedTiles = new IntegerSetting("MinRed", "Minimum Red Tiles", minRedTiles, 0, 20, 1);

        // Add extra info
        nucleusWormholes.setExtraInfo("Applies to each type of wormhole separately.");
        totalWormholes.setExtraInfo("Applies to each type of wormhole separately.");
        minimumRedTiles.setExtraInfo("Across all slices and the Nucleus");

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

        if (json.has("presetSlices")) {
            setPresetSlices(json.get("presetSlices").asText(null));
        }
        if (json.has("presetMapString")) {
            setPresetMapString(json.get("presetMapString").asText(null));
        }
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        // If both presets are set, hide all generation settings
        if (presetSlices != null && presetMapString != null) {
            return ls;
        }
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

        if (showAdvanced && event instanceof ButtonInteractionEvent buttonEvent) {
            String msg =
                    "Be careful with these settings. Nucleus + Slice generation is complex; an impossible configuration may not be obvious.";
            MessageHelper.sendEphemeralMessageToEventChannel(buttonEvent, msg);
        }

        return "success";
    }

    public String setPresetSlices(String sliceString) {
        if (sliceString == null || sliceString.isEmpty()) {
            presetSlices = null;
            parsedSlices = null;
            return null;
        }

        List<ComponentSource> sources = new ArrayList<>();
        int players = 6;
        presetSlices = sliceString;
        if (parent instanceof SliceDraftableSettings sdParent
                && sdParent.getParent() instanceof DraftSystemSettings dparent) {
            players = dparent.getPlayerUserIds().size();
            sources.addAll(dparent.getSourceSettings().getTileSources());
        }

        String error = null;
        try {
            parsedSlices = MiltyDraftHelper.parseSlices(sliceString, sources);
        } catch (Exception e) {
            error = e.getMessage();
        }
        if (parsedSlices == null || error != null) {
            presetSlices = null;
            if (error != null) return error;
            return "Invalid slice string";
        } else if (parsedSlices.size() < players) {
            presetSlices = null;
            parsedSlices = null;
            return "Not enough slices for the number of players.";
        }
        return null;
    }

    public String setPresetMapString(String mapString) {
        if (mapString == null || mapString.isEmpty()) {
            presetMapString = null;
            parsedMapTiles = null;
            return null;
        }

        // Normalize the map string (replace newlines and commas with spaces)
        String normalizedMapString = mapString
                .replace("\n", " ")
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .trim();
        presetMapString = normalizedMapString;

        // Parse using MapStringMapper
        parsedMapTiles = MapStringMapper.getMappedTilesToPosition(normalizedMapString, null);

        if (parsedMapTiles.isEmpty()) {
            presetMapString = null;
            parsedMapTiles = null;
            return "Could not parse map string";
        }

        // Validate all non "-1" tile IDs
        int numSliceTiles = 0;
        int numPlayers = 6;
        if (parent instanceof SliceDraftableSettings sdParent
                && sdParent.getParent() instanceof DraftSystemSettings dparent) {
            numPlayers = dparent.getPlayerUserIds().size();
        }
        for (Map.Entry<String, String> entry : parsedMapTiles.entrySet()) {
            String tileId = entry.getValue();
            // Skip -1 positions (reserved for player slices)
            if ("-1".equals(tileId)) {
                numSliceTiles++;
                continue;
            }
            // Validate tile exists
            if (!TileHelper.isValidTile(tileId)) {
                presetMapString = null;
                parsedMapTiles = null;
                return "Invalid tile ID at position " + entry.getKey() + ": " + tileId;
            }
        }
        if (numSliceTiles / numPlayers != 3) {
            return "Invalid number of slice tiles in map string; required numberOfPlayers × 3";
        }

        return null;
    }

    @Override
    protected String resetSettings() {
        presetSlices = null;
        parsedSlices = null;
        presetMapString = null;
        parsedMapTiles = null;
        return super.resetSettings();
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
        int minRedTiles = Math.min(20, Math.max(Math.round((11.0f / 6.0f) * players), 0));
        minimumRedTiles.setVal(minRedTiles);
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
