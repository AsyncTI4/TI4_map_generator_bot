package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;

// This is a sub-menu
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SliceGenerationSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    public IntegerSetting numSlices, numFactions;
    public BooleanSetting extraWorms;
    public IntegerSetting minimumRes, minimumInf;
    public IntegerRangeSetting totalValue, numLegends;
    public ChoiceSetting<MapTemplateModel> mapTemplate;

    // This is handled fully manually as there's a lot of validation to do
    public String presetSlices = null;
    public List<MiltyDraftSlice> parsedSlices;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public void finishInitialization(Game game, SettingsMenu parent) {
        this.menuId = "slice";
        this.menuName = "Slice value settings";
        this.description = "Advanced settings to fine-tune how rich the galaxy will be";

        // Initialize defaults, including any values loaded from JSON
        numSlices = new IntegerSetting("#Slices", "Number of Slices", 7, 2, 13, 1, numSlices);
        numFactions = new IntegerSetting("#Factions", "Number of Factions", 7, 2, 25, 1, numFactions);
        minimumRes = new IntegerSetting("MinRes", "Min Optimal Res", 2, 0, 4, 1, minimumRes);
        minimumInf = new IntegerSetting("MinInf", "Min Optimal Inf", 3, 0, 5, 1, minimumInf);
        totalValue = new IntegerRangeSetting("TotVal", "Total Optimal Value", 9, 0, 11, 13, 9, 20, 1, totalValue);
        extraWorms = new BooleanSetting("ExtraWH", "More Wormholes", true, extraWorms);
        numLegends = new IntegerRangeSetting("Legends", "Legendary Count", 1, 0, 2, 2, 0, 20, 1, numLegends);
        mapTemplate = new ChoiceSetting<>("MapLayout", "Map template", "6pStandard", mapTemplate);

        // Emojis and stuff
        minimumRes.setEmoji(Emojis.resources);
        minimumInf.setEmoji(Emojis.influence);
        totalValue.setEmoji(Emojis.ResInf);
        extraWorms.setEmoji(Emojis.WHalpha);
        numLegends.setEmoji(Emojis.LegendaryPlanet);
        minimumRes.setExtraInfo("(this value does not account for flexibly spent planets (you may be used to those appearing as +0.5))");
        minimumInf.setExtraInfo("(this value does not account for flexibly spent planets (you may be used to those appearing as +0.5))");

        Map<String, MapTemplateModel> mapTemplates = Mapper.getMapTemplates().stream().collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
        mapTemplate.setAllValues(mapTemplates);
        mapTemplate.setShow(MapTemplateModel::getAlias);

        super.finishInitialization(game, parent);
    }

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
        numSlices.val = 6;
        numFactions.val = 6;
        minimumRes.val = 4;
        minimumInf.val = 5;
        totalValue.valLow = 11;
        totalValue.valHigh = 20;
        extraWorms.val = true;
        //oneWormholePerTypePerSlice.val = true;
        numLegends.valLow = 2;
        numLegends.valHigh = 20;
        return null;
    }

    private String poorGalaxy() {
        numSlices.val = 6;
        numFactions.val = 6;
        minimumRes.val = 0;
        minimumInf.val = 0;
        totalValue.valLow = 0;
        totalValue.valHigh = 9;
        extraWorms.val = false;
        //oneWormholePerTypePerSlice.val = false;
        numLegends.valLow = 0;
        numLegends.valHigh = 1;
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
