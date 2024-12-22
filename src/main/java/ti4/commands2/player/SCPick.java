package ti4.commands2.player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.BannerGenerator;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.ListTurnOrderService;
import ti4.service.player.PlayerStatsService;
import ti4.service.turn.StartTurnService;

class SCPick extends GameStateSubcommand {

    public SCPick() {
        super(Constants.SC_PICK, "Pick a strategy card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC2, "2nd choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC3, "3rd choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC4, "4th choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC5, "5th choice"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC6, "6th choice"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Collection<Player> activePlayers = game.getRealPlayers();
        if (activePlayers.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No active players found.");
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

        boolean pickSuccessful = PlayerStatsService.pickSC(event, game, player, option);
        Set<Integer> playerSCs = player.getSCs();
        if (!pickSuccessful) {
            if (game.isFowMode()) {
                String[] scs = { Constants.SC2, Constants.SC3, Constants.SC4, Constants.SC5, Constants.SC6 };
                int c = 0;
                while (playerSCs.isEmpty() && c < 5 && !pickSuccessful) {
                    if (event.getOption(scs[c]) != null) {
                        pickSuccessful = PlayerStatsService.pickSC(event, game, player, event.getOption(scs[c]));
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

            msgExtra += "\nAll players have picked strategy cards.";
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
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), "Use buttons to pick your strategy card.", Helper.getRemainingSCButtons(game, privatePlayer));
            } else {
                privatePlayer.setInRoundTurnCount(privatePlayer.getInRoundTurnCount() + 1);
                if (game.isShowBanners()) {
                    BannerGenerator.drawFactionBanner(privatePlayer);
                }
                MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getPrivateChannel(), msgExtra + "\n Use Buttons to do turn.",
                    StartTurnService.getStartOfTurnButtons(privatePlayer, game, false, event));
                if (privatePlayer.getGenSynthesisInfantry() > 0) {
                    if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                            "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.",
                            ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation()
                            + ", you had infantry II to be revived, but the bot couldn't find any planets you control in your home system to place them on, so per the rules they now disappear into the ether.");

                    }
                }

            }

        } else {
            if (allPicked) {
                ListTurnOrderService.turnOrder(event, game);
            }
            if (!allPicked) {
                game.updateActivePlayer(privatePlayer);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msgExtra + "\nUse buttons to pick your strategy card.", Helper.getRemainingSCButtons(game, privatePlayer));
                game.setPhaseOfGame("strategy");
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msgExtra);
                privatePlayer.setInRoundTurnCount(privatePlayer.getInRoundTurnCount() + 1);
                if (game.isShowBanners()) {
                    BannerGenerator.drawFactionBanner(privatePlayer);
                }
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "\n Use Buttons to do turn.",
                    StartTurnService.getStartOfTurnButtons(privatePlayer, game, false, event));
                if (privatePlayer.getGenSynthesisInfantry() > 0) {
                    if (!ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer).isEmpty()) {
                        MessageHelper.sendMessageToChannelWithButtons(privatePlayer.getCorrectChannel(),
                            "Use buttons to revive infantry. You have " + privatePlayer.getGenSynthesisInfantry() + " infantry left to revive.",
                            ButtonHelper.getPlaceStatusInfButtons(game, privatePlayer));
                    } else {
                        privatePlayer.setStasisInfantry(0);
                        MessageHelper.sendMessageToChannel(privatePlayer.getCorrectChannel(), privatePlayer.getRepresentation()
                            + ", you had infantry II to be revived, but the bot couldn't find any planets you control in your home system to place them on, so per the rules they now disappear into the ether.");

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
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " you have the opportunity to use _Quantum Datahub Node_.", buttons);
                }
                buttons = new ArrayList<>();
                if (game.getLaws().containsKey("arbiter") && game.getLawsInfo().get("arbiter").equalsIgnoreCase(p2.getFaction())) {
                    buttons.add(Buttons.green("startArbiter", "Use Imperial Arbiter"));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " you have the opportunity to use _Imperial Arbiter_.", buttons);
                }
            }
        }
    }
}
