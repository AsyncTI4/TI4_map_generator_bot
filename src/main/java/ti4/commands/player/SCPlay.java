package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
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

        int sc = player.getSC();
        String emojiName = "SC" + String.valueOf(sc);
        if (sc == 0) {
            sendMessage("No SC selected by player");
            return;
        }

        Boolean isSCPlayed = activeMap.getScPlayed().get(sc);
        if (isSCPlayed != null && isSCPlayed) {
            sendMessage("SC already played");
            return;
        }
        
        activeMap.setSCPlayed(sc, true);
        String categoryForPlayers = Helper.getGamePing(event, activeMap);
        String message = "Strategy card " + Helper.getEmojiFromDiscord(emojiName) + Helper.getSCAsMention(event.getGuild(), sc) + " played by " + Helper.getPlayerRepresentation(event, player) + "\n\n";
        if (activeMap.isFoWMode()) {
            message = "Strategy card " + Helper.getEmojiFromDiscord(emojiName) + Helper.getSCAsMention(event.getGuild(), sc) + " played.\n\n";
        }
        if (!categoryForPlayers.isEmpty()) {
            message += categoryForPlayers + "\n";
        }
        message += "Please indicate your choice by pressing a button below and post additional details in the thread.";

        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-" + Helper.getSCName(sc);
        TextChannel textChannel = (TextChannel) mainGameChannel;
        
        for (Player player2 : activeMap.getPlayers().values()) {
            if (!player2.isRealPlayer()) {
                continue;
            }
            String faction = player2.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")) continue;
            player2.setSCFollowedStatus(sc, false);
        }
        
        ActionRow actionRow = getSCButtons(sc);
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);
        baseMessageObject.addComponents(actionRow);
        
        final Player player_ = player;
        mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            Emoji reactionEmoji = Helper.getPlayerEmoji(activeMap, player_, message_); 
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
                player_.setSCFollowedStatus(sc, true);
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
                threadChannel.queue();
            }
        });
    }

    private ActionRow getSCButtons(int sc) {       
        return switch (sc) {
            case 1 -> getLeadershipButtons();
            case 2 -> getDiplomacyButtons();
            case 3 -> getPoliticsButtons();
            case 4 -> getConstructionButtons();
            case 5 -> getTradeButtons();
            case 6 -> getWarfareButtons();
            case 7 -> getTechnologyButtons();
            case 8 -> getImperialButtons();
            default -> ActionRow.of();
        };
    }

    private ActionRow getLeadershipButtons() {
        Button followButton = Button.success("sc_follow_leadership", "SC Follow #1");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #1");
        return ActionRow.of(followButton, noFollowButton);
    }
    
    private ActionRow getDiplomacyButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #2");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #2");
        return ActionRow.of(followButton, noFollowButton);
    }
    
    private ActionRow getPoliticsButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #3");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #3");
        Button draw_2_ac = Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        return ActionRow.of(followButton, noFollowButton, draw_2_ac);
    }
    
    private ActionRow getPoliticsAssignSpeakerButtons() {
        List<Button> politicsButtons = new ArrayList<>();
        for (Player player : getActiveMap().getPlayers().values()) {
            if (player.isRealPlayer() && player.getUserID().equals(getActiveMap().getSpeaker())) {
                String faction = player.getFaction();
                if (Mapper.isFaction(faction)) {
                    Button button = Button.danger("sc_3_assign_speaker_to_" + faction, "").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction)));
                    politicsButtons.add(button);
                }
            }
        }
        return ActionRow.of(politicsButtons);
    }

    private ActionRow getConstructionButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #4");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #4");
        return ActionRow.of(followButton, noFollowButton);
    }
    
    private ActionRow getTradeButtons() {
        Button trade_primary = Button.success("trade_primary", "Resolve Primary #5");
        Button followButton = Button.success("sc_follow_trade", "SC Follow #5");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #5");
        Button refresh_and_wash = Button.secondary("sc_refresh_and_wash", "Replenish and Wash for SC #5").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button refresh = Button.secondary("sc_refresh", "Replenish Commodities for SC #5").withEmoji(Emoji.fromFormatted(Emojis.comm));
        return ActionRow.of(trade_primary, followButton, noFollowButton, refresh, refresh_and_wash);
    }
    
    private ActionRow getWarfareButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #6");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #6");
        return ActionRow.of(followButton, noFollowButton);
    }
    
    private ActionRow getTechnologyButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #7");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #7");
        return ActionRow.of(followButton, noFollowButton);
    }
    
    private ActionRow getImperialButtons() {
        Button followButton = Button.success("sc_follow", "SC Follow #8");
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following #8");
        Button draw_so = Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective));
        return ActionRow.of(followButton, noFollowButton, draw_so);
    }
}
