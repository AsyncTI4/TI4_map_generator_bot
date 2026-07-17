package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.discord.JdaService;

class GuildWhitelistListener extends ListenerAdapter {

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        JdaService.leaveGuildIfNotWhitelisted(event.getGuild());
    }
}
