package ti4.service.tournament;

import java.util.Collection;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.JdaService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.tournament.TournamentAdminRoleService;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentAccess {

    public static final String TOURNEY_MOD_ROLE = "Tourney Mod";
    public static final String DENIED = "Only Admins, **" + TOURNEY_MOD_ROLE
            + "** and roles added with `/tournament roles` can manage tournaments.";

    public static boolean isTournamentAdmin(Member member) {
        if (member == null) return false;
        if (hasTournamentAdminRole(member.getRoles(), JdaService.adminRoles)) return true;
        Set<String> extraRoleIds = extraRoleIds();
        if (extraRoleIds.isEmpty()) return false;
        return member.getRoles().stream()
                .map(Role::getId)
                .anyMatch(roleId -> roleId != null && extraRoleIds.contains(roleId));
    }

    static boolean hasTournamentAdminRole(Collection<Role> memberRoles, Collection<Role> adminRoles) {
        return memberRoles.stream()
                .anyMatch(role -> adminRoles.contains(role) || TOURNEY_MOD_ROLE.equalsIgnoreCase(role.getName()));
    }

    private static Set<String> extraRoleIds() {
        if (!SpringContext.isInitialized()) return Set.of();
        return TournamentAdminRoleService.getBean().roleIds();
    }

    public static boolean canBeGranted(Role role) {
        return !role.isPublicRole() && !role.isManaged();
    }

    public static String refusal(Member member) {
        if (TournamentService.isUnavailable()) return TournamentService.UNAVAILABLE;
        return isTournamentAdmin(member) ? null : DENIED;
    }

    public static boolean require(GenericInteractionCreateEvent event) {
        String refusal = refusal(event.getMember());
        if (refusal != null) TournamentDiscord.ephemeral(event, refusal);
        return refusal == null;
    }
}
