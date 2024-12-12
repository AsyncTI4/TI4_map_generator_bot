package ti4.cron;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.model.ActionCardModel;
import ti4.service.button.ReactionService;
import ti4.service.player.PlayerReactService;

import static java.util.function.Predicate.not;

@UtilityClass
class AutoReactCron {

    public static void register() {
        CronManager.schedulePeriodically(AutoReactCron.class, AutoReactCron::autoReact, 5, 10, TimeUnit.MINUTES);
    }

    private static void autoReact() {
        GameManager.getManagedGames().stream()
            .filter(not(ManagedGame::isHasEnded))
            .map(ManagedGame::getGame)
            .forEach(AutoReactCron::autoReact);
    }

    private static void autoReact(Game game) {
        try {
            checkAllSaboWindows(game);
        } catch (Exception e) {
            BotLogger.log("AutoPing failed for game: " + game.getName(), e);
        }
    }

    private static void checkAllSaboWindows(Game game) {
        List<String> messageIDs = new ArrayList<>(game.getMessageIDsForSabo());
        for (Player player : game.getRealPlayers()) {
            if (player.getAutoSaboPassMedian() == 0) {
                continue;
            }
            int highNum = player.getAutoSaboPassMedian() * 6 * 3 / 2;
            int result = ThreadLocalRandom.current().nextInt(1, highNum + 1);
            boolean shouldDoIt = result == highNum;
            if (shouldDoIt || !canPlayerConceivablySabo(player, game)) {
                for (String messageID : messageIDs) {
                    if (shouldPlayerLeaveAReact(player, game, messageID)) {
                        String message = game.isFowMode() ? "No Sabotage" : null;
                        ReactionService.addReaction(player, false, message, null, messageID, game);
                    }
                }
            }
            if ("agendawaiting".equals(game.getPhaseOfGame())) {
                int highNum2 = player.getAutoSaboPassMedian() * 4 / 2;
                int result2 = ThreadLocalRandom.current().nextInt(1, highNum2 + 1);
                boolean shouldDoIt2 = result2 == highNum2;
                if (shouldDoIt2) {
                    String whensID = game.getLatestWhenMsg();
                    if (!doesPlayerHaveAnyWhensOrAfters(player)
                        && !PlayerReactService.checkForASpecificPlayerReact(whensID, player, game)) {
                        String message = game.isFowMode() ? "No whens" : null;
                        ReactionService.addReaction(player, false, message, null, whensID, game);
                    }
                    String aftersID = game.getLatestAfterMsg();
                    if (!doesPlayerHaveAnyWhensOrAfters(player)
                        && !PlayerReactService.checkForASpecificPlayerReact(aftersID, player, game)) {
                        String message = game.isFowMode() ? "No afters" : null;
                        ReactionService.addReaction(player, false, message, null, aftersID, game);
                    }
                }
            }
        }
    }

    private static boolean canPlayerConceivablySabo(Player player, Game game) {
        return player.getStrategicCC() > 0 && player.hasTechReady("it") ||
            player.hasUnit("empyrean_mech") && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech).isEmpty() ||
            player.getAc() > 0;
    }

    private static boolean shouldPlayerLeaveAReact(Player player, Game game, String messageID) {
        if (player.hasTechReady("it") && player.getStrategicCC() > 0) {
            return false;
        }
        if ((playerHasSabotage(player)
            || (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180)
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }
        if (player.hasUnit("empyrean_mech")
            && !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, Units.UnitType.Mech).isEmpty()) {
            return false;
        }
        if (player.getAc() == 0) {
            return !PlayerReactService.checkForASpecificPlayerReact(messageID, player, game);
        }
        if (player.isAFK()) {
            return false;
        }
        if (player.getAutoSaboPassMedian() == 0) {
            return false;
        }
        return !PlayerReactService.checkForASpecificPlayerReact(messageID, player, game);
    }

    private static boolean playerHasSabotage(Player player) {
        return player.getActionCards().containsKey("sabo1")
            || player.getActionCards().containsKey("sabo2")
            || player.getActionCards().containsKey("sabo3")
            || player.getActionCards().containsKey("sabo4")
            || player.getActionCards().containsKey("sabotage_ds")
            || player.getActionCards().containsKey("sabotage1_acd2")
            || player.getActionCards().containsKey("sabotage2_acd2")
            || player.getActionCards().containsKey("sabotage3_acd2")
            || player.getActionCards().containsKey("sabotage4_acd2");
    }

    private static boolean doesPlayerHaveAnyWhensOrAfters(Player player) {
        if (!player.doesPlayerAutoPassOnWhensAfters()) {
            return true;
        }
        if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
            || player.getPromissoryNotes().containsKey("riderm")
            || player.hasAbility("radiance") || player.hasAbility("galactic_threat")
            || player.hasAbility("conspirators")
            || player.ownsPromissoryNote("riderx")
            || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")) {
            return true;
        }
        for (String acID : player.getActionCards().keySet()) {
            ActionCardModel actionCard = Mapper.getActionCard(acID);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")
                || actionCardWindow.contains("After an agenda is revealed")) {
                return true;
            }
        }
        for (String pnID : player.getPromissoryNotes().keySet()) {
            if (player.ownsPromissoryNote(pnID)) {
                continue;
            }
            if (pnID.endsWith("_ps") && !pnID.contains("absol_")) {
                return true;
            }
        }
        return false;
    }
}
