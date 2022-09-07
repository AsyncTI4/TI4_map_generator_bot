package ti4.commands.cards;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowACRemainingCardCount extends CardsSubcommandData {
    public ShowACRemainingCardCount() {
        super(Constants.SHOW_AC_REMAINING_CARD_COUNT, "Show Action Card deck card count");
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

        String sb = "Action cards count in deck is: " + activeMap.getActionCards().size();
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
