package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerLimitStats extends BothelperSubcommandData {
    public ServerLimitStats(){
        super(Constants.SERVER_LIMIT_STATS, "Import a recent TTPG Export to a new Async game");

    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        int memberCount = guild.getMemberCount(); //500,000
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); //250
        int channelCount = guild.getChannels().size(); //500
        int threadCount = guild.getThreadChannels().size(); //1000
        int emojiCount = guild.getEmojis().size();
        int maxEmojis = guild.getMaxEmojis();

        StringBuilder sb = new StringBuilder("Server Limit Statistics:\n>>> ");
        sb.append(memberCount).append("/500,000 - members").append("\n");
        sb.append(boostCount).append(" - boosts").append("\n");
        sb.append(roleCount).append("/250 - roles").append("\n");
        sb.append(channelCount).append("/500 - channels").append("\n");
        sb.append(threadCount).append("/1000 - threads").append("\n");
        sb.append(emojiCount).append("/" + maxEmojis + " - emojis").append("\n");

        MessageHelper.replyToMessage(event, sb.toString());

    }
}
