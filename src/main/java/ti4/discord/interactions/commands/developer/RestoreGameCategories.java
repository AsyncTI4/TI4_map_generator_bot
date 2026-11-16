package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.order.ChannelOrderAction;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.service.game.GameCategoryRestorePlanner;
import ti4.service.game.GameCategoryRestorePlanner.CategoryRange;
import ti4.service.game.GameCategoryRestorePlanner.Placement;
import ti4.settings.GlobalSettings;

class RestoreGameCategories extends Subcommand {

    private static final String GUILD_ID = "guild_id";
    private static final String GAMES_PER_CATEGORY = "games_per_category";
    private static final String APPLY = "apply";
    private static final String IGNORE_LIMITS = "ignore_limits";
    private static final int MAX_CHANNELS_PER_GUILD = 500;
    private static final int SECONDS_TO_LET_CACHE_CATCH_UP = 5;

    RestoreGameCategories() {
        super(
                "restore_game_categories",
                "Recreate missing PBD categories and move uncategorized game channels back into them.");
        addOptions(
                new OptionData(OptionType.BOOLEAN, APPLY, "True to make the changes; default false only reports"),
                new OptionData(OptionType.STRING, GUILD_ID, "Server id; defaults to this server"),
                new OptionData(
                                OptionType.INTEGER,
                                GAMES_PER_CATEGORY,
                                "Games per new category; defaults to the most common size on the server")
                        .setRequiredRange(1, 25),
                new OptionData(
                        OptionType.BOOLEAN,
                        IGNORE_LIMITS,
                        "True to skip the 50 per category and 500 per server channel checks"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String guildId = event.getOption(GUILD_ID, event.getGuild().getId(), OptionMapping::getAsString);
        Guild guild = JdaService.jda.getGuildById(guildId);
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "The bot is not in a server with id `" + guildId + "`.");
            return;
        }

        List<CategoryRange> existingRanges = guild.getCategories().stream()
                .map(category -> GameCategoryRestorePlanner.parseCategory(category.getName()))
                .flatMap(Optional::stream)
                .toList();
        int gamesPerCategory = event.getOption(
                GAMES_PER_CATEGORY,
                GameCategoryRestorePlanner.inferGamesPerCategory(existingRanges, defaultGamesPerCategory()),
                OptionMapping::getAsInt);
        List<TextChannel> uncategorized = guild.getTextChannels().stream()
                .filter(channel -> channel.getParentCategory() == null)
                .toList();
        List<Placement<TextChannel>> placements =
                GameCategoryRestorePlanner.plan(uncategorized, GuildChannel::getName, existingRanges, gamesPerCategory);

        if (placements.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No uncategorized game channels on **" + guild.getName() + "**. Nothing to restore.");
            return;
        }

        boolean ignoreLimits = event.getOption(IGNORE_LIMITS, false, OptionMapping::getAsBoolean);
        List<Placement<TextChannel>> fitting = new ArrayList<>();
        List<Placement<TextChannel>> overfull = new ArrayList<>();
        for (Placement<TextChannel> placement : placements) {
            if (!ignoreLimits
                    && channelCountAfter(guild, placement) > GameCategoryRestorePlanner.MAX_CHANNELS_PER_CATEGORY) {
                overfull.add(placement);
            } else {
                fitting.add(placement);
            }
        }

        long newCategoryCount = fitting.stream().filter(Placement::newCategory).count();
        int channelCount = guild.getChannels().size();
        boolean apply = event.getOption(APPLY, false, OptionMapping::getAsBoolean);
        String report = report(guild, fitting, overfull, gamesPerCategory, apply);

        if (!ignoreLimits && channelCount + newCategoryCount > MAX_CHANNELS_PER_GUILD) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    report + "\n**Not applied:** the server has " + channelCount + " / " + MAX_CHANNELS_PER_GUILD
                            + " channels and needs room for " + newCategoryCount
                            + " new categories. Use `" + IGNORE_LIMITS + ": True` to try anyway.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, report);
        if (apply) {
            restore(event, guild, fitting);
        }
    }

    private static void restore(
            SlashCommandInteractionEvent event, Guild guild, List<Placement<TextChannel>> placements) {
        List<CompletableFuture<Void>> moves = new ArrayList<>();
        List<Category> touched = new ArrayList<>();
        AtomicInteger failedMoves = new AtomicInteger();
        List<String> failedCategories = new ArrayList<>();

        for (Placement<TextChannel> placement : placements) {
            Category category = resolveCategory(guild, placement);
            if (category == null) {
                failedCategories.add(placement.categoryName());
                continue;
            }
            touched.add(category);
            for (TextChannel channel : placement.channels()) {
                moves.add(channel.getManager()
                        .setParent(category)
                        .submit()
                        .<Void>thenApply(ignored -> null)
                        .exceptionally(error -> {
                            failedMoves.incrementAndGet();
                            BotLogger.error("Could not move " + channel.getName() + " to " + category.getName(), error);
                            return null;
                        }));
            }
        }

        CompletableFuture.allOf(moves.toArray(CompletableFuture[]::new))
                .thenRunAsync(
                        () -> {
                            touched.forEach(RestoreGameCategories::sortChannels);
                            sortGameCategories(guild);
                            MessageHelper.sendMessageToEventChannel(
                                    event, summary(moves.size(), failedMoves.get(), failedCategories));
                        },
                        CompletableFuture.delayedExecutor(SECONDS_TO_LET_CACHE_CATCH_UP, TimeUnit.SECONDS));
    }

    private static Category resolveCategory(Guild guild, Placement<TextChannel> placement) {
        if (!placement.newCategory()) {
            return guild.getCategoriesByName(placement.categoryName(), false).stream()
                    .findFirst()
                    .orElse(null);
        }
        try {
            return CreateGameService.createGameCategory(guild, placement.categoryName());
        } catch (Exception e) {
            BotLogger.error("Could not create category " + placement.categoryName() + " on " + guild.getName(), e);
            return null;
        }
    }

    private static void sortChannels(Category category) {
        Category current = category.getGuild().getCategoryById(category.getIdLong());
        if (current == null) return;
        current.modifyTextChannelPositions()
                .sortOrder(GameCategoryRestorePlanner.<GuildChannel>channelOrder(GuildChannel::getName))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void sortGameCategories(Guild guild) {
        ChannelOrderAction order = guild.modifyCategoryPositions();
        List<GuildChannel> desired =
                GameCategoryRestorePlanner.categoryOrder(order.getCurrentOrder(), GuildChannel::getName);
        if (desired.equals(order.getCurrentOrder())) return;
        for (int position = 0; position < desired.size(); position++) {
            order.selectPosition(desired.get(position)).moveTo(position);
        }
        order.queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static int channelCountAfter(Guild guild, Placement<TextChannel> placement) {
        int existing = placement.newCategory()
                ? 0
                : guild.getCategoriesByName(placement.categoryName(), false).stream()
                        .findFirst()
                        .map(category -> category.getChannels().size())
                        .orElse(0);
        return existing + placement.channels().size();
    }

    private static int defaultGamesPerCategory() {
        return Math.clamp(GlobalSettings.ImplementedSettings.MAX_GAMES_PER_CATEGORY.getAsInt(10), 1, 25);
    }

    private static String report(
            Guild guild,
            List<Placement<TextChannel>> fitting,
            List<Placement<TextChannel>> overfull,
            int gamesPerCategory,
            boolean apply) {
        StringBuilder sb = new StringBuilder();
        sb.append(apply ? "**Restoring** " : "**Dry run** for ")
                .append("game categories on **")
                .append(guild.getName())
                .append("** (")
                .append(gamesPerCategory)
                .append(" games per new category):\n");
        for (Placement<TextChannel> placement : fitting) {
            sb.append("> **")
                    .append(placement.categoryName())
                    .append("**")
                    .append(placement.newCategory() ? " *(new)*" : "")
                    .append(": ")
                    .append(channelNames(placement))
                    .append('\n');
        }
        for (Placement<TextChannel> placement : overfull) {
            sb.append("> Skipped **")
                    .append(placement.categoryName())
                    .append("**, it would pass ")
                    .append(GameCategoryRestorePlanner.MAX_CHANNELS_PER_CATEGORY)
                    .append(" channels: ")
                    .append(channelNames(placement))
                    .append('\n');
        }
        if (!apply) {
            sb.append("Run again with `apply: True` to make these changes.");
        }
        return sb.toString();
    }

    private static String channelNames(Placement<TextChannel> placement) {
        return placement.channels().stream().map(ICategorizableChannel::getName).collect(Collectors.joining(", "));
    }

    private static String summary(int moveCount, int failedMoves, List<String> failedCategories) {
        StringBuilder sb = new StringBuilder("Finished restoring game categories. Moved ")
                .append(moveCount - failedMoves)
                .append(" / ")
                .append(moveCount)
                .append(" channels.");
        if (!failedCategories.isEmpty()) {
            sb.append("\nCould not create or find: ").append(String.join(", ", failedCategories));
        }
        if (failedMoves > 0) {
            sb.append("\nSee the bot log for the channels that failed to move.");
        }
        return sb.toString();
    }
}
