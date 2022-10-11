package ti4.commands.player;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
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
        MessageChannel channel = event.getChannel();
        if (player == null) {
            MessageHelper.sendMessageToChannel(channel, "Your not a player of this game");
            return;
        }
        int sc = player.getSC();
        if (sc == 0) {
            MessageHelper.sendMessageToChannel(channel, "No SC selected by player");
            return;
        }
        Boolean isSCPlayed = activeMap.getScPlayed().get(sc);
        if (isSCPlayed != null && isSCPlayed) {
            MessageHelper.sendMessageToChannel(channel, "SC already played");
            return;
        }
        activeMap.setSCPlayed(sc, true);
        String categoryForPlayers = Helper.getGamePing(event, activeMap);
        String message = "";
        if (!categoryForPlayers.isEmpty()) {
            message += categoryForPlayers + "\n";
        }
        message += "Strategy card " + Helper.getSCAsMention(sc) + " played. Please react with your faction symbol to pass or post in thread for secondaries.";
        String name = channel.getName();
        if (name.contains("-")) {
            String threadName = name.substring(0, name.indexOf("-")) + "-round-" + activeMap.getRound() + "-" + Helper.getSCName(sc);
            TextChannel textChannel = event.getTextChannel();
            Button followButton;
            if (sc == 1){
                followButton = Button.success("sc_follow_leadership", "SC Follow");
            } else {
                if (sc == 3){
                    followButton = Button.success("sc_follow_politics", "SC Follow");
                } else if (sc == 5){
                    followButton = Button.success("sc_follow_trade", "SC Follow");
                } else {
                    followButton = Button.success("sc_follow", "SC Follow");
                }
            }
            Button noFollowButton = Button.primary("sc_no_follow", "Not Following");

            Button refresh = Button.secondary("sc_refresh", "Refresh Commodities");
            Button refresh_and_wash = Button.secondary("sc_refresh_and_wash", "Refresh and Wash");
            ActionRow of;
            if (sc == 5){
                of = ActionRow.of(followButton, noFollowButton, refresh, refresh_and_wash);
            } else {
                of = ActionRow.of(followButton, noFollowButton);
            }

            Message messageObject = new MessageBuilder()
                    .append(message)
                    .setActionRows(of).build();
            channel.sendMessage(messageObject).queue(message_ -> {
                ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                threadChannel.queue();
            });
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap);
        MessageHelper.replyToMessageTI4Logo(event);
    }
}
