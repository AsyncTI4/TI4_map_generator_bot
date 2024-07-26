package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ACRemoveFromGame extends CustomSubcommandData {
    public ACRemoveFromGame() {
        super(Constants.REMOVE_AC_FROM_GAME, "Remove an action card from the game.");
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "ID of the action card to remove.").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        OptionMapping soOption = event.getOption(Constants.AC_ID);
        if (soOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Action card not specified.");
            return;
        }
        boolean removed = game.removeACFromGame(soOption.getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Action card removed from game deck.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Action card not found in game deck.");
        }
    }
}
