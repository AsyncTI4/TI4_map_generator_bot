package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.cache.CacheManager;
import ti4.cache.CacheStatsToStringConverter;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class CacheStatistics extends Subcommand {

    CacheStatistics() {
        super("cache_statistics", "Get stats related to managed caches.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String cacheStats = CacheStatsToStringConverter.convert(CacheManager.getNamesToCaches());
        MessageHelper.sendMessageToChannel(event.getChannel(), "```\n" + cacheStats + "\n```");
    }
}