package ti4.commands.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.buttons.Buttons;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.event.RevealEvent;
import ti4.commands.relic.RelicDraw;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.CryypterHelper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play a Strategy Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD,
            "Which strategy card to play. If you have more than 1 strategy card, this is mandatory"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);

        Helper.checkThreadLimitAndArchive(event.getGuild());

        MessageChannel eventChannel = event.getChannel();
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? eventChannel
            : game.getMainGameChannel();

        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You're not a player of this game");
            return;
        }

        Set<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card has been selected.");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { // Only one SC selected
            MessageHelper.sendMessageToEventChannel(event, "Player has more than one strategy card. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToPlay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        playSC(event, scToPlay, game, mainGameChannel, player);
    }

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game game, MessageChannel mainGameChannel, Player player) {
        playSC(event, scToPlay, game, mainGameChannel, player, false);
    }

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game game, MessageChannel mainGameChannel, Player player, boolean winnuHero) {
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scToPlay).orElse(null);
        if (scModel == null) { // Temporary Error Reporting
            BotLogger.log("`SCPlay.playSC` - Game: `" + game.getName() + "` - SC Model not found for SC `" + scToPlay + "` from set `" + game.getScSetID() + "`");
        }

        if (game.getPlayedSCs().contains(scToPlay) && !winnuHero) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Strategy card already played.");
            return;
        }

        // HANDLE COUP
        if (!winnuHero && game.getStoredValue("Coup") != null
            && game.getStoredValue("Coup").contains("_" + scToPlay)) {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("Coup").contains(p2.getFaction())
                    && p2.getActionCards().containsKey("coup")) {
                    if (p2 == player) {
                        continue;
                    }
                    PlayAC.playAC(event, game, p2, "coup", game.getMainGameChannel());
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    game.setJustPlayedComponentAC(true);
                    String message = "Use buttons to end turn, or (if Coup is Sabo'd) play your strategy card.";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    game.setStoredValue("Coup", "");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player
                        .getRepresentation()
                        + " you have been Coup'd due to attempting to play " + Helper.getSCName(scToPlay, game) + ". If this is a mistake or the Coup is Sabo'd, feel free to play the strategy card again. Otherwise, end turn after doing any end of turn abilities you have.");
                    return;
                }
            }
        }

        if (!winnuHero) {
            game.setSCPlayed(scToPlay, true);
        }
        Helper.checkThreadLimitAndArchive(event.getGuild());
        StringBuilder message = new StringBuilder();
        message.append(Helper.getSCRepresentation(game, scToPlay));
        message.append(" played");
        if (!game.isFowMode()) {
            message.append(" by ").append(player.getRepresentation());
        }
        message.append(".\n\n");

        StringBuilder gamePing = new StringBuilder(game.getPing());
        List<Player> playersToFollow = game.getRealPlayers();
        if (!"All".equals(scModel.getGroup().orElse("")) && (game.getName().equalsIgnoreCase("pbd1000") || game.getName().equalsIgnoreCase("pbd100two"))) {
            playersToFollow = new ArrayList<>();
            String num = scToPlay + "";
            num = num.substring(num.length() - 1);
            gamePing = new StringBuilder();
            for (Player p2 : game.getRealPlayers()) {
                for (Integer sc : p2.getSCs()) {
                    String num2 = sc + "";
                    num2 = num2.substring(num2.length() - 1);
                    if (num2.equalsIgnoreCase(num) || num.equalsIgnoreCase("0") || num2.equalsIgnoreCase("0")) {
                        gamePing.append(p2.getRepresentation()).append(" ");
                        playersToFollow.add(p2);
                    }
                }
            }
        }
        if (!gamePing.isEmpty()) {
            message.append(gamePing).append("\n");
        }
        message.append("Indicate your choice by pressing a button below");

        String scName = Helper.getSCName(scToPlay, game).toLowerCase();
        if (winnuHero) {
            scName = scName + "WinnuHero";
        }
        String threadName = game.getName() + "-round-" + game.getRound() + "-" + scName;

        TextChannel textChannel = (TextChannel) mainGameChannel;

        for (Player player2 : playersToFollow) {
            if (winnuHero) {
                continue;
            }
            player2.removeFollowedSC(scToPlay);
            player2.getCardsInfoThread(); // force thread to open if closed
        }

        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder();

        // SEND IMAGE OR SEND EMBED IF IMAGE DOES NOT EXIST
        if (scModel != null && scModel.hasImageFile()) {
            MessageHelper.sendFileToChannel(mainGameChannel, Helper.getSCImageFile(scToPlay, game));
        } else if (scModel != null) {
            baseMessageObject.addEmbeds(scModel.getRepresentationEmbed());
        }
        baseMessageObject.addContent(message.toString());

        // GET BUTTONS
        List<Button> scButtons = new ArrayList<>(getSCButtons(scToPlay, game, winnuHero));
        if (scModel != null && scModel.usesAutomationForSCID("pok7technology") && !game.isFowMode() && Helper.getPlayerFromAbility(game, "propagation") != null) {
            scButtons.add(Buttons.gray("nekroFollowTech", "Get CCs", Emojis.Nekro));
        }

        if (scModel != null && scModel.usesAutomationForSCID("pok4construction") && !game.isFowMode() && Helper.getPlayerFromUnit(game, "titans_mech") != null) {
            scButtons.add(Buttons.gray("titansConstructionMechDeployStep1", "Deploy Titan Mech + Inf", Emojis.Titans));
        }
        scButtons.add(Buttons.gray("requestAllFollow_" + scToPlay, "Request All Resolve Now"));

        // set the action rows
        if (!scButtons.isEmpty()) {
            baseMessageObject.addComponents(ButtonHelper.turnButtonListIntoActionRowList(scButtons));
        }
        player.setWhetherPlayerShouldBeTenMinReminded(true);
        mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {

            Emoji reactionEmoji = Helper.getPlayerEmoji(game, player, message_);
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
                player.addFollowedSC(scToPlay, event);
            }
            if (!game.isFowMode() && !game.getName().equalsIgnoreCase("pbd1000") && !game.isHomebrewSCMode() && scToPlay != 5 && scToPlay != 1 && !game.getName().equalsIgnoreCase("pbd100two")) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!player.ownsPromissoryNote("acq") && p2.getStrategicCC() == 0 && !p2.getUnfollowedSCs().contains(1) && (!p2.getTechs().contains("iihq") || !p2.getUnfollowedSCs().contains(8)) && !p2.hasRelicReady("absol_emelpar") && !p2.hasRelicReady("emelpar") && !p2.hasUnexhaustedLeader("mahactagent") && !p2.hasUnexhaustedLeader("yssarilagent")) {
                        Emoji reactionEmoji2 = Helper.getPlayerEmoji(game, p2, message_);
                        if (reactionEmoji2 != null) {
                            message_.addReaction(reactionEmoji2).queue();
                            p2.addFollowedSC(scToPlay, event);
                            if (scToPlay == 8) {
                                String key3 = "potentialBlockers";
                                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                                }

                                String key = "factionsThatAreNotDiscardingSOs";
                                game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
                            }
                            MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), "You were automatically marked as not following SC #" + scToPlay + " because the bot believes you can't follow");
                        }
                    }
                }
            }
            game.setStoredValue("scPlay" + scToPlay, message_.getJumpUrl());
            game.setStoredValue("scPlayMsgID" + scToPlay, message_.getId());
            game.setStoredValue("scPlayMsgTime" + game.getRound() + scToPlay, System.currentTimeMillis() + "");
            for (Player p2 : game.getRealPlayers()) {
                if (!game.getStoredValue("scPlayPingCount" + scToPlay + p2.getFaction()).isEmpty()) {
                    game.removeStoredValue("scPlayPingCount" + scToPlay + p2.getFaction());
                }
            }
            if (game.isFowMode()) {
                // in fow, send a message back to the player that includes their emoji
                String response = "Strategy card played.";
                response += reactionEmoji != null ? " " + reactionEmoji.getFormatted()
                    : "\nUnable to generate initial reaction, please click \"Not Following\" to add your reaction.";
                MessageHelper.sendPrivateMessageToPlayer(player, game, response);
            } else {
                // only do thread in non-fow games
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                threadChannel.queue(m5 -> {
                    ThreadChannel threadChannel_ = m5;
                    if (game.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE) && scModel != null && scModel.hasImageFile()) {
                        MessageHelper.sendFileToChannel(threadChannel_, Helper.getSCImageFile(scToPlay, game));
                    }

                    if (scModel.usesAutomationForSCID("pok5trade")) {
                        Button transaction = Buttons.blue("transaction", "Transaction");
                        scButtons.add(transaction);
                        scButtons.add(Buttons.green("sendTradeHolder_tg_" + player.getFaction(), "Send 1TG"));
                        scButtons.add(Buttons.gray("sendTradeHolder_debt_" + player.getFaction(), "Send 1 debt"));
                    }
                    MessageHelper.sendMessageToChannelWithButtons(threadChannel_, "These buttons will work inside the thread", scButtons);

                    // Trade Neighbour Message
                    if (scModel.usesAutomationForSCID("pok5trade")) {
                        StringBuilder neighborsMsg = new StringBuilder("NOT neighbors with the trade holder:");
                        for (Player p2 : game.getRealPlayers()) {
                            if (!player.getNeighbouringPlayers().contains(p2) && player != p2) {
                                neighborsMsg.append(" ").append(p2.getFactionEmoji());
                            }
                        }
                        StringBuilder neighborsMsg2 = new StringBuilder("Neighbors with the trade holder:");
                        for (Player p2 : game.getRealPlayers()) {
                            if (player.getNeighbouringPlayers().contains(p2) && player != p2) {
                                neighborsMsg2.append(" ").append(p2.getFactionEmoji());
                            }
                        }
                        if (!player.getPromissoryNotesInPlayArea().contains("convoys") && !player.hasAbility("guild_ships")) {
                            MessageHelper.sendMessageToChannel(threadChannel_, neighborsMsg.toString());
                            MessageHelper.sendMessageToChannel(threadChannel_, neighborsMsg2.toString());
                        }
                    }

                });
            }
        });

        // Trade Primary
        if (scModel.usesAutomationForSCID("pok5trade")) {
            ButtonHelper.tradePrimary(game, event, player);
        }

        // Politics Assign Speaker Buttons
        if (scModel.usesAutomationForSCID("pok3politics") || scModel.usesAutomationForSCID("cryypter_3")) {
            game.setStoredValue("hasntSetSpeaker", "waiting");
            String assignSpeakerMessage = player.getRepresentation()
                + ", please, before you draw your action cards or look at agendas, click a faction below to assign Speaker "
                + Emojis.SpeakerToken;

            List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons(game, player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), assignSpeakerMessage, assignSpeakerActionRow);
        }

        // Handle Kyro Hero
        if (scToPlay == ButtonHelper.getKyroHeroSC(game)
            && !player.getFaction().equalsIgnoreCase(game.getStoredValue("kyroHeroPlayer"))) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation()
                    + " this is a reminder that this strategy card is Kyro Cursed and therefore you should only do 1 of its clauses. ");
        }

        // Politics Agenda Draw Buttons
        if (scModel.usesAutomationForSCID("pok3politics") || scModel.usesAutomationForSCID("cryypter_3")) {
            String drawAgendasMessage = player.getRepresentation() + " after assigning speaker, use this button to draw agendas into your cards info thread.";
            Button draw2Agenda = Buttons.green(player.getFinsFactionCheckerPrefix() + "drawAgenda_2", "Draw 2 Agendas", Emojis.Agenda);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), drawAgendasMessage, draw2Agenda);

        }

        // Cryypter's Additional Look at Top Agenda Buttons
        if (scModel.usesAutomationForSCID("cryypter_3")) {
            String lookAtTopMessage = player.getRepresentation() + " after placing the drawn agendas on top/bottom, you must look a the top two cards of the agenda deck.";
            Button draw2Agenda = Buttons.green(player.getFinsFactionCheckerPrefix() + "agendaLookAt[count:2][lookAtBottom:false]", "Look at top 2 Agendas", Emojis.Agenda);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), lookAtTopMessage, draw2Agenda);
        }

        // Red Tape Diplomacy
        if (scToPlay == 2 && game.isRedTapeMode()) {
            ButtonHelper.offerRedTapeButtons(game, player);
        }

        if (scModel.usesAutomationForSCID("pok5trade")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " you may force players to refresh, normally done in order to trigger a Trade Agreement. This is not required and not advised if you are offering them a conditional refresh.";
            List<Button> forceRefresh = ButtonHelper.getForcedRefreshButtons(game, player, playersToFollow);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                assignSpeakerMessage2, forceRefresh);

            for (Player p2 : playersToFollow) {
                if (!p2.getPromissoryNotes().containsKey(p2.getColor() + "_ta")) {
                    String message2 = p2.getRepresentationUnfogged() + " heads up, trade has just been played and this is a reminder that you do not hold your Trade Agreement";
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message2);
                    for (Player p3 : game.getRealPlayers()) {
                        if (p2 == p3) {
                            continue;
                        }
                        if (p3.getPromissoryNotes().containsKey(p2.getColor() + "_ta")) {
                            String message3 = p3.getRepresentationUnfogged() + " heads up, trade has just been played and this is a reminder that hold the trade agreement of " + p2.getColor() + ". If you work out a deal with the trade holder, they may force the player to replenish and then you will be prompted to play the TA. ";
                            MessageHelper.sendMessageToChannel(p3.getCardsInfoThread(), message3);
                        }
                    }
                }
            }

        }

        if (!scModel.usesAutomationForSCID("pok1leadership")) {
            Button emelpar = Buttons.red("scepterE_follow_" + scToPlay, "Exhaust Scepter of Emelpar");
            for (Player player3 : playersToFollow) {
                if (player3 == player) {
                    continue;
                }
                List<Button> empNMahButtons = new ArrayList<>();
                Button deleteB = Buttons.red("deleteButtons", "Delete These Buttons");
                empNMahButtons.add(deleteB);
                if (player3.hasRelic("emelpar") && !player3.getExhaustedRelics().contains("emelpar")) {
                    empNMahButtons.addFirst(emelpar);
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(),
                        player3.getRepresentationUnfogged() + " You may follow " + Helper.getSCName(scToPlay, game) + " with the Scepter of Emelpar.",
                        empNMahButtons);
                }
                if (player3.hasUnexhaustedLeader("mahactagent") && !ButtonHelper.getTilesWithYourCC(player, game, event).isEmpty() && !winnuHero) {
                    Button mahactA = Buttons.red("mahactA_follow_" + scToPlay,
                        "Use Mahact Agent", Emojis.Mahact);
                    empNMahButtons.addFirst(mahactA);
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(),
                        player3.getRepresentationUnfogged() + " You may follow " + Helper.getSCName(scToPlay, game) + " with " + (player3.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.",
                        empNMahButtons);
                }
            }
        }

        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Buttons.red(player.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        Button deleteButton = Buttons.red("doAnotherAction", "Do Another Action");
        conclusionButtons.add(endTurn);

        if (ButtonHelper.getEndOfTurnAbilities(player, game).size() > 1) {
            conclusionButtons.add(Buttons.blue("endOfTurnAbilities", "Do End Of Turn Ability (" + (ButtonHelper.getEndOfTurnAbilities(player, game).size() - 1) + ")"));
        }
        conclusionButtons.add(deleteButton);
        conclusionButtons.add(Buttons.red("endTurnWhenAllReactedTo_" + scToPlay, "End Turn When All Have Reacted"));
        if (player.hasTech("fl")) {
            conclusionButtons.add(Buttons.red("fleetLogWhenAllReactedTo_" + scToPlay, "Use Fleet Logistics When All Have Reacted"));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn or take another action.", conclusionButtons);
        if (!game.isHomebrewSCMode() && player.hasAbility("grace")
            && !player.getExhaustedAbilities().contains("grace")
            && ButtonHelperAbilities.getGraceButtons(game, player, scToPlay).size() > 2) {
            List<Button> graceButtons = new ArrayList<>();
            graceButtons.add(Buttons.green("resolveGrace_" + scToPlay, "Resolve Grace Ability"));
            graceButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you may resolve Grace with the buttons.",
                graceButtons);
        }
        if (player.ownsPromissoryNote("acq") && !scModel.usesAutomationForSCID("pok1leadership") && !winnuHero) {
            for (Player player2 : playersToFollow) {
                if (!player2.getPromissoryNotes().isEmpty()) {
                    for (String pn : player2.getPromissoryNotes().keySet()) {
                        if (!player2.ownsPromissoryNote("acq") && "acq".equalsIgnoreCase(pn)) {
                            String acqMessage = player2.getRepresentationUnfogged()
                                + " you may use this button to play Winnu PN!";
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.green("winnuPNPlay_" + scToPlay, "Use Acquisence"));
                            buttons.add(Buttons.red("deleteButtons", "Decline"));
                            MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), acqMessage,
                                buttons);

                        }
                    }
                }
            }
        }
    }

    /**
     * These buttons are only the buttons to be attached to the SC play itself, for all players to use - any additional buttons for the Primary SC holder should add specific logic to {@link SCPlay#scPlay()}
     * 
     * @return Buttons for the SCPlay message only (for all players to use)
     */
    private static List<Button> getSCButtons(int sc, Game game, boolean winnuHero) {
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel == null) {
            return getGenericButtons(sc);
        }

        String scAutomationID = scModel.getBotSCAutomationID();

        // Handle Special Cases
        switch (scAutomationID) {
            case "pok8imperial" -> handleSOQueueing(game, winnuHero);
        }

        // Return Buttons
        return switch (scAutomationID) {
            case "pok1leadership" -> getLeadershipButtons(sc);
            case "pok2diplomacy" -> getDiplomacyButtons(sc);
            case "pok3politics" -> getPoliticsButtons(sc);
            case "pok4construction" -> getConstructionButtons(sc);
            case "pok5trade" -> getTradeButtons(sc);
            case "pok6warfare" -> getWarfareButtons(sc);
            case "pok7technology" -> getTechnologyButtons(sc);
            case "pok8imperial" -> getImperialButtons(sc);

            // add your own special button resolutions here as additional cases
            // ignis aurora
            case "ignisaurora3" -> getGenericButtons(sc); //TODO: do it
            case "ignisaurora8" -> getIgnisAuroraSC8Buttons(sc);

            // cryypter
            case "cryypter_3" -> CryypterHelper.getCryypterSC3Buttons(sc);

            // unhandled
            default -> getGenericButtons(sc);
        };
    }

    public static void handleSOQueueing(Game game, boolean winnuHero) {
        if (winnuHero) {
            String message = "# Since this is the result of playing Mathis Mathinus, the Winnu hero, SO draws will not be queued or resolved in a particular order.";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            return;
        }
        Player imperialHolder = Helper.getPlayerWithThisSC(game, 8);
        if (game.getPhaseOfGame().contains("agenda")) {
            imperialHolder = game.getPlayer(game.getSpeakerUserID());
        }
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        game.setStoredValue(key, "");
        game.setStoredValue(key2, "");
        game.setStoredValue(key3, "");
        if (game.isQueueSO()) {
            for (Player player : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
                if (player.getSoScored() + player.getSo() < player.getMaxSOCount()
                    || player.getSoScored() == player.getMaxSOCount()
                    || (player == imperialHolder && player.controlsMecatol(true))) {
                    game.setStoredValue(key,
                        game.getStoredValue(key) + player.getFaction() + "*");
                } else {
                    game.setStoredValue(key3,
                        game.getStoredValue(key3) + player.getFaction() + "*");
                }
            }
        }
    }

    private static List<Button> getLeadershipButtons(int sc) {
        Button leadershipGenerateCCButtons = Buttons.green("leadershipGenerateCCButtons", "Spend & Gain CCs");
        //Button exhaust = Buttons.red("leadershipExhaust", "Spend");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(leadershipGenerateCCButtons, noFollowButton);
    }

    private static List<Button> getDiplomacyButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button diploSystemButton = Buttons.blue("diploSystem", "Diplo a System");
        Button refreshButton = Buttons.green("diploRefresh2", "Ready 2 Planets");

        Button noFollowButton = Buttons.red("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, diploSystemButton, refreshButton, noFollowButton);
    }

    private static List<Button> getPoliticsButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button draw2AC = Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", Emojis.ActionCard);
        return List.of(followButton, noFollowButton, draw2AC);
    }

    private static List<Button> getPoliticsAssignSpeakerButtons(Game game, Player politicsHolder) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (!player.isSpeaker()) {
                String faction = player.getFaction();
                if (Mapper.isValidFaction(faction)) {
                    if (!game.isFowMode()) {
                        Button button = Buttons.gray(politicsHolder.getFinsFactionCheckerPrefix() + Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, " ", player.getFactionEmoji());
                        assignSpeakerButtons.add(button);
                    } else {
                        Button button = Buttons.gray(politicsHolder.getFinsFactionCheckerPrefix() + Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, player.getColor(), Emojis.getColorEmoji(player.getColor()));
                        assignSpeakerButtons.add(button);
                    }
                }
            }
        }
        return assignSpeakerButtons;
    }

    private static List<Button> getConstructionButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button sdButton = Buttons.green("construction_spacedock", "Place 1 space dock", Emojis.spacedock);
        Button pdsButton = Buttons.green("construction_pds", "Place 1 PDS", Emojis.pds);
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, sdButton, pdsButton, noFollowButton);
    }

    private static List<Button> getTradeButtons(int sc) {
        // Button tradePrimary = Buttons.green("trade_primary", "Resolve Primary");
        Button followButton = Buttons.green("sc_trade_follow", "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button refreshAndWash = Buttons.gray("sc_refresh_and_wash", "Replenish and Wash", Emojis.Wash);
        Button refresh = Buttons.gray("sc_refresh", "Replenish Commodities", Emojis.comm);
        return List.of(followButton, noFollowButton, refresh, refreshAndWash);
    }

    private static List<Button> getWarfareButtons(int sc) {
        Button warfarePrimary = Buttons.blue("primaryOfWarfare", "Do Warfare Primary");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button homeBuild = Buttons.green("warfareBuild", "Build At Home");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(warfarePrimary, followButton, homeBuild, noFollowButton);
    }

    private static List<Button> getTechnologyButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button getTech = Buttons.green("acquireATechWithSC", "Get a Tech");
        return List.of(followButton, getTech, noFollowButton);
    }

    private static List<Button> getImperialButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawSo = Buttons.gray("sc_draw_so", "Draw Secret Objective", Emojis.SecretObjective);
        Button scoreImperial = Buttons.gray("score_imperial", "Score Imperial", Emojis.Mecatol);
        Button scoreAnObjective = Buttons.gray("scoreAnObjective", "Score A Public", Emojis.Public1);
        return List.of(followButton, noFollowButton, drawSo, scoreImperial, scoreAnObjective);
    }

    private static List<Button> getGenericButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, noFollowButton);
    }

    private static List<Button> getIgnisAuroraSC8Buttons(int sc) {
        Button primary = Buttons.blue("ignisAuroraSC8Primary", "[Primary] Gain Relic & Reveal Event");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy CC");
        Button secondary = Buttons.green("ignisAuroraSC8Secondary", "Draw Unknown Relic Fragment", Emojis.UFrag);
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(primary, followButton, secondary, noFollowButton);
    }

    @ButtonHandler("ignisAuroraSC8Primary")
    public static void resolveIgnisAuroraSC8Primary(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.getSCs().contains(8)) {
            MessageHelper.sendMessageToEventChannel(event, "You don't have the Antiquities strategy card.");
            return;
        }
        event.editButton(event.getButton().asDisabled()).queue();
        RelicDraw.drawRelicAndNotify(player, event, game);
        RevealEvent.revealEvent(event, game, game.getMainGameChannel());
    }

    @ButtonHandler("ignisAuroraSC8Secondary")
    public static void resolveIgnisAuroraSC8Secondary(ButtonInteractionEvent event, Game game, Player player) {
        player.addFragment("urf1");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " gained an " + Emojis.UFrag + " Unknown Relic Fragment");
    }
}
