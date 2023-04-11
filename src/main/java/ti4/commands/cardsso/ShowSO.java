package ti4.commands.cardsso;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowSO extends SOCardsSubcommandData {
    public ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            sendMessage("Please select what Secret Objective to show");
            return;
        }

        int soIndex = option.getAsInt();
        String soID = null;
        for (java.util.Map.Entry<String, Integer> so : player.getSecrets().entrySet()) {
            if (so.getValue().equals(soIndex)) {
                soID = so.getKey();
            }
        }

        if (soID == null) {
            sendMessage("No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Secret Objectives:").append("\n");
        sb.append(Mapper.getSecretObjective(soID)).append("\n");
        player.setSecret(soID);

        Player player_ = Helper.getPlayer(activeMap, null, event);
        if (player_ == null) {
            sendMessage("Player not found");
            return;
        }
        User user = MapGenerator.jda.getUserById(player_.getUserID());
        if (user == null) {
            sendMessage("User for faction not found. Report to ADMIN");
            return;
        }

        sendMessage("SO shown to player - check DMs");
        MessageHelper.sendMessageToUser(sb.toString(), user);
    }
}
