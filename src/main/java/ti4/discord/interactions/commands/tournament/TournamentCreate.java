package ti4.discord.interactions.commands.tournament;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.panel.TournamentPanel;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;

class TournamentCreate extends TournamentSubcommand {

    private static final String NAME = "name";
    private static final String TARGET = "target_players";
    private static final String ROUND = "round";
    private static final String GROUP_SIZE = "players_per_game";
    private static final String FOG = "fog";
    private static final String SIGNUP_CHANNEL = "signup_channel";

    TournamentCreate() {
        super("create", "Create a tournament: posts the sign-up button and the admin panel here");
        addOptions(
                new OptionData(OptionType.STRING, NAME, "Tournament name, e.g. Tourney 8", true).setMaxLength(60),
                new OptionData(
                                OptionType.INTEGER,
                                TARGET,
                                "How many players get a seat; later sign-ups go on the waitlist",
                                true)
                        .setMinValue(1),
                new OptionData(OptionType.STRING, ROUND, "Round name, e.g. Prelims (default: Prelims)")
                        .setMaxLength(40),
                new OptionData(OptionType.INTEGER, GROUP_SIZE, "Players per game (default 6)").setRequiredRange(3, 8),
                new OptionData(OptionType.BOOLEAN, FOG, "Create Fog of War games (default false)"),
                channelOption(SIGNUP_CHANNEL, "Where to post the sign-up button (default: this channel)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        GuildMessageChannel signupChannel = channel(event, SIGNUP_CHANNEL);
        if (guild == null || signupChannel == null) {
            reply(event, "Use this command in a server text channel.");
            return;
        }
        if (refusePanelInGameChannel(event)) return;
        if (isGameChannel(signupChannel)) {
            reply(event, "Post the sign-up button outside game channels, so every player can press it.");
            return;
        }
        String name = event.getOption(NAME).getAsString().trim();
        String round = StringUtils.defaultIfBlank(event.getOption(ROUND, OptionMapping::getAsString), "Prelims")
                .trim();
        int target = event.getOption(TARGET).getAsInt();
        int groupSize = event.getOption(GROUP_SIZE, Tournament.DEFAULT_GROUP_SIZE, OptionMapping::getAsInt);
        boolean fog = event.getOption(FOG, false, OptionMapping::getAsBoolean);

        Tournament tournament = TournamentRoles.ensureRoleSaved(TournamentService.getBean()
                .create(
                        name,
                        round,
                        target,
                        groupSize,
                        fog,
                        guild.getId(),
                        event.getUser().getId()));

        TournamentSignupPosts.post(tournament, signupChannel);
        TournamentPanel.post(
                tournament,
                event.getMessageChannel(),
                "Created. Games go on **" + guild.getName() + "** unless you pick another server below.");
        reply(
                event,
                "Created **" + tournament.displayName() + "**. The sign-up button is in " + signupChannel.getAsMention()
                        + " and the admin panel is in this channel. Use `/tournament post_signup` to add the button elsewhere.");
    }
}
