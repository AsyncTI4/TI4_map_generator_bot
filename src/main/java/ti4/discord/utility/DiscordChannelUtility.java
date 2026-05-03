package ti4.discord.utility;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.GuildImpl;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import net.dv8tion.jda.internal.requests.DeferredRestAction;
import net.dv8tion.jda.internal.requests.RestActionImpl;

@UtilityClass
public class DiscordChannelUtility {

    public CacheRestAction<ThreadChannel> retrieveThreadChannelById(Guild guild, long id) {
        JDA jda = guild.getJDA();
        return new DeferredRestAction<>(
                jda,
                ThreadChannel.class,
                () -> guild.getThreadChannelById(id),
                () -> new RestActionImpl<>(jda, Route.Channels.GET_CHANNEL.compile(Long.toString(id)), (res, _) -> {
                    DataObject dataObject = res.getObject();
                    ChannelType channelType = ChannelType.fromId(dataObject.getInt("type"));
                    if (!channelType.isThread()) {
                        throw new RuntimeException("Invalid channel type, expected a thread, got " + channelType);
                    }

                    return ((JDAImpl) jda)
                            .getEntityBuilder()
                            .createThreadChannel((GuildImpl) guild, dataObject, guild.getIdLong(), true);
                }));
    }

    public RestAction<ThreadChannel> retrieveFirstThreadChannelByNameIgnoringCase(TextChannel channel, String name) {
        JDA jda = channel.getJDA();
        Guild guild = channel.getGuild();
        return new DeferredRestAction<>(
                jda,
                ThreadChannel.class,
                () -> guild.getThreadChannelsByName(name, true).stream()
                        .findFirst()
                        .orElse(null),
                () -> guild.retrieveActiveThreads().flatMap(activeThreads -> {
                    ThreadChannel found = activeThreads.stream()
                            .filter(t -> t.getName().equalsIgnoreCase(name))
                            .findFirst()
                            .orElse(null);

                    if (found != null) {
                        return new CompletedRestAction<>(jda, found);
                    }

                    return channel.retrieveArchivedPrivateThreadChannels()
                            .map(archivedThreads -> archivedThreads.stream()
                                    .filter(t -> t.getName().equalsIgnoreCase(name))
                                    .findFirst()
                                    .orElse(null));
                }));
    }
}
