package ti4.discord.interactions.commands.tournament;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.service.tournament.TournamentInvites;
import ti4.spring.service.tournament.Tournament;

class TournamentPostInvite extends TournamentSubcommand {

    TournamentPostInvite() {
        super("post_invite", "Post the host server invite, pinging players who aren't on it yet");
        addOptions(tournamentOption(), channelOption(CHANNEL, "Where to post it (default: this channel)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Tournament tournament = tournamentOrReply(event);
        if (tournament == null) return;
        GuildMessageChannel channel = channelOrReply(event, CHANNEL);
        if (channel == null) return;
        reply(event, TournamentInvites.send(tournament, channel));
    }
}
