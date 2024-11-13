package ti4.commands.search;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class SearchMyGames extends SearchSubcommandData {

    public SearchMyGames() {
        super(Constants.SEARCH_MY_GAMES, "List all of your games you are currently in");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_MY_TURN, "True to only show games where it is your turn"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_AVERAGE_TURN_TIME, "True to show average turn time as well (default = false)"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to Show"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_spectate", "Do not show games you are spectating (default = true)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_aborted", "Do not show games that have ended without a winner (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean onlyMyTurn = event.getOption(Constants.IS_MY_TURN, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showAverageTurnTime = event.getOption(Constants.SHOW_AVERAGE_TURN_TIME, false, OptionMapping::getAsBoolean);
        boolean ignoreSpectate = event.getOption("ignore_spectate", true, OptionMapping::getAsBoolean);
        boolean ignoreAborted = event.getOption("ignore_aborted", true, OptionMapping::getAsBoolean);

        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        searchGames(user, event, onlyMyTurn, includeEndedGames, showAverageTurnTime, ignoreSpectate, ignoreAborted, false);
    }

    public static int searchGames(User user, GenericInteractionCreateEvent event, boolean onlyMyTurn, boolean includeEndedGames, boolean showAverageTurnTime,
                                  boolean ignoreSpectate, boolean ignoreAborted, boolean wantNum) {
        Predicate<Game> ignoreSpectateFilter = ignoreSpectate ?
                game -> game.getRealPlayers().stream().anyMatch(player -> player.getUserID().equals(user.getId())) :
                game -> game.getRealPlayers().stream().anyMatch(player -> player.getUserID().equals(user.getId()));
        Predicate<Game> onlyMyTurnFilter = onlyMyTurn ?
                game -> Objects.equals(game.getActivePlayerID(), user.getId()) :
                game -> true;
        Predicate<Game> endedGamesFilter = includeEndedGames ?
                game -> true :
                game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<Game> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<Game> ignoreAbortedFilter = ignoreAborted ?
                game -> !game.isHasEnded() || game.hasWinner() :
                game -> true;
        Predicate<Game> allFilterPredicates = ignoreSpectateFilter.and(onlyMyTurnFilter).and(endedGamesFilter).and(onlyEndedFoWGames)
                .and(ignoreAbortedFilter);

        var filteredManagedGames = GameManager.getGameNameToGame().values().stream()
                .filter(allFilterPredicates)
                .sorted(Comparator.comparing(Game::getGameNameForSorting))
                .toList();

        if (wantNum) {
            return filteredManagedGames.size();
        }

        int index = 1;
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (var game : filteredManagedGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            sb.append(getGameListRepresentation(game, user.getId(), showAverageTurnTime));
            sb.append("\n");
            index++;
        }
        if (event instanceof SlashCommandInteractionEvent slash) {
            MessageHelper.sendMessageToThread(slash.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        if (event instanceof ButtonInteractionEvent butt) {
            MessageHelper.sendMessageToThread(butt.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        return filteredManagedGames.size();
    }

    public static String getGameListRepresentation(Game game, String userId, boolean showAverageTurnTime) {
        var actionsChannel = game.getActionsChannel();
        String gameChannelLink = actionsChannel == null ? "" : actionsChannel.getAsMention();

        StringBuilder sb = new StringBuilder();
        sb.append(Emojis.getFactionIconFromDiscord(game.getPlayerToFaction().get(userId)));
        sb.append("**").append(game.getName()).append("**");
        sb.append(gameChannelLink);
        if (showAverageTurnTime) sb.append("  [Average Turn Time: `").append(averageTurnLengthForGame(game, userId)).append("`]");
        var player = game.getPlayer(userId);
        if (game.getWinner().isPresent() && Objects.equals(player, game.getWinner().get())) sb.append(" **👑WINNER👑**");
        if (game.getActivePlayerID() != null && game.getActivePlayerID().equals(userId) && !game.isHasEnded()) sb.append(" **[__IT IS YOUR TURN__]**");
        if (game.isHasEnded()) sb.append(" [GAME IS OVER]");
        return sb.toString();
    }

    private static String averageTurnLengthForGame(Game game, String playerId) {
        long totalMillis = game.getPlayerToTotalTurns().get(playerId);
        int numTurns = game.getPlayerToTotalTurns().get(playerId);
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
