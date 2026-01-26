package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.DraftSetupService;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.milty.MiltyService;

@Getter
public class MiltySettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings
    private final ChoiceSetting<DraftingMode> draftMode;

    // Categories
    private final GameSettings gameSettings;
    private final SliceGenerationSettings sliceSettings;
    private final PlayerFactionSettings playerSettings;
    private final SourceSettings sourceSettings;
    // Bonus Attributes
    @JsonIgnore
    private final Game game;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public MiltySettings(@NotNull Game game, JsonNode json) {
        super("main", "Draft Settings", "Edit draft settings, then start the draft!", null);
        this.game = game;

        // Initialize default values
        draftMode = new ChoiceSetting<>("DraftType", "Draft Type", "milty");
        draftMode.setEmoji(MiltyDraftEmojis.sliceA);
        draftMode.setAllValues(
                Arrays.stream(DraftingMode.values()).collect(Collectors.toMap(DraftingMode::toString, x -> x)));
        draftMode.setShow(DraftingMode::toString);

        // TODO: Need a REAL way to set this up
        if (game.getMapTemplateID() != null) {
            MapTemplateModel template = Mapper.getMapTemplate(game.getMapTemplateID());
            if (template != null && template.isNucleusTemplate()) {
                draftMode.setChosenKey(DraftingMode.nucleus.toString());
            }
        }

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("miltySettings")) json = json.get("miltySettings");

        // Check if this node represents this menu
        List<String> historicIDs = List.of("milty", "main");
        if (json != null
                && json.has("menuId")
                && historicIDs.contains(json.get("menuId").asText(""))) {
            draftMode.initialize(json.get("draftMode"));
        }

        // initialize categories
        gameSettings = new GameSettings(game, json, this);
        sourceSettings = new SourceSettings(game, json, this);
        // frankenSettings = new FrankenSettings(game, json, this);
        playerSettings = new PlayerFactionSettings(game, json, this);
        sliceSettings = new SliceGenerationSettings(game, json, this);

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
        implemented.add(gameSettings);
        implemented.addAll(draftModeCategories());
        implemented.add(playerSettings);
        implemented.add(sourceSettings);
        return implemented;
    }

    @Override
    protected List<SettingInterface> settings() {
        // implemented.add(draftMode);
        return new ArrayList<>();
    }

    @Override
    protected List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";

        buttons.add(Buttons.green(prefix + "startMilty", "Start Milty Draft!"));
        // switch (draftMode.getValue()) {
        //     case milty -> buttons.add(Buttons.green(prefix + "startMilty", "Start Milty Draft!"));
        //     case franken -> buttons.add(Buttons.green(prefix + "startFranken", "Start Franken Draft!"));
        //     default -> buttons.clear();
        // }
        return buttons;
    }

    @Override
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "startMilty" -> startDraft(event);
                    default -> null;
                };
        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    public enum DraftingMode {
        none,
        milty, // Slice, Speaker Order, Faction in Public Snake Draft
        nucleus, // Slice, Speaker Order, Seat, Faction in Public Snake Draft (w/ Nucleus templates)
        franken;

        public String show() {
            return switch (this) {
                case none -> "Select a draft type to begin";
                case milty -> "Milty Draft";
                case franken -> ComponentSource.franken.toString();
                default -> toString();
            };
        }
    }

    private List<SettingsMenu> draftModeCategories() {
        List<SettingsMenu> draftCategories = new ArrayList<>();
        switch (draftMode.getValue()) {
            // case franken -> draftCategories.add(frankenSettings);
            default -> draftCategories.add(sliceSettings);
        }
        return draftCategories;
    }

    private String startDraft(GenericInteractionCreateEvent event) {
        /*
         * For compatibility, everything runs of these same Settings objects for now.
         * TODO: Draft mode should establish which components to pass to the DraftSetupService,
         * and those components should be configured in this settings framework.
         */
        if (draftMode.getValue() == DraftingMode.milty) {
            return MiltyService.startFromSettings(event, this);
        } else if (draftMode.getValue() == DraftingMode.nucleus) {
            if (gameSettings.getMapTemplate().getValue() == null
                    || !gameSettings.getMapTemplate().getValue().isNucleusTemplate()) {
                return "You must select a nucleus map template to use the nucleus draft mode!";
            }
            return DraftSetupService.startFromSettings(event, this);
        }

        return "This draft mode isn't implemented yet!";
    }
}
