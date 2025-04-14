package ti4.service.strategycard;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Constants;
import ti4.helpers.CryypterHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.RiftSetModeService;
import ti4.service.turn.StartTurnService;

@UtilityClass
public class PlayStrategyCardService {

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game game, MessageChannel mainGameChannel, Player player) {
        playSC(event, scToPlay, game, mainGameChannel, player, false);
    }

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game game, MessageChannel mainGameChannel, Player player, boolean winnuHero) {
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scToPlay).orElse(null);
        if (scModel == null) { // Temporary Error Reporting
            BotLogger.warning(new BotLogger.LogMessageOrigin(player), "`PlayStrategyCardService.playSC` - Game: `" + game.getName() + "` - SC Model not found for SC `" + scToPlay + "` from set `" + game.getScSetID() + "`");
        }

        String stratCardName = Helper.getSCName(scToPlay, game);
        if (game.getPlayedSCs().contains(scToPlay) && !winnuHero) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "**" + stratCardName + "** has already been played previously.");
            return;
        }

        // HANDLE COUP
        if (!winnuHero && game.getStoredValue("Coup") != null && game.getStoredValue("Coup").contains("_" + scToPlay)) {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("Coup").contains(p2.getFaction()) && p2.getActionCards().containsKey("coup")) {
                    if (p2 == player) {
                        continue;
                    }
                    ActionCardHelper.playAC(event, game, p2, "coup", game.getMainGameChannel());
                    List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                    game.setJustPlayedComponentAC(true);
                    String message = "Use buttons to end turn, or, if _Coup D'etat_ is Sabo'd, play **" + stratCardName + "**.";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    game.setStoredValue("Coup", "");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                        + " you have been Coup'd due to attempting to play **" + stratCardName
                        + "**. If this is a mistake or the _Coup D'etat_ is Sabo'd, feel free to play **" + stratCardName
                        + "**. Otherwise, please end turn after doing any end of turn abilities you wish to perform.");
                    return;
                }
            }
        }

        if (!winnuHero) {
            game.setSCPlayed(scToPlay, true);
        }
        ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
        StringBuilder message = new StringBuilder();
        message.append(Helper.getSCRepresentation(game, scToPlay)).append(" played");
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
        message.append("Indicate your choice by pressing a button below.");

        for (Player player2 : playersToFollow) {
            if (winnuHero) {
                continue;
            }
            player2.removeFollowedSC(scToPlay);
            player2.getCardsInfoThread(); // force thread to open if closed
        }

        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder();

        // SEND IMAGE OR SEND EMBED IF IMAGE DOES NOT EXIST
        if (!winnuHero) {
            if (scModel.hasImageFile()) {
                MessageHelper.sendMessageToChannel(mainGameChannel, Helper.getScImageUrl(scToPlay, game));
            } else {
                baseMessageObject.addEmbeds(scModel.getRepresentationEmbed());
            }
        }
        baseMessageObject.addContent(message.toString());

        // GET BUTTONS
        List<Button> scButtons = new ArrayList<>(getSCButtons(scToPlay, game, winnuHero, player));
        if (scModel.usesAutomationForSCID("pok7technology") && !game.isFowMode() && Helper.getPlayerFromAbility(game, "propagation") != null) {
            scButtons.add(Buttons.gray("nekroFollowTech", "Get Command Tokens", FactionEmojis.Nekro));
        }

        if (scModel.usesAutomationForSCID("pok4construction") && !game.isFowMode() && Helper.getPlayerFromUnit(game, "titans_mech") != null) {
            scButtons.add(Buttons.gray("titansConstructionMechDeployStep1", "Deploy Titan Mech + Inf", FactionEmojis.Titans));
        }
        scButtons.add(Buttons.gray("requestAllFollow_" + scToPlay, "Request All Resolve Now"));

        // set the action rows
        baseMessageObject.addComponents(ButtonHelper.turnButtonListIntoActionRowList(scButtons));
        AutoPingMetadataManager.setupQuickPing(game.getName());
        sendAndHandleMessageResponse(baseMessageObject.build(), game, player, event, scToPlay, scModel, scButtons);

        // Trade Primary
        if (scModel.usesAutomationForSCID("pok5trade")) {
            TradeStrategyCardService.doPrimary(game, event, player);
        }

        // Politics Assign Speaker Buttons
        if (scModel.usesAutomationForSCID("pok3politics") || scModel.usesAutomationForSCID("cryypter_3")) {
            game.setStoredValue("hasntSetSpeaker", "waiting");
            String assignSpeakerMessage = player.getRepresentation()
                + ", please, __before__ you draw your action cards or look at agendas, choose a faction below to receive the Speaker token."
                + MiscEmojis.SpeakerToken;

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
            String drawAgendasMessage = player.getRepresentation()
                + " __after__ assigning speaker, use this button to look at the top agendas, which will be shown to you in your `#cards-info` thread.";
            Button draw2Agenda = Buttons.green(player.getFinsFactionCheckerPrefix() + "drawAgenda_2", "Draw 2 Agendas", CardEmojis.Agenda);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), drawAgendasMessage, draw2Agenda);
        }

        // Cryypter's Additional Look at Top Agenda Buttons
        if (scModel.usesAutomationForSCID("cryypter_3")) {
            String lookAtTopMessage = player.getRepresentation() + " after placing the drawn agendas on top/bottom, you must look a the top two cards of the agenda deck.";
            Button draw2Agenda = Buttons.green(player.getFinsFactionCheckerPrefix() + "agendaLookAt[count:2][lookAtBottom:false]", "Look at top 2 Agendas", CardEmojis.Agenda);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), lookAtTopMessage, draw2Agenda);
        }

        // Red Tape Diplomacy
        if (scToPlay == 2 && game.isRedTapeMode()) {
            ButtonHelper.offerRedTapeButtons(game, player);
        }

        if (scToPlay == 9 && RiftSetModeService.isActive(game)) {
            RiftSetModeService.resolveSacrifice(event, game, player);
        }

        if (scModel.usesAutomationForSCID("pok5trade")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " you may force players to replenish commodities. This is normally done in order to trigger a _Trade Agreement_ or because of a pre-existing deal."
                + " This is not required, and not advised if you are offering them a conditional replenishment.";
            List<Button> forceRefresh = ButtonHelper.getForcedRefreshButtons(game, player, playersToFollow);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), assignSpeakerMessage2, forceRefresh);

            for (Player p2 : playersToFollow) {
                if (!p2.getPromissoryNotes().containsKey(p2.getColor() + "_ta")) {
                    String message2 = "Heads up, " + p2.getRepresentationUnfogged() + ", **Trade** has just been played and this is a reminder that you do not hold your _Trade Agreement_.";
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message2);
                    for (Player p3 : game.getRealPlayers()) {
                        if (p2 == p3) {
                            continue;
                        }
                        if (p3.getPromissoryNotes().containsKey(p2.getColor() + "_ta")) {
                            String message3 = "Heads up, " + p3.getRepresentationUnfogged()
                                + ", **Trade** has just been played and this is a reminder that you hold the _Trade Agreement_ of "
                                + p2.getColor() + ". ";
                            if (p3 == player) {
                                message3 += "If you force that player to replenish commodities, then you will be prompted to play the _Trade Agreemnt_.";
                            } else {
                                message3 += "If you work out a deal with the **Trade** holder,"
                                    + " they may force the player to replenish commodities, and then you will be prompted to play the _Trade Agreemnt_.";
                            }
                            MessageHelper.sendMessageToChannel(p3.getCardsInfoThread(), message3);
                        }
                    }
                }
            }

        }

        if (!scModel.usesAutomationForSCID("pok1leadership")) {
            Button emelpar = Buttons.red("scepterE_follow_" + scToPlay, "Exhaust " + RelicHelper.sillySpelling());
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
                        player3.getRepresentationUnfogged() + ", you may follow **" + stratCardName + "** with the _" + RelicHelper.sillySpelling() + "_.",
                        empNMahButtons);
                }
                if (player3.hasUnexhaustedLeader("mahactagent") && !ButtonHelper.getTilesWithYourCC(player, game, event).isEmpty() && !winnuHero) {
                    empNMahButtons.addFirst(Buttons.red("mahactA_follow_" + scToPlay, "Use Mahact Agent", FactionEmojis.Mahact));
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(),
                        player3.getRepresentationUnfogged() + " You may follow **" + stratCardName + "** with " + (player3.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.",
                        empNMahButtons);
                }
            }
        }

        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Buttons.red(player.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        Button deleteButton = Buttons.red(player.getFinsFactionCheckerPrefix() + "doAnotherAction", "Do Another Action");
        conclusionButtons.add(endTurn);

        if (ButtonHelper.getEndOfTurnAbilities(player, game).size() > 1) {
            conclusionButtons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "endOfTurnAbilities", "Do End Of Turn Ability (" + (ButtonHelper.getEndOfTurnAbilities(player, game).size() - 1) + ")"));
        }
        conclusionButtons.add(deleteButton);
        conclusionButtons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "endTurnWhenAllReactedTo_" + scToPlay, "End Turn When All Have Reacted"));
        if (player.hasTech("fl")) {
            conclusionButtons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "fleetLogWhenAllReactedTo_" + scToPlay, "Use Fleet Logistics When All Have Reacted"));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn or take another action.", conclusionButtons);
        if (!game.isHomebrewSCMode() && player.hasAbility("grace")
            && !player.getExhaustedAbilities().contains("grace")
            && ButtonHelperAbilities.getGraceButtons(game, player, scToPlay).size() > 2) {
            List<Button> graceButtons = new ArrayList<>();
            graceButtons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "resolveGrace_" + scToPlay, "Resolve Grace Ability"));
            graceButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you may resolve **Grace** with the buttons.",
                graceButtons);
        }
        if (scModel.usesAutomationForSCID("anarchy8")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " to resolve the 3rd primary effect, " +
                "a tactical action, we advise you just click the do another action button, and when you do the primary of warfare, gain an extra CC " +
                "into tactics, to account for the tactical action spending from reinforcements");
        }
        if (player.ownsPromissoryNote("acq") && !scModel.usesAutomationForSCID("pok1leadership") && !winnuHero) {
            for (Player player2 : playersToFollow) {
                if (!player2.getPromissoryNotes().isEmpty()) {
                    for (String pn : player2.getPromissoryNotes().keySet()) {
                        if (!player2.ownsPromissoryNote("acq") && "acq".equalsIgnoreCase(pn)) {
                            String acqMessage = player2.getRepresentationUnfogged()
                                + " you may use this button to play _Acquiescence_ to perform the secondary without spending a command token from your strategy pool.";
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.green("winnuPNPlay_" + scToPlay, "Use Acquiescence"));
                            buttons.add(Buttons.red("deleteButtons", "Decline"));
                            MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), acqMessage, buttons);
                        }
                    }
                }
            }
        }
    }

    private static void sendAndHandleMessageResponse(MessageCreateData toSend, Game game, Player player, GenericInteractionCreateEvent event, int scToPlay, StrategyCardModel scModel, List<Button> scButtons) {
        var mainGameChannel = game.getMainGameChannel();
        Message message = mainGameChannel.sendMessage(toSend).complete();
        Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, message);
        String stratCardName = Helper.getSCName(scToPlay, game);
        if (reactionEmoji != null) {
            message.addReaction(reactionEmoji).queue();
            player.addFollowedSC(scToPlay, event);
        }
        if (!game.isFowMode() && !game.getName().equalsIgnoreCase("pbd1000") && !game.isHomebrewSCMode() && scToPlay != 5 && scToPlay != 1 && !game.getName().equalsIgnoreCase("pbd100two")) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!player.ownsPromissoryNote("acq") && p2.getStrategicCC() == 0 && !p2.getUnfollowedSCs().contains(1)
                    && (!p2.getTechs().contains("iihq") || !p2.getUnfollowedSCs().contains(8))
                    && !p2.hasRelicReady("absol_emelpar") && !p2.hasRelicReady("emelpar")
                    && !p2.hasUnexhaustedLeader("mahactagent") && !p2.hasUnexhaustedLeader("yssarilagent")) {
                    Emoji reactionEmoji2 = Helper.getPlayerReactionEmoji(game, p2, message);
                    if (reactionEmoji2 != null) {
                        message.addReaction(reactionEmoji2).queue();
                        p2.addFollowedSC(scToPlay, event);
                        if (scToPlay == 8) {
                            String key3 = "potentialBlockers";
                            if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                                game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                            }

                            String key = "factionsThatAreNotDiscardingSOs";
                            game.setStoredValue(key, game.getStoredValue(key) + player.getFaction() + "*");
                        }
                        MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), "You were automatically marked as not following **"
                            + stratCardName + "** because the bot believes you can't follow due to a lack of command tokens in your strategy pool.");
                    }
                }
            }
        }
        game.setStoredValue("scPlay" + scToPlay, message.getJumpUrl());
        game.setStoredValue("scPlayMsgID" + scToPlay, message.getId());
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
            String threadName = game.getName() + "-round-" + game.getRound() + "-" + scModel.getName();
            ThreadChannelAction threadChannel = mainGameChannel.createThreadChannel(threadName, message.getId());
            threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
            threadChannel.queue(m5 -> {
                if (game.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE) && scModel.hasImageFile()) {
                    MessageHelper.sendMessageToChannel(m5, Helper.getScImageUrl(scToPlay, game));
                }

                if (scModel.usesAutomationForSCID("pok5trade")) {
                    Button transaction = Buttons.blue("transaction", "Transaction");
                    scButtons.add(transaction);
                    scButtons.add(Buttons.green("sendTradeHolder_tg_" + player.getFaction(), "Send 1 Trade Good"));
                    scButtons.add(Buttons.gray("sendTradeHolder_debt_" + player.getFaction(), "Send 1 Debt"));
                }
                MessageHelper.sendMessageToChannelWithButtons(m5, "These buttons will work inside the thread.", scButtons);

                // Trade Neighbour Message
                if (scModel.usesAutomationForSCID("pok5trade")) {
                    if (player.hasAbility("guild_ships")) {
                        MessageHelper.sendMessageToChannel(m5,
                            "The **Trade** player has the **Guild Ships** ability, and thus may perform transactions with all players.");
                    } else if (player.getPromissoryNotesInPlayArea().contains("convoys")) {
                        MessageHelper.sendMessageToChannel(m5,
                            "The **Trade** player has _Trade Convoys_, and thus may perform transactions with all players.");
                    } else {
                        StringBuilder neighborsMsg = new StringBuilder("__Are__ neighbors with the **Trade** holder:");
                        StringBuilder notNeighborsMsg = new StringBuilder("__Not__ neighbors with the **Trade** holder:");
                        boolean anyNeighbours = false;
                        boolean allNeighbours = true;
                        for (Player p2 : game.getRealPlayers()) {
                            if (player != p2) {
                                if (player.getNeighbouringPlayers(true).contains(p2)) {
                                    neighborsMsg.append(" ").append(p2.getFactionEmoji());
                                    anyNeighbours = true;
                                } else {
                                    notNeighborsMsg.append(" ").append(p2.getFactionEmoji());
                                    allNeighbours = false;
                                }
                            }
                        }
                        if (allNeighbours) {
                            MessageHelper.sendMessageToChannel(m5, "The **Trade** player is neighbors with __all__ other players.");
                        } else if (!anyNeighbours) {
                            MessageHelper.sendMessageToChannel(m5, "The **Trade** player is neighbors with __no__ other players.");
                        } else {
                            MessageHelper.sendMessageToChannel(m5, neighborsMsg + "\n" + notNeighborsMsg);
                        }
                    }
                }
            });
        }
    }

    private static List<Button> getSCButtons(int sc, Game game, boolean winnuHero, Player player) {
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel == null) {
            return getGenericButtons(sc);
        }

        String scAutomationID = scModel.getBotSCAutomationID();

        // Handle Special Cases
        if (scAutomationID.equals("pok8imperial")) {
            handleSOQueueing(game, winnuHero);
        }

        // Return Buttons
        return switch (scAutomationID) {
            case "pok1leadership" -> getLeadershipButtons(sc);
            case "pok2diplomacy" -> getDiplomacyButtons(sc, player);
            case "pok3politics" -> getPoliticsButtons(sc);
            case "pok4construction" -> getConstructionButtons(sc);
            case "pok5trade" -> getTradeButtons(sc);
            case "pok6warfare" -> getWarfareButtons(sc);
            case "anarchy1" -> getAnarchy1Buttons(sc);
            case "anarchy2" -> getAnarchy2Buttons(sc);
            case "anarchy3" -> getAnarchy3Buttons(sc, player);
            case "anarchy7" -> getAnarchy7Buttons(sc);
            case "anarchy8" -> getAnarchy8Buttons(sc);
            case "anarchy10" -> getAnarchy10Buttons(sc);
            case "anarchy11" -> getAnarchy11Buttons(sc);
            case "pok7technology" -> getTechnologyButtons(sc);
            case "pok8imperial" -> getImperialButtons(sc);

            // add your own special button resolutions here as additional cases
            // ignis aurora
            case "ignisaurora3" -> getGenericButtons(sc); //TODO: do it
            case "ignisaurora2" -> getIgnisAuroraSC8Buttons(sc);

            // cryypter
            case "cryypter_3" -> CryypterHelper.getCryypterSC3Buttons(sc);

            // monuments
            case "monuments4construction" -> getMonumentsConstructionButtons(sc);

            //riftset
            case "riftset_9" -> RiftSetModeService.getSacrificeButtons();

            // unhandled
            default -> getGenericButtons(sc);
        };
    }

    public static void handleSOQueueing(Game game, boolean winnuHero) {
        if (winnuHero) {
            String message = "# Since this is the result of playing Mathis Mathinus, the Winnu hero, secret objectives draws will not be queued or resolved in a particular order.";
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
                    || (player == imperialHolder && player.controlsMecatol(true) && !game.getPhaseOfGame().contains("agenda"))) {
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
        Button leadershipGenerateCCButtons = Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens");
        //Button exhaust = Buttons.red("leadershipExhaust", "Spend");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(leadershipGenerateCCButtons, noFollowButton);
    }

    private static List<Button> getAnarchy1Buttons(int sc) {
        Button leadershipGenerateCCButtons = Buttons.green("leadershipGenerateCCButtons", "Spend & Gain Command Tokens");
        Button an1 = Buttons.green("primaryOfAnarchy1", "Resolve a chosen and unexhausted secondary");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(leadershipGenerateCCButtons, an1, noFollowButton);
    }

    private static List<Button> getDiplomacyButtons(int sc, Player player) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button diploSystemButton = Buttons.blue(player.getFinsFactionCheckerPrefix() + "diploSystem", "Diplo a System");
        Button refreshButton = Buttons.green("diploRefresh2", "Ready 2 Planets");

        Button noFollowButton = Buttons.red("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, diploSystemButton, refreshButton, noFollowButton);
    }

    private static List<Button> getAnarchy2Buttons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button diploSystemButton = Buttons.gray("anarchy2secondary", "Ready a non-SC Card");
        Button refreshButton = Buttons.green("diploRefresh2", "Ready Planets");

        Button noFollowButton = Buttons.red("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, diploSystemButton, refreshButton, noFollowButton);
    }

    private static List<Button> getAnarchy3Buttons(int sc, Player player) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button diploSystemButton = Buttons.blue(player.getFinsFactionCheckerPrefix() + "diploSystem", "Diplo a System");
        Button refreshButton = Buttons.gray("anarchy3secondary", "Perform Unchosen Or Exhausted Secondary");

        Button noFollowButton = Buttons.red("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, diploSystemButton, refreshButton, noFollowButton);
    }

    private static List<Button> getPoliticsButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button draw2AC = Buttons.gray("sc_ac_draw", "Draw 2 Action Cards", CardEmojis.ActionCard);
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
                        Button button = Buttons.gray(politicsHolder.getFinsFactionCheckerPrefix() + Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, player.getColor(), ColorEmojis.getColorEmoji(player.getColor()));
                        assignSpeakerButtons.add(button);
                    }
                }
            }
        }
        return assignSpeakerButtons;
    }

    /**
     * @return buttons which hit {@link ButtonHelperSCs#construction}
     */
    private static List<Button> getConstructionButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button sdButton = Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock);
        Button pdsButton = Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds);
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, sdButton, pdsButton, noFollowButton);
    }

    private static List<Button> getTradeButtons(int sc) {
        // Button tradePrimary = Buttons.green("trade_primary", "Resolve Primary");
        Button followButton = Buttons.green("sc_trade_follow", "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button refreshAndWash = Buttons.gray("sc_refresh_and_wash", "Replenish and Wash", MiscEmojis.Wash);
        Button refresh = Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm);
        return List.of(followButton, noFollowButton, refresh, refreshAndWash);
    }

    private static List<Button> getWarfareButtons(int sc) {
        Button warfarePrimary = Buttons.blue("primaryOfWarfare", "Do Warfare Primary");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button homeBuild = Buttons.green("warfareBuild", "Build At Home");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(warfarePrimary, followButton, homeBuild, noFollowButton);
    }

    private static List<Button> getAnarchy8Buttons(int sc) {
        Button warfarePrimary = Buttons.blue("primaryOfWarfare", "Do Warfare Primary");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button homeBuild = Buttons.green("resolveAnarchy8Secondary", "Lift a token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(warfarePrimary, followButton, homeBuild, noFollowButton);
    }

    private static List<Button> getAnarchy7Buttons(int sc) {
        Button warfarePrimary = Buttons.blue("primaryOfAnarchy7", "Resolve PRODUCTION in a system");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button homeBuild = Buttons.green("warfareBuild", "Build At Home");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(warfarePrimary, followButton, homeBuild, noFollowButton);
    }

    private static List<Button> getTechnologyButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button getTech = Buttons.green("acquireATechWithSC_first", "Get a Technology");
        return List.of(followButton, getTech, noFollowButton);
    }

    private static List<Button> getImperialButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawSo = Buttons.gray("sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective);
        Button scoreImperial = Buttons.gray("score_imperial", "Score Imperial", PlanetEmojis.Mecatol);
        Button scoreAnObjective = Buttons.gray("scoreAnObjective", "Score A Public", CardEmojis.Public1);
        return List.of(followButton, noFollowButton, drawSo, scoreImperial, scoreAnObjective);
    }

    private static List<Button> getAnarchy10Buttons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawSo = Buttons.gray("anarchy10PeekStart", "Peek at Public", CardEmojis.Public1);
        Button scoreAnObjective = Buttons.gray("scoreAnObjective", "Score A Public", CardEmojis.Public1);
        return List.of(followButton, noFollowButton, drawSo, scoreAnObjective);
    }

    private static List<Button> getAnarchy11Buttons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawSo = Buttons.gray("sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective);
        Button scoreImperial = Buttons.gray("score_imperial", "Score Imperial", PlanetEmojis.Mecatol);
        Button scoreAnObjective = Buttons.blue("get_so_score_buttons", "Score A Secret Objective");
        Button reverseOrder = Buttons.gray("reverseSpeakerOrder", "Reverse Speaker Order", MiscEmojis.SpeakerToken);
        return List.of(followButton, noFollowButton, drawSo, scoreImperial, scoreAnObjective, reverseOrder);
    }

    private static List<Button> getGenericButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, noFollowButton);
    }

    private static List<Button> getIgnisAuroraSC8Buttons(int sc) {
        Button primary = Buttons.blue("ignisAuroraSC8Primary", "[Primary] Gain Relic & Reveal Event");
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button secondary = Buttons.green("ignisAuroraSC8Secondary", "Draw Unknown Relic Fragment", ExploreEmojis.UFrag);
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(primary, followButton, secondary, noFollowButton);
    }

    /**
     * @return buttons which hit {@link ButtonHelperSCs#construction}
     */
    private static List<Button> getMonumentsConstructionButtons(int sc) {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button sdButton = Buttons.green("construction_spacedock", "Place 1 space dock", UnitEmojis.spacedock);
        Button pdsButton = Buttons.green("construction_pds", "Place 1 PDS", UnitEmojis.pds);
        Button monumentButton = Buttons.red("construction_monument", "Place 1 Monument", UnitEmojis.Monument);
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, sdButton, pdsButton, monumentButton, noFollowButton);
    }
}
