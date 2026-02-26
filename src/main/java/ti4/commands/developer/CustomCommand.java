package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class CustomCommand extends Subcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the command against.")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.FACTION_COLOR,
                        "Faction/color of the player to spawn Alliance Rider buttons for")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Not implemented.");
    }
}
