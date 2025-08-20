package ti4.commands.statistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class GameStatisticsFilterer {

    public static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String MIN_PLAYER_COUNT_FILTER = "min_player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    public static final String GAME_TYPES_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";
    private static final String HAS_WINNER_FILTER = "has_winner";
    public static final String WINNING_FACTION_FILTER = "winning_faction";
    public static final String EXCLUDED_GAME_TYPES_FILTER = "exclude_game_types";
    private static final String HAS_GALACTIC_EVENT_FILTER = "has_galactic_event";
    private static final String HAS_SCENARIO_FILTER = "has_scenario";

    private static final int MINIMUM_ROUND = 3;

    public static List<OptionData> gameStatsFilters() {
        List<OptionData> filters = new ArrayList<>();
        filters.add(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter games by player count, e.g. 3-8"));
        filters.add(new OptionData(
                OptionType.INTEGER, MIN_PLAYER_COUNT_FILTER, "Filter games by minimum player count, e.g. 3-8"));
        filters.add(new OptionData(
                OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter games by victory point goal, e.g. 10-14"));
        filters.add(new OptionData(
                        OptionType.STRING,
                        GAME_TYPES_FILTER,
                        "Filter games by game type, comma seperated, e.g. base, pok, absol, ds, action_deck_2")
                .setAutoComplete(true));
        filters.add(new OptionData(
                        OptionType.STRING,
                        EXCLUDED_GAME_TYPES_FILTER,
                        "Filter excluded games by game type, comma seperated, e.g. base, pok, absol, ds, action_deck_2")
                .setAutoComplete(true));
        filters.add(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter games by if the game is a fog game"));
        filters.add(
                new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter games by if the game has any homebrew"));
        filters.add(new OptionData(OptionType.BOOLEAN, HAS_WINNER_FILTER, "Filter games by if the game has a winner"));
        filters.add(new OptionData(
                OptionType.BOOLEAN, HAS_GALACTIC_EVENT_FILTER, "Filter games by if the game has a galactic event"));
        filters.add(
                new OptionData(OptionType.BOOLEAN, HAS_SCENARIO_FILTER, "Filter games by if the game has a scenario"));
        return filters;
    }

    public static Predicate<Game> getGamesFilterForWonGame(SlashCommandInteractionEvent event) {
        return getGamesFilter(event, true);
    }

    public static Predicate<Game> getGamesFilter(SlashCommandInteractionEvent event) {
        return getGamesFilter(event, null);
    }

    private static Predicate<Game> getGamesFilter(SlashCommandInteractionEvent event, Boolean defaultHasWinner) {
        Integer playerCountFilter = event.getOption(PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer minPlayerCountFilter = event.getOption(MIN_PLAYER_COUNT_FILTER, null, OptionMapping::getAsInt);
        Integer victoryPointGoalFilter = event.getOption(VICTORY_POINT_GOAL_FILTER, null, OptionMapping::getAsInt);
        Boolean homebrewFilter = event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean);
        Boolean hasWinnerFilter = event.getOption(HAS_WINNER_FILTER, defaultHasWinner, OptionMapping::getAsBoolean);
        String gameTypesFilter = event.getOption(GAME_TYPES_FILTER, null, OptionMapping::getAsString);
        String excludedGameTypesFilter = event.getOption(EXCLUDED_GAME_TYPES_FILTER, null, OptionMapping::getAsString);
        Boolean fogFilter = event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean);
        String winningFactionFilter = event.getOption(WINNING_FACTION_FILTER, null, OptionMapping::getAsString);
        Boolean galacticEventFilter = event.getOption(HAS_GALACTIC_EVENT_FILTER, null, OptionMapping::getAsBoolean);
        Boolean scenarioFilter = event.getOption(HAS_SCENARIO_FILTER, null, OptionMapping::getAsBoolean);

        Predicate<Game> playerCountPredicate = game -> filterOnPlayerCount(playerCountFilter, game);
        return playerCountPredicate
                .and(game -> filterOnMinPlayerCount(minPlayerCountFilter, game))
                .and(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
                .and(game -> filterOnGameTypes(gameTypesFilter, game))
                .and(game -> filterOnExcludedGameTypes(excludedGameTypesFilter, game))
                .and(game -> filterOnFogType(fogFilter, game))
                .and(game -> filterOnHomebrew(homebrewFilter, game))
                .and(game -> filterOnHasWinner(hasWinnerFilter, game))
                .and(game -> filterOnWinningFaction(winningFactionFilter, game))
                .and(game -> filterOnGalacticEvent(galacticEventFilter, game))
                .and(game -> filterOnScenario(scenarioFilter, game))
                .and(GameStatisticsFilterer::filterAbortedGames)
                .and(GameStatisticsFilterer::filterEarlyRounds);
    }

    private static boolean filterOnWinningFaction(String winningFactionFilter, Game game) {
        if (winningFactionFilter == null) {
            return true;
        }
        return game.getWinners().stream()
                .anyMatch(winner -> winner.getFaction().equalsIgnoreCase(winningFactionFilter));
    }

    public static Predicate<Game> getNormalFinishedGamesFilter(
            Integer playerCountFilter, Integer victoryPointGoalFilter) {
        Predicate<Game> playerCountPredicate = game -> filterOnPlayerCount(playerCountFilter, game);
        return playerCountPredicate
                .and(game -> filterOnVictoryPointGoal(victoryPointGoalFilter, game))
                .and(game -> filterOnIsNormal(Boolean.TRUE, game))
                .and(game -> filterOnHasWinner(Boolean.TRUE, game))
                .and(GameStatisticsFilterer::filterAbortedGames)
                .and(GameStatisticsFilterer::filterEarlyRounds);
    }

    private static boolean filterOnFogType(Boolean fogFilter, Game game) {
        if (fogFilter == null) {
            return true;
        }
        boolean isFogMode = game.isFowMode() || game.isLightFogMode();
        return fogFilter == isFogMode;
    }

    private static boolean filterOnGameTypes(String gameTypesFilter, Game game) {
        if (gameTypesFilter == null) {
            return true;
        }
        return Arrays.stream(gameTypesFilter.split(","))
                .map(String::strip)
                .allMatch(gameType -> hasGameType(gameType, game));
    }

    private static boolean hasGameType(String type, Game game) {
        return switch (type) {
            case "base" -> game.isBaseGameMode();
            case "absol" -> game.isAbsolMode();
            case "ds" -> isDiscordantStarsGame(game);
            case "pok" -> !game.isBaseGameMode();
            case "action_deck_2" -> "action_deck_2".equals(game.getAcDeckID());
            case "franken" -> game.isFrankenGame();
            case "milty_mod" -> isMiltyModGame(game);
            case "red_tape" -> game.isRedTapeMode();
            case "age_of_exploration" -> game.isAgeOfExplorationMode();
            case "minor_factions" -> game.isMinorFactionsMode();
            case "alliance" -> game.isAllianceMode();
            case "age_of_commerce" -> game.isAgeOfCommerceMode();
            case "facilities" -> game.isFacilitiesMode();
            case "total_war" -> game.isTotalWarMode();
            case "liberation" -> game.isLiberationC4Mode();
            case "ordinian" -> game.isOrdinianC1Mode();
            default -> false;
        };
    }

    private static boolean filterOnExcludedGameTypes(String excludedGameTypesFilter, Game game) {
        if (excludedGameTypesFilter == null) {
            return true;
        }
        return Arrays.stream(excludedGameTypesFilter.split(","))
                .map(String::strip)
                .noneMatch(gameType -> hasGameType(gameType, game));
    }

    private static boolean filterOnHasWinner(Boolean hasWinnerFilter, Game game) {
        return hasWinnerFilter == null
                || (hasWinnerFilter && game.getWinner().isPresent())
                || (!hasWinnerFilter && game.getWinner().isEmpty());
    }

    private static boolean filterAbortedGames(Game game) {
        return !game.isHasEnded() || game.getWinner().isPresent();
    }

    private static boolean filterEarlyRounds(Game game) {
        return game.getRound() >= MINIMUM_ROUND;
    }

    private static boolean filterOnHomebrew(Boolean homebrewFilter, Game game) {
        return homebrewFilter == null || homebrewFilter == game.hasHomebrew();
    }

    private static boolean filterOnIsNormal(Boolean isNormalFilter, Game game) {
        return isNormalFilter == null || isNormalFilter == game.isNormalGame();
    }

    private static boolean filterOnGalacticEvent(Boolean galacticEventFilter, Game game) {
        if (galacticEventFilter == null) {
            return true;
        }
        boolean hasEvent = game.isAgeOfExplorationMode()
                || game.isTotalWarMode()
                || game.isAgeOfCommerceMode()
                || game.isMinorFactionsMode();
        return galacticEventFilter == hasEvent;
    }

    private static boolean filterOnScenario(Boolean scenarioFilter, Game game) {
        if (scenarioFilter == null) {
            return true;
        }
        boolean hasScenario = game.isLiberationC4Mode() || game.isOrdinianC1Mode();
        return scenarioFilter == hasScenario;
    }

    private static boolean isDiscordantStarsGame(Game game) {
        return game.isDiscordantStarsMode()
                || Mapper.getFactionsValues().stream()
                        .filter(faction -> faction.getSource() == ComponentSource.ds)
                        .anyMatch(faction -> game.getFactions().contains(faction.getAlias()));
    }

    private static boolean isMiltyModGame(Game game) {
        return game.isMiltyModMode()
                || Mapper.getFactionsValues().stream()
                        .filter(faction -> faction.getSource() == ComponentSource.miltymod)
                        .anyMatch(faction -> game.getFactions().contains(faction.getAlias()));
    }

    private static boolean filterOnVictoryPointGoal(Integer victoryPointGoal, Game game) {
        return victoryPointGoal == null || victoryPointGoal == game.getVp();
    }

    private static boolean filterOnPlayerCount(Integer playerCount, Game game) {
        return playerCount == null
                || playerCount == game.getRealAndEliminatedPlayers().size();
    }

    private static boolean filterOnMinPlayerCount(Integer minPlayerCount, Game game) {
        return minPlayerCount == null
                || minPlayerCount <= game.getRealAndEliminatedPlayers().size();
    }
}
