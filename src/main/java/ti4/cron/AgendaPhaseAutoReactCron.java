package ti4.cron;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.model.ActionCardModel;
import ti4.service.button.ReactionService;

import static java.util.function.Predicate.not;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@UtilityClass
public class AgendaPhaseAutoReactCron {

    private static final int SCHEDULED_PERIOD_MINUTES = 10;
    private static final int RUNS_PER_HOUR = 60 / SCHEDULED_PERIOD_MINUTES;

    public static void register() {
        CronManager.schedulePeriodically(AgendaPhaseAutoReactCron.class, AgendaPhaseAutoReactCron::autoReact, 5, SCHEDULED_PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    private static void autoReact() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .forEach(AgendaPhaseAutoReactCron::autoReact);
    }

    private static void autoReact(Game game) {
        try {
            automaticallyReactToWhensAndAfters(game);
        } catch (Exception e) {
            BotLogger.log("AgendaPhaseAutoReactCron failed for game: " + game.getName(), e);
        }
    }

    private static void automaticallyReactToWhensAndAfters(Game game) {
        if (!"agendawaiting".equals(game.getPhaseOfGame())) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (!shouldRandomlyReact(player)) {
                continue;
            }
            handleWhens(game, player);
            handleAfters(game, player);
        }
    }

    private static boolean shouldRandomlyReact(Player player) {
        if (player.getAutoSaboPassMedian() == 0 || !player.doesPlayerAutoPassOnWhensAfters()) {
            return false;
        }
        int rollMax = player.getAutoSaboPassMedian() * RUNS_PER_HOUR;
        int rollResult = ThreadLocalRandom.current().nextInt(1, rollMax + 1);
        return rollResult == rollMax;
    }

    private static void handleWhens(Game game, Player player) {
        String whensId = game.getLatestWhenMsg();
        if (isNotBlank(whensId) && !playerHasWhens(player) && !ReactionService.checkForASpecificPlayerReact(whensId, player, game)) {
            String message = game.isFowMode() ? "No whens" : null;
            ReactionService.addReaction(player, false, message, null, whensId, game);//TODO: updates game...
        }
    }

    private static boolean playerHasWhens(Player player) {
        if (player.hasAbility("quash") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            return true;
        }
        for (String acId : player.getActionCards().keySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")) {
                return true;
            }
        }
        for (String pnId : player.getPromissoryNotes().keySet()) {
            if (!player.ownsPromissoryNote(pnId) && pnId.endsWith("_ps")) {
                return true;
            }
        }
        return false;
    }

    private static void handleAfters(Game game, Player player) {
        String aftersId = game.getLatestAfterMsg();
        //TODO: updates game...
        if (isNotBlank(aftersId) && !playerHasAfters(player) && !PlayerReactService.checkForASpecificPlayerReact(aftersId, player, game)) {
            String message = game.isFowMode() ? "No afters" : null;
            ReactionService.addReaction(player, false, message, null, aftersId, game);//TODO: updates game...
        }
    }

    private static boolean playerHasAfters(Player player) {
        if (player.ownsPromissoryNote("rider") ||
                player.getPromissoryNotes().containsKey("riderm") ||
                player.hasAbility("radiance") ||
                player.hasAbility("galactic_threat") ||
                player.hasAbility("conspirators") ||
                player.ownsPromissoryNote("riderx") ||
                player.ownsPromissoryNote("riderm") ||
                player.ownsPromissoryNote("ridera")) {
            return true;
        }
        for (String acId : player.getActionCards().keySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("After an agenda is revealed")) {
                return true;
            }
        }
        return false;
    }


}
