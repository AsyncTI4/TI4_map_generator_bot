package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerLimitStats extends BothelperSubcommandData {
    public ServerLimitStats(){
        super(Constants.SERVER_LIMIT_STATS, "Import a recent TTPG Export to a new Async game");

    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        List<ThreadChannel> threadChannels = guild.getThreadChannels();

        int memberCount = guild.getMemberCount();
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); //250
        int channelCount = guild.getChannels().size(); //500
        int threadCount = threadChannels.size(); //1000
        long cardsInfoThreadCount = threadChannels.stream().filter(t -> t.getName().startsWith("Cards Info")).count();
        long botThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-bot-")).count();
        long scThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-round-")).count();
        int emojiCount = guild.getEmojis().size();
        int emojiMax = guild.getMaxEmojis();

        StringBuilder sb = new StringBuilder("Server Limit Statistics:\n>>> ");
        sb.append(memberCount).append(" / " + memberMax + " - members").append("\n");
        sb.append(boostCount).append(" - boosts").append("\n");
        sb.append(emojiCount).append(" / " + emojiMax + " - emojis").append("\n");
        sb.append(roleCount).append(" / 250 - roles").append("\n");
        sb.append(channelCount).append(" / 500 - channels").append("\n");
        sb.append(threadCount).append(" / 1000 - threads").append("\n");
        sb.append("     - ").append(cardsInfoThreadCount).append("   'Cards Info' threads (/ac info)").append("\n");
        sb.append("     - ").append(botThreadCount).append("   '-bot' threads").append("\n");
        sb.append("     - ").append(scThreadCount).append("   '-round-' threads (/sc play)").append("\n");

        MessageHelper.replyToMessage(event, sb.toString());

        // for (ThreadChannel thread : threadChannels.stream().sorted((object1, object2) -> object2.getTimeArchiveInfoLastModified().compareTo(object1.getTimeArchiveInfoLastModified())).toList()) {
        //     System.out.println(thread.getName() + "  " + thread.getTimeArchiveInfoLastModified());
        // }


    }
}
