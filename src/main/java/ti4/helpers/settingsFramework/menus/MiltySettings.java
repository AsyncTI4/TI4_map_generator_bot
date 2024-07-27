package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.milty.StartMilty;
import ti4.helpers.Emojis;
import ti4.helpers.settingsFramework.settings.ChoiceSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;

@Getter
public class MiltySettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings
    private ChoiceSetting<DraftingMode> draftMode;

    // Categories
    private GameSettings gameSettings;
    private SliceGenerationSettings sliceSettings;
    //private FrankenSettings frankenSettings;
    private PlayerFactionSettings playerSettings;
    private SourceSettings sourceSettings;

    // Bonus Attributes
    @JsonIgnore
    private Game game;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------
    public MiltySettings(Game game, JsonNode json) {
        super("main", "Draft Settings", "Edit draft settings, then start the draft!", null);
        this.game = game;

        // Initialize default values
        draftMode = new ChoiceSetting<>("DraftType", "Draft Type", "milty");
        draftMode.setEmoji(Emojis.sliceA);
        draftMode.setAllValues(Arrays.asList(DraftingMode.values()).stream().collect(Collectors.toMap(DraftingMode::toString, x -> x)));
        draftMode.setShow(DraftingMode::toString);

        // Get the correct JSON node for initialization if applicable.
        // Add additional names here to support new generated JSON as needed.
        if (json != null && json.has("miltySettings")) json = json.get("miltySettings");

        // Check if this node represents this menu
        List<String> historicIDs = List.of("milty", "main");
        if (json != null && json.has("menuId") && historicIDs.contains(json.get("menuId").asText(""))) {
            draftMode.initialize(json.get("draftMode"));
        }

        // initialize categories
        gameSettings = new GameSettings(game, json, this);
        sourceSettings = new SourceSettings(game, json, this);
        //frankenSettings = new FrankenSettings(game, json, this);
        sliceSettings = new SliceGenerationSettings(game, json, this);
        playerSettings = new PlayerFactionSettings(game, json, this);
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
        List<SettingInterface> implemented = new ArrayList<>();
        // implemented.add(draftMode);
        return implemented;
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
        String error = switch (action) {
            case "startMilty" -> startMilty(event);
            default -> null;
        };
        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    public enum DraftingMode {
        none, milty, franken, twilightfalls;

        public String show() {
            return switch (this) {
                case none -> "Select a draft type to begin";
                case milty -> "Milty Draft";
                case franken -> ComponentSource.franken.toString();
                default -> toString();
            };
        }
    }

    protected List<SettingsMenu> draftModeCategories() {
        List<SettingsMenu> draftCategories = new ArrayList<>();
        switch (draftMode.getValue()) {
            //case franken -> draftCategories.add(frankenSettings);
            default -> draftCategories.add(sliceSettings);
        }
        return draftCategories;
    }

    protected String startMilty(GenericInteractionCreateEvent event) {
        String errorMessage = StartMilty.startFromSettings(event, this);
        return errorMessage;
    }
}
