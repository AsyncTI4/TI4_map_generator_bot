package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftSlice;

@Getter
@JsonIgnoreProperties("messageId")
public class SliceDraftableSettings extends SettingsMenu {

    // TODO: Pre-made maps would be cool.
    public enum MapGenerationMode {
        Milty,
        Nucleus
    }

    // Setting
    private final IntegerSetting numSlices;
    private final ChoiceSetting<MapTemplateModel> mapTemplate;
    private final ChoiceSetting<MapGenerationMode> mapGenerationMode;
    // Categories
    private final NucleusSliceDraftableSettings nucleusSettings;
    private final MiltySliceDraftableSettings miltySettings;
    // This is handled fully manually as there's a lot of validation to do
    private String presetSlices;

    @JsonIgnore
    private List<MiltyDraftSlice> parsedSlices;

    private int bpp;

    private static final String MENU_ID = "dsSlice";

    public SliceDraftableSettings(Game game, JsonNode json, DraftSystemSettings parent) {
        super(MENU_ID, "Slice Settings", "Basic Slice draft setup.", parent);

        // Initialize settings
        int players = game != null ? game.getPlayers().size() : 6;
        numSlices = new IntegerSetting("#Slices", "Number of Slices", players + 1, 2, 13, 1);
        mapTemplate = new ChoiceSetting<>("Template", "Map Template", "6pStandard");
        mapGenerationMode = new ChoiceSetting<>("MapGenMode", "Map Generation Mode", "Milty");

        if (isNucleusMode()) {
            MapTemplateModel defaultNucleus = Mapper.getDefaultNucleusTemplate(players);
            mapTemplate.setDefaultKey(defaultNucleus.getAlias());
            mapTemplate.setChosenKey(defaultNucleus.getAlias());
        } else {
            MapTemplateModel defaultStandard = Mapper.getDefaultMapTemplateForPlayerCount(players);
            mapTemplate.setDefaultKey(defaultStandard.getAlias());
            mapTemplate.setChosenKey(defaultStandard.getAlias());
        }
        // For initialization, load all templates unfiltered. This helps the default/chosen key be found.
        // It will be filtered properly before offering choices in updateTransientSettings()
        mapTemplate.setAllValues(
                Mapper.getMapTemplates().stream().collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x)));

        mapTemplate.setShow(MapTemplateModel::getAlias);
        mapTemplate.setGetExtraInfo(MapTemplateModel::getDescr);
        mapGenerationMode.setShow(MapGenerationMode::toString);
        mapGenerationMode.setAllValues(
                Map.of("Milty", MapGenerationMode.Milty, "Nucleus", MapGenerationMode.Nucleus), "Milty");

        // Emojis
        mapTemplate.setEmoji(MiltyDraftEmojis.sliceA);

        // Load JSON if applicable
        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText("")))) {
            numSlices.initialize(json.get("numSlices"));
            mapTemplate.initialize(json.get("mapTemplate"));
            mapGenerationMode.initialize(json.get("mapGenerationMode"));

            if (isNucleusMode()) {
                MapTemplateModel defaultNucleus = Mapper.getDefaultNucleusTemplate(players);
                mapTemplate.setDefaultKey(defaultNucleus.getAlias());
            } else {
                MapTemplateModel defaultStandard = Mapper.getDefaultMapTemplateForPlayerCount(players);
                mapTemplate.setDefaultKey(defaultStandard.getAlias());
            }

            if (json.has("presetSlices")) {
                setPresetSlices(json.get("presetSlices").asText(null));
            }
        }

        bpp = mapTemplate.getValue().bluePerPlayer();

        // Initialize sub-menus
        nucleusSettings =
                new NucleusSliceDraftableSettings(game, json != null ? json.get("nucleusSettings") : null, this);
        miltySettings = new MiltySliceDraftableSettings(game, json != null ? json.get("miltySettings") : null, this);
    }

    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(numSlices);
        ls.add(mapTemplate);
        ls.add(mapGenerationMode);
        if (presetSlices != null) {
            return ls;
        }
        ls.addAll(getCurrentModeSettings().settings());
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        List<Button> ls = new ArrayList<>(super.specialButtons());
        List<Button> proxiedButtons = getCurrentModeSettings().specialButtons();
        for (Button b : proxiedButtons) {
            String customId = b.getCustomId();
            String[] idParts = customId.split("_");
            if (idParts.length < 3) continue;
            String proxyId = idPrefix + idParts[2];
            ls.add(b.withCustomId(proxyId));
        }
        if (!isNucleusMode()) {
            ls.add(Buttons.blue(idPrefix + "presetSlices~MDL", "Use preset slices", MiltyDraftEmojis.sliceA));
        }
        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "presetSlices~MDL" -> getPresetSlicesFromUser(event);
                    case "presetSlices" -> setPresetSlices(event);
                    default -> getCurrentModeSettings().handleSpecialButtonAction(event, action);
                };

        if (action.startsWith("changeTemplate_") && event instanceof StringSelectInteractionEvent sEvent) {
            afterChangeMapTemplateHandler(sEvent);
        }

        return (error == null ? "success" : error);
    }

    @Override
    public String menuSummaryString(String lastSettingTouched) {
        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        for (String line : description) sb.append("\n- *").append(line).append("*");
        sb.append("\n");

        int pad = enabledSettings().stream()
                .map(x -> x.getName().length())
                .max(Comparator.comparingInt(x -> x))
                .orElse(15);
        for (SettingInterface setting : enabledSettings()) {
            sb.append("> ");
            sb.append(setting.longSummary(pad, lastSettingTouched));
            sb.append("\n");
        }
        if (presetSlices != null)
            sb.append("> Using preset slices: ").append(presetSlices).append("\n");
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
                if (sb.length() + catStr.length() > 1999) catStr = ""; // give up
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

    @Override
    protected void updateTransientSettings() {
        if (parent instanceof DraftSystemSettings dss) {
            int players = dss.getPlayerUserIds().size();
            Map<String, MapTemplateModel> allowed = Mapper.getMapTemplatesForPlayerCount(players).stream()
                    .filter(getNucleusTemplatePredicate())
                    .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
            MapTemplateModel defaultTemplate = null;
            if (isNucleusMode()) {
                defaultTemplate = Mapper.getDefaultNucleusTemplate(players);
            } else {
                defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
            }
            if (defaultTemplate != null) {
                mapTemplate.setAllValues(allowed, defaultTemplate.getAlias());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Helper functions
    // ---------------------------------------------------------------------------------------------------------------------------------

    private void afterChangeMapTemplateHandler(StringSelectInteractionEvent event) {
        FileUpload preview = null;
        if (parent != null && parent instanceof DraftSystemSettings dsparent)
            preview = MapTemplateHelper.generateTemplatePreviewImage(event, dsparent.getGame(), mapTemplate.getValue());
        if (preview != null)
            event.getHook()
                    .sendMessage("Here is a preview of the selected map template:")
                    .addFiles(preview)
                    .setEphemeral(true)
                    .queue();
        if (mapTemplate.getValue() != null && mapTemplate.getValue().bluePerPlayer() != bpp) {
            if (isNucleusMode()) {
                nucleusSettings.setDefaultsForTemplate(event, mapTemplate.getValue());
            } else {
                miltySettings.setDefaultsForTemplate(event, mapTemplate.getValue());
            }
        }
    }

    private Predicate<MapTemplateModel> getNucleusTemplatePredicate() {
        if (isNucleusMode()) {
            return (t -> t.isNucleusTemplate());
        }
        return (t -> !t.isNucleusTemplate());
    }

    private SettingsMenu getCurrentModeSettings() {
        return isNucleusMode() ? nucleusSettings : miltySettings;
    }

    private String getPresetSlicesFromUser(GenericInteractionCreateEvent event) {
        if (isNucleusMode()) {
            return "Preset slices are only available in Milty mode.";
        }
        String modalId = menuAction + "_" + navId() + "_presetSlices";
        TextInput ttsString = TextInput.create("sliceStrings", TextInputStyle.PARAGRAPH)
                .setPlaceholder("25,69,34,49,45;24,28,46,47,67;...")
                .setMinLength(1)
                .setRequired(true)
                .build();
        Modal modal = Modal.create(modalId, "Enter some stuff")
                .addComponents(Label.of("TTS String", ttsString))
                .build();
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent.replyModal(modal).queue();
            return null;
        }
        return "Unknown Event";
    }

    private String setPresetSlices(GenericInteractionCreateEvent event) {
        if (isNucleusMode()) {
            return "Preset slices are only available in Milty mode.";
        }
        if (event instanceof ModalInteractionEvent modalEvent) {
            String slices = modalEvent.getValue("sliceStrings").getAsString();
            return setPresetSlices(slices);
        }
        return "Unknown Event";
    }

    public String setPresetSlices(String sliceString) {
        if (sliceString == null || sliceString.isEmpty()) {
            return null;
        }

        if (isNucleusMode()) {
            return "Preset slices are only available in Milty mode.";
        }

        List<ComponentSource> sources = new ArrayList<>();
        int players = 6;
        presetSlices = sliceString;
        if (parent instanceof DraftSystemSettings dparent) {
            players = dparent.getPlayerUserIds().size();
            sources.addAll(dparent.getSourceSettings().getTileSources());
        }

        parsedSlices = MiltyDraftHelper.parseSlicesFromString(sliceString, sources);
        if (parsedSlices == null) {
            presetSlices = null;
            return "Invalid slice string";
        } else if (parsedSlices.size() < players) {
            presetSlices = null;
            parsedSlices = null;
            return "Not enough slices for the number of players.";
        }
        return null;
    }

    private boolean isNucleusMode() {
        return MapGenerationMode.Nucleus.equals(mapGenerationMode.getValue());
    }
}
