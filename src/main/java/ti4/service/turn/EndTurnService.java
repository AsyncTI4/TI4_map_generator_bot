package ti4.service.turn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.thundersedge.TeHelperGeneral;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.game.EndPhaseService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.PlayHeroService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class EndTurnService {

    public static Player findNextUnpassedPlayer(Game game, Player currentPlayer) {
        List<Player> turnOrder = game.getActionPhaseTurnOrder();
        if (turnOrder.isEmpty()) {
            return null;
        }
        while (!turnOrder.getLast().equals(currentPlayer) && turnOrder.contains(currentPlayer)) {
            Collections.rotate(turnOrder, 1);
        }
        for (Player p : turnOrder) {
            if (!p.isPassed() && !p.isEliminated()) {
                return p;
            }
        }
        return null;
    }

    public static void endTurnAndUpdateMap(GenericInteractionCreateEvent event, Game game, Player player) {
        pingNextPlayer(event, game, player);
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(
                    game,
                    event,
                    "End of Turn " + player.getInRoundTurnCount() + ", Round " + game.getRound() + " for "
                            + player.getRepresentationNoPing() + ".");
        }
    }

    public static void pingNextPlayer(GenericInteractionCreateEvent event, Game game, Player mainPlayer) {
        pingNextPlayer(event, game, mainPlayer, false);
    }

    private static void resetStoredValuesEndOfTurn(Game game, Player player) {
        if (player.hasAbility("phantom_energy")
                && !game.getStoredValue("phantomEnergy").isEmpty()) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.hasPlayerCC(player)) {
                    for (String asyncID : tile.getSpaceUnitHolder()
                            .getUnitAsyncIdsOnHolder(player.getColorID())
                            .keySet()) {
                        game.setStoredValue(
                                "phantomEnergy",
                                game.getStoredValue("phantomEnergy").replace(asyncID, ""));
                    }
                }
            }
        }
        game.removeStoredValue("fortuneSeekers");
        game.setStoredValue("lawsDisabled", "no");
        game.removeStoredValue("endTurnWhenSCFinished");
        game.removeStoredValue("endTurnWhenSpliceEnds");
        game.removeStoredValue("fleetLogWhenSCFinished");
        ButtonHelperAbilities.oceanBoundCheck(game);
        TeHelperGeneral.checkCoexistTransfer(game);
        game.removeStoredValue("mahactHeroTarget");
        game.removeStoredValue("possiblyUsedRift");
        game.removeStoredValue("heartWarnedThisTurn");
        game.setActiveSystem("");
    }

    public static void pingNextPlayer(
            GenericInteractionCreateEvent event, Game game, Player mainPlayer, boolean justPassed) {
        resetStoredValuesEndOfTurn(game, mainPlayer);

        var userSettings = UserSettingsManager.get(mainPlayer.getUserID());

        if ("No Preference".equalsIgnoreCase(userSettings.getSandbagPref())) {
            String msg = mainPlayer.getRepresentation() + " the bot could auto pass on scoring secrets for you in the "
                    + "status phase if you cannot score any secrets. This might speed up the game as people won't be waiting on you in the cases "
                    + "where you can score nothing. Do you want to enable this feature? Once you answer, you will not be asked again. ";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("sandbagPref_bot", "Allow the bot"));
            buttons.add(Buttons.red("sandbagPref_manual", "Always manual"));
            MessageHelper.sendMessageToChannel(mainPlayer.getCardsInfoThread(), msg, buttons);
        }

        CommanderUnlockCheckService.checkPlayer(mainPlayer, "sol", "hacan");
        for (Player player : game.getRealPlayers()) {
            for (Player player_ : game.getRealPlayers()) {
                if (player_ == player) {
                    continue;
                }
                String key = player.getFaction() + "whisperHistoryTo" + player_.getFaction();
                if (!game.getStoredValue(key).isEmpty()) {
                    game.setStoredValue(key, "");
                }
            }
        }
        if (game.isFowMode()) {
            FowCommunicationThreadService.checkNewCommPartners(game, mainPlayer);
            MessageHelper.sendMessageToChannel(
                    mainPlayer.getPrivateChannel(),
                    "# End of Turn " + mainPlayer.getInRoundTurnCount() + ", Round " + game.getRound() + " for "
                            + mainPlayer.getRepresentation());
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), mainPlayer.getRepresentation(true, false) + " ended turn.");
            if ("statushomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                return;
            }
        }

        MessageChannel gameChannel =
                game.getMainGameChannel() == null ? event.getMessageChannel() : game.getMainGameChannel();

        // MAKE ALL NON-REAL PLAYERS PASSED
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                player.setPassed(true);
            }
        }

        // First, check for the ralnel hero and play it if it has been preset
        if (game.getRealPlayers().stream().allMatch(Player::isPassed)
                && !game.getStoredValue("ralnelHero").isEmpty()) {
            String value = game.getStoredValue("ralnelHero");
            Matcher matcher = Pattern.compile(RegexHelper.factionRegex(game)).matcher(value);
            if (matcher.find()) {
                game.removeStoredValue("ralnelHero");
                String faction = matcher.group("faction");
                Player ralnel = game.getPlayerFromColorOrFaction(faction);
                Leader hero =
                        ralnel == null ? null : ralnel.getLeader("ralnelhero").orElse(null);
                if (hero != null) PlayHeroService.playHero(event, game, ralnel, hero);
            }
        }

        // Next, check if puppets on a string has been pre-played
        if (game.getRealPlayers().stream().allMatch(Player::isPassed)
                && !game.getStoredValue("Puppets On A String").isEmpty()) {
            String value = game.getStoredValue("Puppets On A String");
            Player puppeteer = game.getPlayerFromColorOrFaction(value);
            if (puppeteer != null && puppeteer.getPlayableActionCards().contains("puppetsonastring")) {
                game.removeStoredValue("Puppets On A String");
                ActionCardHelper.playAC(event, game, puppeteer, "puppetsonastring", game.getMainGameChannel());
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.red("startScoring", "Start Scoring"));
                buttons.add(Buttons.gray("deleteButtons", "Was not sabod"));
                MessageHelper.sendMessageToChannel(
                        puppeteer.getCorrectChannel(),
                        "Use these buttons to start scoring if puppets is sabod",
                        buttons);
                return;
            }
        }

        if (game.getRealPlayers().stream().allMatch(Player::isPassed)) {
            if (mainPlayer.getSecretsUnscored().containsKey("pe")) {
                MessageHelper.sendMessageToChannel(
                        mainPlayer.getCardsInfoThread(),
                        "You were the last player to pass, and so you can score _Prove Endurance_.");
            }
            CommanderUnlockCheckService.checkPlayer(mainPlayer, "ralnel");
            if (!ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, mainPlayer, event)) {
                EndPhaseService.EndActionPhase(event, game, gameChannel);
                game.updateActivePlayer(null);
                ButtonHelperAgents.checkForEdynAgentActive(game, event);
            }

            return;
        }
        Player nextPlayer = findNextUnpassedPlayer(game, mainPlayer);
        if (!game.isFowMode()) {
            GameMessageManager.remove(game.getName(), GameMessageType.TURN)
                    .ifPresent(messageId -> game.getMainGameChannel()
                            .deleteMessageById(messageId)
                            .queue(Consumers.nop(), BotLogger::catchRestError));
        }
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game);
        if (isFowPrivateGame) {
            game.removeStoredValue("ghostagent_active");
            FoWHelper.pingAllPlayersWithFullStats(game, event, mainPlayer, "ended turn");
        }
        ButtonHelper.checkFleetInEveryTile(mainPlayer, game);
        if (mainPlayer != nextPlayer) {
            ButtonHelper.checkForPrePassing(game, mainPlayer);
        }
        CommanderUnlockCheckService.checkPlayer(nextPlayer, "sol");
        if (!game.isFowMode()
                && !game.getStoredValue("currentActionSummary" + mainPlayer.getFaction())
                        .isEmpty()) {
            for (ThreadChannel summary : game.getActionsChannel().getThreadChannels()) {
                if ("Turn Summary".equalsIgnoreCase(summary.getName())) {
                    MessageHelper.sendMessageToChannel(
                            summary,
                            "(Turn " + mainPlayer.getInRoundTurnCount() + ") " + mainPlayer.getFactionEmoji()
                                    + game.getStoredValue("currentActionSummary" + mainPlayer.getFaction()));
                    game.removeStoredValue("currentActionSummary" + mainPlayer.getFaction());
                }
            }
        }
        if (justPassed) {
            if (!ButtonHelperAgents.checkForEdynAgentPreset(game, mainPlayer, nextPlayer, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        } else {
            if (!ButtonHelperAgents.checkForEdynAgentActive(game, event)) {
                StartTurnService.turnStart(event, game, nextPlayer);
            }
        }
    }
}
