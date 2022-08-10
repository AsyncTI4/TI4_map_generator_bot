package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
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
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

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
        Player player_ = Helper.getGamePlayer(activeMap, null, event, null);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }
        User user = MapGenerator.jda.getUserById(player_.getUserID());
        if (user == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User for faction not found. Report to ADMIN");
            return;
        }

        MessageHelper.sentToMessageToUser(event, sb.toString(), user);
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
