package ti4.service.actioncard;

import java.util.Calendar;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.model.LeaderModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.GMService;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class SabotageService {

    private static final Set<String> SHATTER_CARD_ALIASES = Set.of("tf-shatter1", "tf-shatter2");
    private static final Set<String> SABOTAGE_CARD_ALIASES = Set.of("sabo1", "sabo2", "sabo3", "sabo4");
    private static final Set<String> ACD2_SABOTAGE_CARD_ALIASES =
            Set.of("sabotage1_acd2", "sabotage2_acd2", "sabotage3_acd2", "sabotage4_acd2");
    private static final Set<String> ALL_SABOTAGE_CARD_ALIASES = Set.of(
            "sabo1",
            "sabo2",
            "sabo3",
            "sabo4",
            "sabotage_ds",
            "sabotage1_acd2",
            "sabotage2_acd2",
            "sabotage3_acd2",
            "sabotage4_acd2",
            "tf-shatter1",
            "tf-shatter2");

    public static boolean couldFeasiblySabotage(Player player, Game game) {
        if (player.isNpc()) {
            return false;
        }

        if (couldUseInstinctTraining(player) || couldUseWatcherMech(player, game)) {
            return true;
        }

        if (player.getAcCount() == 0) {
            return false;
        }

        if (checkForAllSabotagesDiscarded(game)
                || checkAcd2ForAllSabotagesDiscarded(game)
                || checkForAllShattersDiscarded(game)) {
            return false;
        }

        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }

        return !player.isPassed()
                || game.getActivePlayer() == null
                || (!game.getActivePlayer().hasTech("tp")
                        && !game.getActivePlayer().hasTech("tf-crafty"));
    }

    public static boolean couldUseInstinctTraining(Player player) {
        return player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"));
    }

    public static boolean couldUseWatcherMech(Player player, Game game) {
        return player.hasUnit("empyrean_mech")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Mech)
                        .isEmpty();
    }

    public static boolean canSabotage(Player player, Game game) {
        if (player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            return true;
        }

        if (player.hasUnit("empyrean_mech")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Mech)
                        .isEmpty()) {
            return true;
        }

        if (player.hasUnit("tf-triune")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Fighter)
                        .isEmpty()) {
            return true;
        }

        boolean bigAcDeckGame =
                (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180;
        return (bigAcDeckGame || playerHasSabotage(player))
                && !IsPlayerElectedService.isPlayerElected(game, player, "censure")
                && !IsPlayerElectedService.isPlayerElected(game, player, "absol_censure");
    }

    private static boolean playerHasSabotage(Player player) {
        return player.getActionCards().keySet().stream().anyMatch(ALL_SABOTAGE_CARD_ALIASES::contains);
    }

    public static boolean isSaboAllowed(Game game, Player player) {
        if (game.isTwilightsFallMode()) {
            if (checkForAllShattersDiscarded(game)) {
                return false;
            }
        } else if (checkForAllSabotagesDiscarded(game) || checkAcd2ForAllSabotagesDiscarded(game)) {
            return false;
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "bastioncommander")) {
            GMService.logPlayerActivity(
                    game, player, "Sabotage not allowed due to Last Bastion commander.", null, true);
            if (!game.isFowMode()) {
                return false;
            }
        }
        if (player.hasTech("tf-biosyntheticsynergy")) {
            return false;
        }
        if ((player.hasTech("tp") || player.hasTech("tf-crafty"))
                && game.getActivePlayerID() != null
                && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!p2.isPassed()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static String noSaboReason(Game game, Player player) {
        if (game.isTwilightsFallMode()) {
            if (checkForAllShattersDiscarded(game)) {
                return "All _Shatter_ cards are in the discard.";
            }
        } else if (checkForAllSabotagesDiscarded(game) || checkAcd2ForAllSabotagesDiscarded(game)) {
            return "All _Sabotages_ are in the discard.";
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "bastioncommander")) {
            LeaderModel nipAndTuck = Mapper.getLeader("bastioncommander");
            return "Player has access to the Last Bastion commander, " + nipAndTuck.getNameRepresentation();
        }
        if (player.hasTech("tp")
                && game.getActivePlayerID() != null
                && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) continue;
                if (!p2.isPassed()) return null;
            }
            return "Player has " + FactionEmojis.Yssaril
                    + " _Transparasteel Plating_, and all other players have passed.";
        }
        if (player.hasTech("baarvag")) {
            return "Player has Unyielding Will and thus their ACs cannot be canceled.";
        }
        return null;
    }

    private static boolean checkForAllShattersDiscarded(Game game) {
        return game.getDiscardActionCards().keySet().containsAll(SHATTER_CARD_ALIASES);
    }

    private static boolean checkForAllSabotagesDiscarded(Game game) {
        return game.getDiscardActionCards().keySet().containsAll(SABOTAGE_CARD_ALIASES);
    }

    private static boolean checkAcd2ForAllSabotagesDiscarded(Game game) {
        return game.isAcd2() && game.getDiscardActionCards().keySet().containsAll(ACD2_SABOTAGE_CARD_ALIASES);
    }

    public static void startOfTurnSaboWindowReminders(Game game, Player player) {
        var gameMessages = GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD);
        for (GameMessageManager.GameMessage gameMessage : gameMessages) {
            if (ReactionService.checkForSpecificPlayerReact(gameMessage.messageId(), player, game)) continue;

            game.getMainGameChannel()
                    .retrieveMessageById(gameMessage.messageId())
                    .queue(mainMessage -> {
                        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, gameMessage.messageId());
                        MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                        if (reaction == null) {
                            Calendar rightNow = Calendar.getInstance();
                            if (rightNow.get(Calendar.DAY_OF_YEAR)
                                                    - mainMessage
                                                            .getTimeCreated()
                                                            .getDayOfYear()
                                            > 2
                                    || rightNow.get(Calendar.DAY_OF_YEAR)
                                                    - mainMessage
                                                            .getTimeCreated()
                                                            .getDayOfYear()
                                            < -100) {
                                GameMessageManager.remove(game.getName(), gameMessage.messageId());
                            }
                        }
                    });
        }
    }
}
