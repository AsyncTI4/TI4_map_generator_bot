package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PutEventBottom extends EventSubcommandData {
    public PutEventBottom() {
        super(Constants.PUT_BOTTOM, "Put Event bottom");
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        Integer numericalID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        if (numericalID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Event ID defined");
            return;
        }
        putBottom(event, numericalID, game, player);
    }

    public void putBottom(GenericInteractionCreateEvent event, int eventID, Game game, Player player) {
        boolean success = game.putEventBottom(eventID, player);
        if (success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Event put on bottom");
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Event ID found");
        }
    }
}
