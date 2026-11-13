package ti4.service.tournament;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.JdaService;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentAccess {

    public static final String TOURNEY_MOD_ROLE = "Tourney Mod";
    public static final String DENIED = "Only Admins and **" + TOURNEY_MOD_ROLE + "** can manage tournaments.";

    public static boolean isTournamentAdmin(Member member) {
        return member != null && hasTournamentAdminRole(member.getRoles(), JdaService.adminRoles);
    }

    static boolean hasTournamentAdminRole(Collection<Role> memberRoles, Collection<Role> adminRoles) {
        return memberRoles.stream()
                .anyMatch(role -> adminRoles.contains(role) || TOURNEY_MOD_ROLE.equalsIgnoreCase(role.getName()));
    }

    public static boolean require(GenericInteractionCreateEvent event) {
        if (TournamentService.isUnavailable()) {
            TournamentDiscord.ephemeral(event, TournamentService.UNAVAILABLE);
            return false;
        }
        if (isTournamentAdmin(event.getMember())) return true;
        TournamentDiscord.ephemeral(event, DENIED);
        return false;
    }
}
