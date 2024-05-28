package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import ti4.commands.milty.StartMilty;
import ti4.helpers.Emojis;
import ti4.map.Game;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MiltySettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private GameSettings gameSettings;
    private SliceGenerationSettings sliceSettings;
    private PlayerFactionSettings playerSettings;
    private SourceSettings sourceSettings;

    // Bonus Attributes
    @JsonIgnore
    private Game game;

    // unimplemented stuff //TODO: Jazz
    //  - map template setting ?
    //  - start milty draft
    //  - cross-validation between SourceSettings and other settings
    //  - franken draft?
    //  - traumatic mahact experience draft?

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    @JsonIgnore
    public void finishInitialization(Game game, SettingsMenu parent) {
        this.menuId = "milty";
        this.menuName = "Milty Draft Settings";
        this.description = "Edit milty draft settings, then start the draft!";
        this.game = game;

        if (gameSettings == null) gameSettings = new GameSettings();
        if (sourceSettings == null) sourceSettings = new SourceSettings();
        if (sliceSettings == null) sliceSettings = new SliceGenerationSettings();
        if (playerSettings == null) playerSettings = new PlayerFactionSettings();

        super.finishInitialization(game, parent);
    }

    @Override
    public List<SettingsMenu> categories() {
        List<SettingsMenu> implemented = new ArrayList<>();
        implemented.add(gameSettings);
        implemented.add(sliceSettings);
        implemented.add(playerSettings);
        implemented.add(sourceSettings);
        return implemented;
    }

    @Override
    public List<Button> specialButtons() {
        List<Button> buttons = new ArrayList<>();
        String prefix = menuAction + "_" + navId() + "_";
        buttons.add(Button.of(ButtonStyle.SUCCESS, prefix + "startMilty", "Start Milty Draft!", Emoji.fromFormatted(Emojis.sliceA)));
        return buttons;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error = switch (action) {
            case "startMilty" -> startMilty(event);
            default -> null;
        };

        return (error == null ? "success" : error);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Specific Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    public String startMilty(GenericInteractionCreateEvent event) {
        String errorMessage = StartMilty.startFromSettings(event, this);
        return errorMessage;
    }
}
