package ti4.commands.cardsso;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.HashSet;
import java.util.Set;

public class ScoreSO extends SOCardsSubcommandData {
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select which Secret Objective to score");
            return;
        }

        int soID = option.getAsInt();
        scoreSO(event, activeMap, player, soID, event.getChannel(), null);
    }

    public static void scoreSO(SlashCommandInteractionEvent event, Map activeMap, Player player, int soID, MessageChannel channel, ButtonInteractionEvent buttonInteractionEvent) {
        Set<String> alreadyScoredSO = new HashSet<>(player.getSecretsScored().keySet());
        boolean scored = activeMap.scoreSecretObjective(player.getUserID(), soID, activeMap);
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel, "No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player) + " scored " + Emojis.SecretObjectiveAlt + " ");
        for (java.util.Map.Entry<String, Integer> entry : player.getSecretsScored().entrySet()) {
            if (alreadyScoredSO.contains(entry.getKey())) {
                continue;
            }
            String[] soText = Mapper.getSecretObjective(entry.getKey()).split(";");
            String soName = soText[0];
            String soPhase = soText[1];
            String soDescription = soText[2];
            message.append("__**" + soName + "**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        }
        MessageHelper.sendMessageToChannel(channel, message.toString());
        CardsInfo.sentUserCardInfo(event, activeMap, player, buttonInteractionEvent);
    }
}
