package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.TargetType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.message.logging.BotLogger;
import ti4.service.async.BanCleanupService;
import ti4.spring.jda.JdaService;

public class BanListener extends ListenerAdapter {

    @Override
    public void onGuildAuditLogEntryCreate(@Nonnull GuildAuditLogEntryCreateEvent event) {
        try {
        AuditLogEntry log = event.getEntry();
        if (log.getType() == ActionType.BAN) {
            BanCleanupService.banSpamAccount(log, getTargetUser(log), getInitiatingUser(log));
        }
        } catch (Exception e) {
            BotLogger.error("Error attepting to propagate ban", e);
        }
    }

    private User getTargetUser(AuditLogEntry log) {
        if (log.getTargetType() == TargetType.MEMBER) {
            if (log.getTargetId() != null)
                return JdaService.jda.getUserById(log.getTargetId());
        }
        return null;
    }

    private User getInitiatingUser(AuditLogEntry log) {
        if (log.getUserId() != null)
            return JdaService.jda.getUserById(log.getUserId());
        return null;
    }
}
