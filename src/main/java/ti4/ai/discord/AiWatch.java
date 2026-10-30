package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.selfplay.SelfPlaySetup;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.RandomHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class AiWatch extends Subcommand {

    private static final String SEATS = "seats";
    private static final String PACE = "pace";
    private static final String NORMAL = "normal";
    private static final String NAME_PREFIX = "aiwatch";

    AiWatch() {
        super("watch", "Developer: start a new game with an AI player in every seat and watch it play itself");
        addOptions(
                new OptionData(OptionType.INTEGER, SEATS, "Number of AI seats, 3 to 6 (default 6)")
                        .setRequiredRange(SelfPlaySetup.MIN_SEATS, SelfPlaySetup.MAX_SEATS),
                AiGameLauncher.gameNameOption(NAME_PREFIX),
                new OptionData(OptionType.STRING, PACE, "How quickly the AIs move the game on (default fast)")
                        .addChoice("Fast", AiSettings.FAST_PACE)
                        .addChoice("Normal", NORMAL));
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int seats = event.getOption(SEATS, SelfPlaySetup.MAX_SEATS, OptionMapping::getAsInt);
        boolean fast = !NORMAL.equals(event.getOption(PACE, AiSettings.FAST_PACE, OptionMapping::getAsString));
        List<String> factions = SelfPlaySetup.pickFactions(seats, RandomHelper.current());
        AiGameLauncher.launch(
                        event,
                        NAME_PREFIX,
                        (name, owner, channels) -> SelfPlaySetup.setUp(
                                name, owner, channels, SelfPlaySetup.Table.allAi(factions), fast, event))
                .ifPresent(started -> {
                    shareThreads(started.game(), started.owner());
                    MessageHelper.replyToMessage(
                            event, startedMessage(started.game(), started.actions(), started.mapUpdates(), fast));
                });
    }

    private static String startedMessage(Game game, TextChannel actions, ThreadChannel mapUpdates, boolean fast) {
        List<String> names = AiSeats.aiSeats(game).stream()
                .map(seat -> seat.getUserName() + (seat.isSpeaker() ? " (speaker)" : ""))
                .toList();
        return "Started **" + game.getName() + "**, an AI self-play game with " + names.size() + " AI seats: "
                + String.join(", ", names) + ". Watch it in " + actions.getAsMention() + "; the map is posted in "
                + mapUpdates.getAsMention() + ", and you have been added to each AI's private thread. "
                + (fast ? "It plays at fast pace. " : "")
                + "The AIs deal the secret objectives and play on their own until someone reaches the victory point "
                + "goal. Use `/ai status` or `/ai pause` in the game's channel to check on them or stop them.";
    }

    private static void shareThreads(Game game, Member developer) {
        for (Player seat : AiSeats.aiSeats(game)) {
            ThreadChannel thread = seat.getCardsInfoThread();
            if (thread != null) {
                thread.addThreadMemberById(developer.getId()).queue(ignored -> {}, BotLogger::catchRestError);
            }
        }
    }
}
