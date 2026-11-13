package ti4.service.tournament;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.logging.BotLogger;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentRoles {

    public static String roleName(Tournament tournament) {
        return StringUtils.left(tournament.getSeriesName() + " Player", 100);
    }

    public static Role role(Tournament tournament) {
        Guild guild = TournamentDiscord.hostGuild(tournament);
        if (guild == null || StringUtils.isBlank(tournament.getParticipantRoleId())) return null;
        return guild.getRoleById(tournament.getParticipantRoleId());
    }

    public static String ensureRole(Tournament tournament) {
        Role existing = role(tournament);
        if (existing != null) return existing.getId();
        Guild guild = TournamentDiscord.hostGuild(tournament);
        if (guild == null) return null;
        try {
            Role role = guild.getRolesByName(roleName(tournament), true).stream()
                    .findFirst()
                    .orElseGet(() -> guild.createRole()
                            .setName(roleName(tournament))
                            .setMentionable(false)
                            .complete());
            return role.getId();
        } catch (RuntimeException e) {
            BotLogger.error("Could not create the tournament role on " + guild.getName(), e);
            return null;
        }
    }

    public static boolean give(Tournament tournament, String userId) {
        Role role = role(tournament);
        Member member = TournamentDiscord.member(TournamentDiscord.hostGuild(tournament), userId);
        if (role == null || member == null) return false;
        if (!member.getRoles().contains(role)) {
            role.getGuild().addRoleToMember(member, role).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        return true;
    }

    public static void take(Tournament tournament, String userId) {
        Role role = role(tournament);
        Member member = TournamentDiscord.member(TournamentDiscord.hostGuild(tournament), userId);
        if (role == null || member == null || !member.getRoles().contains(role)) return;
        role.getGuild().removeRoleFromMember(member, role).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static int sync(Tournament tournament, List<TournamentEntry> entries) {
        int given = 0;
        for (TournamentEntry entry : entries) {
            if (entry.isWaitlisted()) continue;
            if (give(tournament, entry.getUserId())) given++;
        }
        return given;
    }

    public static void deleteIfUnused(Tournament tournament) {
        Role role = role(tournament);
        if (role == null) return;
        if (TournamentService.getBean().isRoleUsedByAnotherActiveTournament(role.getId(), tournament.getId())) return;
        role.delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
