package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.ListSetting;
import ti4.helpers.settingsFramework.settings.ReadOnlyTextSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftComponentFactory;
import ti4.service.draft.DraftSetupService;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.MahactKingDraftable;
import ti4.service.draft.draftables.MantisTileDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftSlice;

// TODO: A library of pre-made maps would be cool.

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
    private final MantisTileDraftableSettings mantisTileSettings;
    private final FactionDraftableSettings factionSettings;
    private final AndcatReferenceCardsDraftableSettings andcatReferenceCardsDraftableSettings;
    private final MahactKingDraftableSettings mahactKingDraftableSettings;
    private final PublicSnakeDraftSettings publicSnakeDraftSettings;
    // Bonus Attributes
    @Setter
    private String preset = null;

    @JsonIgnore
    private final Game game;

    private static final String MENU_ID = "draft";

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public DraftSystemSettings(Game game, JsonNode json) {
        super(MENU_ID, "Draft Settings", "Edit draft settings, then start the draft!", null);
        this.game = game;

        // Initialize values & keys for draft components
        List<String> draftables = DraftComponentFactory.getKnownDraftableClasses();
        List<String> draftOrchestrators = DraftComponentFactory.getKnownOrchestratorClasses();
        draftOrchestrator = new ChoiceSetting<>("Orchestrator", "Draft Orchestrator", draftOrchestrators.getFirst());
        draftOrchestrator.setAllValues(draftOrchestrators.stream().collect(Collectors.toMap(o -> o, o -> o)));
        draftablesList = new ListSetting<>(
                "Draftables",
                "Draftable Types",
                "Add draftable",
                "Remove draftable",
                draftables.stream().collect(Collectors.toMap(d -> d, d -> d)).entrySet(),
                Set.of(),
                Set.of());

        // Other Initialization
        draftablesList.setShow(s -> s);
        draftOrchestrator.setShow(s -> s);

        // Load JSON if applicable
        if (json != null && json.has("menuId") && json.get("menuId").asText("").equals(MENU_ID)) {
            draftOrchestrator.initialize(json.get("draftOrchestrator"));
            draftablesList.initialize(json.get("draftablesList"));
            preset = json.get("preset") != null ? json.get("preset").asText(null) : null;
        }

        // initialize categories
        gameSetupSettings = new GameSetupSettings(game, json != null ? json.get("gameSetupSettings") : null, this);
        sourceSettings = new SourceSettings(game, json, this);
        sliceSettings = new SliceDraftableSettings(game, json != null ? json.get("sliceSettings") : null, this);
        mantisTileSettings =
                new MantisTileDraftableSettings(game, json != null ? json.get("mantisTileSettings") : null, this);
        factionSettings = new FactionDraftableSettings(game, json != null ? json.get("factionSettings") : null, this);
        andcatReferenceCardsDraftableSettings = new AndcatReferenceCardsDraftableSettings(
                game, json != null ? json.get("andcatReferenceCardsDraftableSettings") : null, this);
        mahactKingDraftableSettings = new MahactKingDraftableSettings(
                game, json != null ? json.get("mahactKingDraftableSettings") : null, this);
        publicSnakeDraftSettings =
                new PublicSnakeDraftSettings(game, json != null ? json.get("publicSnakeDraftSettings") : null, this);

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
        implemented.add(gameSetupSettings);
        // TODO: Should probably have a better way to add/remove categories than comparing class names.
        if (draftablesList.getKeys().contains(SliceDraftable.class.getSimpleName())) {
            implemented.add(sliceSettings);
        }
        if (draftablesList.getKeys().contains(FactionDraftable.class.getSimpleName())) {
            implemented.add(factionSettings);
        }
        if (draftablesList.getKeys().contains(MantisTileDraftable.class.getSimpleName())) {
            implemented.add(mantisTileSettings);
        }
        if (draftablesList.getKeys().contains(AndcatReferenceCardsDraftable.class.getSimpleName())) {
            implemented.add(andcatReferenceCardsDraftableSettings);
        }
        if (draftablesList.getKeys().contains(MahactKingDraftable.class.getSimpleName())) {
            implemented.add(mahactKingDraftableSettings);
        }
        if (draftOrchestrator.getValue().equals(PublicSnakeDraftOrchestrator.class.getSimpleName())) {
            implemented.add(publicSnakeDraftSettings);
        }
        implemented.add(sourceSettings);
        return implemented;
    }

    @Override
    protected List<SettingInterface> settings() {
        List<SettingInterface> settings = new ArrayList<>();
        if (preset == null) {
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

        buttons.add(Buttons.green(prefix + "startSetup", "Start Draft!"));
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "startSetup" -> startSetup(event);
                    default -> null;
                };
        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------

    public Set<String> getPlayerUserIds() {
        return gameSetupSettings.getGamePlayers().getKeys();
    }

    private String startSetup(GenericInteractionCreateEvent event) {
        return DraftSetupService.startFromSettings(event, this);
    }

    @ButtonHandler("startDraftSystem")
    public static void startDraft(ButtonInteractionEvent event, Game game) {
        String buttonCustomId = event.getButton().getCustomId();
        String preset = buttonCustomId != null ? buttonCustomId.replace("startDraftSystem", "") : null;
        DraftSystemSettings settings = new DraftSystemSettings(game, null);
        if ("_nucleusPreset".equals(preset)) {
            if (game.getPlayers().size() < 3) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, "Nucleus draft requires at least 3 players");
                return;
            }
            if (game.getPlayers().size() > 8) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Nucleus draft supports at most 8 players; you'll need to remove excess players from the draft.");
            }
            settings.setupNucleusPreset();
        }
        if ("_andcatPreset".equals(preset)) {

            if (game.getPlayers().size() > 8) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "This draft supports at most 8 players; you'll need to remove excess players from the draft.");
            }
            settings.setupAndcatTwilightsFallPreset();
            ButtonHelper.deleteMessage(event);
        }
        game.setDraftSystemSettings(settings);
        settings.postMessageAndButtons(event);
    }

    public void setupMiltyPreset(int numSlices, int numFactions, List<MiltyDraftSlice> presetSlices) {
        // TODO
    }

    public void setupNucleusPreset() {
        getDraftablesList()
                .setKeys(List.of(
                        FactionDraftable.class.getSimpleName(),
                        SliceDraftable.class.getSimpleName(),
                        SeatDraftable.class.getSimpleName(),
                        SpeakerOrderDraftable.class.getSimpleName()));
        getDraftOrchestrator().setChosenKey(PublicSnakeDraftOrchestrator.class.getSimpleName());
        getSliceSettings().getMapGenerationMode().setChosenKey("Nucleus");
        setPreset("Nucleus Draft");
    }

    public void setupAndcatTwilightsFallPreset() {
        getDraftablesList()
                .setKeys(List.of(
                        AndcatReferenceCardsDraftable.class.getSimpleName(),
                        SliceDraftable.class.getSimpleName(),
                        MahactKingDraftable.class.getSimpleName()));
        getDraftOrchestrator().setChosenKey(PublicSnakeDraftOrchestrator.class.getSimpleName());
        getSliceSettings().getMapGenerationMode().setChosenKey("Milty");
        setPreset("Twilights Fall (Andcat Draft)");
    }
}
