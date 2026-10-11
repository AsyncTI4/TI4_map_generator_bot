package ti4.discord.interactions.commands.search;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.model.EmbeddableModel;
import ti4.model.Source.ComponentSource;

abstract class SearchComponentModelSubcommand<T extends EmbeddableModel> extends Subcommand {

    private final Predicate<String> isExactMatch;
    private final Function<String, T> lookup;
    private final Supplier<Stream<T>> allModels;
    private final Function<T, MessageEmbed> embed;
    private final Comparator<T> order;

    SearchComponentModelSubcommand(
            String name,
            String description,
            Predicate<String> isExactMatch,
            Function<String, T> lookup,
            Supplier<Stream<T>> allModels,
            Function<T, MessageEmbed> embed,
            Comparator<T> order) {
        super(name, description);
        this.isExactMatch = isExactMatch;
        this.lookup = lookup;
        this.allModels = allModels;
        this.embed = embed;
        this.order = order;
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.SEARCH,
                        "Searches the text and limits results to those containing this string.")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Limit results to a specific source.")
                .setAutoComplete(true));
    }

    static <T> Supplier<Stream<T>> valuesOf(Supplier<? extends Collection<T>> collection) {
        return () -> collection.get().stream();
    }

    protected MessageEmbed exactMatchEmbed(T model, SlashCommandInteractionEvent event) {
        return embed.apply(model);
    }

    protected MessageEmbed resultEmbed(T model, SlashCommandInteractionEvent event) {
        return embed.apply(model);
    }

    protected Predicate<T> extraFilter(SlashCommandInteractionEvent event) {
        return model -> true;
    }

    protected static boolean includeAliases(SlashCommandInteractionEvent event) {
        return event.getOption(Constants.INCLUDE_ALIASES, Boolean.FALSE, OptionMapping::getAsBoolean);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));

        if (isExactMatch.test(searchString)) {
            event.getChannel()
                    .sendMessageEmbeds(exactMatchEmbed(lookup.apply(searchString), event))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        Stream<T> results = allModels.get().filter(model -> model.search(searchString, source));
        results = results.filter(extraFilter(event));
        if (order != null) results = results.sorted(order);
        List<MessageEmbed> messageEmbeds =
                results.map(model -> resultEmbed(model, event)).toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }
}
