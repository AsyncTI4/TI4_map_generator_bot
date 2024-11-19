package ti4.commands2.statistics;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GameManager;

@UtilityClass
public class GameStatisticFilterer {

    public static final String PLAYER_COUNT_FILTER = "player_count";
    public static final String MIN_PLAYER_COUNT_FILTER = "min_player_count";
    public static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    public static final String GAME_TYPE_FILTER = "game_type";
    public static final String FOG_FILTER = "is_fog";
    public static final String HOMEBREW_FILTER = "has_homebrew";
    public static final String HAS_WINNER_FILTER = "has_winner";

    public static List<OptionData> gameStatsFilters() {
        List<OptionData> filters = new ArrayList<>();
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticFilterer.PLAYER_COUNT_FILTER, "Filter games of player by player count, e.g. 3-8"));
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticFilterer.MIN_PLAYER_COUNT_FILTER, "Filter games of player by minimum player count, e.g. 3-8"));
        filters.add(new OptionData(OptionType.INTEGER, GameStatisticFilterer.VICTORY_POINT_GOAL_FILTER, "Filter games of player by victory point goal, e.g. 10-14"));
        filters.add(new OptionData(OptionType.STRING, GameStatisticFilterer.GAME_TYPE_FILTER, "Filter games of player by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticFilterer.FOG_FILTER, "Filter games of player by if the game is a fog game"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticFilterer.HOMEBREW_FILTER, "Filter games of player by if the game has any homebrew"));
        filters.add(new OptionData(OptionType.BOOLEAN, GameStatisticFilterer.HAS_WINNER_FILTER, "Filter games of player by if the game has a winner"));
        return filters;
    }

    public static List<Game> getFilteredGames(SlashCommandInteractionEvent event) {
        Integer playerCountFilter = event.getOption(PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer minPlayerCountFilter = event.getOption(MIN_PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer victoryPointGoalFilter = event.getOption(VICTORY_POINT_GOAL_FILTER, null, OptionMapping::getAsInt);
        Boolean homebrewFilter = event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean);
        Boolean hasWinnerFilter = event.getOption(HAS_WINNER_FILTER, true, OptionMapping::getAsBoolean);
        String gameTypeFilter = event.getOption(GAME_TYPE_FILTER, null, OptionMapping::getAsString);
        Boolean fogFilter = event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean);
        return getFilteredGames(playerCountFilter, minPlayerCountFilter, victoryPointGoalFilter, gameTypeFilter, fogFilter, homebrewFilter, hasWinnerFilter);
    }

    public static List<Game> getFilteredGames(Integer playerCountFilter, Integer minPlayerCountFilter, Integer victoryPointGoalFilter, String gameTypeFilter, Boolean fogFilter, Boolean homebrewFilter, Boolean hasWinnerFilter) {
        return GameManager.getGameNameToGame().values().stream()
            .filter(GameStatisticFilterer::filterAbortedGames)
            .filter(game -> filterOnPlayerCount(playerCountFilter, game))
            .filter(game -> filterOnMinPlayerCount(minPlayerCountFilter, game))
            .filter(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
            .filter(game -> filterOnGameType(gameTypeFilter, game))
            .filter(game -> filterOnFogType(fogFilter, game))
            .filter(game -> filterOnHomebrew(homebrewFilter, game))
            .filter(game -> filterOnHasWinner(hasWinnerFilter, game))
            .toList();
    }

    public static List<Game> getNormalFinishedGames(Integer playerCountFilter, Integer victoryPointGoalFilter) {
        return GameManager.getGameNameToGame().values().stream()
            .filter(GameStatisticFilterer::filterAbortedGames)
            .filter(game -> filterOnPlayerCount(playerCountFilter, game))
            .filter(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
            .filter(game -> filterOnHomebrew(Boolean.FALSE, game))
            .filter(game -> filterOnHasWinner(Boolean.TRUE, game))
            .toList();
    }

    private static boolean filterOnFogType(Boolean fogFilter, Game game) {
        return fogFilter == null
            || (fogFilter && (game.isFowMode() || game.isLightFogMode()))
            || (!fogFilter && (!game.isFowMode() && !game.isLightFogMode()));
    }

    private static boolean filterOnGameType(String gameTypeFilter, Game game) {
        if (gameTypeFilter == null) {
            return true;
        }
        switch (gameTypeFilter) {
            case "base" -> {
                return game.isBaseGameMode();
            }
            case "absol" -> {
                return game.isAbsolMode();
            }
            case "ds" -> {
                return isDiscordantStarsGame(game);
            }
            case "pok" -> {
                return !game.isBaseGameMode();
            }
            case "action_deck_2" -> {
                return "action_deck_2".equals(game.getAcDeckID());
            }
            case "little_omega" -> {
                return game.isLittleOmega();
            }
            case "franken" -> {
                return game.isFrankenGame();
            }
            case "milty_mod" -> {
                return game.isMiltyModMode();
            }
            case "red_tape" -> {
                return game.isRedTapeMode();
            }
            case "age_of_exploration" -> {
                return game.isAgeOfExplorationMode();
            }
            case "minor_factions" -> {
                return game.isMinorFactionsMode();
            }
            case "alliance" -> {
                return game.isAllianceMode();
            }
            default -> {
                return false;
            }
        }
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
