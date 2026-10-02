package ti4.discord.interactions.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.settingsFramework.menus.MiltySettings;

class RandomSetup extends GameStateSubcommand {

    RandomSetup() {
        super("random_setup", "Configure and run a random Milty-style game setup", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MiltySettings menu = getGame().initializeMiltySettings();
        menu.setRandomSetup(true);
        menu.postMessageAndButtons(event);
    }
}
