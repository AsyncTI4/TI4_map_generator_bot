package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PutEventBottom extends GameStateSubcommand {

    public PutEventBottom() {
        super(Constants.PUT_BOTTOM, "Put event on the bottom of the deck", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Integer numericalID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        Game game = getGame();
        Player player = getPlayer();
        putBottom(numericalID, game, player);
    }

    public void putBottom(int eventID, Game game, Player player) {
        boolean success = game.putEventBottom(eventID, player);
        if (success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Event put on bottom of deck.");
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Event ID found.");
        }
    }
}
