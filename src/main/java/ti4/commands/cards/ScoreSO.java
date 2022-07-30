package ti4.commands.cards;

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

public class ScoreSO extends CardsSubcommandData {
    public ScoreSO() {
        super(Constants.SCORE_SO, "Score Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
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
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Secret Objective to score");
            return;
        }

        int soID = option.getAsInt();
        boolean scored = activeMap.scoreSecretObjective(getUser().getId(), soID, activeMap);
        if (!scored) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Secret Objective ID found, please retry");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Scored Secret Objective:").append("\n");
        for (java.util.Map.Entry<String, Integer> entry : player.getSecretsScored().entrySet()) {
            if (soID == entry.getValue()){
                sb.append(Mapper.getSecretObjective(entry.getKey())).append("\n");
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
