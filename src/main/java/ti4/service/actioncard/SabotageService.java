package ti4.service.actioncard;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.message.GameMessage;
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
        if (player.isNpc()) return false;

        if (couldUseInstinctTraining(player) || couldUseWatcherMech(player, game) || couldUseTriune(player, game)) {
            return true;
        }

        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }

        if (isAffectedByTransparasteel(player, game)) return false;

        if (playerHasSabotage(player)) return true;

        if (player.getAcCount() == 0) return false;

        return !allSabotagesAreDiscarded(game, player);
    }

    private static boolean isAffectedByTransparasteel(Player player, Game game) {
        if (!player.isPassed() || game.getActivePlayer() == null) return false;

        return game.getActivePlayer().hasTech("tp") || game.getActivePlayer().hasTech("tf-crafty");
    }

    public static boolean couldUseInstinctTraining(Player player) {
        return player.hasTechReady("it") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"));
    }

    public static boolean couldUseWatcherMech(Player player, Game game) {
        return player.hasUnit("empyrean_mech")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Mech)
                        .isEmpty();
    }

    private static boolean couldUseTriune(Player player, Game game) {
        return player.hasUnit("tf-triune")
                && !CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, Units.UnitType.Fighter)
                        .isEmpty();
    }

    public static boolean canSabotage(Player player, Game game) {
        if (couldUseInstinctTraining(player) || couldUseWatcherMech(player, game) || couldUseTriune(player, game)) {
            return true;
        }

        boolean bigAcDeckGame =
                (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180;
        return bigAcDeckGame || playerHasSabotage(player);
    }

    private static boolean playerHasSabotage(Player player) {
        return player.getPlayableActionCards().stream().anyMatch(ALL_SABOTAGE_CARD_ALIASES::contains);
    }

    public static boolean isSaboAllowed(Game game, Player player) {
        if (allSabotagesAreDiscarded(game, player)) return false;

        if (game.playerHasLeaderUnlockedOrAlliance(player, "bastioncommander")) {
            GMService.logPlayerActivity(game, player, "Sabotage not allowed due to Nip and Tuck.", null, true);
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
        if (allSabotagesAreDiscarded(game, player)) {
            if (game.isTwilightsFallMode()) return "All _Shatter_ cards are in the discard.";
            return "All _Sabotages_ are in the discard.";
        }

        String playerName = game.isFowMode() ? "Player" : player.getRepresentationNoPing();
        if (game.playerHasLeaderUnlockedOrAlliance(player, "bastioncommander")) {
            LeaderModel nipAndTuck = Mapper.getLeader("bastioncommander");
            return playerName + " has access to the Last Bastion commander, " + nipAndTuck.getNameRepresentation()
                    + ".";
        }
        if (player.hasTech("tf-biosyntheticsynergy")) {
            return playerName + " has _Bio Synthetic Synergy_.";
        }
        if (player.hasTech("tp")
                && game.getActivePlayerID() != null
                && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) continue;
                if (!p2.isPassed()) return null;
            }
            return playerName + " has " + FactionEmojis.Yssaril
                    + " _Transparasteel Plating_, and all other players have passed.";
        }
        if (player.hasTech("tf-crafty")
                && game.getActivePlayerID() != null
                && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) continue;
                if (!p2.isPassed()) return null;
            }
            return playerName + " has " + FactionEmojis.Yssaril + " _Crafty_, and all other players have passed.";
        }
        if (player.hasTech("baarvag")) {
            return playerName + " has _Unyielding Will_ and, thus their action cards cannot be cancelled.";
        }
        return null;
    }

    private static final long ACD2_SABOTAGE_REMOVAL_CUTOFF = ZonedDateTime.of(
                    2026, 5, 19, 0, 0, 0, 0, ZoneId.of("America/New_York"))
            .toInstant()
            .toEpochMilli();

    private static boolean allSabotagesAreDiscarded(Game game, Player player) {
        Set<String> sabotageCardAliases = getSabotageCardAliasesToCheck(game);
        return sabotageCardAliases.stream().allMatch(alias -> isActionCardNotPlayable(game, player, alias));
    }

    private static Set<String> getSabotageCardAliasesToCheck(Game game) {
        Set<String> trackedSabotages = getTrackedSabotageCardAliases(game);
        if (hasTrackedActionCardState(game)) {
            return trackedSabotages;
        }

        if (game.isAcd2() && game.getCreationDateTime() < ACD2_SABOTAGE_REMOVAL_CUTOFF) {
            return ACD2_SABOTAGE_CARD_ALIASES;
        }

        Set<String> deckSabotages = new HashSet<>();
        Mapper.getDeck(game.getAcDeckID()).getCardIDs().stream()
                .filter(ALL_SABOTAGE_CARD_ALIASES::contains)
                .forEach(deckSabotages::add);
        return deckSabotages;
    }

    private static Set<String> getTrackedSabotageCardAliases(Game game) {
        Set<String> actionCards = new HashSet<>();
        if (game.getActionCards() != null) {
            actionCards.addAll(game.getActionCards());
        }
        actionCards.addAll(game.getDiscardActionCards().keySet());
        actionCards.addAll(game.getDiscardACStatus().keySet());
        for (Player player : game.getPlayers().values()) {
            actionCards.addAll(player.getActionCards().keySet());
        }

        Set<String> trackedSabotages = new HashSet<>();
        actionCards.stream().filter(ALL_SABOTAGE_CARD_ALIASES::contains).forEach(trackedSabotages::add);
        return trackedSabotages;
    }

    private static boolean hasTrackedActionCardState(Game game) {
        if (game.getActionCards() != null) return true;
        if (!game.getDiscardActionCards().isEmpty()) return true;
        if (!game.getDiscardACStatus().isEmpty()) return true;
        return game.getPlayers().values().stream()
                .anyMatch(player -> !player.getActionCards().isEmpty());
    }

    private static boolean isActionCardNotPlayable(Game game, Player player, String acAlias) {
        ActionCardHelper.ACStatus status = game.getDiscardACStatus().get(acAlias);
        if (status == ActionCardHelper.ACStatus.garbozia) {
            return !player.hasPlanet("garbozia");
        }
        // this first condition could go away if getDiscardACStatus starts correctly tracking discarded ACs
        return game.getDiscardActionCards().containsKey(acAlias) || status != null;
    }

    public static void startOfTurnSaboWindowReminders(Game game, Player player) {
        var gameMessages = GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD);
        for (GameMessage gameMessage : gameMessages) {
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
