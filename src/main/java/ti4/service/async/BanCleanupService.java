package ti4.service.async;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.spring.jda.JdaService;

@UtilityClass
public class BanCleanupService {

    private static final List<String> BOT_QUESTION_CHANNEL_NAMES = List.of(
            "bot-questions-and-support-and-feedback",
            "bot-questions-and-feedback",
            "bot-questions",
            "bot-questions-and-discussion",
            "bot-questions-and-discussions");

    private User getUser(UserSnowflake id) {
        return JdaService.jda.getUserById(id.getId());
    }

    private String getIdent(UserSnowflake id) {
        if (id == null) return "NULL USER";

        User user = getUser(id);
        if (user != null) {
            return "user `" + user.getEffectiveName() + "` with ID `" + id.getId() + "`";
        } else {
            return "user `unknownUser` with ID `" + id.getId() + "`";
        }
    }

    public boolean banSpamAccount(UserContextInteractionEvent event, UserSnowflake target, UserSnowflake admin) {
        if (shouldBanFromAllGuilds(null, event, target, admin)) {
            String reason = "Banned by " + getUser(admin).getEffectiveName();
            BotLogger.info("Banning " + getIdent(target) + " for reason: " + reason);
            int errors = removeUserFromAllGuilds(target, reason);
            auditPostBanAction(target, event.getGuild(), reason, errors);
            return errors == 0;
        }
        return false;
    }

    public boolean banSpamAccount(AuditLogEntry log, UserSnowflake target, UserSnowflake admin) {
        if (shouldBanFromAllGuilds(log, null, target, admin)) {
            String reason = log.getReason();
            BotLogger.info("Banning " + getIdent(target) + " for reason: " + reason);
            int errors = removeUserFromAllGuilds(target, log.getReason());
            auditPostBanAction(target, log.getGuild(), reason, errors);
            return errors == 0;
        }
        return false;
    }

    private boolean shouldBanFromAllGuilds(
            AuditLogEntry log, UserContextInteractionEvent event, UserSnowflake target, UserSnowflake admin) {
        // Don't need to audit these
        if (log != null && log.getType() != ActionType.BAN) {
            return false;
        }
        if (event != null && !"Ban".equals(event.getName())) {
            return false;
        }
        if (admin != null && admin.getId().equals(JdaService.getBotId())) { // bot-propagated
            return false;
        }

        // Go ahead and audit all other reasons for failing to propagate a ban
        String prefix = "Could not fully ban " + getIdent(target);
        if (log != null) {
            prefix += " for audit log entry `" + log.getId() + "`: ";
        } else if (event != null) {
            prefix += " for user event `" + event.getId() + "`: ";
        }
        if (admin == null) {
            BotLogger.warning(prefix + " Initiating user not found.");
            return false;
        }
        if (target == null) {
            BotLogger.warning(prefix + " Target user not found.");
            return false;
        }
        ManagedPlayer mp = GameManager.getManagedPlayer(target.getId());
        if (mp != null && !mp.getGames().isEmpty()) {
            BotLogger.warning(prefix + " User is in at least 1 game.");
            return false;
        }
        return true;
    }

    private int removeUserFromAllGuilds(UserSnowflake user, String reason) {
        int errors = 0;
        Collection<UserSnowflake> banList = Collections.singleton(user);
        for (Guild guild : JdaService.guilds) {
            try {
                guild.ban(banList, 24, TimeUnit.HOURS).reason(reason).queue(Consumers.nop(), BotLogger::catchRestError);
                errors += cleanupBotQuestionChannel(guild, getUser(user));
            } catch (Exception e) {
                BotLogger.error(formatGuildActionError("trying to ban", user, guild), e);
                errors++;
            }
        }
        return errors;
    }

    private void auditPostBanAction(UserSnowflake target, Guild guild, String reason, int errors) {
        String msg = getIdent(target);
        msg += " was banned from server " + guild.getName();
        msg += " for the reason \"" + reason + "\".";
        if (errors > 0) {
            msg += "\n## Errors were encountered. Check the error log for more details.";
        }
        postMessageToModLog(msg);
    }

    private void postMessageToModLog(String message) {
        TextChannel moderationLogChannel =
                JdaService.guildPrimary.getTextChannelsByName("moderation-log", true).stream()
                        .findFirst()
                        .orElse(null);
        if (moderationLogChannel == null) return;
        MessageHelper.sendMessageToChannel(moderationLogChannel, message);
    }

    public void cleanupBotQuestionChannels(User user) {
        for (Guild guild : JdaService.guilds) {
            try {
                cleanupBotQuestionChannel(guild, user);
            } catch (Exception e) {
                BotLogger.error(formatGuildActionError("cleaning bot question channels for", user, guild), e);
            }
        }
    }

    private int cleanupBotQuestionChannel(Guild guild, User user) {
        if (user == null) return 0;
        int errors = 0;
        try {
            for (String channelName : BOT_QUESTION_CHANNEL_NAMES) {
                List<TextChannel> channels = guild.getTextChannelsByName(channelName, true);
                if (!channels.isEmpty()) {
                    TextChannel channel = channels.getFirst();
                    channel.getHistoryAround(channel.getLatestMessageId(), 100)
                            .queue(hist -> findAndDeleteSpamPosts(user, hist), BotLogger::catchRestError);
                }
            }
        } catch (Exception e) {
            BotLogger.error(formatGuildActionError("cleaning bot question messages for", user, guild), e);
            errors++;
        }
        return errors;
    }

    private void findAndDeleteSpamPosts(User user, MessageHistory hist) {
        for (Message m : hist.getRetrievedHistory()) {
            if (authorIsUser(m.getAuthor(), user)) {
                m.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
    }

    private boolean authorIsUser(User author, User user) {
        String authorNameLower = author.getName().toLowerCase();
        List<String> names = Stream.of(user.getName(), user.getEffectiveName(), user.getGlobalName())
                .filter(Objects::nonNull)
                .toList();
        for (String n : names) {
            if (authorNameLower.startsWith(n.toLowerCase())) return true;
        }
        return author.getId().equals(user.getId());
    }

    private String formatGuildActionError(String action, UserSnowflake user, Guild guild) {
        return "Error encountered while " + action + " " + getIdent(user) + " in guild `" + guild.getName() + "`";
    }
}
