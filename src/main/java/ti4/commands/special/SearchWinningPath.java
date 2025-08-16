package ti4.commands.special;

import java.util.HashSet;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PatternHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.WinningPathHelper;

class SearchWinningPath extends Subcommand {

    SearchWinningPath() {
        super(Constants.SEARCH_WINNING_PATH, "List games with the provided winning path");
        addOptions(new OptionData(OptionType.STRING, Constants.WINNING_PATH, "Winning path to search for")
                .setRequired(true));
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchedPath = event.getOption(Constants.WINNING_PATH, OptionMapping::getAsString);

        var foundGames = new HashSet<String>();
        StringBuilder sb = new StringBuilder("__**Games with Winning Path:**__ ")
                .append(searchedPath)
                .append("\n");

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event).and(game -> game.getWinner()
                        .map(winner -> hasWinningPath(game, winner, searchedPath))
                        .orElse(false)),
                game -> {
                    foundGames.add(game.getName());
                    sb.append(formatGame(game)).append("\n");
                });

        if (foundGames.isEmpty()) {
            sb.append("No games match the selected path.");
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Winning Path Games", sb.toString());
    }

    private static boolean hasWinningPath(Game game, Player winner, String searchedPath) {
        return PatternHelper.UNDERSCORE_PATTERN
                .matcher(WinningPathHelper.buildWinningPath(game, winner))
                .replaceAll("") // needed due to Support for the Throne being italicized
                .contains(searchedPath);
    }

    private static String formatGame(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(game.getName()).append("** ");
        sb.append("`").append(game.getCreationDate()).append("`-`");
        if (game.isHasEnded() && game.getEndedDate() > 100) {
            sb.append(Helper.getDateRepresentation(game.getEndedDate()));
        } else {
            sb.append(Helper.getDateRepresentation(game.getLastModifiedDate()));
        }
        sb.append("`  ");
        for (Player player : game.getPlayers().values()) {
            if (!game.isFowMode() && player.getFaction() != null) {
                sb.append(player.getFactionEmoji());
            }
        }
        sb.append(" [").append(game.getGameModesText()).append("] ");
        if (game.isHasEnded()) sb.append(" ENDED");
        return sb.toString();
    }
}
