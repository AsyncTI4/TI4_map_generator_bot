package ti4.discord.interactions.commands.tournament;

import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.spring.service.tournament.Tournament;

class TournamentPostSignup extends TournamentSubcommand {

    TournamentPostSignup() {
        super("post_signup", "Post the tournament's sign-up button in another channel");
        addOptions(tournamentOption(), channelOption(CHANNEL, "Where to post it (default: this channel)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Tournament tournament = tournament(event);
        if (tournament == null) {
            reply(event, "Pick a tournament from the list.");
            return;
        }
        GuildMessageChannel channel = channel(event, CHANNEL);
        if (channel == null) {
            reply(event, "Pick a text channel.");
            return;
        }
        if (isGameChannel(channel)) {
            reply(event, "Post the sign-up button outside game channels, so every player can press it.");
            return;
        }
        TournamentSignupPosts.post(tournament, channel);
        reply(
                event,
                "Posted the **" + tournament.displayName() + "** sign-up button in " + channel.getAsMention() + ".");
    }
}
