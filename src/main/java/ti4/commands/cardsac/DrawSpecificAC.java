package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawSpecificAC extends ACCardsSubcommandData {
    public DrawSpecificAC() {
        super(Constants.DRAW_SPECIFIC_AC, "Draw Specific Action Card");
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "ID of the card you want to draw").setRequired(true));
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
        OptionMapping option = event.getOption(Constants.AC_ID);
        
        if (option != null) {
            String providedID = option.getAsString();
            activeMap.drawSpecificActionCard(providedID, player.getUserID());
        }
        ACInfo.sendActionCardInfo(activeMap, player);
    }
}
