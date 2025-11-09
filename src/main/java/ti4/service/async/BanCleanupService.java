package ti4.service.async;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.spring.jda.JdaService;

@UtilityClass
public class BanCleanupService {

    public void banSpamAccount(User user, AuditLogEntry log) {
        ManagedPlayer mp = GameManager.getManagedPlayer(user.getId());
        if (mp != null) {
            return;
        }

        int errors = removeUserFromAllGuilds(user);
        auditPostBanAction(user, log, errors);
    }

    private int removeUserFromAllGuilds(User user) {
        int errors = 0;
        UserSnowflake snowflake = UserSnowflake.fromId(user.getId());
        Collection<UserSnowflake> banList = Collections.singleton(snowflake);
        for (Guild guild : JdaService.guilds) {
            try {
                guild.ban(banList, 24, TimeUnit.HOURS).queue();
                errors += cleanupBotQuestionChannel(guild, user);
            } catch (Exception e) {
                String msg = "Error encountered trying to ban user `" + user.getName() + "`";
                msg += " from guild `" + guild.getName() + "`";
                BotLogger.error(msg, e);
                errors++;
            }
        }
        return errors;
    }

    private void auditPostBanAction(User user, AuditLogEntry log, int errors) {
        String msg = "User `" + user.getName() + "` (ID:" + user.getId() + ") ";
        msg += " was banned from primary server " + log.getGuild().getName();
        msg += " for the reason \"" + log.getReason() + "\".";
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

    private int cleanupBotQuestionChannel(Guild guild, User user) {
        int errors = 0;
        List<String> channelNames = List.of(
                "bot-questions-and-support-and-feedback",
                "bot-questions-and-feedback",
                "bot-questions-and-discussion",
                "bot-questions-and-discussions");

        try {
            for (String channelName : channelNames) {
                List<TextChannel> channels = guild.getTextChannelsByName(channelName, true);
                if (!channels.isEmpty()) {
                    TextChannel channel = channels.getFirst();
                    channel.getHistoryAround(channel.getLatestMessageId(), 100)
                            .queue(hist -> findAndDeleteSpamPosts(user, hist), BotLogger::catchRestError);
                }
            }
        } catch (Exception e) {
            String msg = "Error encountered trying to clean up messages from synced channel while banning user `%s`";
            BotLogger.error(String.format(msg, user.getName()), e);
            errors++;
        }
        return errors;
    }

    private void findAndDeleteSpamPosts(User user, MessageHistory hist) {
        for (Message m : hist.getRetrievedHistory()) {
            boolean delete = false;
            if (m.getAuthor().getId().equals(user.getId())) delete = true;
            else if (m.getAuthor().getName().startsWith(user.getName())) delete = true;
            else if (m.getAuthor().getEffectiveName().startsWith(user.getEffectiveName())) delete = true;
            else if (m.getAuthor().getGlobalName().startsWith(user.getGlobalName())) delete = true;
            if (delete) {
                m.delete().queue();
            }
        }
    }
}
