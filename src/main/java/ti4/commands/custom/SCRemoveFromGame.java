package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SCRemoveFromGame extends CustomSubcommandData {

    public SCRemoveFromGame() {
        super(Constants.REMOVE_SC_FROM_GAME, "Remove a strategy card from the game.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Initiative value of strategy card to remove").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Integer sc = event.getOption(Constants.STRATEGY_CARD, null, OptionMapping::getAsInt);
        if (sc == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No initiative value given.");
            return;
        }

        if (game.removeSC(sc)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed strategy card: " + sc);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy card did not exist: " + sc);
        }
    }

}
