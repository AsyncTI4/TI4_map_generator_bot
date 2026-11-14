package ti4.discord.interactions.commands.tournament;

import java.util.List;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.JdaService;
import ti4.service.tournament.TournamentAccess;
import ti4.spring.service.tournament.TournamentAdminRole;
import ti4.spring.service.tournament.TournamentAdminRoleService;

class TournamentRoles extends TournamentSubcommand {

    private static final String ACTION = "action";
    private static final String ROLE = "role";

    TournamentRoles() {
        super("roles", "Give or take tournament admin rights from a role, or list the roles that have them");
        addOptions(
                new OptionData(OptionType.STRING, ACTION, "What to do", true)
                        .addChoice("add", "add")
                        .addChoice("remove", "remove")
                        .addChoice("list", "list"),
                new OptionData(OptionType.ROLE, ROLE, "The role (not needed for list)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String action = event.getOption(ACTION).getAsString();
        TournamentAdminRoleService service = TournamentAdminRoleService.getBean();
        if ("list".equals(action)) {
            reply(event, list(service.list()));
            return;
        }
        Role role = event.getOption(ROLE, OptionMapping::getAsRole);
        if (role == null) {
            reply(event, "Pick a role.");
            return;
        }
        if ("add".equals(action)) {
            if (!TournamentAccess.canBeGranted(role)) {
                reply(event, "@everyone and bot-managed roles can't get tournament admin rights.");
                return;
            }
            boolean added = service.add(
                    role.getGuild().getId(), role.getId(), event.getUser().getId());
            reply(
                    event,
                    added
                            ? role.getAsMention() + " can now manage tournaments, like Admins and Tourney Mods."
                            : role.getAsMention() + " already has tournament admin rights.");
            return;
        }
        boolean removed = service.remove(role.getId());
        reply(
                event,
                removed
                        ? role.getAsMention() + " no longer has tournament admin rights."
                        : role.getAsMention()
                                + " didn't have rights added with this command. Admin and Tourney Mod roles"
                                + " always have them.");
    }

    private static String list(List<TournamentAdminRole> roles) {
        StringBuilder text = new StringBuilder(
                "**Tournament admins:** Admin roles and any role named **" + TournamentAccess.TOURNEY_MOD_ROLE + "**");
        if (roles.isEmpty()) return text.append(".\nNo extra roles added.").toString();
        text.append(", plus:");
        for (TournamentAdminRole added : roles) {
            Role role = JdaService.jda.getRoleById(added.getRoleId());
            text.append("\n> ")
                    .append(
                            role == null
                                    ? "deleted role " + added.getRoleId()
                                    : role.getAsMention() + " ("
                                            + role.getGuild().getName() + ")")
                    .append(" · added by <@")
                    .append(added.getAddedBy())
                    .append(">");
        }
        return text.toString();
    }
}
