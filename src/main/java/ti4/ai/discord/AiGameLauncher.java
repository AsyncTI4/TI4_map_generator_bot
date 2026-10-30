package ti4.ai.discord;

import java.util.Optional;
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
import ti4.ai.AiSettings;
import ti4.ai.selfplay.SelfPlaySetup;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.service.game.GameUndoNameService;

final class AiGameLauncher {

    private static final int MAX_GAME_NUMBER = 999;
    private static final int MAX_CATEGORY_CHANNELS = 48;
    private static final long CHANNEL_ACCESS = Permission.VIEW_CHANNEL.getRawValue()
            | Permission.MESSAGE_SEND.getRawValue()
            | Permission.MESSAGE_HISTORY.getRawValue();

    record Started(Game game, TextChannel actions, ThreadChannel mapUpdates, Member owner) {}

    @FunctionalInterface
    interface Setup {
        Game setUp(String name, Member owner, SelfPlaySetup.Channels channels) throws SelfPlaySetup.SetupFailed;
    }

    private AiGameLauncher() {}

    static OptionData gameNameOption(String namePrefix) {
        return new OptionData(
                OptionType.STRING,
                Constants.GAME_NAME,
                "Name for the game: lowercase letters and digits (default " + namePrefix + "<N>)");
    }

    static Optional<Started> launch(SlashCommandInteractionEvent event, String namePrefix, Setup setup) {
        if (!AiSettings.isEnabled()) {
            MessageHelper.replyToMessage(
                    event,
                    "AI players are turned off on this bot. Turn them on with "
                            + "`/developer setting setting_name:ai_players_enabled setting_value:true setting_type:bool`.");
            return Optional.empty();
        }
        Guild guild = event.getGuild();
        Member owner = event.getMember();
        if (guild == null || owner == null) {
            MessageHelper.replyToMessage(event, "Run this in a channel of a server.");
            return Optional.empty();
        }
        if (!CreateGameService.serverCanHostNewGame(guild)) {
            MessageHelper.replyToMessage(event, "This server has no room for another game's channels.");
            return Optional.empty();
        }
        Category category = categoryOf(event);
        if (category == null || category.getChannels().size() > MAX_CATEGORY_CHANNELS) {
            MessageHelper.replyToMessage(
                    event, "Run this in a channel that sits in a category with room for 2 more channels.");
            return Optional.empty();
        }
        String requested = event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString);
        synchronized (AiGameLauncher.class) {
            Optional<String> name = requested == null ? freeName(namePrefix, guild) : usableName(requested, guild);
            if (name.isEmpty()) {
                MessageHelper.replyToMessage(
                        event,
                        requested == null
                                ? "There is no free `" + namePrefix + "` game name left."
                                : "`" + requested
                                        + "` is not a free game name: use 3 to 20 lowercase letters and digits.");
                return Optional.empty();
            }
            return start(event, guild, category, owner, name.get(), setup);
        }
    }

    private static Optional<Started> start(
            SlashCommandInteractionEvent event,
            Guild guild,
            Category category,
            Member owner,
            String name,
            Setup setup) {
        ThreadArchiveHelper.checkThreadLimitAndArchive(guild);
        TextChannel tableTalk = createChannel(guild, category, name + "-table-talk", owner);
        TextChannel actions = createChannel(guild, category, name + Constants.ACTIONS_CHANNEL_SUFFIX, owner);
        ThreadChannel mapUpdates = actions.createThreadChannel(name + Constants.BOT_CHANNEL_SUFFIX)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK)
                .complete();
        try {
            Game game = setup.setUp(name, owner, new SelfPlaySetup.Channels(actions, tableTalk, mapUpdates));
            return Optional.of(new Started(game, actions, mapUpdates, owner));
        } catch (SelfPlaySetup.SetupFailed e) {
            actions.delete().queue(ignored -> {}, BotLogger::catchRestError);
            tableTalk.delete().queue(ignored -> {}, BotLogger::catchRestError);
            MessageHelper.replyToMessage(event, "Could not start the game: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static Category categoryOf(SlashCommandInteractionEvent event) {
        GuildChannel channel = event.getChannel() instanceof ThreadChannel thread
                ? thread.getParentChannel()
                : event.getGuildChannel();
        return channel instanceof ICategorizableChannel categorizable ? categorizable.getParentCategory() : null;
    }

    private static TextChannel createChannel(Guild guild, Category category, String name, Member owner) {
        return guild.createTextChannel(name, category)
                .syncPermissionOverrides()
                .addMemberPermissionOverride(owner.getIdLong(), CHANNEL_ACCESS, 0)
                .complete();
    }

    private static Optional<String> freeName(String namePrefix, Guild guild) {
        return IntStream.rangeClosed(1, MAX_GAME_NUMBER)
                .mapToObj(number -> namePrefix + number)
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
