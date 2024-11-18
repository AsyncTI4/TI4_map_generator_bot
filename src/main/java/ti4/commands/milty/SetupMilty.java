package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;

public class SetupMilty extends GameStateSubcommand {

    private static final String setup = "setup";

    public SetupMilty() {
        super(setup, "Setup Milty Draft", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        miltySetup(event, getGame());
    }

    @ButtonHandler("miltySetup")
    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltySettings menu = game.initializeMiltySettings();
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }
}
