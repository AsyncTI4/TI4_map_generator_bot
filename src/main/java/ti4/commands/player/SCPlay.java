package ti4.commands.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

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
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SCPlay extends PlayerSubcommandData {
    public SCPlay() {
        super(Constants.SC_PLAY, "Play SC");
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Which SC to play. If you have more than 1 SC, this is mandatory"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);

        Helper.checkThreadLimitAndArchive(event.getGuild());

        MessageChannel eventChannel = event.getChannel();
        MessageChannel mainGameChannel = activeMap.getMainGameChannel() == null ? eventChannel : activeMap.getMainGameChannel();

        if (player == null) {
            sendMessage("You're not a player of this game");
            return;
        }

        LinkedHashSet<Integer> playersSCs = player.getSCs();
        if (playersSCs.isEmpty()) {
            sendMessage("No SC has been selected by the player");
            return;
        }

        if (playersSCs.size() != 1 && event.getOption(Constants.STRATEGY_CARD) == null) { //Only one SC selected
            sendMessage("Player has more than one SC. Please try again, using the `strategy_card` option.");
            return;
        }

        Integer scToPlay = event.getOption(Constants.STRATEGY_CARD, Collections.min(player.getSCs()), OptionMapping::getAsInt);
        playSC(event, scToPlay, activeMap, mainGameChannel, player);

    }

    public void playSC(GenericInteractionCreateEvent event, Integer scToPlay, Map activeMap, MessageChannel mainGameChannel, Player player) {
        Integer scToDisplay = scToPlay;
        String pbd100group = null;
        boolean pbd100or500 = activeMap.getName().equals("pbd100") || activeMap.getName().equals("pbd500");
        if (pbd100or500) {
            scToDisplay = scToPlay / 10;
            int groupNum = scToPlay % 10;
            switch (groupNum) {
                case 1 -> pbd100group = "A";
                case 2 -> pbd100group = "B";
                case 3 -> pbd100group = "C";
                case 4 -> pbd100group = "D";
                default -> pbd100group = "Unknown";
            }
        }

        String emojiName = "SC" + String.valueOf(scToPlay);

        if (activeMap.getPlayedSCs().contains(scToPlay)) {
            sendMessage("SC already played");
            return;
        }

        activeMap.setSCPlayed(scToPlay, true);
        String categoryForPlayers = Helper.getGamePing(event, activeMap);
        String message = "Strategy card " + Helper.getEmojiFromDiscord(emojiName) + Helper.getSCAsMention(event.getGuild(), scToDisplay) + (pbd100or500 ? " Group " + pbd100group : "") + " played by " + Helper.getPlayerRepresentation(player, activeMap) + "\n\n";
        if (activeMap.isFoWMode()) {
            if(activeMap.isHomeBrewSCMode())
            {
                message = "Strategy card #" + scToPlay + " played.\n\n";
            }
            else
            {
                message = "Strategy card " + Helper.getEmojiFromDiscord(emojiName) + Helper.getSCAsMention(event.getGuild(), scToDisplay) + " played.\n\n";
            }
        }
        if (!categoryForPlayers.isEmpty()) {
            message += categoryForPlayers + "\n";
        }
        message += "Please indicate your choice by pressing a button below and post additional details in the thread.";

        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-" + Helper.getSCName(scToDisplay) + (pbd100or500 ? "-group_" + pbd100group : "");
        TextChannel textChannel = (TextChannel) mainGameChannel;

        for (Player player2 : activeMap.getPlayers().values()) {
            if (!player2.isRealPlayer()) {
                continue;
            }
            String faction = player2.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")) continue;
            player2.removeFollowedSC(scToPlay);
        }

        if (activeMap.getOutputVerbosity().equals(Constants.VERBOSITY_VERBOSE)) {
            MessageHelper.sendMessageToChannel(mainGameChannel, Helper.getSCImageLink(scToDisplay));
        }


        
            MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);
            //GET BUTTONS
            ActionRow actionRow = null;
            List<Button> scButtons = getSCButtons(scToDisplay, activeMap);
            if (scButtons != null && !scButtons.isEmpty()) actionRow = ActionRow.of(scButtons);
            if (actionRow != null) baseMessageObject.addComponents(actionRow);

            final Player player_ = player;
            mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                Emoji reactionEmoji = Helper.getPlayerEmoji(activeMap, player_, message_);
                if (reactionEmoji != null) {
                    message_.addReaction(reactionEmoji).queue();
                    player_.addFollowedSC(scToPlay);
                }

                if (activeMap.isFoWMode()) {
                    //in fow, send a message back to the player that includes their emoji
                    String response = "SC played.";
                    response += reactionEmoji != null ? " " + reactionEmoji.getFormatted() : "\nUnable to generate initial reaction, please click \"Not Following\" to add your reaction.";
                    MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, response);
                } else {
                    //only do thread in non-fow games
                    ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                    threadChannel.queue(m5 -> {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels != null) {
                            // SEARCH FOR EXISTING OPEN THREAD
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) threadChannel_, "These buttons will work inside the thread", scButtons);
                                }
                            }
                        }
                    });

                }
            });
        


        //POLITICS - SEND ADDITIONAL ASSIGN SPEAKER BUTTONS
        if (!activeMap.isFoWMode() && scToPlay == 3) {
            String assignSpeakerMessage = Helper.getPlayerRepresentation(player, activeMap) + ", please click a faction below to assign Speaker " + Emojis.SpeakerToken;
            List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons();
            

            for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(assignSpeakerMessage, assignSpeakerActionRow)) {
                mainGameChannel.sendMessage(messageCreateData).queue();
            }
        }

        if (scToPlay == 3 && !activeMap.isHomeBrewSCMode()) {
            List<Button> drawAgendaButton = new ArrayList<Button>();
            Button draw2Agenda = Button.success("FFCC_"+player.getFaction()+"_"+"drawAgenda_2", "Draw 2 agendas");
            drawAgendaButton.add(draw2Agenda);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel)player.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), false)+" click this after assigning speaker.", drawAgendaButton);
        }

        if (scToPlay == 6 && !activeMap.isHomeBrewSCMode()) {
            List<Button> redistributeButton = new ArrayList<Button>();
            Button redistribute= Button.success("FFCC_"+player.getFaction()+"_"+"redistributeCCButtons", "Redistribute & Gain CCs");
            Button deleButton= Button.danger("FFCC_"+player.getFaction()+"_"+"deleteButtons", "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel)player.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), false) +" click this after picking up a CC.", redistributeButton);
        }

        List<Button> conclusionButtons = new ArrayList<Button>();
        Button endTurn = Button.danger("turnEnd", "End Turn");
        Button deleteButton = Button.danger("deleteButtons", "Do Another Action");
        conclusionButtons.add(endTurn);
        conclusionButtons.add(deleteButton);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to end turn or take another action.", conclusionButtons);



        if (player.getFaction().equalsIgnoreCase("winnu")&& scToPlay != 1) {
            for (Player player2 :activeMap.getPlayers().values()) {
                if (!player2.getPromissoryNotes().isEmpty()) {
                    for (String pn : player2.getPromissoryNotes().keySet()) {
                        if (!player2.getFaction().equalsIgnoreCase("winnu") && pn.equalsIgnoreCase("acq")) {
                            String acqMessage = Helper.getPlayerRepresentation(player2, activeMap, event.getGuild(), true) + " reminder you can use Winnu's PN!";
                            if (activeMap.isFoWMode()) {
                                MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), acqMessage);
                            } else {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), acqMessage);
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Button> getSCButtons(int sc, Map activeMap) {

        if (activeMap.isHomeBrewSCMode()) {
            return getGenericButtons(sc);
        }
        return switch (sc) {
            case 1 -> getLeadershipButtons();
            case 2 -> getDiplomacyButtons();
            case 3 -> getPoliticsButtons();
            case 4 -> getConstructionButtons();
            case 5 -> getTradeButtons();
            case 6 -> getWarfareButtons();
            case 7 -> getTechnologyButtons();
            case 8 -> getImperialButtons();
            default -> getGenericButtons(sc);
        };
    }

    private List<Button> getLeadershipButtons() {
        Button followButton = Button.success("sc_leadership_follow", "SC Follow");
        Button leadershipGenerateCCButtons = Button.success("leadershipGenerateCCButtons", "Gain CCs");
        Button exhaust = Button.danger("leadershipExhaust", "Exhaust Planets");
        Button noFollowButton = Button.primary("sc_no_follow_1", "Not Following");
        return List.of(followButton, leadershipGenerateCCButtons, exhaust,noFollowButton);
    }

    private List<Button> getDiplomacyButtons() {
        Button followButton = Button.success("sc_follow_2", "Spend A Strategy CC");
        Button diploSystemButton = Button.primary("diploSystem", "Diplo a System");
        Button refreshButton = Button.success("diploRefresh2", "Ready 2 Planets");

        Button noFollowButton = Button.danger("sc_no_follow_2", "Not Following");
        return List.of(followButton,diploSystemButton, refreshButton, noFollowButton);
    }

    private List<Button> getPoliticsButtons() {
        Button followButton = Button.success("sc_follow_3", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_3", "Not Following");
        Button draw_2_ac = Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        return List.of(followButton, noFollowButton, draw_2_ac);
    }

    private List<Button> getPoliticsAssignSpeakerButtons() {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : getActiveMap().getPlayers().values()) {
            if (player.isRealPlayer() && !player.getUserID().equals(getActiveMap().getSpeaker())) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = Button.secondary(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX + faction, " ");
                    String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

    private List<Button> getConstructionButtons() {
        Button followButton = Button.success("sc_follow_4", "Spend A Strategy CC");
        Button sdButton = Button.success("construction_sd", "Place A SD");
        sdButton = sdButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("spacedock")));
        Button pdsButton = Button.success("construction_pds", "Place a PDS");

        pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("pds")));
        Button noFollowButton = Button.primary("sc_no_follow_4", "Not Following");
        return List.of(followButton, sdButton, pdsButton, noFollowButton);
    }

    private List<Button> getTradeButtons() {
        Button trade_primary = Button.success("trade_primary", "Resolve Primary");
        Button followButton = Button.success("sc_trade_follow", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_5", "Not Following");
        Button refresh_and_wash = Button.secondary("sc_refresh_and_wash", "Replenish and Wash for SC").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button refresh = Button.secondary("sc_refresh", "Replenish Commodities for SC").withEmoji(Emoji.fromFormatted(Emojis.comm));
        return List.of(trade_primary, followButton, noFollowButton, refresh, refresh_and_wash);
    }

    private List<Button> getWarfareButtons() {
        Button followButton = Button.success("sc_follow_6", "Spend A Strategy CC");
        Button homeBuild = Button.success("warfareBuild", "Build At Home");
        Button noFollowButton = Button.primary("sc_no_follow_6", "Not Following");
        return List.of(followButton,homeBuild, noFollowButton);
    }

    private List<Button> getTechnologyButtons() {
        Button followButton = Button.success("sc_follow_7", "Spend A Strategy CC");
        Button getTech = Button.success("acquireATech", "Get a Tech");
        Button noFollowButton = Button.primary("sc_no_follow_7", "Not Following");
        return List.of(followButton, getTech ,noFollowButton);
    }

    private List<Button> getImperialButtons() {
        Button followButton = Button.success("sc_follow_8", "Spend A Strategy CC");
        Button noFollowButton = Button.primary("sc_no_follow_8", "Not Following");
        Button draw_so = Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective));
        Button scoreImperial = Button.secondary("score_imperial", "Score Imperial").withEmoji(Emoji.fromFormatted(Emojis.MecatolRex));
        return List.of(followButton, noFollowButton, draw_so, scoreImperial);
    }

    private List<Button> getGenericButtons(int sc) {
        Button followButton = Button.success("sc_follow_"+sc, "Spend A Strategy CC" );
        Button noFollowButton = Button.primary("sc_no_follow_"+sc, "Not Following");
        return List.of(followButton, noFollowButton);
    }
}
