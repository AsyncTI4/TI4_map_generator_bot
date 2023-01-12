package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowAC extends CardsSubcommandData {
    public ShowAC() {
        super(Constants.SHOW_AC, "Show Action Card to player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
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
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to show");
            return;
        }

        int acIndex = option.getAsInt();
        String acID = null;
        for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }


        StringBuilder sb = new StringBuilder();
        sb.append("---------\n");
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(Helper.getPlayerRepresentation(event, player));
        sb.append("\n");
        sb.append("Showed Action Cards:").append("\n");
        sb.append(Mapper.getActionCard(acID)).append("\n");
        sb.append("---------\n");
        player.setActionCard(acID);

        Player player_ = Helper.getPlayer(activeMap, null, event);
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
