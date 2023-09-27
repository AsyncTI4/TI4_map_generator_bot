package ti4.commands.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListMyGames extends HelpSubcommandData {
    public ListMyGames() {
        super(Constants.LIST_MY_GAMES, "List all of your games you are currently in");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_MY_TURN, "True to only show games where it is your turn"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_AVERAGE_TURN_TIME, "True to show average turn time as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_SECONDARIES, "True to show secondaries you need to follow in each game (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean onlyMyTurn = event.getOption(Constants.IS_MY_TURN, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showAverageTurnTime = event.getOption(Constants.SHOW_AVERAGE_TURN_TIME, false, OptionMapping::getAsBoolean);
        boolean showSecondaries = event.getOption(Constants.SHOW_SECONDARIES, false, OptionMapping::getAsBoolean);

        User user = event.getUser();
        String userID = user.getId();

        Predicate<Game> onlyMyTurnFilter = onlyMyTurn ? m -> m.getActivePlayer() != null && m.getActivePlayer().equals(userID) : m -> true;
        Predicate<Game> endedGamesFilter = includeEndedGames ? m -> m.getPlayerIDs().contains(userID) : m -> !m.isHasEnded() && !m.isFoWMode() && m.getPlayerIDs().contains(userID);

        Comparator<Game> mapSort = Comparator.comparing(Game::getName);

        List<Game> games = GameManager.getInstance().getGameNameToGame().values().stream().filter(onlyMyTurnFilter).filter(endedGamesFilter).sorted(mapSort).toList();

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (Game playerGame : games) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            sb.append(getPlayerMapListRepresentation(playerGame, userID, showAverageTurnTime, showSecondaries));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), user.getName() + "'s Game List", sb.toString());
    }

    private String getPlayerMapListRepresentation(Game playerGame, String userID, boolean showAverageTurnTime, boolean showSecondaries) {
        Player player = playerGame.getPlayer(userID);
        if (player == null) return "";
        String gameNameAndChannelLink = playerGame.getActionsChannel() == null ? playerGame.getName() : playerGame.getActionsChannel().getAsMention();
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getFactionIconFromDiscord(player.getFaction()));
        sb.append("**").append(gameNameAndChannelLink).append("**");
        if (playerGame.getActivePlayer() != null && playerGame.getActivePlayer().equals(userID)) sb.append("  **[__IT IS YOUR TURN__]**");
        if (showAverageTurnTime) sb.append("  [Average Turn Time: `").append(playerAverageMapTurnLength(player)).append("`]");
        if (playerGame.isHasEnded()) sb.append("  [GAME HAS ENDED]");
        if (showSecondaries) {
            List<String> secondaries = new ArrayList<>();
            for (int sc : playerGame.getPlayedSCs()) {
                if (!player.hasFollowedSC(sc) && !player.getSCs().contains(sc)) {
                    secondaries.add(Helper.getSCBackRepresentation(playerGame, sc));
                }
            }
            if (!secondaries.isEmpty()) {
                sb.append("\n> Please follow: ").append(String.join(" ", secondaries));
            }
        }
        return sb.toString();
    }

    private static String playerAverageMapTurnLength(Player player) {
        long totalMillis = player.getTotalTurnTime();
        int numTurns = player.getNumberTurns();
        if (numTurns == 0 || totalMillis == 0) {
            return String.format("%02dh:%02dm:%02ds", 0, 0, 0);
        }

        long total = totalMillis / numTurns;

        total = total / 1000; //total seconds (truncates)
        long seconds = total % 60;

        total = total / 60; //total minutes (truncates)
        long minutes = total % 60;
        long hours = total / 60; //total hours (truncates)
        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }

}
