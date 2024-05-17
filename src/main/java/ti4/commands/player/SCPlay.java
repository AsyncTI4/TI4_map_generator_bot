package ti4.commands.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.apache.commons.lang3.StringUtils;

import ti4.commands.cardsac.PlayAC;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD,
            "Which SC to play. If you have more than 1 SC, this is mandatory"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);

        Helper.checkThreadLimitAndArchive(event.getGuild());

        MessageChannel eventChannel = event.getChannel();
        MessageChannel mainGameChannel = activeGame.getMainGameChannel() == null ? eventChannel
            : activeGame.getMainGameChannel();

        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "You're not a player of this game");
            return;
        }

        Set<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No SC has been selected");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { // Only one SC selected
            MessageHelper.sendMessageToEventChannel(event, "Player has more than one SC. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToPlay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        playSC(event, scToPlay, activeGame, mainGameChannel, player);
    }

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game activeGame, MessageChannel mainGameChannel, Player player) {
        playSC(event, scToPlay, activeGame, mainGameChannel, player, false);
    }

    public static void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Game activeGame, MessageChannel mainGameChannel, Player player, boolean winnuHero) {
        StrategyCardModel scModel = activeGame.getStrategyCardModelByInitiative(scToPlay).orElse(null);
        if (scModel == null) { // Temporary Error Reporting
            BotLogger.log("`SCPlay.playSC` - Game: `" + activeGame.getName() + "` - SC Model not found for SC `" + scToPlay + "` from set `" + activeGame.getScSetID() + "`");
        }

        if (activeGame.getPlayedSCs().contains(scToPlay) && !winnuHero) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "SC already played");
            return;
        }

        // HANDLE COUP
        if (!winnuHero && activeGame.getStoredValue("Coup") != null
            && activeGame.getStoredValue("Coup").contains("_" + scToPlay)) {
            for (Player p2 : activeGame.getRealPlayers()) {
                if (activeGame.getStoredValue("Coup").contains(p2.getFaction())
                    && p2.getActionCards().containsKey("coup")) {
                    if (p2 == player) {
                        continue;
                    }
                    PlayAC.playAC(event, activeGame, p2, "coup", activeGame.getMainGameChannel(), event.getGuild());
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                    activeGame.setJustPlayedComponentAC(true);
                    String message = "Use buttons to end turn or play your SC (assuming coup is sabod)";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    activeGame.setStoredValue("Coup", "");
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player
                        .getRepresentation()
                        + " you have been couped. If this is a mistake or the coup is sabod, feel free to play the SC again. Otherwise, end turn after doing any end of turn abilities you have.");
                    return;
                }
            }
        }

        if (!winnuHero) {
            activeGame.setSCPlayed(scToPlay, true);
        }
        Helper.checkThreadLimitAndArchive(event.getGuild());
        StringBuilder message = new StringBuilder();
        message.append(Helper.getSCRepresentation(activeGame, scToPlay));
        message.append(" played");
        if (!activeGame.isFoWMode()) {
            message.append(" by ").append(player.getRepresentation());
        }
        message.append(".\n\n");

        String gamePing = activeGame.getPing();
        if (!gamePing.isEmpty()) {
            message.append(gamePing).append("\n");
        }
        message.append("Indicate your choice by pressing a button below");

        String scName = Helper.getSCName(scToPlay, activeGame).toLowerCase();
        if (winnuHero) {
            scName = scName + "WinnuHero";
        }
        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-" + scName;

        TextChannel textChannel = (TextChannel) mainGameChannel;

        for (Player player2 : activeGame.getPlayers().values()) {
            if (!player2.isRealPlayer() || winnuHero) {
                continue;
            }
            String faction = player2.getFaction();
            if (faction == null || faction.isEmpty() || "null".equals(faction))
                continue;
            player2.removeFollowedSC(scToPlay);
        }

        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder();

        // SEND IMAGE OR SEND EMBED IF IMAGE DOES NOT EXIST
        if (scModel != null && scModel.hasImageFile()) {
            MessageHelper.sendFileToChannel(mainGameChannel, Helper.getSCImageFile(scToPlay, activeGame));
        } else if (scModel != null) {
            baseMessageObject.addEmbeds(scModel.getRepresentationEmbed());
        }
        baseMessageObject.addContent(message.toString());

        // GET BUTTONS
        ActionRow actionRow = null;
        List<Button> scButtons = new ArrayList<>(getSCButtons(scToPlay, activeGame, winnuHero));
        if (scModel.usesAutomationForSCID("pok7technology") && !activeGame.isFoWMode() && Helper.getPlayerFromAbility(activeGame, "propagation") != null) {
            scButtons.add(Button.secondary("nekroFollowTech", "Get CCs").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        }

        if (scModel.usesAutomationForSCID("pok4construction") && !activeGame.isFoWMode() && Helper.getPlayerFromUnit(activeGame, "titans_mech") != null) {
            scButtons.add(Button.secondary("titansConstructionMechDeployStep1", "Deploy Titan Mech + Inf").withEmoji(Emoji.fromFormatted(Emojis.Titans)));
        }
        if (!scButtons.isEmpty()) {
            actionRow = ActionRow.of(scButtons);
        }
        if (actionRow != null) {
            baseMessageObject.addComponents(actionRow);
        }
        player.setWhetherPlayerShouldBeTenMinReminded(true);
        mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            Emoji reactionEmoji = Helper.getPlayerEmoji(activeGame, player, message_);
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
                player.addFollowedSC(scToPlay);
            }
            activeGame.setStoredValue("scPlay" + scToPlay, message_.getJumpUrl().replace(":", "666fin"));
            activeGame.setStoredValue("scPlayMsgID" + scToPlay, message_.getId().replace(":", "666fin"));
            activeGame.setStoredValue("scPlayMsgTime" + scToPlay, new Date().getTime() + "");
            for (Player p2 : activeGame.getRealPlayers()) {
                if (!activeGame.getStoredValue("scPlayPingCount" + scToPlay + p2.getFaction())
                    .isEmpty()) {
                    activeGame.removeStoredValue("scPlayPingCount" + scToPlay + p2.getFaction());
                }
            }
            if (activeGame.isFoWMode()) {
                // in fow, send a message back to the player that includes their emoji
                String response = "SC played.";
                response += reactionEmoji != null ? " " + reactionEmoji.getFormatted()
                    : "\nUnable to generate initial reaction, please click \"Not Following\" to add your reaction.";
                MessageHelper.sendPrivateMessageToPlayer(player, activeGame, response);
            } else {
                // only do thread in non-fow games
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                threadChannel.queue(m5 -> {
                    List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                    // SEARCH FOR EXISTING OPEN THREAD
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            if (activeGame.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE)) {
                                MessageHelper.sendFileToChannel(threadChannel_, Helper.getSCImageFile(scToPlay, activeGame));
                            }
                            if (scToPlay == 5) {
                                Button transaction = Button.primary("transaction", "Transaction");
                                scButtons.add(transaction);
                                scButtons.add(Button.success("sendTradeHolder_tg", "Send 1tg"));
                                scButtons.add(Button.secondary("sendTradeHolder_debt", "Send 1 debt"));
                            }
                            MessageHelper.sendMessageToChannelWithButtons(threadChannel_,
                                "These buttons will work inside the thread", scButtons);
                        }
                    }
                });

            }
        });

        // POLITICS - SEND ADDITIONAL ASSIGN SPEAKER BUTTONS
        if (scModel.usesAutomationForSCID("pok3politics")) {
            String assignSpeakerMessage = player.getRepresentation()
                + ", please, before you draw your action cards or look at agendas, click a faction below to assign Speaker "
                + Emojis.SpeakerToken;

            List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons(activeGame);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                assignSpeakerMessage, assignSpeakerActionRow);
        }
        if (scToPlay == ButtonHelper.getKyroHeroSC(activeGame)
            && !player.getFaction().equalsIgnoreCase(activeGame.getStoredValue("kyroHeroPlayer"))) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation()
                    + " this is a reminder that this SC is kyro cursed and therefore you should only do 1 of its clauses. ");
        }

        if (scModel.usesAutomationForSCID("pok3politics")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " after assigning speaker, Use this button to draw agendas into your cards info thread.";

            List<Button> drawAgendaButton = new ArrayList<>();
            Button draw2Agenda = Button.success("FFCC_" + player.getFaction() + "_" + "drawAgenda_2", "Draw 2 agendas");
            drawAgendaButton.add(draw2Agenda);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                assignSpeakerMessage2, drawAgendaButton);

        }
        if (scToPlay == 2 && activeGame.isRedTapeMode()) {
            ButtonHelper.offerRedTapButtons(activeGame, player);
        }

        if (scModel.usesAutomationForSCID("pok5trade")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " you can force players to refresh, normally done in order to trigger a trade agreement. This is not required and not advised if you are offering them a conditional refresh.";
            List<Button> forceRefresh = ButtonHelper.getForcedRefreshButtons(activeGame, player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                assignSpeakerMessage2, forceRefresh);
        }

        if (!scModel.usesAutomationForSCID("pok1leadership")) {
            Button emelpar = Button.danger("scepterE_follow_" + scToPlay, "Exhaust Scepter of Emelpar");
            for (Player player3 : activeGame.getRealPlayers()) {
                if (player3 == player) {
                    continue;
                }
                List<Button> empNMahButtons = new ArrayList<>();
                Button deleteB = Button.danger("deleteButtons", "Delete These Buttons");
                empNMahButtons.add(deleteB);
                if (player3.hasRelic("emelpar") && !player3.getExhaustedRelics().contains("emelpar")) {
                    empNMahButtons.add(0, emelpar);
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(),
                        player3.getRepresentation(true, true) + " You can follow SC #" + scToPlay
                            + " with the scepter of emelpar",
                        empNMahButtons);
                }
                if (player3.hasUnexhaustedLeader("mahactagent") && ButtonHelper.getTilesWithYourCC(player, activeGame, event).size() > 0 && !winnuHero) {
                    Button mahactA = Button.danger("mahactA_follow_" + scToPlay,
                        "Use Mahact Agent").withEmoji(Emoji.fromFormatted(Emojis.Mahact));
                    empNMahButtons.add(0, mahactA);
                    MessageHelper.sendMessageToChannelWithButtons(player3.getCardsInfoThread(),
                        player3.getRepresentation(true, true) + " You can follow SC #" + scToPlay + " with " + (player3.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Jae Mir Kan (Mahact Agent)", empNMahButtons);
                }
            }
        }

        List<Button> conclusionButtons = new ArrayList<>();
        Button endTurn = Button.danger(player.getFinsFactionCheckerPrefix() + "turnEnd", "End Turn");
        Button deleteButton = Button.danger("doAnotherAction", "Do Another Action");
        conclusionButtons.add(endTurn);
        if (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (ButtonHelper.getEndOfTurnAbilities(player, activeGame).size() - 1) + ")"));
        }
        conclusionButtons.add(deleteButton);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn or take another action.", conclusionButtons);
        if (!activeGame.isHomeBrewSCMode() && player.hasAbility("grace")
            && !player.getExhaustedAbilities().contains("grace")
            && ButtonHelperAbilities.getGraceButtons(activeGame, player, scToPlay).size() > 2) {
            List<Button> graceButtons = new ArrayList<>();
            graceButtons.add(Button.success("resolveGrace_" + scToPlay, "Resolve Grace Ability"));
            graceButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true) + " you can resolve grace with the buttons",
                graceButtons);
        }
        if (player.ownsPromissoryNote("acq") && scToPlay != 1 && !winnuHero) {
            for (Player player2 : activeGame.getPlayers().values()) {
                if (!player2.getPromissoryNotes().isEmpty()) {
                    for (String pn : player2.getPromissoryNotes().keySet()) {
                        if (!player2.ownsPromissoryNote("acq") && "acq".equalsIgnoreCase(pn)) {
                            String acqMessage = player2.getRepresentation(true, true)
                                + " you can use this button to play Winnu PN!";
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Button.success("winnuPNPlay_" + scToPlay, "Use Acquisence"));
                            buttons.add(Button.danger("deleteButtons", "Decline"));
                            MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), acqMessage,
                                buttons);

                        }
                    }
                }
            }
        }
    }

    private static List<Button> getSCButtons(int sc, Game activeGame, boolean winnuHero) {
        StrategyCardModel scModel = activeGame.getStrategyCardModelByInitiative(sc).orElse(null);
        if (scModel == null) {
            return getGenericButtons(sc);
        }

        String scAutomationID = scModel.getBotSCAutomationID();

        // Handle Special Cases
        switch (scAutomationID) {
            case "pok8imperial" -> handleSOQueueing(activeGame, winnuHero);
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
            default -> getGenericButtons(sc);
        };
    }

    private static void handleSOQueueing(Game activeGame, boolean winnuHero) {
        if (winnuHero) {
            String message = "# Since this is a Winnu Hero play, SO draws will not be queued or resolved in a particular order";
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
            return;
        }
        Player imperialHolder = Helper.getPlayerWithThisSC(activeGame, 8);
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        activeGame.setStoredValue(key, "");
        activeGame.setStoredValue(key2, "");
        activeGame.setStoredValue(key3, "");
        if (activeGame.getQueueSO()) {
            for (Player player : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, activeGame)) {
                if (player.getSoScored() + player.getSo() < player.getMaxSOCount()
                    || player.getSoScored() == player.getMaxSOCount()
                    || (player == imperialHolder && player.getPlanets().contains("mr"))) {
                    activeGame.setStoredValue(key,
                        activeGame.getStoredValue(key) + player.getFaction() + "*");
                } else {
                    activeGame.setStoredValue(key3,
                        activeGame.getStoredValue(key3) + player.getFaction() + "*");
                }
            }
        }
    }

    private static List<Button> getLeadershipButtons(int sc) {
        Button leadershipGenerateCCButtons = Button.success("leadershipGenerateCCButtons", "Spend & Gain CCs");
        //Button exhaust = Button.danger("leadershipExhaust", "Spend");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        return List.of(leadershipGenerateCCButtons, noFollowButton);
    }

    private static List<Button> getDiplomacyButtons(int sc) {
        Button followButton = Button.success("sc_follow_2", "Spend A Strategy CC");
        Button diploSystemButton = Button.primary("diploSystem", "Diplo a System");
        Button refreshButton = Button.success("diploRefresh2", "Ready 2 Planets");

        Button noFollowButton = Button.danger("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, diploSystemButton, refreshButton, noFollowButton);
    }

    private static List<Button> getPoliticsButtons(int sc) {
        Button followButton = Button.success("sc_follow_3", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        Button draw2AC = Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        return List.of(followButton, noFollowButton, draw2AC);
    }

    private static List<Button> getPoliticsAssignSpeakerButtons(Game activeGame) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : activeGame.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(activeGame.getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    if (!activeGame.isFoWMode()) {
                        Button button = Button.secondary(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, " ");
                        String factionEmojiString = player.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                        assignSpeakerButtons.add(button);
                    } else {
                        Button button = Button.secondary(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction,
                            player.getColor());
                        assignSpeakerButtons.add(button);
                    }

                }
            }
        }
        return assignSpeakerButtons;
    }

    private static List<Button> getConstructionButtons(int sc) {
        Button followButton = Button.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button sdButton = Button.success("construction_spacedock", "Place A SD").withEmoji(Emoji.fromFormatted(Emojis.spacedock));
        Button pdsButton = Button.success("construction_pds", "Place a PDS").withEmoji(Emoji.fromFormatted(Emojis.pds));
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, sdButton, pdsButton, noFollowButton);
    }

    private static List<Button> getTradeButtons(int sc) {
        Button tradePrimary = Button.success("trade_primary", "Resolve Primary");
        Button followButton = Button.success("sc_trade_follow", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        Button refreshAndWash = Button.secondary("sc_refresh_and_wash", "Replenish and Wash").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button refresh = Button.secondary("sc_refresh", "Replenish Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
        return List.of(tradePrimary, followButton, noFollowButton, refresh, refreshAndWash);
    }

    private static List<Button> getWarfareButtons(int sc) {
        Button warfarePrimary = Button.primary("primaryOfWarfare", "Do Warfare Primary");
        Button followButton = Button.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button homeBuild = Button.success("warfareBuild", "Build At Home");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        return List.of(warfarePrimary, followButton, homeBuild, noFollowButton);
    }

    private static List<Button> getTechnologyButtons(int sc) {
        Button followButton = Button.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        Button getTech = Button.success("acquireATechWithSC", "Get a Tech");
        return List.of(followButton, getTech, noFollowButton);
    }

    private static List<Button> getImperialButtons(int sc) {
        Button followButton = Button.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        Button drawSo = Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective));
        Button scoreImperial = Button.secondary("score_imperial", "Score Imperial").withEmoji(Emoji.fromFormatted(Emojis.Mecatol));
        Button scoreAnObjective = Button.secondary("scoreAnObjective", "Score A Public").withEmoji(Emoji.fromFormatted(Emojis.Public1));
        return List.of(followButton, noFollowButton, drawSo, scoreImperial, scoreAnObjective);
    }

    private static List<Button> getGenericButtons(int sc) {
        Button followButton = Button.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_" + sc, "Not Following");
        return List.of(followButton, noFollowButton);
    }
}
