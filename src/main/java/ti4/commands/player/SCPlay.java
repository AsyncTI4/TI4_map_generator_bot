package ti4.commands.player;

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
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(message);

        Button followButton;
        if (sc == 1) {
            followButton = Button.success("sc_follow_leadership", "SC Follow");
        } else {
            if (sc == 5) {
                followButton = Button.success("sc_follow_trade", "SC Follow");
            } else {
                followButton = Button.success("sc_follow", "SC Follow");
            }
        }
        Button noFollowButton = Button.primary("sc_no_follow", "Not Following");
        Button trade_primary = Button.success("trade_primary", "Resolve Primary");
        Button refresh = Button.secondary("sc_refresh", "Replenish Commodities").withEmoji(Emoji.fromFormatted(Emojis.comm));
        Button refresh_and_wash = Button.secondary("sc_refresh_and_wash", "Replenish and Wash").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button draw_2_ac = Button.secondary("sc_ac_draw", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
        Button draw_so = Button.secondary("sc_draw_so", "Draw Secret Objective").withEmoji(Emoji.fromFormatted(Emojis.SecretObjective));

        ActionRow of;
        if (sc == 5) {
            of = ActionRow.of(trade_primary, followButton, noFollowButton, refresh, refresh_and_wash);
        } else if (sc == 3) {
            of = ActionRow.of(followButton, noFollowButton, draw_2_ac);
        } else if (sc == 8) {
            of = ActionRow.of(followButton, noFollowButton, draw_so);
        } else {
            of = ActionRow.of(followButton, noFollowButton);
        }
        baseMessageObject.addComponents(of);
        
        final Player player_ = player;
        mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
            Emoji reactionEmoji = Helper.getPlayerEmoji(activeMap, player_, message_); 
            if (reactionEmoji != null) {
                message_.addReaction(reactionEmoji).queue();
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
        event.getHook().deleteOriginal().queue();
    }
}
