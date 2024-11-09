package ti4.commands.search;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class SearchMyGames extends SearchSubcommandData {

    public SearchMyGames() {
        super(Constants.SEARCH_MY_GAMES, "List all of your games you are currently in");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_MY_TURN, "True to only show games where it is your turn"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_AVERAGE_TURN_TIME, "True to show average turn time as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_SECONDARIES, "True to show secondaries you need to follow in each game (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_GAME_MODES, "True to the game's set modes (default = false)"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to Show"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_spectate", "Do not show games you are spectating (default = true)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_aborted", "Do not show games that have ended without a winner (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean onlyMyTurn = event.getOption(Constants.IS_MY_TURN, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showAverageTurnTime = event.getOption(Constants.SHOW_AVERAGE_TURN_TIME, false, OptionMapping::getAsBoolean);
        boolean showSecondaries = event.getOption(Constants.SHOW_SECONDARIES, true, OptionMapping::getAsBoolean);
        boolean showGameModes = event.getOption(Constants.SHOW_GAME_MODES, false, OptionMapping::getAsBoolean);
        boolean ignoreSpectate = event.getOption("ignore_spectate", true, OptionMapping::getAsBoolean);
        boolean ignoreAborted = event.getOption("ignore_aborted", true, OptionMapping::getAsBoolean);

        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        searchGames(user, event, onlyMyTurn, includeEndedGames, showAverageTurnTime, showSecondaries, showGameModes, ignoreSpectate, ignoreAborted, false);
    }

    public static int searchGames(User user, GenericInteractionCreateEvent event, boolean onlyMyTurn, boolean includeEndedGames, boolean showAverageTurnTime,
                                  boolean showSecondaries, boolean showGameModes, boolean ignoreSpectate, boolean ignoreAborted, boolean wantNum) {
        String userID = user.getId();

        Predicate<Game> ignoreSpectateFilter = ignoreSpectate ? game -> game.getRealPlayerIDs().contains(userID) : game -> game.getPlayerIDs().contains(userID);
        Predicate<Game> onlyMyTurnFilter = onlyMyTurn ? game -> game.getActivePlayerID() != null && game.getActivePlayerID().equals(userID) : game -> true;
        Predicate<Game> endedGamesFilter = includeEndedGames ? game -> true : game -> !game.isHasEnded() && !game.isFowMode();
        Predicate<Game> onlyEndedFoWGames = game -> !game.isFowMode() || game.isHasEnded();
        Predicate<Game> ignoreAbortedFilter = ignoreAborted ? game -> !game.isHasEnded() || game.getWinner().isPresent() : game -> true;
        Predicate<Game> allFilterPredicates = ignoreSpectateFilter.and(onlyMyTurnFilter).and(endedGamesFilter).and(onlyEndedFoWGames).and(ignoreAbortedFilter);

        Comparator<Game> mapSort = Comparator.comparing(Game::getGameNameForSorting);

        List<Game> filteredGames = new ArrayList<>();
        int currentPage = 0;
        GameManager.PagedGames pagedGames;
        do {
            pagedGames = GameManager.getGamesPage(currentPage++);
            var pagedFilteredGames = pagedGames.getGames().stream()
                    .filter(allFilterPredicates)
                    .sorted(mapSort)
                    .toList();
            filteredGames.addAll(pagedFilteredGames);
        } while (pagedGames.hasNextPage());

        int index = 1;
        if (wantNum) {
            return filteredGames.size();
        }
        StringBuilder sb = new StringBuilder("**__").append(user.getName()).append("'s Games__**\n");
        for (Game playerGame : filteredGames) {
            sb.append("`").append(Helper.leftpad("" + index, 2)).append(".`");
            sb.append(getPlayerMapListRepresentation(playerGame, userID, showAverageTurnTime, showSecondaries, showGameModes));
            sb.append("\n");
            index++;
        }
        if (event instanceof SlashCommandInteractionEvent slash) {
            MessageHelper.sendMessageToThread(slash.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        if (event instanceof ButtonInteractionEvent butt) {
            MessageHelper.sendMessageToThread(butt.getChannel(), user.getName() + "'s Game List", sb.toString());
        }
        return filteredGames.size();
    }

    public static String getPlayerMapListRepresentation(Game game, String userID, boolean showAverageTurnTime, boolean showSecondaries, boolean showGameModes) {
        Player player = game.getPlayer(userID);
        if (player == null) return "";
        String gameChannelLink = game.getActionsChannel() == null ? "" : game.getActionsChannel().getAsMention();
        StringBuilder sb = new StringBuilder();
        if (Mapper.isValidFaction(player.getFaction())) sb.append(player.getFactionEmoji());
        if (player.getColor() != null && !"null".equals(player.getColor())) sb.append(Emojis.getColorEmoji(player.getColor()));
        sb.append("**").append(game.getName()).append("**");
        sb.append(gameChannelLink);
        if (showAverageTurnTime) sb.append("  [Average Turn Time: `").append(playerAverageMapTurnLength(player)).append("`]");
        if (game.getWinner().filter(winner -> winner.getUserID().equals(player.getUserID())).isPresent()) sb.append(" **👑WINNER👑**");
        if (game.getActivePlayerID() != null && game.getActivePlayerID().equals(userID) && !game.isHasEnded()) sb.append(" **[__IT IS YOUR TURN__]**");
        if (showSecondaries && !game.isHasEnded()) {
            List<String> secondaries = new ArrayList<>();
            for (int sc : game.getPlayedSCs()) {
                if (!player.hasFollowedSC(sc) && !player.getSCs().contains(sc)) {
                    secondaries.add(Emojis.getSCBackEmojiFromInteger(sc));
                }
            }
            if (!secondaries.isEmpty()) {
                sb.append("\n> Please follow: ").append(String.join(" ", secondaries));
            }
        }
        if (showGameModes) sb.append(" | Game Modes: ").append(game.getGameModesText());
        if (game.isHasEnded()) sb.append(" [GAME IS OVER]");
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
