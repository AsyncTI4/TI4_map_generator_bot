package ti4.commands.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.image.Mapper;
import ti4.map.Game;

@UtilityClass
public class GameStatisticsFilterer {

    public static final String PLAYER_COUNT_FILTER = "player_count";
    public static final String MIN_PLAYER_COUNT_FILTER = "min_player_count";
    public static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    public static final String GAME_TYPE_FILTER = "game_type";
    public static final String FOG_FILTER = "is_fog";
    public static final String HOMEBREW_FILTER = "has_homebrew";
    public static final String HAS_WINNER_FILTER = "has_winner";
    public static final String WINNING_FACTION_FILTER = "winning_faction";

    public static List<OptionData> gameStatsFilters() {
        List<OptionData> filters = new ArrayList<>();
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticsFilterer.PLAYER_COUNT_FILTER, "Filter games by player count, e.g. 3-8"));
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticsFilterer.MIN_PLAYER_COUNT_FILTER, "Filter games by minimum player count, e.g. 3-8"));
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticsFilterer.VICTORY_POINT_GOAL_FILTER, "Filter games by victory point goal, e.g. 10-14"));
        filters.add(new OptionData(OptionType.STRING, GameStatisticsFilterer.GAME_TYPE_FILTER, "Filter games by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticsFilterer.FOG_FILTER, "Filter games by if the game is a fog game"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticsFilterer.HOMEBREW_FILTER, "Filter games by if the game has any homebrew"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticsFilterer.HAS_WINNER_FILTER, "Filter games by if the game has a winner"));
        filters.add(new OptionData(OptionType.STRING, GameStatisticsFilterer.WINNING_FACTION_FILTER, "Filter games by if the game was won by said faction")
            .setAutoComplete(true));
        return filters;
    }

    public static Predicate<Game> getGamesFilter(SlashCommandInteractionEvent event) {
        Integer playerCountFilter = event.getOption(PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer minPlayerCountFilter = event.getOption(MIN_PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer victoryPointGoalFilter = event.getOption(VICTORY_POINT_GOAL_FILTER, null, OptionMapping::getAsInt);
        Boolean homebrewFilter = event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean);
        Boolean hasWinnerFilter = event.getOption(HAS_WINNER_FILTER, true, OptionMapping::getAsBoolean);
        String gameTypeFilter = event.getOption(GAME_TYPE_FILTER, null, OptionMapping::getAsString);
        Boolean fogFilter = event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean);
        String winningFactionFilter = event.getOption(WINNING_FACTION_FILTER, null, OptionMapping::getAsString);

        Predicate<Game> playerCountPredicate = game -> filterOnPlayerCount(playerCountFilter, game);
        return playerCountPredicate
            .and(game -> filterOnMinPlayerCount(minPlayerCountFilter, game))
            .and(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
            .and(game -> filterOnGameType(gameTypeFilter, game))
            .and(game -> filterOnFogType(fogFilter, game))
            .and(game -> filterOnHomebrew(homebrewFilter, game))
            .and(game -> filterOnHasWinner(hasWinnerFilter, game))
            .and(game -> filterOnWinningFaction(winningFactionFilter, game))
            .and(GameStatisticsFilterer::filterAbortedGames);
    }

    private static boolean filterOnWinningFaction(String winningFactionFilter, Game game) {
        if (winningFactionFilter == null) {
            return true;
        }
        return game.getWinner().isPresent() && game.getWinner().get().getFaction().equals(winningFactionFilter);
    }

    public static Predicate<Game> getNormalFinishedGamesFilter(Integer playerCountFilter, Integer victoryPointGoalFilter) {
        Predicate<Game> playerCountPredicate = game -> filterOnPlayerCount(playerCountFilter, game);
        return playerCountPredicate
            .and(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
            .and(game -> filterOnHomebrew(Boolean.FALSE, game))
            .and(game -> filterOnHasWinner(Boolean.TRUE, game))
            .and(GameStatisticsFilterer::filterAbortedGames);
    }

    private static boolean filterOnFogType(Boolean fogFilter, Game game) {
        if (fogFilter == null) {
            return true;
        }
        boolean isFogMode = game.isFowMode() || game.isLightFogMode();
        return fogFilter == isFogMode;
    }

    private static boolean filterOnGameType(String gameTypeFilter, Game game) {
        if (gameTypeFilter == null) {
            return true;
        }
        return switch (gameTypeFilter) {
            case "base" -> game.isBaseGameMode();
            case "absol" -> game.isAbsolMode();
            case "ds" -> isDiscordantStarsGame(game);
            case "pok" -> !game.isBaseGameMode();
            case "action_deck_2" -> "action_deck_2".equals(game.getAcDeckID());
            case "little_omega" -> game.isLittleOmega();
            case "franken" -> game.isFrankenGame();
            case "milty_mod" -> game.isMiltyModMode();
            case "red_tape" -> game.isRedTapeMode();
            case "age_of_exploration" -> game.isAgeOfExplorationMode();
            case "minor_factions" -> game.isMinorFactionsMode();
            case "alliance" -> game.isAllianceMode();
            default -> false;
        };
    }

    private static boolean filterOnHasWinner(Boolean hasWinnerFilter, Game game) {
        return hasWinnerFilter == null || (hasWinnerFilter && game.getWinner().isPresent()) || (!hasWinnerFilter && game.getWinner().isEmpty());
    }

    private static boolean filterAbortedGames(Game game) {
        return !game.isHasEnded() || game.getWinner().isPresent();
    }

    private static boolean filterOnHomebrew(Boolean homebrewFilter, Game game) {
        return homebrewFilter == null || homebrewFilter == game.hasHomebrew();
    }

    private static boolean isDiscordantStarsGame(Game game) {
        return game.isDiscordantStarsMode() ||
            Mapper.getFactions().stream()
                .filter(faction -> "ds".equals(faction.getSource().name()))
                .anyMatch(faction -> game.getFactions().contains(faction.getAlias()));
    }

    private static boolean filterOnVictoryPointGoal(Integer victoryPointGoal, Game game) {
        return victoryPointGoal == null || victoryPointGoal == game.getVp();
    }

    private static boolean filterOnPlayerCount(Integer playerCount, Game game) {
        return playerCount == null || playerCount == game.getRealAndEliminatedPlayers().size();
    }

    private static boolean filterOnMinPlayerCount(Integer minPlayerCount, Game game) {
        return minPlayerCount == null || minPlayerCount <= game.getRealAndEliminatedPlayers().size();
    }

}
