package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowDiscardActionCards extends SOCardsSubcommandData {
    public ShowDiscardActionCards() {
        super(Constants.SHOW_AC_DISCARD_LIST, "Show Action Card discard list");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Action card discard list: ").append("\n");
        int index = 1;
        for (java.util.Map.Entry<String, Integer> ac : activeMap.getDiscardActionCards().entrySet()) {
            sb.append(index).append(". (").append(ac.getValue()).append(") - ").append(Mapper.getActionCard(ac.getKey())).append("\n");
            index++;
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}
