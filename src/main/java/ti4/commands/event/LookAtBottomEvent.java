package ti4.commands.event;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class LookAtBottomEvent extends EventSubcommandData {
    public LookAtBottomEvent() {
        super(Constants.LOOK_AT_BOTTOM, "Look at bottom Event from deck");
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of events to look at");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);

        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append(event.getUser().getAsMention()).append("\n");
        sb.append("`").append(event.getCommandString()).append("`").append("\n");
        if (count > 1) {
            sb.append("__**Bottom ").append(count).append(" events:**__\n");
        } else {
            sb.append("__**Bottom event:**__\n");
        }
        for (int i = 0; i < count; i++) {
            String eventID = game.lookAtBottomEvent(i);
            sb.append(i + 1).append(": ");
            EventModel eventModel = Mapper.getEvent(eventID);
            sb.append(eventModel.getRepresentation());
            sb.append("\n");
        }
        sb.append("-----------\n");

        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToUser(sb.toString(), event);
        } else {
            User userById = event.getJDA().getUserById(player.getUserID());
            if (userById != null) {
                if (game.isCommunityMode() && player.getPrivateChannel() instanceof MessageChannel) {
                    MessageHelper.sendMessageToChannel(player.getPrivateChannel(), sb.toString());
                } else {
                    MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
                }
            } else {
                MessageHelper.sendMessageToUser(sb.toString(), event);
            }
        }
    }
}
