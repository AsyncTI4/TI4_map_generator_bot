package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import ti4.commands.milty.MiltyDraftHelper;
import ti4.commands.milty.MiltyDraftSlice;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
public class SliceGenerationSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private IntegerSetting numSlices, numFactions;
    private BooleanSetting extraWorms;
    private IntegerSetting minimumRes, minimumInf;
    private IntegerRangeSetting totalValue, numLegends;

    // This is handled fully manually as there's a lot of validation to do
    private String presetSlices = null;
    private List<MiltyDraftSlice> parsedSlices;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public SliceGenerationSettings(Game game, JsonNode json, SettingsMenu parent) {
        super("slice", "Slice value settings", "Advanced settings to fine-tune how rich the galaxy will be", parent);

        // Initialize Settings to default values
        int players = game != null ? game.getPlayers().size() : 6;
        numSlices = new IntegerSetting("#Slices", "Number of Slices", players + 1, 2, 13, 1);
        numFactions = new IntegerSetting("#Factions", "Number of Factions", players + 1, 2, 24, 1);
        minimumRes = new IntegerSetting("MinRes", "Min Optimal Res", 2, 0, 4, 1);
        minimumInf = new IntegerSetting("MinInf", "Min Optimal Inf", 3, 0, 5, 1);
        totalValue = new IntegerRangeSetting("TotVal", "Total Optimal Value", 9, 0, 11, 13, 9, 20, 1);
        extraWorms = new BooleanSetting("ExtraWH", "More Wormholes", true);
        numLegends = new IntegerRangeSetting("Legends", "Legendary Count", 1, 0, 2, 2, 0, 20, 1);

        // Emojis
        minimumRes.setEmoji(Emojis.resources);
        minimumInf.setEmoji(Emojis.influence);
        totalValue.setEmoji(Emojis.ResInf);
        extraWorms.setEmoji(Emojis.WHalpha);
        numLegends.setEmoji(Emojis.LegendaryPlanet);

        // Other Initialization
        minimumRes.setExtraInfo("(this value does not account for flexibly spent planets (you may be used to those appearing as +0.5))");
        minimumInf.setExtraInfo("(this value does not account for flexibly spent planets (you may be used to those appearing as +0.5))");

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("sliceSettings")) json = json.get("sliceSettings");

        // Verify this is the correct JSON node and continue initialization
        List<String> historicIDs = new ArrayList<>(List.of("slice"));
        if (json != null && json.has("menuId") && historicIDs.contains(json.get("menuId").asText(""))) {
            numSlices.initialize(json.get("numSlices"));
            numFactions.initialize(json.get("numFactions"));
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
        List<SettingInterface> ls = new ArrayList<SettingInterface>();
        if (presetSlices != null) {
            return ls;
        }
        ls.add(numSlices);
        ls.add(numFactions);
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
        List<Button> ls = new ArrayList<>();
        ls.addAll(super.specialButtons());
        ls.add(Button.of(ButtonStyle.DANGER, idPrefix + "richPreset", "Rich galaxy", Emoji.fromFormatted(Emojis.tg)));
        ls.add(Button.of(ButtonStyle.DANGER, idPrefix + "poorPreset", "Poor galaxy", Emoji.fromFormatted(Emojis.comm)));
        ls.add(Button.of(ButtonStyle.SECONDARY, idPrefix + "presetSlices~MDL", "Use preset slices", Emoji.fromFormatted(Emojis.sliceA)));
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "richPreset" -> richGalaxy();
            case "poorPreset" -> poorGalaxy();
            case "presetSlices~MDL" -> getPresetSlicesFromUser(event);
            case "presetSlices" -> setPresetSlices(event);
            default -> null;
        };

        return (error == null ? "success" : error);
    }

    @Override
    public String shortSummaryString(boolean descrOnly) {
        StringBuilder sb = new StringBuilder("**__" + menuName + ":__**");
        if (presetSlices != null) {
            sb.append("\n> Using preset slices: ").append(presetSlices);
            return sb.toString();
        }
        return super.shortSummaryString(descrOnly);
    }

    @Override
    protected String resetSettings() {
        presetSlices = null;
        parsedSlices = null;
        return super.resetSettings();
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    private String richGalaxy() {
        numSlices.setVal(6);
        numFactions.setVal(6);
        minimumRes.setVal(4);
        minimumInf.setVal(5);
        totalValue.setValLow(11);
        totalValue.setValHigh(20);
        extraWorms.setVal(true);
        //oneWormholePerTypePerSlice.setVal(true);
        numLegends.setValLow(2);
        numLegends.setValHigh(20);
        return null;
    }

    private String poorGalaxy() {
        numSlices.setVal(6);
        numFactions.setVal(6);
        minimumRes.setVal(0);
        minimumInf.setVal(0);
        totalValue.setValLow(0);
        totalValue.setValHigh(9);
        extraWorms.setVal(false);
        //oneWormholePerTypePerSlice.setVal(false);
        numLegends.setValLow(0);
        numLegends.setValHigh(1);
        return null;
    }

    private String getPresetSlicesFromUser(GenericInteractionCreateEvent event) {
        String modalId = menuAction + "_" + navId() + "_presetSlices";
        TextInput ttsString = TextInput.create("sliceStrings", "TTS String", TextInputStyle.PARAGRAPH)
            .setPlaceholder("25,69,34,49,45;24,28,46,47,67;...")
            .setMinLength(1)
            .setRequired(true)
            .build();
        Modal modal = Modal.create(modalId, "Enter some stuff")
            .addActionRow(ttsString)
            .build();
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent.replyModal(modal).queue();
            return null;
        }
        return "Unknown Event";
    }

    private String setPresetSlices(GenericInteractionCreateEvent event) {
        if (event instanceof ModalInteractionEvent modalEvent) {
            String ttsString = modalEvent.getValue("sliceStrings").getAsString();
            presetSlices = ttsString;
            List<ComponentSource> sources = new ArrayList<>();
            int players = 6;
            if (parent instanceof MiltySettings mparent) {
                players = mparent.getPlayerSettings().getGamePlayers().getKeys().size();
                sources.addAll(mparent.getSourceSettings().getTileSources());
            }

            this.parsedSlices = MiltyDraftHelper.parseSlicesFromString(ttsString, sources);
            if (this.parsedSlices == null) {
                presetSlices = null;
                return "Invalid slice string";
            } else if (parsedSlices.size() < players) {
                presetSlices = null;
                parsedSlices = null;
                return "Not enough slices for the number of players.";
            }
            return null;
        }
        return "Unknown Event";
    }
}
