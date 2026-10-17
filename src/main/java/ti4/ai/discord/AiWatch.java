package ti4.ai.discord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.service.game.GameUndoNameService;

class AiWatch extends Subcommand {

    private static final String SEATS = "seats";
    private static final String PACE = "pace";
    private static final String NORMAL = "normal";
    private static final String NAME_PREFIX = "aiwatch";
    private static final int MAX_GAME_NUMBER = 999;
    private static final int MAX_CATEGORY_CHANNELS = 48;
    private static final long CHANNEL_ACCESS = Permission.VIEW_CHANNEL.getRawValue()
            | Permission.MESSAGE_SEND.getRawValue()
            | Permission.MESSAGE_HISTORY.getRawValue();

    AiWatch() {
        super("watch", "Developer: start a new game with an AI player in every seat and watch it play itself");
        addOptions(
                new OptionData(OptionType.INTEGER, SEATS, "Number of AI seats, 3 to 6 (default 6)")
                        .setRequiredRange(SelfPlaySetup.MIN_SEATS, SelfPlaySetup.MAX_SEATS),
                new OptionData(
                        OptionType.STRING,
                        Constants.GAME_NAME,
                        "Name for the game: lowercase letters and digits (default aiwatch<N>)"),
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
        if (!AiSettings.isEnabled()) {
            MessageHelper.replyToMessage(
                    event,
                    "AI players are turned off on this bot. Turn them on with "
                            + "`/developer setting setting_name:ai_players_enabled setting_value:true setting_type:bool`.");
            return;
        }
        Guild guild = event.getGuild();
        Member developer = event.getMember();
        if (guild == null || developer == null) {
            MessageHelper.replyToMessage(event, "Run this in a channel of a server.");
            return;
        }
        if (!CreateGameService.serverCanHostNewGame(guild)) {
            MessageHelper.replyToMessage(event, "This server has no room for another game's channels.");
            return;
        }
        Category category = categoryOf(event);
        if (category == null || category.getChannels().size() > MAX_CATEGORY_CHANNELS) {
            MessageHelper.replyToMessage(
                    event, "Run this in a channel that sits in a category with room for 2 more channels.");
            return;
        }
        int seats = event.getOption(SEATS, SelfPlaySetup.MAX_SEATS, OptionMapping::getAsInt);
        boolean fast = !NORMAL.equals(event.getOption(PACE, AiSettings.FAST_PACE, OptionMapping::getAsString));
        String requested = event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString);
        synchronized (AiWatch.class) {
            Optional<String> name = requested == null ? freeName(guild) : usableName(requested, guild);
            if (name.isEmpty()) {
                MessageHelper.replyToMessage(
                        event,
                        requested == null
                                ? "There is no free `aiwatch` game name left."
                                : "`" + requested
                                        + "` is not a free game name: use 3 to 20 lowercase letters and digits.");
                return;
            }
            start(event, guild, category, developer, name.get(), seats, fast);
        }
    }

    private static void start(
            SlashCommandInteractionEvent event,
            Guild guild,
            Category category,
            Member developer,
            String name,
            int seats,
            boolean fast) {
        ThreadArchiveHelper.checkThreadLimitAndArchive(guild);
        TextChannel tableTalk = createChannel(guild, category, name + "-table-talk", developer);
        TextChannel actions = createChannel(guild, category, name + Constants.ACTIONS_CHANNEL_SUFFIX, developer);
        ThreadChannel mapUpdates = actions.createThreadChannel(name + Constants.BOT_CHANNEL_SUFFIX)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .complete();
        List<String> factions = SelfPlaySetup.pickFactions(seats, ThreadLocalRandom.current());
        Game game;
        try {
            game = SelfPlaySetup.setUp(
                    name, developer, new SelfPlaySetup.Channels(actions, tableTalk, mapUpdates), factions, fast, event);
        } catch (SelfPlaySetup.SetupFailed e) {
            actions.delete().queue(ignored -> {}, BotLogger::catchRestError);
            tableTalk.delete().queue(ignored -> {}, BotLogger::catchRestError);
            MessageHelper.replyToMessage(event, "Could not start the game: " + e.getMessage());
            return;
        }
        shareThreads(game, developer);
        MessageHelper.replyToMessage(event, startedMessage(game, actions, mapUpdates, fast));
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

    private static Category categoryOf(SlashCommandInteractionEvent event) {
        GuildChannel channel = event.getChannel() instanceof ThreadChannel thread
                ? thread.getParentChannel()
                : event.getGuildChannel();
        return channel instanceof ICategorizableChannel categorizable ? categorizable.getParentCategory() : null;
    }

    private static TextChannel createChannel(Guild guild, Category category, String name, Member developer) {
        return guild.createTextChannel(name, category)
                .syncPermissionOverrides()
                .addMemberPermissionOverride(developer.getIdLong(), CHANNEL_ACCESS, 0)
                .complete();
    }

    private static void shareThreads(Game game, Member developer) {
        for (Player seat : AiSeats.aiSeats(game)) {
            ThreadChannel thread = seat.getCardsInfoThread();
            if (thread != null) {
                thread.addThreadMemberById(developer.getId()).queue(ignored -> {}, BotLogger::catchRestError);
            }
        }
    }

    private static Optional<String> freeName(Guild guild) {
        return IntStream.rangeClosed(1, MAX_GAME_NUMBER)
                .mapToObj(number -> NAME_PREFIX + number)
                .filter(name -> isFree(name, guild))
                .findFirst();
    }

    private static Optional<String> usableName(String name, Guild guild) {
        return SelfPlaySetup.isValidName(name) && isFree(name, guild) ? Optional.of(name) : Optional.empty();
    }

    private static boolean isFree(String name, Guild guild) {
        if (GameManager.isValid(name) || CreateGameService.gameOrRoleAlreadyExists(name)) return false;
        boolean channelTaken = guild.getTextChannels().stream()
                .anyMatch(channel -> channel.getName().startsWith(name + "-"));
        return !channelTaken && noUndoHistory(name);
    }

    private static boolean noUndoHistory(String name) {
        try {
            return GameUndoNameService.getSortedUndoNumbers(name).isEmpty();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
