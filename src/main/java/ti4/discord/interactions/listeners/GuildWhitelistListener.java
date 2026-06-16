package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;

class GuildWhitelistListener extends ListenerAdapter {

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getGuilds().stream()
                .filter(GuildWhitelistListener::isNotWhitelistedGuild)
                .forEach(GuildWhitelistListener::leaveGuild);
    }

    private static boolean isNotWhitelistedGuild(Guild guild) {
        return JdaService.guilds.stream()
                .noneMatch(whitelistGuild -> whitelistGuild.getId().equals(guild.getId()));
    }

    private static void leaveGuild(Guild badGuild) {
        logLeavingGuildWarning(badGuild);
        //badGuild.leave().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void logLeavingGuildWarning(Guild eventGuild) {
        BotLogger.warning("Leaving guild '" + eventGuild.getName() + "' because it wasn't whitelisted!");
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild eventGuild = event.getGuild();
        if (isNotWhitelistedGuild(eventGuild)) leaveGuild(eventGuild);
    }
}
