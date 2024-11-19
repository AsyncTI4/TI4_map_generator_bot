package ti4.commands2.player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.MapGenerator;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.info.ListTurnOrderService;
import ti4.service.leader.CommanderUnlockCheckService;

public class SCPick extends GameStateSubcommand {

    public SCPick() {
        super(Constants.SC_PICK, "Pick a Strategy Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card #").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC2, "2nd choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC3, "3rd"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC4, "4th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC5, "5th"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC6, "6th"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Collection<Player> activePlayers = game.getRealPlayers();
        if (activePlayers.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No active players found");
            return;
        }

        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer <= 0) maxSCsPerPlayer = 1;

        Player player = getPlayer();
        int playerSCCount = player.getSCs().size();
        if (playerSCCount >= maxSCsPerPlayer) {
            MessageHelper.sendMessageToEventChannel(event, "Player may not pick another strategy card. Max strategy cards per player for this game is " + maxSCsPerPlayer + ".");
            return;
        }

        int scPicked = event.getOption(Constants.STRATEGY_CARD, 0, OptionMapping::getAsInt);

        boolean pickSuccessful = attemptToPickSC(event, game, player, scPicked);
        Set<Integer> playerSCs = player.getSCs();

        // If FoW, try to use additional choices
        if (!pickSuccessful && game.isFowMode()) {
            String[] scs = { Constants.SC2, Constants.SC3, Constants.SC4, Constants.SC5, Constants.SC6 };
            int c = 0;
            while (playerSCs.isEmpty() && c < 5 && !pickSuccessful) {
                OptionMapping scOption = event.getOption(scs[c]);
                if (scOption != null) {
                    pickSuccessful = attemptToPickSC(event, game, player, scOption.getAsInt());
                }
                playerSCs = player.getSCs();
                c++;
            }
            if (!pickSuccessful) {
                return;
            }
        }
        //ONLY DEAL WITH EXTRA PICKS IF IN FoW
        if (playerSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card picked.");
            return;
        }
        doAdditionalStuffAfterPickingSC(event, player, game, scPicked);
    }

    public static void doAdditionalStuffAfterPickingSC(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        List<Player> activePlayers = game.getPlayers().values().stream()
            .filter(Player::isRealPlayer)
            .collect(Collectors.toList());
        if (game.isReverseSpeakerOrder() || !game.getStoredValue("willRevolution").isEmpty()) {
            Collections.reverse(activePlayers);
        }
        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }
        if (!game.getStoredValue("exhaustedSC" + scPicked).isEmpty()) {
            game.setSCPlayed(scPicked, true);
        }

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += player_.getRepresentationUnfogged() + " to pick strategy card.";
                game.setPhaseOfGame("strategy");
                privatePlayer = player_;
                allPicked = false;
                break;
            }
            if (player_ == player) {
                nextCorrectPing = true;
            }
            if (player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                players.add(player_);
            }
        }

        //INFORM ALL PLAYER HAVE PICKED
        if (allPicked) {
            msgExtra += "\nAll players picked strategy cards.";
            Set<Integer> scPickedList = new HashSet<>();
            for (Player player_ : activePlayers) {
                scPickedList.addAll(player_.getSCs());
            }

            //ADD A TG TO UNPICKED SC
            game.incrementScTradeGoods();

            for (int sc : scPickedList) {
                game.setScTradeGood(sc, 0);
            }

            Player nextPlayer = null;
            int lowestSC = 100;
            for (Player player_ : activePlayers) {
                int playersLowestSC = player_.getLowestSC();
                String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player_, Integer.toString(playersLowestSC));
                if (scNumberIfNaaluInPlay.startsWith("0/")) {
                    nextPlayer = player_; //no further processing, this player has the 0 token
                    break;
                }
                if (playersLowestSC < lowestSC) {
                    lowestSC = playersLowestSC;
                    nextPlayer = player_;
                }
            }

            //INFORM FIRST PLAYER IS UP FOR ACTION
            if (nextPlayer != null) {
                msgExtra += " " + nextPlayer.getRepresentation() + " is up for an action";
                privatePlayer = nextPlayer;
                game.updateActivePlayer(nextPlayer);
                ButtonHelperFactionSpecific.resolveMilitarySupportCheck(nextPlayer, game);
                ButtonHelperFactionSpecific.resolveKolleccAbilities(nextPlayer, game);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, nextPlayer, "started turn");
                }
                game.setStoredValue("willRevolution", "");
                game.setPhaseOfGame("action");
                if (!game.isFowMode()) {
                    ButtonHelper.updateMap(game, event,
                        "Start of Action Phase For Round #" + game.getRound());
                }
            }
        }

        //SEND EXTRA MESSAGE
        if (isFowPrivateGame) {
            sendFoWExtraMessage(event, game, msgExtra, allPicked, privatePlayer);
        } else {
            sendExtraMessage(event, game, msgExtra, allPicked, privatePlayer);
        }

        if (allPicked) {
            doEndOfStrategyPhaseReminders(game, event);
        }
    }

    private static void sendExtraMessage(GenericInteractionCreateEvent event, Game game, String msgExtra, boolean allPicked, Player privatePlayer) {
        if (allPicked) {
            ListTurnOrderService.turnOrder(event, game);
        }
        if (!allPicked) {
            game.updateActivePlayer(privatePlayer);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra + "\nUse buttons to pick your strategy card.", Helper.getRemainingSCButtons(event, game, privatePlayer));
            game.setPhaseOfGame("strategy");
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
            privatePlayer.setTurnCount(privatePlayer.getTurnCount() + 1);
            if (game.isShowBanners()) {
                MapGenerator.drawBanner(privatePlayer);
            }
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "\n Use Buttons to do turn.",
                TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));
            if (privatePlayer.getGenSynthesisInfantry() > 0) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " Infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation()
                        + " You had Infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");
                }
            }
            game.setPhaseOfGame("action");
        }
    }

    private static void sendFoWExtraMessage(GenericInteractionCreateEvent event, Game game, String msgExtra, boolean allPicked, Player privatePlayer) {
        if (allPicked) {
            msgExtra = privatePlayer.getRepresentationUnfogged() + " UP NEXT";
        }
        String fail = "User for next faction not found. Report to ADMIN";
        String success = "The next player has been notified";
        MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
        game.updateActivePlayer(privatePlayer);

        if (!allPicked) {
            game.setPhaseOfGame("strategy");
            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use buttons to pick your strategy card.", Helper.getRemainingSCButtons(event, game, privatePlayer));
        } else {
            privatePlayer.setTurnCount(privatePlayer.getTurnCount() + 1);
            if (game.isShowBanners()) {
                MapGenerator.drawBanner(privatePlayer);
            }
            MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.",
                TurnStart.getStartOfTurnButtons(privatePlayer, game, false, event));
            if (privatePlayer.getGenSynthesisInfantry() > 0) {
                if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                        "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.",
                        ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                } else {
                    privatePlayer.setStasisInfantry(0);
                    MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation()
                        + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                }
            }

        }
    }

    public static boolean pickSC(GenericInteractionCreateEvent event, Game game, Player player, OptionMapping optionSC) {
        if (optionSC == null) {
            return false;
        }
        int scNumber = optionSC.getAsInt();
        return attemptToPickSC(event, game, player, scNumber);
    }

    public static boolean attemptToPickSC(GenericInteractionCreateEvent event, Game game, Player player, int scNumber) {
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Can only pick strategy card if both faction and color have been picked.");
            return false;
        }
        if (!scTradeGoods.containsKey(scNumber)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Strategy Card must be from possible ones in Game: " + scTradeGoods.keySet());
            return false;
        }

        Map<String, Player> players = game.getPlayers();
        for (Player playerStats : players.values()) {
            if (playerStats.getSCs().contains(scNumber)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getSCName(scNumber, game) + " is already picked.");
                return false;
            }
        }

        player.addSC(scNumber);
        if (game.isFowMode()) {
            String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " picked " + Helper.getSCName(scNumber, game);
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNumber).orElse(null);

        // WARNING IF PICKING TRADE WHEN PLAYER DOES NOT HAVE THEIR TRADE AGREEMENT
        if (scModel != null && scModel.usesAutomationForSCID("pok5trade") && !player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
            String message = player.getRepresentationUnfogged() + " heads up, you just picked Trade but don't currently hold your Trade Agreement";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(player.getRepresentationUnfogged())
            .append("\n> Picked: " + Helper.getSCRepresentation(game, scNumber));

        Integer tgCountOnSC = scTradeGoods.get(scNumber);
        if (tgCountOnSC == null || tgCountOnSC != 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        } else {
            String gainTG = player.gainTG(tgCountOnSC);
            sb.append(" gaining ").append(Emojis.tg(tgCountOnSC)).append(gainTG);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());

            if (game.isFowMode()) {
                String fowMessage = player.getFactionEmojiOrColor() + " gained " + Emojis.tg(tgCountOnSC) + " " + gainTG + " from picking " + Helper.getSCRepresentation(game, scNumber);
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, fowMessage);
            }

            CommanderUnlockCheckService.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            if (scNumber == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCountOnSC; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        return true;
    }

    private static void doEndOfStrategyPhaseReminders(Game game, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            ButtonHelperActionCards.checkForAssigningCoup(game, p2);
            naaluGiftReminder(game, event, p2);

            hacanQDNReminder(p2);
            imperialArbiterReminder(game, p2);
        }
    }

    private static void naaluGiftReminder(Game game, GenericInteractionCreateEvent event, Player p2) {
        if (game.getStoredValue("Play Naalu PN") != null && game.getStoredValue("Play Naalu PN").contains(p2.getFaction())) {
            if (!p2.getPromissoryNotesInPlayArea().contains("gift") && p2.getPromissoryNotes().containsKey("gift")) {
                PromissoryNoteHelper.resolvePNPlay("gift", p2, game, event);
            }
        }
    }

    private static void hacanQDNReminder(Player p2) {
        if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startQDN", "Use QDN", Emojis.CyberneticTech));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use " + Emojis.CyberneticTech + "**Quantum Datahub Node**", buttons);
        }
    }

    private static void imperialArbiterReminder(Game game, Player p2) {
        if (game.getLaws().containsKey("arbiter") && game.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startArbiter", "Use Imperial Arbiter", Emojis.Agenda));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use " + Emojis.Agenda + "**Imperial Arbiter**", buttons);
        }
    }
}
