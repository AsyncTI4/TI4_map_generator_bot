package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;

class GuildWhitelistListener extends ListenerAdapter {

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getGuilds().stream()
                .filter(guild -> JdaService.guilds.stream()
                        .noneMatch(whitelistGuild -> whitelistGuild.getId().equals(guild.getId())))
                .forEach(badGuild -> {
                    postLeavingGuildWarning(badGuild);
                    badGuild.leave().queue(Consumers.nop(), BotLogger::catchRestError);
                });
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild eventGuild = event.getGuild();
        boolean isBadGuild = JdaService.guilds.stream()
                .noneMatch(whitelistGuild -> whitelistGuild.getId().equals(eventGuild.getId()));
        if (isBadGuild) {
            postLeavingGuildWarning(eventGuild);
            event.getGuild().leave().queue();
        }
    }

    private static void postLeavingGuildWarning(Guild eventGuild) {
        BotLogger.warning("Leaving guild '" + eventGuild.getName() + "' because it wasn't whitelisted!");
    }
}
