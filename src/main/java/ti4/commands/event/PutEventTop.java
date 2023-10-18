package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PutEventTop extends EventSubcommandData {
    public PutEventTop() {
        super(Constants.PUT_TOP, "Put Event on top");
        addOptions(new OptionData(OptionType.INTEGER, Constants.EVENT_ID, "Event ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        Integer numericalID = event.getOption(Constants.EVENT_ID, null, OptionMapping::getAsInt);
        if (numericalID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(event, numericalID, activeGame, player);
    }

    public void putTop(GenericInteractionCreateEvent event, int eventID, Game activeGame, Player player) {
        boolean success = activeGame.putEventTop(eventID, player);

        if (success) {
            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Event put on top");
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "No Event ID found");
        }
    }
}
