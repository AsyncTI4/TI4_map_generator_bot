package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerLimitStats extends BothelperSubcommandData {
    public ServerLimitStats(){
        super(Constants.SERVER_LIMIT_STATS, "Server Limit Stats");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        
        int memberCount = guild.getMemberCount();
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); //250
        
        //CHANNELS
        List<GuildChannel> channels = guild.getChannels();
        int channelCount = channels.size(); //500
        long pbdChannelCount = channels.stream().filter(c -> c.getName().startsWith("pbd")).count();
        long categoryChannelCount = channels.stream().filter(c -> c.getType() == ChannelType.CATEGORY).count();
        
        //THREADS
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        int threadCount = threadChannels.size(); //1000
        long cardsInfoThreadCount = threadChannels.stream().filter(t -> t.getName().startsWith("Cards Info")).count();
        long botThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-bot-")).count();
        long mapThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-map-")).count();
        long scThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-round-")).count();
        long privateThreadCount = threadChannels.stream().filter(t -> !t.isPublic()).count();
        long publicThreadCount = threadChannels.stream().filter(t -> t.isPublic()).count();

        //IN LIMBO
        Category inLimboArchive = guild.getCategoryById("972686637094039562");
        List<GuildChannel> inLimboChannels = inLimboArchive == null ? new ArrayList<>() : inLimboArchive.getChannels();
        long inLimboChannelCount = inLimboChannels.size();
        long inLimboThreadCount = 0;
        for (GuildChannel channel : inLimboChannels) {
            if(channel.getType() == ChannelType.TEXT) {
                inLimboThreadCount += ((TextChannel) channel).getThreadChannels().size();
            }
        }
        
        

        int emojiCount = guild.getEmojis().size();
        int emojiMax = guild.getMaxEmojis();

        StringBuilder sb = new StringBuilder("Server Limit Statistics:\n>>> ");
        sb.append(memberCount).append(" / " + memberMax + getPercentage(memberCount, memberMax) + " - members").append("\n");
        sb.append(boostCount).append(" - boosts").append("\n");
        sb.append(emojiCount).append(" / " + emojiMax + getPercentage(emojiCount, emojiMax) + " - emojis").append("\n");
        sb.append(roleCount).append(" / 250" + getPercentage(roleCount, 250) + " - roles").append("\n");
        sb.append("**").append(channelCount).append(" / 500" + getPercentage(channelCount, 500) + " - channels**").append("\n");
        sb.append("     - ").append(pbdChannelCount).append("   " + getPercentage(pbdChannelCount, channelCount) + "  'pbd' channels").append("\n");
        sb.append("     - ").append(categoryChannelCount).append("   " + getPercentage(categoryChannelCount, channelCount) + "  categories").append("\n");
        sb.append("**").append(threadCount).append(" / 1000" + getPercentage(threadCount, 1000) + " - threads**").append("\n");
        sb.append("     - ").append(privateThreadCount).append("   " + getPercentage(privateThreadCount, threadCount) + "  private threads").append("\n");
        sb.append("     - ").append(cardsInfoThreadCount).append("   " + getPercentage(cardsInfoThreadCount, threadCount) + "  'Cards Info' threads (/ac info)").append("\n");
        sb.append("     - ").append(publicThreadCount).append("   " + getPercentage(publicThreadCount, threadCount) + "  public threads").append("\n");
        sb.append("     - ").append(botThreadCount + mapThreadCount).append("   " + getPercentage(botThreadCount + mapThreadCount, threadCount) + "  '-bot-' and '-map-' threads").append("\n");
        sb.append("     - ").append(scThreadCount).append("   " + getPercentage(scThreadCount, threadCount) + "  '-round-' threads (/sc play)").append("\n");
        if (inLimboArchive != null) {
            sb.append("**In Limbo:** ").append(inLimboArchive.getAsMention()).append("\n");
            sb.append("     - ").append(inLimboChannelCount).append("   " + getPercentage(inLimboChannelCount, channelCount) + "  'in-limbo' channels").append("\n");
            sb.append("     - ").append(inLimboThreadCount).append("   " + getPercentage(inLimboThreadCount, threadCount) + "  'in-limbo' threads").append("\n");
        }
        MessageHelper.replyToMessage(event, sb.toString());
    }

    private String getPercentage(double numerator, double denominator) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMinimumFractionDigits(1);
        String formatted = formatPercent.format(denominator == 0 ? 0.0 : (numerator / denominator));
        formatted = " *(" + formatted + ")* ";
        return formatted;
    }
}
