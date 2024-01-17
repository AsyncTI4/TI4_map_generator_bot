package ti4.commands.statistics;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;

public final class GameStatisticFilterer {

  public static final String PLAYER_COUNT_FILTER = "player_count";
  public static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
  public static final String GAME_TYPE_FILTER = "game_type";
  public static final String FOG_FILTER = "is_fog";
  public static final String HOMEBREW_FILTER = "has_homebrew";
  public static final String HAS_WINNER_FILTER = "has_winner";

  private GameStatisticFilterer() {}

  public static List<Game> getFilteredGames(SlashCommandInteractionEvent event) {
    return GameManager.getInstance().getGameNameToGame().values().stream()
        .filter(game -> filterOnPlayerCount(event, game))
        .filter(game -> filterOnVictoryPointGoal(event, game))
        .filter(game -> filterOnGameType(event, game))
        .filter(game -> filterOnFogType(event, game))
        .filter(game -> filterOnHomebrew(event, game))
        .filter(game -> filterOnHasWinner(event, game))
        .toList();
  }

  private static boolean filterOnFogType(SlashCommandInteractionEvent event, Game game) {
    Boolean fogFilter = event.getOption(FOG_FILTER, null, OptionMapping::getAsBoolean);
    return fogFilter == null
        || (fogFilter && (game.isFoWMode() || game.isLightFogMode()))
        || (!fogFilter && (!game.isFoWMode() && !game.isLightFogMode())) ;
  }

  private static boolean filterOnGameType(SlashCommandInteractionEvent event, Game game) {
    String gameTypeFilter = event.getOption(GAME_TYPE_FILTER, null, OptionMapping::getAsString);
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
        return "public_stage_1_objectives_little_omega".equals(game.getStage1PublicDeckID())
            || "public_stage_2_objectives_little_omega".equals(game.getStage2PublicDeckID())
            || "agendas_little_omega".equals(game.getAgendaDeckID());
      }
      default -> {
        return false;
      }
    }
  }

  private static boolean filterOnHasWinner(SlashCommandInteractionEvent event, Game game) {
    Boolean hasWinnerFilter = event.getOption(HAS_WINNER_FILTER, null, OptionMapping::getAsBoolean);
    return hasWinnerFilter == null || (hasWinnerFilter && getWinner(game) != null) || (!hasWinnerFilter && getWinner(game) == null);
  }

  private static boolean filterOnHomebrew(SlashCommandInteractionEvent event, Game game) {
    Boolean homebrewFilter = event.getOption(HOMEBREW_FILTER, null, OptionMapping::getAsBoolean);
    return homebrewFilter == null || game.hasHomebrew() == homebrewFilter;
  }

  private static boolean isDiscordantStarsGame(Game game) {
    return game.isDiscordantStarsMode() ||
        Mapper.getFactions().stream()
            .filter(faction -> "ds".equals(faction.getSource().name()))
            .anyMatch(faction -> game.getFactions().contains(faction.getAlias()));
  }

  private static boolean filterOnVictoryPointGoal(SlashCommandInteractionEvent event, Game game) {
    int victoryPointGoal = event.getOption(VICTORY_POINT_GOAL_FILTER, 0, OptionMapping::getAsInt);
    return victoryPointGoal <= 0 || game.getVp() == victoryPointGoal;
  }

  private static boolean filterOnPlayerCount(SlashCommandInteractionEvent event, Game game) {
    int playerCountFilter = event.getOption(PLAYER_COUNT_FILTER, 0, OptionMapping::getAsInt);
    return playerCountFilter <= 0 || game.getPlayerCountForMap() == playerCountFilter;
  }

  public static Player getWinner(Game game) {
    Player winner = null;
    for (Player player : game.getRealPlayers()) {
      if (game.getVp() <= player.getTotalVictoryPoints()) {
        if (winner == null) {
          winner = player;
        } else if (isNotEmpty(player.getSCs()) && isNotEmpty(winner.getSCs())) {
          winner = getLowestInitiativePlayer(player, winner);
        } else {
          return null;
        }
      }
    }
    return winner;
  }

  private static Player getLowestInitiativePlayer(Player player1, Player player2) {
    if (Collections.min(player1.getSCs()) < Collections.min(player2.getSCs())) {
      return player1;
    }
    return player2;
  }

}
