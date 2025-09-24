package ti4.helpers.settingsFramework.menus;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.map.Game;
import ti4.model.MapTemplateModel;

public class NucleusSliceDraftableSettings extends SettingsMenu {

    // Primary setting
    private final IntegerSetting numSlices;
    private final ChoiceSetting<MapTemplateModel> mapTemplate;

    // Depends on map template / player count
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
    private final IntegerRangeSetting totalValue;

    private final static Map<Integer, Integer> SEAT_COUNT_TO_MINIMUM_RED_TILES = Map.of(
            3, 6,
            4, 7,
            5, 9,
            6, 11,
            7, 13,
            8, 15);

    public NucleusSliceDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super("nucleus", "Nucleus & Slice value settings", "Advanced settings for map features and slice quality", parent);

        
    }

    private void initializeForDraftSystem(DraftSystemSettings draftSystemSettings) {
        int playerCount = parent.getGamePlayers().getKeys().size();
    }
    
}

/*
        if (minSliceSpend < 4) {
            return "A player slice has less than 4 total optimal spend.";
        }
        if (maxSliceSpend > 9) {
            return "A player slice has more than 9 total optimal spend.";
        }
        if (minSlicePlanets < 2) {
            return "A player slice has less than 2 planets.";
        }
        if (maxSlicePlanets > 5) {
            return "A player slice has more than 5 planets.";
        }
        if (totalRedTiles < expectedRedTiles) {
            return "The map has less than the expected " + expectedRedTiles + " red/anomaly tiles total.";
        }
        if (minCoreSpend < 4) {
            return "A core slice has less than 4 total optimal spend.";
        }
        if (maxCoreSpend > 8) {
            return "A core slice has more than 8 total optimal spend.";
        }
        if (coreSliceBalance > 3) {
            return "The core slices are imbalanced by more than 3 total optimal spend.";
        }
 */