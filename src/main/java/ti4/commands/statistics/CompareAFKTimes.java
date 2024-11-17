package ti4.commands.statistics;

import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

class CompareAFKTimes extends Subcommand {

    public CompareAFKTimes() {
        super(Constants.COMPARE_AFK_TIMES, "Compare different players set AFK Times");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String times = "";
        times = times + getUsersAFKTime(event, Constants.PLAYER1);
        times = times + getUsersAFKTime(event, Constants.PLAYER2);
        times = times + getUsersAFKTime(event, Constants.PLAYER3);
        times = times + getUsersAFKTime(event, Constants.PLAYER4);
        times = times + getUsersAFKTime(event, Constants.PLAYER5);
        times = times + getUsersAFKTime(event, Constants.PLAYER6);
        times = times + getUsersAFKTime(event, Constants.PLAYER7);
        times = times + getUsersAFKTime(event, Constants.PLAYER8);
        MessageHelper.sendMessageToChannel(event.getChannel(), times);
    }

    private String getUsersAFKTime(SlashCommandInteractionEvent event, String playerID) {
        if (playerID == null) {
            return "";
        }
        OptionMapping option = event.getOption(playerID);
        if (option == null) {
            return "";
        }
        User extraUser = option.getAsUser();
        playerID = extraUser.getId();
        Map<String, Game> mapList = GameManager.getGameNameToGame();
        for (Game game : mapList.values()) {
            if (!game.isHasEnded()) {
                for (Player player2 : game.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(playerID)) {
                        return player2.getRepresentationUnfogged() + "afk hours are: " + player2.getHoursThatPlayerIsAFK().replace(";", ", ") + "\n";
                    }
                }
            }
        }
        return "No games found with this user";
    }
}