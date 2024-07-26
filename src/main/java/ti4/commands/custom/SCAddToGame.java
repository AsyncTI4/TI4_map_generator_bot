package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class SCAddToGame extends CustomSubcommandData {

    public SCAddToGame() {
        super(Constants.ADD_SC_TO_GAME, "Add a strategy card to the game.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Initiative value of strategy card to add").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Integer sc = event.getOption(Constants.STRATEGY_CARD, null, OptionMapping::getAsInt);
        if (sc == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No initiative value given.");
            return;
        }

        if (game.addSC(sc)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added strategy card: " + sc);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy card already exists: " + sc);
        }
    }

}
