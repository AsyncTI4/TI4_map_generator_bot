package ti4.cron;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessage;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.button.ReactionService;
import ti4.service.strategycard.StrategyCardMessageService;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class FastScFollowCron {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000;

    public static void register() {
        CronManager.schedulePeriodically(
                FastScFollowCron.class, FastScFollowCron::handleFastScFollow, 5, 10, TimeUnit.MINUTES);
    }

    private static void handleFastScFollow() {
        if (!ActiveLeaseService.shouldCurrentProcessRunScheduledWork()) return;
        BotLogger.logCron("Running FastScFollowCron");

        List<String> gameNames = GameManager.getManagedGames().stream()
                .filter(not(ManagedGame::isHasEnded))
                .map(ManagedGame::getName)
                .toList();
        ConsumeGameUtility.consumeGames(gameNames, FastScFollowCron::handleFastScFollow, ExecutionLockType.WRITE);

        BotLogger.logCron("Finished FastScFollowCron");
    }

    private static void handleFastScFollow(Game game) {
        try {
            handleFastScFollowMode(game);
            GameManager.save(
                    game, "FastScFollowCron"); // TODO: This should be a property outside game, as it can be UNDO'd
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "FastScFollowCron failed for game: " + game.getName(), e);
        }
    }

    private static void handleFastScFollowMode(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (int sc : game.getPlayedSCsInOrder(player)) {
                if (player.hasFollowedSC(sc)) continue;

                GameMessage scMessage = StrategyCardMessageService.getStrategyCardMessage(
                                game.getName(), game.getRound(), sc)
                        .orElse(null);
                if (scMessage == null) continue;

                int twenty4 = 24;
                int half = 12;
                if (!game.getStoredValue("fastSCFollow").isEmpty()) {
                    twenty4 = Integer.parseInt(game.getStoredValue("fastSCFollow"));
                    half = twenty4 / 2;
                }
                long twelveHoursInMilliseconds = half * ONE_HOUR_IN_MILLISECONDS;
                long twentyFourHoursInMilliseconds = twenty4 * ONE_HOUR_IN_MILLISECONDS;
                long scPlayTime = scMessage.gameSaveTime();
                long timeDifference = System.currentTimeMillis() - scPlayTime;
                String timesPinged = game.getStoredValue("scPlayPingCount" + sc + player.getFaction());
                if (timeDifference > twelveHoursInMilliseconds
                        && timeDifference < twentyFourHoursInMilliseconds
                        && !"1".equalsIgnoreCase(timesPinged)) {
                    StringBuilder sb = new StringBuilder()
                            .append(player.getRepresentationUnfogged())
                            .append(" You are getting this ping because ")
                            .append(Helper.getSCName(sc, game))
                            .append(
                                    " has been played and now it has been half the allotted time and you haven't reacted. Please do so, or after another")
                            .append(" half you will be marked as not following.");
                    appendScMessages(game, player, scMessage, sb);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "1");
                }
                if (timeDifference > twentyFourHoursInMilliseconds && !"2".equalsIgnoreCase(timesPinged)) {
                    String message = player.getRepresentationUnfogged() + Helper.getSCName(sc, game)
                            + " has been played and now it has been the allotted time and they haven't reacted, so they have"
                            + " been marked as not following.\n";
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, ButtonHelper.getStratName(sc));
                    player.addFollowedSC(sc);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "2");
                    ReactionService.addReaction(player, true, "not following.", "", scMessage.messageId(), game);

                    StrategyCardModel scModel =
                            game.getStrategyCardModelByInitiative(sc).orElse(null);
                    if (scModel != null && scModel.usesAutomationForSCID("pok8imperial")) {
                        handleSecretObjectiveDrawOrder(game, player);
                    }
                }
            }
        }
    }

    private static void appendScMessages(Game game, Player player, GameMessage scMessage, StringBuilder sb) {
        sb.append("Message link is: ")
                .append(scMessage.asJumpLink(game.getMainGameChannel()))
                .append('\n')
                .append("You currently have ")
                .append(player.getStrategicCC())
                .append(" command token")
                .append(player.getStrategicCC() == 1 ? "" : "s")
                .append(" in your strategy pool.");

        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
    }

    private static void handleSecretObjectiveDrawOrder(Game game, Player player) {
        String key = "factionsThatAreNotDiscardingSOs";
        if (!game.getStoredValue(key).contains(player.getFaction() + "*")) {
            game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
        }

        String key2 = "queueToDrawSOs";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }

        String key3 = "potentialBlockers";
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
        }
    }
}
