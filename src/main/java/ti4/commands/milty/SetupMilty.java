package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.map.Game;

public class SetupMilty extends MiltySubcommandData {

    private static final String setup = "setup";

    public SetupMilty() {
        super(setup, "Setup Milty Draft");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        MiltySettings menu = game.initializeMiltySettings();
        menu.postMessageAndButtons(event);
    }
}
