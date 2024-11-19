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

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.game.StartPhase;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.MapGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
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

        OptionMapping option = event.getOption(Constants.STRATEGY_CARD);
        int scPicked = option.getAsInt();

        boolean pickSuccessful = Stats.pickSC(event, game, player, option);
        Set<Integer> playerSCs = player.getSCs();
        if (!pickSuccessful) {
            if (game.isFowMode()) {
                String[] scs = { Constants.SC2, Constants.SC3, Constants.SC4, Constants.SC5, Constants.SC6 };
                int c = 0;
                while (playerSCs.isEmpty() && c < 5 && !pickSuccessful) {
                    if (event.getOption(scs[c]) != null) {
                        pickSuccessful = Stats.pickSC(event, game, player, event.getOption(scs[c]));
                    }
                    playerSCs = player.getSCs();
                    c++;
                }
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
        secondHalfOfSCPick(event, player, game, scPicked);
    }

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
            SCPick.secondHalfOfSCPickWhenChecksNBalances(event, player, game, scpick);
        } else {
            boolean pickSuccessful = Stats.secondHalfOfPickSC(event, game, player, scpick);
            if (pickSuccessful) {
                SCPick.secondHalfOfSCPick(event, player, game, scpick);
                ButtonHelper.deleteMessage(event);
            }
        }
    }

    public static List<Button> getPlayerOptionsForChecksNBalances(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
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
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, game, scPicked);
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
                String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " gained " + tgCount + " TG" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scPicked, game);
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

    @ButtonHandler("checksNBalancesPt2_")
    public static void resolvePt2ChecksNBalances(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String scPicked = buttonID.split("_")[1];
        int scpick = Integer.parseInt(scPicked);
        String factionPicked = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(factionPicked);

        Stats.secondHalfOfPickSC(event, game, p2, scpick);

        String recipientMessage = p2.getRepresentationUnfogged() + " was given " + Helper.getSCName(scpick, game)
            + (!game.isFowMode() ? " by " + player.getFactionEmoji() : "");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), recipientMessage);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), p2.getColor() + " was given " + Helper.getSCName(scpick, game));

        }
        event.getMessage().delete().queue();
        List<Button> buttons = getPlayerOptionsForChecksNBalances(event, player, game, scpick);
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

    public static void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
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

            for (Player p2 : game.getRealPlayers()) {
                ButtonHelperActionCards.checkForAssigningCoup(game, p2);
                if (game.getStoredValue("Play Naalu PN") != null && game.getStoredValue("Play Naalu PN").contains(p2.getFaction())) {
                    if (!p2.getPromissoryNotesInPlayArea().contains("gift") && p2.getPromissoryNotes().containsKey("gift")) {
                        PromissoryNoteHelper.resolvePNPlay("gift", p2, game, event);
                    }
                }
            }

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

        } else {
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
                            "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.",
                            ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation()
                            + " You had infantry II to be revived, but the bot couldn't find planets you own in your HS to place them, so per the rules they now disappear into the ether.");

                    }
                }
                game.setPhaseOfGame("action");
            }
        }
        if (allPicked) {
            for (Player p2 : game.getRealPlayers()) {
                List<Button> buttons = new ArrayList<>();
                if (p2.hasTechReady("qdn") && p2.getTg() > 2 && p2.getStrategicCC() > 0) {
                    buttons.add(Buttons.green("startQDN", "Use Quantum Datahub Node"));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use QDN", buttons);
                }
                buttons = new ArrayList<>();
                if (game.getLaws().containsKey("arbiter") && game.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())) {
                    buttons.add(Buttons.green("startArbiter", "Use Imperial Arbiter"));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " you have the opportunity to use Imperial Arbiter", buttons);
                }
            }
        }
    }
}
