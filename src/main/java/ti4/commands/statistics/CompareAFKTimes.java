package ti4.commands.statistics;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CompareAFKTimes extends StatisticsSubcommandData {

    private static final List<String> PLAYER_OPTIONS_TO_CHECK = List.of(
            Constants.PLAYER1, Constants.PLAYER2, Constants.PLAYER3, Constants.PLAYER4,
            Constants.PLAYER5, Constants.PLAYER6, Constants.PLAYER7, Constants.PLAYER8);

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
        List<String> playersToCheck = PLAYER_OPTIONS_TO_CHECK.stream()
                .map(playerOptionName -> event.getOption(playerOptionName, null, OptionMapping::getAsUser))
                .filter(Objects::nonNull)
                .map(User::getId)
                .toList();

        Map<String, String> playerIdToAfkTimeMessage = new HashMap<>();
        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getInstance().getGamesPage(currentPage++);
            for (String player : playersToCheck) {
                var afkTime = getUsersAFKTime(pagedGames.getGames(), player);
                if (afkTime != null) {
                    playerIdToAfkTimeMessage.put(player, afkTime);
                }
            }
        } while (pagedGames.hasNextPage() && playerIdToAfkTimeMessage.size() < playersToCheck.size());

        if (playerIdToAfkTimeMessage.size() < playersToCheck.size()) {
            for (String player : playersToCheck) {
                if (!playerIdToAfkTimeMessage.containsKey(player)) {
                    playerIdToAfkTimeMessage.put(player, "No active games found with this user");
                }
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), "");
    }

    private String getUsersAFKTime(List<Game> games, String playerId) {
        for (Game game : games) {
            if (game.isHasEnded()) {
                continue;
            }
            for (Player player : game.getRealPlayers()) {
                if (player.getUserID().equalsIgnoreCase(playerId)) {
                    return player.getRepresentationUnfogged() + "afk hours are: " + player.getHoursThatPlayerIsAFK().replace(";", ", ") + "\n";
                }
            }
        }
        return null;
    }
}