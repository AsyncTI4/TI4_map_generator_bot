package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class ShowAllAC extends CardsSubcommandData {
    public ShowAllAC() {
        super(Constants.SHOW_ALL_AC, "Show Action Cards to player");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to which to show Action Cards").setRequired(true));
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

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            StringBuilder sb = new StringBuilder();
            sb.append("Game: ").append(activeMap.getName()).append("\n");
            sb.append("Player: ").append(player.getUserName()).append("\n");
            sb.append("Showed Action Cards:").append("\n");
            LinkedHashMap<String, Integer> actionCards = player.getActionCards();
            int index = 1;
            for (String id : actionCards.keySet()) {
                sb.append(index).append(". ").append(Mapper.getActionCard(id)).append("\n");
                index++;

            }
            MessageHelper.sentToMessageToUser(event, sb.toString(), user);
            CardsInfo.sentUserCardInfo(event, activeMap, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Need to specify player");
            return;
        }

    }
}
