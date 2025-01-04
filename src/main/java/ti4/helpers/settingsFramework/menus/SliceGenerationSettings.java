package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.IntegerRangeSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftSlice;

// This is a sub-menu
@Getter
@JsonIgnoreProperties({ "messageId" })
public class SliceGenerationSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final IntegerSetting numSlices;
    private final IntegerSetting numFactions;
    private final BooleanSetting extraWorms;
    private final IntegerSetting minimumRes;
    private final IntegerSetting minimumInf;
    private final IntegerRangeSetting totalValue;
    private final IntegerRangeSetting numLegends;

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
        minimumRes.setEmoji(MiscEmojis.resources);
        minimumInf.setEmoji(MiscEmojis.influence);
        totalValue.setEmoji(MiscEmojis.ResInf);
        extraWorms.setEmoji(MiscEmojis.WHalpha);
        numLegends.setEmoji(MiscEmojis.LegendaryPlanet);

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
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(numFactions);
        if (presetSlices != null) {
            return ls;
        }
        ls.add(numSlices);
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
        ls.add(Buttons.gray(idPrefix + "scpt2025quals", "SCPT 2025 Qualifiers", "<:scpt:1289722139750039634>"));
        ls.add(Buttons.gray(idPrefix + "scpt2025prelim", "SCPT 2025 Prelims", "<:scpt:1289722139750039634>"));
        ls.add(Buttons.red(idPrefix + "richPreset", "Rich galaxy", MiscEmojis.tg));
        ls.add(Buttons.red(idPrefix + "poorPreset", "Poor galaxy", MiscEmojis.comm));
        ls.add(Buttons.blue(idPrefix + "presetSlices~MDL", "Use preset slices", MiltyDraftEmojis.sliceA));
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "scpt2025quals" -> scpt2025quals();
            case "scpt2025prelim" -> scpt2025prelim();
            case "richPreset" -> richGalaxy();
            case "poorPreset" -> poorGalaxy();
            case "presetSlices~MDL" -> getPresetSlicesFromUser(event);
            case "presetSlices" -> setPresetSlices(event);
            default -> null;
        };

        return (error == null ? "success" : error);
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        for (String line : description)
            sb.append("\n- *").append(line).append("*");
        sb.append("\n");

        int pad = enabledSettings().stream().map(x -> x.getName().length()).max(Comparator.comparingInt(x -> x)).orElse(15);
        for (SettingInterface setting : enabledSettings()) {
            sb.append("> ");
            sb.append(setting.longSummary(pad, lastSettingTouched));
            sb.append("\n");
        }
        if (presetSlices != null) sb.append("> Using preset slices: ").append(presetSlices).append("\n");
        if (!enabledSettings().isEmpty()) sb.append("\n"); // extra line for formatting

        if (!categories().isEmpty()) {
            List<String> catStrings = new ArrayList<>();
            for (SettingsMenu cat : categories()) {
                catStrings.add(cat.shortSummaryString(false));
            }
            String catStr = String.join("\n\n", catStrings);
            if (sb.length() + catStr.length() > 1999) {
                List<String> shorterCatStrings = new ArrayList<>();
                for (SettingsMenu cat : categories()) {
                    shorterCatStrings.add(cat.shortSummaryString(true));
                }
                catStr = String.join("\n\n", shorterCatStrings);
                if (sb.length() + catStr.length() > 1999) catStr = ""; //give up
            }
            sb.append(catStr);
        }
        return sb.toString();
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
    private String scpt2025quals() {
        int players = 6;
        if (getParent() instanceof MiltySettings ms) {
            players = ms.getPlayerSettings().getGamePlayers().getKeys().size();
        }
        //27,73,47,44,26;30,39,76,80,65;79,37,50,71,66;42,64,75,72,49;34,41,70,78,25;40,20,36,45,74
        numSlices.setVal(players);
        numFactions.setVal(players);
        List<String> slices = new ArrayList<>(List.of("27,73,47,44,26", "30,39,76,80,65", "79,37,50,71,66", "42,64,75,72,49", "34,41,70,78,25", "40,20,36,45,74"));
        Collections.shuffle(slices);
        for (int i = players; i < 6; i++)
            slices.removeFirst();
        String ttsString = String.join("|", slices);
        return setPresetSlices(ttsString);
    }

    private String scpt2025prelim() {
        int players = 6;
        numSlices.setVal(players);
        numFactions.setVal(players);
        

        List<List<String>> allSlices = new ArrayList<>();
        allSlices.add(new ArrayList<>(List.of("30,72,49,79,59", "29,66,50,80,31", "70,36,40,67,63", "73,76,48,45,26", "74,69,47,41,61", "37,65,46,68,64")));
        allSlices.add(new ArrayList<>(List.of("28,19,25,43,47", "34,77,36,41,64", "37,60,39,50,67", "42,75,78,59,24", "76,66,40,62,44", "68,73,79,20,65", "46,71,63,31,26")));
        allSlices.add(new ArrayList<>(List.of("63,40,72,46,68", "45,64,34,62,49", "36,25,24,50,41", "48,22,66,79,32", "39,61,59,43,71", "42,26,73,78,21", "47,70,65,44,19")));
        allSlices.add(new ArrayList<>(List.of("33,62,41,25,32", "44,36,19,40,72", "45,70,35,64,78", "50,74,65,26,63", "69,21,23,79,49", "38,59,42,39,24")));
        
        Collections.shuffle(allSlices);
        List<String> slices = allSlices.getFirst();
        Collections.shuffle(slices);
        while (slices.size() > 6) {
            slices.removeFirst();
        }
        String tts = String.join("|", slices);
        return setPresetSlices(tts);
    }

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
            String slices = modalEvent.getValue("sliceStrings").getAsString();
            return setPresetSlices(slices);
        }
        return "Unknown Event";
    }

    private String setPresetSlices(String sliceString) {
        List<ComponentSource> sources = new ArrayList<>();
        int players = 6;
        presetSlices = sliceString;
        if (parent instanceof MiltySettings mparent) {
            players = mparent.getPlayerSettings().getGamePlayers().getKeys().size();
            sources.addAll(mparent.getSourceSettings().getTileSources());
        }

        this.parsedSlices = MiltyDraftHelper.parseSlicesFromString(sliceString, sources);
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
}
