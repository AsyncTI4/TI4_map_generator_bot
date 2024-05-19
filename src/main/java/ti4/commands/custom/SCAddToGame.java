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
        super(Constants.ADD_SC_TO_GAME, "Add a Stategy Card # to the game");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card to add").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        Integer sc = event.getOption(Constants.STRATEGY_CARD, null, OptionMapping::getAsInt);
        if (sc == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "SC was null?");
            return;
        }

        if (game.addSC(sc)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added Strategy Card: " + sc);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card already exists: " + sc);
        }
    }

}
