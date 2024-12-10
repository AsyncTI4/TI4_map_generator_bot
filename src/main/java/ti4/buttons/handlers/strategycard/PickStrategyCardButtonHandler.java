package ti4.buttons.handlers.strategycard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;
import ti4.service.game.StartPhaseService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.player.PlayerStatsService;
import ti4.service.strategycard.PickStrategyCardService;

@UtilityClass
class PickStrategyCardButtonHandler {

    @ButtonHandler("scPick_")
    public static void scPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String num = buttonID.replace("scPick_", "");
        int scpick = Integer.parseInt(num);
        if (game.getStoredValue("Public Disgrace") != null
            && game.getStoredValue("Public Disgrace").contains("_" + scpick)
            && (game.getStoredValue("Public Disgrace Only").isEmpty() || game.getStoredValue("Public Disgrace Only").contains(player.getFaction()))) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (game.getStoredValue("Public Disgrace").contains(p2.getFaction())
                    && p2.getActionCards().containsKey("disgrace")) {
                    ActionCardHelper.playAC(event, game, p2, "disgrace", game.getMainGameChannel());
                    game.setStoredValue("Public Disgrace", "");
                    String msg = player.getRepresentationUnfogged() +
                        "\n> Picked: " + Helper.getSCRepresentation(game, scpick);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation()
                            + " you have been Public Disgrace'd because someone preset it to occur when the number " + scpick
                            + " was chosen. If this is a mistake or the Public Disgrace is Sabo'd, feel free to pick the strategy card again. Otherwise, pick a different strategy card.");
                    return;
                }
            }
        }
        if (game.getStoredValue("deflectedSC").equalsIgnoreCase(num)) {
            if (player.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " You cant pick this SC because it has the deflection ability on it and you have no strat CC to spend");
                return;
            } else {
                player.setStrategicCC(player.getStrategicCC() - 1);
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " spent 1 strat CC due to deflection");
            }
        }

        if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
            secondHalfOfSCPickWhenChecksNBalances(event, player, game, scpick);
        } else {
            boolean pickSuccessful = PlayerStatsService.secondHalfOfPickSC(event, game, player, scpick);
            if (pickSuccessful) {
                PickStrategyCardService.secondHalfOfSCPick(event, player, game, scpick);
                ButtonHelper.deleteMessage(event);
            }
        }
    }

    @ButtonHandler("checksNBalancesPt2_")
    public static void resolvePt2ChecksNBalances(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String scPicked = buttonID.split("_")[1];
        int scpick = Integer.parseInt(scPicked);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(factionPicked);

        PlayerStatsService.secondHalfOfPickSC(event, game, p2, scpick);

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
            StartPhaseService.startActionPhase(event, game);
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

    public static void secondHalfOfSCPickWhenChecksNBalances(ButtonInteractionEvent event, Player player, Game game, int scPicked) {
        List<Button> buttons = getPlayerOptionsForChecksNBalances(player, game, scPicked);
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();

        for (Player playerStats : game.getRealPlayers()) {
            if (playerStats.getSCs().contains(scPicked)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getSCName(scPicked, game) + " is already picked.");
                return;
            }
        }
        Integer tgCount = scTradeGoods.get(scPicked);
        if (tgCount != null && tgCount != 0) {
            int tg = player.getTg();
            tg += tgCount;
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " gained " + tgCount + " TG" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scPicked, game));
            if (game.isFowMode()) {
                String messageToSend = ColorEmojis.getColorEmojiWithName(player.getColor()) + " gained " + tgCount + " TG" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scPicked, game);
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
            }
            player.setTg(tg);
            CommanderUnlockCheckService.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            game.setScTradeGood(scPicked, 0);
            if (scPicked == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCount; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " chose which player to give this stratgy card to.", buttons);
        event.getMessage().delete().queue();
    }
}
