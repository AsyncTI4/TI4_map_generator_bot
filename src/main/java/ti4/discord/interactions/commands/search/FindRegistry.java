package ti4.discord.interactions.commands.search;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.EmbeddableModel;
import ti4.model.ModelInterface;
import ti4.model.Source.ComponentSource;

@UtilityClass
class FindRegistry {

    private static final Map<String, FindSpec> TYPES = Stream.of(
                    spec(
                            Constants.SEARCH_ABILITIES,
                            "Abilities",
                            () -> Mapper.getAbilities().values(),
                            model -> model.getRepresentationEmbed(),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_ACTION_CARDS,
                            "Action Cards",
                            () -> Mapper.getActionCards().values(),
                            FindRegistry::actionCardSearchText,
                            model -> model.getRepresentationEmbed(true, true),
                            model -> model.getRepresentationEmbed(true, true)),
                    spec(
                            Constants.SEARCH_AGENDAS,
                            "Agendas",
                            () -> Mapper.getAgendas().values(),
                            FindRegistry::agendaSearchText,
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_ATTACHMENTS,
                            "Attachments",
                            () -> Mapper.getAttachments().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_BREAKTHROUGHS,
                            "Breakthroughs",
                            () -> Mapper.getBreakthroughs().values(),
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_DECKS,
                            "Decks",
                            () -> Mapper.getDecks().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_EVENTS,
                            "Events",
                            () -> Mapper.getEvents().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_EXPLORES,
                            "Explores",
                            () -> Mapper.getExplores().values(),
                            model -> model.getRepresentationEmbed(true, true),
                            model -> model.getRepresentationEmbed(true, true)),
                    spec(
                            Constants.SEARCH_FACTIONS,
                            "Factions",
                            Mapper::getFactionsValues,
                            model -> model.getRepresentationEmbed(true, false),
                            model -> model.getRepresentationEmbed(true, false)),
                    spec(
                            Constants.SEARCH_GALACTIC_EVENTS,
                            "Galactic Events",
                            () -> Mapper.getGalacticEvents().values(),
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_LEADERS,
                            "Leaders",
                            () -> Mapper.getLeaders().values(),
                            model -> model.getRepresentationEmbed(true, true, true, true),
                            model -> model.getRepresentationEmbed(true, true, true, true)),
                    spec(
                            Constants.SEARCH_GENOMES,
                            "Genomes",
                            () -> Mapper.getDeck(Constants.TF_GENOME).getNewDeck().stream()
                                    .map(Mapper::getLeader)
                                    .toList(),
                            model -> model.getRepresentationEmbed(true, true, false, true, true),
                            model -> model.getRepresentationEmbed(true, true, false, true, true)),
                    spec(
                            Constants.SEARCH_PARADIGMS,
                            "Paradigms",
                            () -> Mapper.getDeck(Constants.TF_PARADIGM).getNewDeck().stream()
                                    .map(Mapper::getLeader)
                                    .toList(),
                            model -> model.getRepresentationEmbed(true, true, false, true, true),
                            model -> model.getRepresentationEmbed(true, true, false, true, true)),
                    spec(
                            Constants.SEARCH_PLANETS,
                            "Planets",
                            TileHelper::getAllPlanetModels,
                            EmbeddableModel::getRepresentationEmbed,
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_PLOTS,
                            "Plots",
                            () -> Mapper.getPlots().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_PROMISSORY_NOTES,
                            "Promissory Notes",
                            () -> Mapper.getPromissoryNotes().values(),
                            model -> model.getRepresentationEmbed(false, true, true),
                            model -> model.getRepresentationEmbed(false, true, true)),
                    spec(
                            Constants.SEARCH_PUBLIC_OBJECTIVES,
                            "Public Objectives",
                            () -> Mapper.getPublicObjectives().values(),
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_RELICS,
                            "Relics",
                            () -> Mapper.getRelics().values(),
                            model -> model.getRepresentationEmbed(true, true),
                            model -> model.getRepresentationEmbed(true, true)),
                    spec(
                            Constants.SEARCH_RULES,
                            "Rules",
                            () -> Mapper.getRules().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_SECRET_OBJECTIVES,
                            "Secret Objectives",
                            () -> Mapper.getSecretObjectives().values(),
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_STRATEGY_CARDS,
                            "Strategy Cards",
                            () -> Mapper.getStrategyCards().values(),
                            model -> model.getRepresentationEmbed(true),
                            model -> model.getRepresentationEmbed(true)),
                    spec(
                            Constants.SEARCH_TECHS,
                            "Techs",
                            () -> Mapper.getTechs().values(),
                            model -> model.getRepresentationEmbed(true, true),
                            model -> model.getRepresentationEmbed(true, true)),
                    spec(
                            Constants.SEARCH_TILES,
                            "Tiles",
                            TileHelper::getAllTileModels,
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_TOKENS,
                            "Tokens",
                            () -> Mapper.getTokens().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            EmbeddableModel::getRepresentationEmbed),
                    spec(
                            Constants.SEARCH_UNITS,
                            "Units",
                            () -> Mapper.getUnits().values(),
                            EmbeddableModel::getRepresentationEmbed,
                            model -> model.getRepresentationEmbed(true)))
            .collect(
                    Collectors.toMap(FindSpec::key, Function.identity(), (first, second) -> first, LinkedHashMap::new));

    List<FindSpec> getTypes() {
        return List.copyOf(TYPES.values());
    }

    FindSpec getType(String key) {
        return TYPES.get(key);
    }

    private static <T extends ModelInterface & EmbeddableModel> FindSpec spec(
            String key,
            String displayName,
            Supplier<Collection<T>> models,
            Function<T, String> textSearch,
            Function<T, MessageEmbed> exactEmbed,
            Function<T, MessageEmbed> listEmbed) {
        return new FindSpec(key, displayName, () -> models.get().stream()
                .map(model -> new FindItem(
                        model.getAlias(),
                        model.getSource(),
                        model.getAutoCompleteName(),
                        model.getNameRepresentation(),
                        textSearch.apply(model),
                        model::search,
                        () -> exactEmbed.apply(model),
                        () -> listEmbed.apply(model)))
                .toList());
    }

    private static <T extends ModelInterface & EmbeddableModel> FindSpec spec(
            String key,
            String displayName,
            Supplier<Collection<T>> models,
            Function<T, MessageEmbed> exactEmbed,
            Function<T, MessageEmbed> listEmbed) {
        return spec(key, displayName, models, model -> "", exactEmbed, listEmbed);
    }

    record FindSpec(String key, String displayName, Supplier<List<FindItem>> items) {}

    record FindItem(
            String alias,
            ComponentSource source,
            String autoCompleteName,
            String nameRepresentation,
            String textSearchBlob,
            Predicate<String> searchMatcher,
            Supplier<MessageEmbed> exactEmbed,
            Supplier<MessageEmbed> listEmbed) {

        boolean matches(String query) {
            return searchMatcher.test(query);
        }

        boolean hasTextSearch() {
            return StringUtils.isNotBlank(textSearchBlob);
        }
    }

    private static String actionCardSearchText(ActionCardModel model) {
        return String.join(" ", model.getWindow(), model.getText(), model.getNotes() == null ? "" : model.getNotes());
    }

    private static String agendaSearchText(AgendaModel model) {
        return String.join(" ", model.getType(), model.getTarget(), model.getText1(), model.getText2());
    }
}
