package ti4.discord.interactions.commands.tournament;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.service.tournament.TournamentDiscord;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;

class TournamentList extends TournamentSubcommand {

    TournamentList() {
        super("list", "List tournaments that are not archived");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TournamentService service = TournamentService.getBean();
        List<String> lines = new ArrayList<>();
        for (Tournament tournament : service.listActive()) {
            List<TournamentEntry> entries = service.entries(tournament.getId());
            int seated = TournamentService.activeCount(entries);
            Guild host = TournamentDiscord.hostGuild(tournament);
            lines.add("`#" + tournament.getId() + "` **" + tournament.displayName() + "**"
                    + (tournament.isFogMode() ? " (Fog)" : "") + " · "
                    + tournament.getStatus().name().toLowerCase()
                    + " · " + seated + "/" + tournament.getTargetSize() + " players"
                    + (entries.size() > seated ? " · " + (entries.size() - seated) + " waitlisted" : "")
                    + " · " + (host == null ? "no host server" : host.getName()));
        }
        reply(
                event,
                lines.isEmpty() ? "No active tournaments." : StringUtils.abbreviate(String.join("\n", lines), 2000));
    }
}
