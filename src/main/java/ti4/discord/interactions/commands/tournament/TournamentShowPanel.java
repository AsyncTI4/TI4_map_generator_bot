package ti4.discord.interactions.commands.tournament;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.service.tournament.panel.TournamentPanel;
import ti4.spring.service.tournament.Tournament;

class TournamentShowPanel extends TournamentSubcommand {

    TournamentShowPanel() {
        super("panel", "Post a tournament's admin panel in this channel");
        addOptions(tournamentOption());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Tournament tournament = tournamentOrReply(event);
        if (tournament == null) return;
        if (refusePanelInGameChannel(event)) return;
        TournamentPanel.post(tournament, event.getMessageChannel(), null);
        reply(event, "Posted the **" + tournament.displayName() + "** panel.");
    }
}
