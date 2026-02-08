package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.emoji.MiscEmojis;

// This is a sub-menu
@Getter
@JsonIgnoreProperties("messageId")
public class MiltySliceDraftableSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final BooleanSetting extraWorms;
    private final IntegerSetting minimumRes;
    private final IntegerSetting minimumInf;
    private final IntegerRangeSetting totalValue;
    private final IntegerRangeSetting numLegends;

    private static final String MENU_ID = "miltySlice";

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public MiltySliceDraftableSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Draft settings", "Advanced settings to fine-tune how rich the galaxy will be", parent);

        // Initialize Settings to default values
        minimumRes = new IntegerSetting("MinRes", "Min Optimal Res", 2, 0, 4, 1);
        minimumInf = new IntegerSetting("MinInf", "Min Optimal Inf", 3, 0, 5, 1);
        totalValue = new IntegerRangeSetting("TotVal", "Total Optimal Value", 9, 0, 11, 13, 9, 20, 1);
        extraWorms = new BooleanSetting("ExtraWH", "More Wormholes", true);
        numLegends = new IntegerRangeSetting("Legends", "Legendary Count", 1, 0, 2, 2, 0, 20, 1);

        // Emojis
        minimumRes.setEmoji(MiscEmojis.resources);
        minimumInf.setEmoji(MiscEmojis.influence);
        totalValue.setEmoji(MiscEmojis.ResInf);
        extraWorms.setEmoji(MiscEmojis.WHalpha);
        numLegends.setEmoji(MiscEmojis.LegendaryPlanet);

        // Other Initialization
        minimumRes.setExtraInfo(
                "(this value does not account for flexibly spent planets - you may be used to those appearing as +0.5)");
        minimumInf.setExtraInfo(
                "(this value does not account for flexibly spent planets - you may be used to those appearing as +0.5)");

        // Verify this is the correct JSON node and continue initialization
        if (json != null
                && json.has("menuId")
                && MENU_ID.equals(json.get("menuId").asText(""))) {
            minimumRes.initialize(json.get("minimumRes"));
            minimumInf.initialize(json.get("minimumInf"));
            totalValue.initialize(json.get("totalValue"));
            extraWorms.initialize(json.get("extraWorms"));
            numLegends.initialize(json.get("numLegends"));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(minimumRes);
        ls.add(minimumInf);
        ls.add(totalValue);
        ls.add(extraWorms);
        ls.add(numLegends);
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        List<Button> ls = new ArrayList<>(super.specialButtons());
        ls.add(Buttons.gray(idPrefix + "scpt2025finals", "SCPT 2025 Finals", "<:scpt:1289722139750039634>"));
        if (parent instanceof SliceDraftableSettings sds && sds.getPresetSlices() == null) {
            ls.add(Buttons.red(idPrefix + "richPreset", "Rich galaxy", MiscEmojis.tg));
            ls.add(Buttons.red(idPrefix + "poorPreset", "Poor galaxy", MiscEmojis.comm));
        }
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "scpt2025finals" -> scpt2025finals(event);
                    case "richPreset" -> richGalaxy();
                    case "poorPreset" -> poorGalaxy();
                    default -> null;
                };

        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    private String scpt2025finals(GenericInteractionCreateEvent event) {
        Game game = null;
        if (getParent() instanceof MiltySettings ms) {
            game = ms.getGame();
            ms.getGameSettings().getMapTemplate().setChosenKey("2025scptFinals");
            List<String> factions = new ArrayList<>(List.of("sol", "xxcha", "jolnar", "keleresm", "ghost", "naalu"));
            ms.getPlayerSettings().getBanFactions().setKeys(Collections.emptyList());
            ms.getPlayerSettings().getPriFactions().setKeys(factions);
        }
        List<String> slices = new ArrayList<>(List.of(
                "64,22,45,75,70",
                "74,68,40,60,21",
                "62,39,67,72,38",
                "42,27,34,26,50",
                "41,30,29,80,63",
                "31,25,79,76,78"));
        String ttsString = String.join("|", slices);
        if (game != null) {
            String msg = "Howdy " + game.getPing()
                    + ",\nThis map is WEIRD!!!\nPlease note that the map template was updated to `2025scptFinals`, ";
            msg += "and the faction list was set to a default of: Sol, Xxcha, Jol Nar, Keleres, Creuss, and Naalu.";
            msg +=
                    "\nIf you wish to play instead on a normal map, or with different factions, feel free to edit those settings accordingly.";
            msg += "\nHave fun :)";
            MessageHelper.sendMessageToEventChannel(event, msg);
        }
        if (parent instanceof SliceDraftableSettings sds) {
            sds.getNumSlices().setVal(6);
            return sds.setPresetSlices(ttsString);
        }
        return "Unknown Event";
    }

    private String richGalaxy() {
        if (parent instanceof SliceDraftableSettings sds) {
            sds.getNumSlices().setVal(6);
        }
        minimumRes.setVal(4);
        minimumInf.setVal(5);
        totalValue.setValLow(11);
        totalValue.setValHigh(20);
        extraWorms.setVal(true);
        numLegends.setValLow(2);
        numLegends.setValHigh(20);
        return null;
    }

    private String poorGalaxy() {
        if (parent instanceof SliceDraftableSettings sds) {
            sds.getNumSlices().setVal(6);
        }
        minimumRes.setVal(0);
        minimumInf.setVal(0);
        totalValue.setValLow(0);
        totalValue.setValHigh(9);
        extraWorms.setVal(false);
        numLegends.setValLow(0);
        numLegends.setValHigh(1);
        return null;
    }

    public void setDefaultsForTemplate(StringSelectInteractionEvent event, MapTemplateModel mapTemplateModel) {
        if (mapTemplateModel == null) {
            return;
        }

        int bpp = mapTemplateModel.bluePerPlayer();
        if (bpp == 3) {
            minimumRes.setVal(2);
            minimumInf.setVal(3);
            totalValue.setValLow(9);
            totalValue.setValHigh(13);
        } else if (bpp == 2) {
            minimumRes.setVal(1);
            minimumInf.setVal(1);
            totalValue.setValLow(5);
            totalValue.setValHigh(8);
        } else if (bpp == 1) {
            minimumRes.setVal(0);
            minimumInf.setVal(0);
            totalValue.setValLow(0);
            totalValue.setValHigh(6);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "The number of blue tiles per player in the map template changed, your slice settings have been reset to accomodate the change.");
    }
}
