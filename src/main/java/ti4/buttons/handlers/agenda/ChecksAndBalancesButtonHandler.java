package ti4.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.game.StartPhase;
import ti4.commands2.player.SCPick;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class ChecksAndBalancesButtonHandler {

    @ButtonHandler("checksNBalancesPt2_")
    public static void resolvePt2ChecksNBalances(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String scPicked = buttonID.split("_")[1];
        int scpick = Integer.parseInt(scPicked);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(factionPicked);

        SCPick.attemptToPickSC(event, game, p2, scpick);

        String recipientMessage = p2.getRepresentationUnfogged() + " was given " + Helper.getSCName(scpick, game)
            + (!game.isFowMode() ? " by " + player.getFactionEmoji() : "");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), recipientMessage);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), p2.getColor() + " was given " + Helper.getSCName(scpick, game));

        }
        event.getMessage().delete().queue();
        List<Button> buttons = getPlayerOptionsForChecksNBalances(player, game, scpick);
        if (buttons.isEmpty()) {
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : game.getRealPlayers()) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
            game.incrementScTradeGoods();

            for (int sc : scPickedList) {
                game.setScTradeGood(sc, 0);
            }
            StartPhase.startActionPhase(event, game);
            game.setStoredValue("willRevolution", "");
        } else {
            boolean foundPlayer = false;
            Player privatePlayer = null;
            List<Player> players = game.getRealPlayers();
            if (game.isReverseSpeakerOrder() || !game.getStoredValue("willRevolution").isEmpty()) {
                Collections.reverse(players);
            }
            for (Player p3 : players) {
                if (p3.getFaction().equalsIgnoreCase(game.getStoredValue("politicalStabilityFaction"))) {
                    continue;
                }
                if (foundPlayer) {
                    privatePlayer = p3;
                    foundPlayer = false;
                }
                if (p3 == player) {
                    foundPlayer = true;
                }
            }
            if (privatePlayer == null) {
                privatePlayer = game.getRealPlayers().getFirst();
            }
            game.setPhaseOfGame("strategy");
            game.updateActivePlayer(privatePlayer);
            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                privatePlayer.getRepresentationUnfogged() + "Use buttons to pick which strategy card you want to give someone else.", Helper.getRemainingSCButtons(event, game, privatePlayer));
        }
    }

    public static List<Button> getPlayerOptionsForChecksNBalances(Player player, Game game, int scPicked) {
        List<Button> buttons = new ArrayList<>();
        List<Player> activePlayers = game.getRealPlayers();

        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }
        int minNumOfSCs = 10;
        for (Player p2 : activePlayers) {
            if (p2.getSCs().size() < minNumOfSCs) {
                minNumOfSCs = p2.getSCs().size();
            }
        }
        if (minNumOfSCs == maxSCsPerPlayer) {
            return buttons;
        }
        for (Player p2 : activePlayers) {
            if (p2 == player) {
                continue;
            }
            if (p2.getSCs().size() < maxSCsPerPlayer) {
                if (game.isFowMode()) {
                    buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + p2.getFaction(), p2.getColor()));
                } else {
                    buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + p2.getFaction(), " ").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
            }
        }
        if (buttons.isEmpty()) {
            buttons.add(Buttons.gray("checksNBalancesPt2_" + scPicked + "_" + player.getFaction(), " ").withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
        }

        return buttons;
    }
}
