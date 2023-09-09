package ti4.commands.explore;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicInfo extends ExploreSubcommandData {
    public RelicInfo() {
		super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendRelicInfo(activeGame, player, event);
    }

    public static void sendRelicInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendRelicInfo(activeGame, player);
    }

    public static void sendRelicInfo(Game activeGame, Player player) {
        //RELIC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getRelicInfoText(player));

        //BUTTONS
        String purgeRelicMessage = "_ _\nClick a button below to exhaust or purge a Relic";
        List<Button> relicButtons = getRelicButtons(activeGame, player);
        if (relicButtons != null && !relicButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(purgeRelicMessage, relicButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeGame);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
    }

    private static String getRelicInfoText(Player player) {
        List<String> playerRelics = player.getRelics();
        StringBuilder sb = new StringBuilder("__**Relic Info**__\n");
        if (playerRelics == null || playerRelics.isEmpty()) {
            sb.append("> No Relics");
            return sb.toString();
        }
        for (String relicID : playerRelics) {
            sb.append(Helper.getRelicRepresentation(relicID));
        }
        return sb.toString();
    }

    private static List<Button> getRelicButtons(Game activeGame, Player player) {
        return null;
    }
}
