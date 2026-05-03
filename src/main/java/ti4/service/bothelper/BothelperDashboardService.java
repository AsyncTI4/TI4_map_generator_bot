package ti4.service.bothelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.apache.commons.lang3.function.Consumers;
import ti4.cron.CronManager;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.logging.BotLogger;
import ti4.service.game.CreateGameService;

@UtilityClass
public class BothelperDashboardService {

    private static final String DASHBOARD_CHANNEL_NAME = "bothelper-dashboard";
    private static final String BUTTON_ID = "bothelperDashboard_manageRoles";
    private static final String SELECTION_ID = "bothelperManageRoles";
    private static final String BOTHELPER_ROLE_NAME = "Bothelper";

    // ---------------------------------------------------------------------------
    // Dashboard content builder
    // ---------------------------------------------------------------------------

    public static String buildDashboardContent() {
        List<Guild> servers = JdaService.serversToCreateNewGamesOn;
        Set<String> uniqueUserIds = new HashSet<>();
        StringBuilder sb = new StringBuilder("# @Bothelper Dashboard\n");

        StringBuilder serverBlocks = new StringBuilder();
        for (Guild guild : servers) {
            Role bothelperRole = CreateGameService.getRole(BOTHELPER_ROLE_NAME, guild);
            if (bothelperRole == null) continue;
            List<Member> members = guild.getMembersWithRoles(bothelperRole);
            serverBlocks
                    .append("## ")
                    .append(guild.getName())
                    .append(" (")
                    .append(members.size())
                    .append(")\n");
            for (Member member : members) {
                uniqueUserIds.add(member.getId());
                serverBlocks.append("- ").append(member.getEffectiveName()).append("\n");
            }
        }

        sb.append("Total Bothelpers: ").append(uniqueUserIds.size()).append("\n");
        sb.append(serverBlocks);
        return sb.toString();
    }

    // ---------------------------------------------------------------------------
    // Dashboard message refresh
    // ---------------------------------------------------------------------------

    public static void refreshDashboardMessage() {
        Guild hub = JdaService.guildPrimary;
        if (hub == null) {
            BotLogger.warning("BothelperDashboardService: guildPrimary is null, cannot refresh dashboard.");
            return;
        }
        List<TextChannel> channels = hub.getTextChannelsByName(DASHBOARD_CHANNEL_NAME, true);
        if (channels.isEmpty()) {
            BotLogger.warning(
                    "BothelperDashboardService: #" + DASHBOARD_CHANNEL_NAME + " channel not found on the Hub server.");
            return;
        }
        TextChannel channel = channels.getFirst();
        String content = buildDashboardContent();
        var manageRolesButton = Buttons.blue(BUTTON_ID, "Manage Roles");

        channel.getHistory()
                .retrievePast(1)
                .queue(
                        messages -> {
                            if (messages.isEmpty()) {
                                channel.sendMessage(content)
                                        .addComponents(ActionRow.of(manageRolesButton))
                                        .queue(Consumers.nop(), BotLogger::catchRestError);
                            } else {
                                messages.getFirst()
                                        .editMessage(content)
                                        .setComponents(ActionRow.of(manageRolesButton))
                                        .queue(Consumers.nop(), BotLogger::catchRestError);
                            }
                        },
                        BotLogger::catchRestError);
    }

    // ---------------------------------------------------------------------------
    // Button handler: "Manage Roles" button on the dashboard
    // ---------------------------------------------------------------------------

    @ButtonHandler(value = BUTTON_ID, save = false)
    public static void handleManageRolesButton(ButtonInteractionEvent event) {
        if (!CommandHelper.hasRole(event, JdaService.bothelperRoles)) {
            event.getHook()
                    .editOriginal("You must have the Bothelper role to use this.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        sendManageRolesMenu(event.getUser(), event.getHook());
    }

    // ---------------------------------------------------------------------------
    // Slash-command entry point (reused by /bothelper manage_roles)
    // ---------------------------------------------------------------------------

    public static void handleManageRolesSlashCommand(SlashCommandInteractionEvent event) {
        sendManageRolesMenu(event.getUser(), event.getHook());
    }

    // ---------------------------------------------------------------------------
    // Shared helper: build and send the ephemeral select menu
    // ---------------------------------------------------------------------------

    private static void sendManageRolesMenu(User user, InteractionHook hook) {
        List<Guild> servers = JdaService.serversToCreateNewGamesOn;
        if (servers.isEmpty()) {
            hook.editOriginal("No overflow servers are currently configured.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(SELECTION_ID);
        menuBuilder.setMinValues(0);
        menuBuilder.setMaxValues(servers.size());
        menuBuilder.setPlaceholder("Select servers to have the Bothelper role on");

        List<String> preselected = new ArrayList<>();
        for (Guild guild : servers) {
            SelectOption option = SelectOption.of(guild.getName(), guild.getId());
            Role bothelperRole = CreateGameService.getRole(BOTHELPER_ROLE_NAME, guild);
            if (bothelperRole != null) {
                Member member = guild.getMember(user);
                if (member != null && member.getRoles().contains(bothelperRole)) {
                    preselected.add(guild.getId());
                }
            }
            menuBuilder.addOptions(option);
        }
        menuBuilder.setDefaultValues(preselected);

        hook.editOriginal("Select which servers you want the Bothelper role on:")
                .setComponents(ActionRow.of(menuBuilder.build()))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    // ---------------------------------------------------------------------------
    // Selection handler: process the user's server choices
    // ---------------------------------------------------------------------------

    @SelectionHandler(SELECTION_ID)
    public static void handleManageRolesSelection(StringSelectInteractionEvent event) {
        List<String> selectedGuildIds = event.getValues();
        List<Guild> servers = JdaService.serversToCreateNewGamesOn;

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Guild guild : servers) {
            Role bothelperRole = CreateGameService.getRole(BOTHELPER_ROLE_NAME, guild);
            if (bothelperRole == null) {
                skipped.add(guild.getName() + " (no Bothelper role found)");
                continue;
            }
            Member member = guild.getMember(event.getUser());
            if (member == null) {
                // User is not in this server; skip silently
                continue;
            }
            boolean userWantsRole = selectedGuildIds.contains(guild.getId());
            boolean userHasRole = member.getRoles().contains(bothelperRole);
            if (userWantsRole && !userHasRole) {
                guild.addRoleToMember(member, bothelperRole).queue(Consumers.nop(), BotLogger::catchRestError);
                added.add(guild.getName());
            } else if (!userWantsRole && userHasRole) {
                guild.removeRoleFromMember(member, bothelperRole).queue(Consumers.nop(), BotLogger::catchRestError);
                removed.add(guild.getName());
            }
        }

        StringBuilder summary = new StringBuilder("**Bothelper role update complete.**\n");
        if (!added.isEmpty()) {
            summary.append("✅ Added on: ").append(String.join(", ", added)).append("\n");
        }
        if (!removed.isEmpty()) {
            summary.append("❌ Removed from: ")
                    .append(String.join(", ", removed))
                    .append("\n");
        }
        if (added.isEmpty() && removed.isEmpty()) {
            summary.append("No changes were made.");
        }
        if (!skipped.isEmpty()) {
            summary.append("⚠️ Skipped: ").append(String.join(", ", skipped)).append("\n");
        }

        // Replace the select menu with the summary in place (works for both ephemeral and non-ephemeral)
        event.getHook()
                .editOriginal(summary.toString())
                .setComponents()
                .queue(Consumers.nop(), BotLogger::catchRestError);

        // Refresh the dashboard after a short delay to allow role changes to propagate
        CronManager.scheduleOnce(
                BothelperDashboardService.class,
                BothelperDashboardService::refreshDashboardMessage,
                5,
                TimeUnit.SECONDS);
    }
}
