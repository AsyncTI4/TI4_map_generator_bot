package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.button.ReactionService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.strategycard.StrategyCardMessageService;

@UtilityClass
public class FowAutoDeclineService {

    private static final String KEY_PREFIX = "fowAutoDecline_";
    public static final String BASE_HOURS_KEY = "fowAutoDeclineBaseHours";
    public static final String SPREAD_HOURS_KEY = "fowAutoDeclineSpreadHours";
    public static final double DEFAULT_BASE_HOURS = 1;
    public static final double DEFAULT_SPREAD_HOURS = 1;
    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000;

    public static void schedule(Game game, Player player, int sc) {
        long due = dueTime(
                System.currentTimeMillis(),
                baseHours(game),
                spreadHours(game),
                ThreadLocalRandom.current().nextDouble());
        game.setStoredValue(KEY_PREFIX + sc + "_" + player.getFaction(), due + "_" + game.getRound());
    }

    static long dueTime(long now, double baseHours, double spreadHours, double random01) {
        double delayHours = Math.max(0, baseHours + (random01 * 2 - 1) * spreadHours);
        return now + (long) (delayHours * ONE_HOUR_IN_MILLISECONDS);
    }

    public static double baseHours(Game game) {
        return parseHours(game.getStoredValue(BASE_HOURS_KEY), DEFAULT_BASE_HOURS);
    }

    public static double spreadHours(Game game) {
        return parseHours(game.getStoredValue(SPREAD_HOURS_KEY), DEFAULT_SPREAD_HOURS);
    }

    static double parseHours(String value, double fallback) {
        try {
            double hours = Double.parseDouble(value);
            return hours < 0 ? fallback : hours;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean resolveDue(Game game, BiConsumer<Game, Player> onImperialDecline) {
        if (!FoWHelper.isFogQol01(game)) return false;
        List<String> keys = new ArrayList<>();
        for (String key : game.getStoredValueMap().keySet()) {
            if (key.startsWith(KEY_PREFIX)) keys.add(key);
        }
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (String key : keys) {
            try {
                changed |= resolveKey(game, key, now, onImperialDecline);
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(game), "Fog auto-decline failed for " + key, e);
                game.removeStoredValue(key);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean resolveKey(Game game, String key, long now, BiConsumer<Game, Player> onImperialDecline) {
        String[] scAndFaction = key.substring(KEY_PREFIX.length()).split("_", 2);
        String[] dueAndRound = game.getStoredValue(key).split("_", 2);
        if (scAndFaction.length < 2 || dueAndRound.length < 2) {
            game.removeStoredValue(key);
            return true;
        }
        int sc = Integer.parseInt(scAndFaction[0]);
        Player player = game.getPlayerFromColorOrFaction(scAndFaction[1]);
        boolean staleRound = !String.valueOf(game.getRound()).equals(dueAndRound[1]);
        if (player == null || staleRound || player.hasFollowedSC(sc)) {
            game.removeStoredValue(key);
            return true;
        }
        if (now < Long.parseLong(dueAndRound[0])) return false;

        game.removeStoredValue(key);
        if (stillCannotFollow(game, player, sc)) {
            decline(game, player, sc, onImperialDecline);
        }
        return true;
    }

    private static boolean stillCannotFollow(Game game, Player player, int sc) {
        Player primary = game.getPlayerFromSC(sc);
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        return primary != null
                && scModel != null
                && PlayStrategyCardService.lacksStrategyTokensToFollow(game, primary, player, sc, scModel);
    }

    private static void decline(Game game, Player player, int sc, BiConsumer<Game, Player> onImperialDecline) {
        player.addFollowedSC(sc);
        StrategyCardMessageService.getStrategyCardMessage(game.getName(), game.getRound(), sc)
                .ifPresent(scMessage ->
                        ReactionService.addReaction(player, false, null, null, scMessage.messageId(), game));
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "You were automatically marked as not following **" + Helper.getSCName(sc, game)
                        + "**, because the bot believes you can't follow due to a lack of command tokens in your strategy pool.");
        boolean imperial = game.getStrategyCardModelByInitiative(sc)
                .map(model -> model.usesAutomationForSCID("pok8imperial"))
                .orElse(false);
        if (imperial) {
            onImperialDecline.accept(game, player);
        }
    }
}
