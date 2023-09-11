package ti4.commands.cardsso;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            sendMessage("Please select which Secret Objective to score");
            return;
        }

        int soID = option.getAsInt();
        scoreSO(event, activeGame, player, soID, event.getChannel());
    }

    public static void scoreSO(GenericInteractionCreateEvent event, Game activeGame, Player player, int soID, MessageChannel channel) {
        Set<String> alreadyScoredSO = new HashSet<>(player.getSecretsScored().keySet());
        boolean scored = activeGame.scoreSecretObjective(player.getUserID(), soID, activeGame);
        if (!scored) {
            MessageHelper.sendMessageToChannel(channel, "No such Secret Objective ID found, please retry");
            return;
        }

        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame) + " scored " + Emojis.SecretObjectiveAlt + " ");
        for (Map.Entry<String, Integer> entry : player.getSecretsScored().entrySet()) {
            if (alreadyScoredSO.contains(entry.getKey())) {
                continue;
            }
            message.append(SOInfo.getSecretObjectiveRepresentation(entry.getKey())).append("\n");
        }
        if (event != null && channel.getName().equalsIgnoreCase(event.getChannel().getName())) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        } else {
            MessageHelper.sendMessageToChannel(channel, message.toString());
        }

        // FoW logic, specific for players with visilibty, generic for the rest
        if (activeGame.isFoWMode()) {
            FoWHelper.pingPlayersDifferentMessages(activeGame, event, player, message.toString(), "Scores changed");
            MessageHelper.sendMessageToChannel(channel, "All players notified");
        }
        String headerText = Helper.getPlayerRepresentation(player, activeGame);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
        Helper.checkIfHeroUnlocked(event, activeGame, player);
        if(player.getLeaderIDs().contains("nomadcommander") && !player.hasLeaderUnlocked("nomadcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeGame, "nomad", event);
        }
        Helper.checkEndGame(activeGame, player);
    }
}
