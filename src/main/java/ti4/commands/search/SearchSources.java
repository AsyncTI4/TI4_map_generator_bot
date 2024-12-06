package ti4.commands2.search;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gwt.thirdparty.guava.common.base.Joiner;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;

/* When no arguments list all sources appearing in all of data (related to game only))
 * First argument is Source, and if completed, lists all component types with the number for this source
 */

class SearchSources extends Subcommand {

    private static final String CHECK_SOURCES = "sources_check";
    private static final String CANAL = "canal_official";
    
    public SearchSources() {
        super(Constants.SEARCH_SOURCES, "List all sources the bot has");
        addOptions(
            new OptionData(OptionType.STRING, Constants.SOURCE, "Limit results to a specific source.").setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, CHECK_SOURCES, "True: Cancel search, counts elements in json files per source, and compare to sources file"),
            new OptionData(OptionType.BOOLEAN, CANAL, "unspecified for all sources, True for 'official' only, False for 'community' only")
        );
    }

    /*  If CheckSources = True Then get all sources from data jsons & all sources from sources.json
            Show (Total nb sources) & (Total nb elements), show list of each (source) with (nb of elements) & (if is only in 1 of the 2 source types)
        Else Then
            Show embeds of each (source) with (data from sources.json) & (component types which have this source)
            Use Source & Canal as filters  */
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        
        String sourceString = event.getOption(Constants.SOURCE, null, OptionMapping::getAsString);
        ComponentSource source = ComponentSource.fromString(sourceString);
        boolean sourcesCheck = event.getOption(CHECK_SOURCES, false, OptionMapping::getAsBoolean);
        Boolean canalBool = event.getOption(CANAL, null, OptionMapping::getAsBoolean);

        if (sourcesCheck) {
            checkSources(event);
            return;
        }

        if (Mapper.isValidSource(sourceString)) {
            event.getChannel().sendMessageEmbeds(Mapper.getSource(sourceString).getRepresentationEmbed()).queue();
            return;
        }
        
        // TO DO: add filter for canal
        List<MessageEmbed> messageEmbeds = Mapper.getSources().values().stream()
            .filter(model -> model.search(sourceString, source))
            .filter(model -> canalBool == null || canalBool == model.isCanalOfficial())
            .map(model -> model.getRepresentationEmbed())
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }

    private void checkSources(SlashCommandInteractionEvent event) {
        Stream<String> abilitySources = Mapper.getAbilities().values() // Collection<AbilityModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> actioncardSources = Mapper.getActionCards().values() // Collection<ActionCardModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> agendaSources = Mapper.getAgendas().values() // Collection<AgendaModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> attachmentSources = Mapper.getAttachments() // List<AttachmentModel>
            .stream().map(model -> model.getSource().toString());
        // colors not sourced
        // combat_modifiers not sourced
        Stream<String> deckSources = Mapper.getDecks().values() // Collection<DeckModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> eventSources = Mapper.getEvents().values() // Collection<DeckModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> exploreSources = Mapper.getExplores().values() // Collection<ExploreModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> factionSources = Mapper.getFactions() // List<FactionModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> frankenerrataSources = Mapper.getFrankenErrata().values() // Collection<DraftErrataModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> genericcardSources = Mapper.getGenericCards().values() // Collection<GenericCardModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> leaderSources = Mapper.getLeaders().values() // Collection<LeaderModel>
            .stream().map(model -> model.getSource().toString());
        // map_templates not sourced
        Stream<String> promissorynoteSources = Mapper.getPromissoryNotes().values() // Collection<PromissoryNoteModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> publicobjectiveSources = Mapper.getPublicObjectives().values() // Collection<PublicObjectiveModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> relicSources = Mapper.getRelics().values() // Collection<RelicModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> secretobjectiveSources = Mapper.getSecretObjectives().values() // Collection<SecretObjectiveModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> strategycardsetSources = Mapper.getStrategyCardSets().values() // Collection<StrategyCardSetModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> strategycardSources = Mapper.getStrategyCards().values() // Collection<StrategyCardModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> technologySources = Mapper.getTechs().values() // Collection<TechnologyModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> tokenSources = Mapper.getTokens2() // List<TokenModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> unitSources = Mapper.getUnits().values() // Collection<UnitModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> planetSources = TileHelper.getAllPlanetModels() // Collection<PlanetModel>
            .stream().map(model -> model.getSource().toString());
        Stream<String> tileSources = TileHelper.getAllTileModels() // Collection<TileModel>
            .stream().map(model -> model.getSource().toString());

        List<String> componentSources = Stream.of(abilitySources, actioncardSources, agendaSources, attachmentSources,
                deckSources, eventSources, exploreSources, factionSources, frankenerrataSources, genericcardSources,
                leaderSources, promissorynoteSources, publicobjectiveSources, relicSources, secretobjectiveSources,
                strategycardsetSources, strategycardSources, technologySources, tokenSources, unitSources,
                planetSources, tileSources)
            .flatMap(i -> i).toList();
        Map<String, Long> uniqueComponentSources = componentSources.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting())) // Removes duplicates and counts occurrences
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) // Sorts by values DESC then untie with key ASC
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)); // Converts back to Map
        String uniqueComponentSourcesTextList = uniqueComponentSources.size() + " sources: "+ uniqueComponentSources.values().stream().mapToLong(d -> d).sum() +" elements\r\n"
            + "- " + Joiner.on("\r\n- ").withKeyValueSeparator(": ").join(uniqueComponentSources); // added guava class for "Joiner"

        //MessageHelper.sendMessageToChannel(event.getChannel(), uniqueComponentSourcesTextList);
        MessageHelper.sendMessageToThread(event.getChannel(), "Sources check", uniqueComponentSourcesTextList);
    }

}
