package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.StringHelper;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.IntegerSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.ReadOnlyTextSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.DraftComponentFactory;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.DraftSetupService;
import ti4.service.draft.DraftableType;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.milty.MiltyDraftSlice;

/*
 * TO FIGURE OUT:
 * - Where do we track which draftables/orchestrator is used?
 *   - e.g. does the DraftSystemSettings have a List<DraftableType>?
 *   - Not all Draftables will provide a settings menu...right? e.g. Speaker Order.
 *   - So maybe it's List<DraftableType> AND List<SettingsMenu>?
 * - How do produce an actual Draftable?
 *   - Do we make DraftableSettingsMenu and OrchestratorSettingsMenu to provide a produce method?
 *   - Or do we have a DraftableFactory and OrchestratorFactory that take in the settings menu?
 *   - Or do we use DraftComponentFactory to instantiate things, which then have a method which takes a SettingsMenu? (!!)
 *     - How do we tie SettingsMenu to DraftableType, if its two separate lists? Maybe its a map with a nullable value? (Optional<SettingsMenu>?)
 *       - Except, it's an ordered list. So tuples, not maps.
 *   - Or some intermediate class (DraftSetupService) that does the work?
 */

// TODO: Pick from a library of pre-made maps, then choose or draft seats. Print a preview of the selected map!
// TODO: Maybe Draftables need a "builds map" property, so we can choose exactly 1 way of building the map, and
//  that way nucleus vs milty vs premade vs star-by-star conflicts can be detected.
//   ....Really, each Draftable needs a "sets X" method, which gives categories (map, faction, home system tile position, speaker token)
//       and a singular description for whats set, e.g. "Create the map from smaller drafted slices and a central Nucleus."

@Getter
public class DraftSystemSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------

    // Settings
    private final ChoiceSetting<String> draftOrchestrator;
    private final ListSetting<String> draftablesList;
    // Categories
    private final GameSetupSettings gameSetupSettings;
    private final SourceSettings sourceSettings;
    private final SliceDraftableSettings sliceSettings;
    private final FactionDraftableSettings factionSettings;
    private final PublicSnakeDraftSettings publicSnakeDraftSettings;
    // Bonus Attributes
    @Setter
    private String preset = null;
    @JsonIgnore
    private final Game game;

    private final static String MENU_ID = "draft";

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public DraftSystemSettings(Game game, JsonNode json) {
        super(MENU_ID, "Draft Settings", "Edit draft settings, then start the draft!", null);
        this.game = game;


        // Initialize values & keys for draft components
        List<String> draftables = DraftComponentFactory.getKnownDraftableTypes();
        List<String> draftOrchestrators = DraftComponentFactory.getKnownOrchestratorTypes();
        // Map<String, DraftableType> draftableMap = draftables.stream()
        //         .collect(Collectors.toMap(d -> d, d -> DraftComponentFactory.createDraftable(d).getType()));
        // Map<String, DraftOrchestrator> orchestratorMap = draftOrchestrators.stream()
        //         .collect(Collectors.toMap(o -> o, o -> DraftComponentFactory.createOrchestrator(o)));
        draftOrchestrator = new ChoiceSetting<>("Orchestrator", "Draft Orchestrator", draftOrchestrators.getFirst());
        draftOrchestrator.setAllValues(draftOrchestrators.stream().collect(Collectors.toMap(o -> o, o -> o)));
        draftablesList = new ListSetting<>("Draftables", "Draftable Types", "Add draftable", "Remove draftable",
                draftables.stream().collect(Collectors.toMap(d -> d, d -> d)).entrySet(), Set.of(), Set.of());

        // Other Initialization
        draftablesList.setShow(s -> s);
        draftOrchestrator.setShow(s -> s);

        // Load JSON if applicable
        if (json != null && json.has("menuId") && json.get("menuId").asText("").equals(MENU_ID)) {
            draftOrchestrator.initialize(json.get("draftOrchestrator"));
            draftablesList.initialize(json.get("draftablesList"));
            preset = json.get("preset") != null ? json.get("preset").asText(null) : null;
        }

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        // if (json != null && json.has("miltySettings")) json =
        // json.get("miltySettings");

        // Check if this node represents this menu
        // List<String> historicIDs = List.of("milty", "main");
        // if (json != null
        // && json.has("menuId")
        // && historicIDs.contains(json.get("menuId").asText(""))) {
        // draftMode.initialize(json.get("draftMode"));
        // }

        // initialize categories
        // gameSettings = new GameSettings(game, json, this);
        gameSetupSettings = new GameSetupSettings(game, json != null ? json.get("gameSetupSettings") : null, this);
        sourceSettings = new SourceSettings(game, json, this);
        sliceSettings = new SliceDraftableSettings(game, json != null ? json.get("sliceSettings") : null, this);
        factionSettings = new FactionDraftableSettings(game, json != null ? json.get("factionSettings") : null, this);
        publicSnakeDraftSettings = new PublicSnakeDraftSettings(game, json != null ? json.get("publicSnakeDraftSettings") : null, this);
        // frankenSettings = new FrankenSettings(game, json, this);
        // playerSettings = new PlayerFactionSettings(game, json, this);
        // sliceSettings = new SliceGenerationSettings(game, json, this);

        if (json != null && json.has("messageId")) {
            setMessageId(json.get("messageId").asText(null));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    protected List<SettingsMenu> categories() {
        List<SettingsMenu> implemented = new ArrayList<>();
        // If there is no draft mode selected, then don't return any settings
        // if (draftMode.getValue() == DraftingMode.none) return implemented;
        implemented.add(gameSetupSettings);
        // Set<DraftableType> selectedDraftables = getSelectedDraftables();
        if (draftablesList.getKeys().contains(SliceDraftable.class.getSimpleName())) {
            implemented.add(sliceSettings);
        }
        if (draftablesList.getKeys().contains(FactionDraftable.class.getSimpleName())) {
            implemented.add(factionSettings);
        }
        if(draftOrchestrator.getValue().equals(PublicSnakeDraftOrchestrator.class.getSimpleName())) {
            implemented.add(publicSnakeDraftSettings);
        }
        implemented.add(sourceSettings);
        return implemented;
    }

    @Override
    protected List<SettingInterface> settings() {
        // implemented.add(draftMode);
        List<SettingInterface> settings = new ArrayList<>();
        if(preset == null) {
            settings.add(draftOrchestrator);
            settings.add(draftablesList);
        } else {
            SettingInterface displayPreset = new ReadOnlyTextSetting("preset", "Preset", preset);
            settings.add(displayPreset);
        }
        return settings;
    }

    @Override
    protected List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";

        buttons.add(Buttons.green(prefix + "startSetup", "Start!"));
        // switch (draftMode.getValue()) {
        // case milty -> buttons.add(Buttons.green(prefix + "startMilty", "Start Milty
        // Draft!"));
        // case franken -> buttons.add(Buttons.green(prefix + "startFranken", "Start
        // Franken Draft!"));
        // default -> buttons.clear();
        // }
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "startSetup" -> startSetup(event);
            default -> null;
        };
        return (error == null ? "success" : error);
    }

    @Override
    protected void updateTransientSettings() {
        // if (parent instanceof MiltySettings m) {
        // int players = m.getPlayerSettings().getGamePlayers().getKeys().size();
        // Map<String, MapTemplateModel> allowed =
        // Mapper.getMapTemplatesForPlayerCount(players).stream()
        // .filter(getNucleusTemplatePredicate())
        // .collect(Collectors.toMap(MapTemplateModel::getAlias, x -> x));
        // var defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);
        // if (defaultTemplate == null) {
        // return;
        // }
        // // TODO: IMPROVE THIS
        // if (m.getDraftMode().getValue() == DraftingMode.nucleus &&
        // !defaultTemplate.isNucleusTemplate()) {
        // defaultTemplate = Mapper.getMapTemplate(defaultTemplate.getAlias() +
        // "Nucleus");
        // if (defaultTemplate == null) {
        // return;
        // }
        // }
        // mapTemplate.setAllValues(allowed, defaultTemplate.getAlias());
        // }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------

    public Set<String> getPlayerUserIds() {
        return gameSetupSettings.getGamePlayers().getKeys();
    }

    // private Set<DraftableType> getSelectedDraftables() {
    //     return draftablesList.getKeys().stream()
    //             .map(d -> draftablesList.getAllValues().get(d))
    //             .collect(Collectors.toSet());
    // }

    private String startSetup(GenericInteractionCreateEvent event) {
        return DraftSetupService.startFromSettings(event, this);
    }

    @ButtonHandler("startDraftSystem")
    public static void startDraft(ButtonInteractionEvent event, Game game) {
        String preset = event.getButton().getCustomId().replace("startDraftSystem", "");
        DraftSystemSettings settings = new DraftSystemSettings(game, null);
        if (preset.equals("_nucleusPreset")) {
            settings.getDraftablesList()
                    .setKeys(List.of(FactionDraftable.class.getSimpleName(), SliceDraftable.class.getSimpleName(),
                            SeatDraftable.class.getSimpleName(), SpeakerOrderDraftable.class.getSimpleName()));
            settings.getDraftOrchestrator().setChosenKey(PublicSnakeDraftOrchestrator.class.getSimpleName());
            settings.getSliceSettings().getMapGenerationMode().setChosenKey("Nucleus");
            settings.preset = "Nucleus Draft, publicly in a snaking order";
            settings.setPreset("Nucleus Draft, publicly in a snaking order");
        }
        game.setDraftSystemSettings(settings);
        settings.postMessageAndButtons(event);
    }

    public void setupMiltyPreset(int numSlices, int numFactions, List<MiltyDraftSlice> presetSlices) {
        // TODO
    }
}
