package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.executors.ExecutorServiceManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.async.RoleService;
import ti4.spring.jda.JdaService;

public class UserJoinServerListener extends ListenerAdapter {

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        if (!validateEvent(event)) return;
        ExecutorServiceManager.runAsync("UserJoinServerListener task", () -> handleGuildMemberJoin(event));
    }

    private static boolean validateEvent(GenericGuildEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) {
            return false;
        }
        String eventGuild = event.getGuild().getId();
        return JdaService.isValidGuild(eventGuild);
    }

    private void handleGuildMemberJoin(GuildMemberJoinEvent event) {
        try {
            welcomeNewUserToHUBServer(event);
            RoleService.checkIfNewUserIsInExistingGamesAndAutoAddRole(event.getGuild(), event.getUser());
            RoleService.checkIfNewUserIsInAnyGamesAndAddRole(event.getUser());
        } catch (Exception e) {
            BotLogger.error("Error in `UserJoinServerListener.handleGuildMemberJoin`", e);
        }
    }

    private void welcomeNewUserToHUBServer(GuildMemberJoinEvent event) {
        if (event.getGuild() == JdaService.guildPrimary) {
            JdaService.guildPrimary.getTextChannelsByName("welcome-and-waving", true).stream()
                    .findFirst()
                    .ifPresent(welcomeChannel -> {
                        int memberCount = event.getGuild().getMemberCount();
                        String formattedMemberCount = String.format("%,d", memberCount);
                        if (memberCount % 10 == 0) {
                            formattedMemberCount = "*" + formattedMemberCount + "*";
                        } else if (memberCount % 100 == 0) {
                            formattedMemberCount = "**" + formattedMemberCount + "**";
                        } else if (memberCount % 1000 == 0) {
                            formattedMemberCount = "***" + formattedMemberCount + "***";
                        } else if (memberCount % 10000 == 0) {
                            formattedMemberCount = "\n# ***#" + formattedMemberCount + "***";
                        }
                        MessageHelper.sendMessageToChannel(
                                welcomeChannel,
                                "**Welcome** " + event.getUser().getAsMention()
                                        + "! We're glad you're here as lucky number # "
                                        + formattedMemberCount + "!\n"
                                        + "To get started, check out the how to play documentation here: https://discord.com/channels/943410040369479690/947727176105623642/1349555940340404265. \n"
                                        + "If you ever have any questions or difficulty, ping the Bothelper role. It's full of helpful people who should be able to assist you.");
                    });
        }
    }
}
