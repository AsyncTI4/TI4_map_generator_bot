package ti4.discord.interactions.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.service.bothelper.BothelperDashboardService;

class ManageRoles extends Subcommand {

    ManageRoles() {
        super("manage_roles", "Manage your Bothelper role assignments across overflow servers");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        BothelperDashboardService.handleManageRolesSlashCommand(event);
    }
}
