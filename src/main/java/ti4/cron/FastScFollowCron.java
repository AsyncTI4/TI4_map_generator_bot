package ti4.cron;

import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.button.ReactionService;

import static java.util.function.Predicate.not;

@UtilityClass
public class FastScFollowCron {

    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000;

    public static void register() {
        CronManager.schedulePeriodically(FastScFollowCron.class, FastScFollowCron::handleFastScFollow, 5, 10, TimeUnit.MINUTES);
    }

    private static void handleFastScFollow() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .filter(Game::isFastSCFollowMode)
            .forEach(FastScFollowCron::handleFastScFollow);
    }

    private static void handleFastScFollow(Game game) {
        try {
            handleFastScFollowMode(game);
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "FastScFollowCron failed for game: " + game.getName(), e);
        }
    }

    private static void handleFastScFollowMode(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (int sc : game.getPlayedSCsInOrder(player)) {
                if (player.hasFollowedSC(sc)) continue;

                String scTime = game.getStoredValue("scPlayMsgTime" + game.getRound() + sc);
                if (scTime.isEmpty()) continue;

                int twenty4 = 24;
                int half = 12;
                if (!game.getStoredValue("fastSCFollow").isEmpty()) {
                    twenty4 = Integer.parseInt(game.getStoredValue("fastSCFollow"));
                    half = twenty4 / 2;
                }
                long twelveHoursInMilliseconds = (long) half * ONE_HOUR_IN_MILLISECONDS;
                long twentyFourHoursInMilliseconds = (long) twenty4 * ONE_HOUR_IN_MILLISECONDS;
                long scPlayTime = Long.parseLong(scTime);
                long timeDifference = System.currentTimeMillis() - scPlayTime;
                String timesPinged = game.getStoredValue("scPlayPingCount" + sc + player.getFaction());
                if (timeDifference > twelveHoursInMilliseconds && timeDifference < twentyFourHoursInMilliseconds && !timesPinged.equalsIgnoreCase("1")) {
                    StringBuilder sb = new StringBuilder()
                        .append(player.getRepresentationUnfogged())
                        .append(" You are getting this ping because ").append(Helper.getSCName(sc, game))
                        .append(" has been played and now it has been half the allotted time and you haven't reacted. Please do so, or after another")
                        .append(" half you will be marked as not following.");
                    appendScMessages(game, player, sc, sb);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "1");
                    GameManager.save(game, "Fast SC Ping");
                }
                if (timeDifference > twentyFourHoursInMilliseconds && !timesPinged.equalsIgnoreCase("2")) {
                    String message = player.getRepresentationUnfogged() + Helper.getSCName(sc, game) +
                        " has been played and now it has been the allotted time and they haven't reacted, so they have" +
                        " been marked as not following.\n";
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, ButtonHelper.getStratName(sc));
                    player.addFollowedSC(sc);
                    game.setStoredValue("scPlayPingCount" + sc + player.getFaction(), "2");
                    GameManager.save(game, "Fast SC Ping 2");
                    String messageID = game.getStoredValue("scPlayMsgID" + sc);
                    ReactionService.addReaction(player, true, "not following.", "", messageID, game);

                    StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                    if (scModel != null && scModel.usesAutomationForSCID("pok8imperial")) {
                        handleSecretObjectiveDrawOrder(game, player);
                    }
                }
            }
        }
    }

    private static void appendScMessages(Game game, Player player, int sc, StringBuilder sb) {
        if (!game.getStoredValue("scPlay" + sc).isEmpty()) {
            sb.append("Message link is: ").append(game.getStoredValue("scPlay" + sc)).append("\n");
        }
        sb.append("You currently have ").append(player.getStrategicCC())
            .append(" command token").append(player.getStrategicCC() == 1 ? "" : "s").append(" in your strategy pool.");
        if (!player.hasFollowedSC(sc)) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                sb.toString());
        }
    }

    private static void handleSecretObjectiveDrawOrder(Game game, Player player) {
        String key = "factionsThatAreNotDiscardingSOs";
        if (!game.getStoredValue(key).contains(player.getFaction() + "*")) {
            game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
            GameManager.save(game, "Secret Objective Draw Order");
        }

        String key2 = "queueToDrawSOs";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
            GameManager.save(game, "Secret Objective Draw Order");
        }

        String key3 = "potentialBlockers";
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            Helper.resolveQueue(game);
            GameManager.save(game, "Secret Objective Draw Order");
        }
    }
}
