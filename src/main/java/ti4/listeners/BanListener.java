package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.message.logging.BotLogger;
import ti4.service.async.BanCleanupService;

public class BanListener extends ListenerAdapter {

    @Override
    public void onGuildAuditLogEntryCreate(@Nonnull GuildAuditLogEntryCreateEvent event) {
        try {
            AuditLogEntry log = event.getEntry();
            UserSnowflake target = getTargetUser(log);
            UserSnowflake admin = getInitiatingUser(log);
            BanCleanupService.banSpamAccount(log, target, admin);
        } catch (Exception e) {
            BotLogger.error("Error attempting to propagate ban", e);
        }
    }

    private UserSnowflake getTargetUser(AuditLogEntry log) {
        if (log.getTargetType() == TargetType.MEMBER) {
            if (log.getTargetId() != null) {
                return UserSnowflake.fromId(log.getTargetId());
            }
        }
        return null;
    }

    private UserSnowflake getInitiatingUser(AuditLogEntry log) {
        if (log.getUserId() != null) {
            return UserSnowflake.fromId(log.getUserId());
        }
        return null;
    }
}
