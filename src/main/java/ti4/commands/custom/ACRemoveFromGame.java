package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ACRemoveFromGame extends GameStateSubcommand {

    public ACRemoveFromGame() {
        super(Constants.REMOVE_AC_FROM_GAME, "Remove an action card from the game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "Action card ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean removed = getGame().removeACFromGame(event.getOption(Constants.AC_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Action card removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Action card not found in game deck.");
        }
    }
}
