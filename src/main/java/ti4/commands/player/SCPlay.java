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
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);

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
            MessageHelper.sendMessageToEventChannel(event, "No SC has been selected");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { // Only one SC selected
            MessageHelper.sendMessageToEventChannel(event, "Player has more than one SC. Please try again, using the `strategy_card` option.");
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
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "SC already played");
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
                    PlayAC.playAC(event, game, p2, "coup", game.getMainGameChannel(), event.getGuild());
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    game.setJustPlayedComponentAC(true);
                    String message = "Use buttons to end turn or play your SC (assuming coup is sabod)";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    game.setStoredValue("Coup", "");
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), player
                        .getRepresentation()
                        + " you have been couped. If this is a mistake or the coup is sabod, feel free to play the SC again. Otherwise, end turn after doing any end of turn abilities you have.");
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
        if (!game.isFoWMode()) {
            message.append(" by ").append(player.getRepresentation());
        }
        message.append(".\n\n");

        String gamePing = game.getPing();
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

        for (Player player2 : game.getPlayers().values()) {
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
            MessageHelper.sendFileToChannel(mainGameChannel, Helper.getSCImageFile(scToPlay, game));
        } else if (scModel != null) {
            baseMessageObject.addEmbeds(scModel.getRepresentationEmbed());
        }
        baseMessageObject.addContent(message.toString());

        // GET BUTTONS
        ActionRow actionRow = null;
        List<Button> scButtons = new ArrayList<>(getSCButtons(scToPlay, game, winnuHero));
        if (scModel.usesAutomationForSCID("pok7technology") && !game.isFoWMode() && Helper.getPlayerFromAbility(game, "propagation") != null) {
            scButtons.add(Button.secondary("nekroFollowTech", "Get CCs").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        }

        if (scModel.usesAutomationForSCID("pok4construction") && !game.isFoWMode() && Helper.getPlayerFromUnit(game, "titans_mech") != null) {
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
            Emoji reactionEmoji = Helper.getPlayerEmoji(game, player, message_);
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
                player.addFollowedSC(scToPlay);
            }
            game.setStoredValue("scPlay" + scToPlay, message_.getJumpUrl().replace(":", "666fin"));
            game.setStoredValue("scPlayMsgID" + scToPlay, message_.getId().replace(":", "666fin"));
            game.setStoredValue("scPlayMsgTime" + scToPlay, new Date().getTime() + "");
            for (Player p2 : game.getRealPlayers()) {
                if (!game.getStoredValue("scPlayPingCount" + scToPlay + p2.getFaction())
                    .isEmpty()) {
                    game.removeStoredValue("scPlayPingCount" + scToPlay + p2.getFaction());
                }
            }
            if (game.isFoWMode()) {
                // in fow, send a message back to the player that includes their emoji
                String response = "SC played.";
                response += reactionEmoji != null ? " " + reactionEmoji.getFormatted()
                    : "\nUnable to generate initial reaction, please click \"Not Following\" to add your reaction.";
                MessageHelper.sendPrivateMessageToPlayer(player, game, response);
            } else {
                // only do thread in non-fow games
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                threadChannel.queue(m5 -> {
                    List<ThreadChannel> threadChannels = game.getActionsChannel().getThreadChannels();
                    // SEARCH FOR EXISTING OPEN THREAD
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            if (game.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE)) {
                                MessageHelper.sendFileToChannel(threadChannel_, Helper.getSCImageFile(scToPlay, game));
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

            List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons(game);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
                assignSpeakerMessage, assignSpeakerActionRow);
        }
        if (scToPlay == ButtonHelper.getKyroHeroSC(game)
            && !player.getFaction().equalsIgnoreCase(game.getStoredValue("kyroHeroPlayer"))) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game),
                player.getRepresentation()
                    + " this is a reminder that this SC is kyro cursed and therefore you should only do 1 of its clauses. ");
        }

        if (scModel.usesAutomationForSCID("pok3politics")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " after assigning speaker, Use this button to draw agendas into your cards info thread.";

            List<Button> drawAgendaButton = new ArrayList<>();
            Button draw2Agenda = Button.success("FFCC_" + player.getFaction() + "_" + "drawAgenda_2", "Draw 2 agendas");
            drawAgendaButton.add(draw2Agenda);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
                assignSpeakerMessage2, drawAgendaButton);

        }
        if (scToPlay == 2 && game.isRedTapeMode()) {
            ButtonHelper.offerRedTapButtons(game, player);
        }

        if (scModel.usesAutomationForSCID("pok5trade")) {
            String assignSpeakerMessage2 = player.getRepresentation()
                + " you can force players to refresh, normally done in order to trigger a trade agreement. This is not required and not advised if you are offering them a conditional refresh.";
            List<Button> forceRefresh = ButtonHelper.getForcedRefreshButtons(game, player);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
                assignSpeakerMessage2, forceRefresh);

            for (Player p2 : game.getRealPlayers()) {
                if (!p2.getPromissoryNotes().containsKey(p2.getColor() + "_ta")) {
                    String message2 = p2.getRepresentation(true, true) + " heads up, trade has just been played and this is a reminder that you do not hold your Trade Agreement";
                    MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message2);
                }
            }

        }

        if (!scModel.usesAutomationForSCID("pok1leadership")) {
            Button emelpar = Button.danger("scepterE_follow_" + scToPlay, "Exhaust Scepter of Emelpar");
            for (Player player3 : game.getRealPlayers()) {
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
                if (player3.hasUnexhaustedLeader("mahactagent") && ButtonHelper.getTilesWithYourCC(player, game, event).size() > 0 && !winnuHero) {
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
        if (ButtonHelper.getEndOfTurnAbilities(player, game).size() > 1) {
            conclusionButtons.add(Button.primary("endOfTurnAbilities", "Do End Of Turn Ability (" + (ButtonHelper.getEndOfTurnAbilities(player, game).size() - 1) + ")"));
        }
        conclusionButtons.add(deleteButton);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use the buttons to end turn or take another action.", conclusionButtons);
        if (!game.isHomeBrewSCMode() && player.hasAbility("grace")
            && !player.getExhaustedAbilities().contains("grace")
            && ButtonHelperAbilities.getGraceButtons(game, player, scToPlay).size() > 2) {
            List<Button> graceButtons = new ArrayList<>();
            graceButtons.add(Button.success("resolveGrace_" + scToPlay, "Resolve Grace Ability"));
            graceButtons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
                player.getRepresentation(true, true) + " you can resolve grace with the buttons",
                graceButtons);
        }
        if (player.ownsPromissoryNote("acq") && scToPlay != 1 && !winnuHero) {
            for (Player player2 : game.getPlayers().values()) {
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
            default -> getGenericButtons(sc);
        };
    }

    private static void handleSOQueueing(Game game, boolean winnuHero) {
        if (winnuHero) {
            String message = "# Since this is a Winnu Hero play, SO draws will not be queued or resolved in a particular order";
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
            return;
        }
        Player imperialHolder = Helper.getPlayerWithThisSC(game, 8);
        String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        game.setStoredValue(key, "");
        game.setStoredValue(key2, "");
        game.setStoredValue(key3, "");
        if (game.getQueueSO()) {
            for (Player player : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
                if (player.getSoScored() + player.getSo() < player.getMaxSOCount()
                    || player.getSoScored() == player.getMaxSOCount()
                    || (player == imperialHolder && player.getPlanets().contains("mr"))) {
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

    private static List<Button> getPoliticsAssignSpeakerButtons(Game game) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(game.getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isValidFaction(faction)) {
                    if (!game.isFoWMode()) {
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
