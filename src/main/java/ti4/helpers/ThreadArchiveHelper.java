package ti4.helpers;

import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.message.BotLogger;

public class ThreadArchiveHelper {

	public static void archiveOldThreads(Guild guild, Integer threadCount) {
		// Try gathering all threads that are not bot-map or cards-info threads
		List<ThreadChannel> threadChannels = guild.getThreadChannels().stream()
			.filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
			.filter(threadChannel -> !threadChannel.getName().contains("bot-map-updates") && !threadChannel.getName().contains("cards-info"))
			.sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
			.limit(threadCount)
			.toList();

		// If there are fewer channels in the list than requested to close, include cards-info threads
		if (threadChannels.size() < (threadCount - 1)) {
			threadChannels = guild.getThreadChannels().stream()
				.filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
				.filter(threadChannel -> !threadChannel.getName().contains("bot-map-updates"))
				.sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
				.limit(threadCount)
				.toList();
		}

		for (ThreadChannel threadChannel : threadChannels) {
			threadChannel.getManager()
				.setArchived(true)
				.queue(null, BotLogger::catchRestError);
		}
	}
}
