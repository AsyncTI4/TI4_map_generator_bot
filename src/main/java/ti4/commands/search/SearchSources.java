package ti4.commands2.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import ti4.model.AbilityModel;
import ti4.model.ActionCardModel;
import ti4.model.EmbeddableModel;
import ti4.model.ModelInterface;
import ti4.model.Source.ComponentSource;
import ti4.model.SourceModel;

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

    /*
        Needs (all sources from data jsons) & (all sources from sources.json)
        If CheckSources = True Then
            Show (Total nb sources) & (Total nb elements), show list of each (source) with (nb of elements) & (if is only in 1 of the 2 source types)
        Else Then
            Show embeds of each (source) with (data from sources.json) & (component types which have this source)
            Use Source & Canal as filters
    */
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
            SourceModel model = Mapper.getSource(sourceString);
            event.getChannel().sendMessageEmbeds(model.getRepresentationEmbed(getOccurrencesByCompType(model.getSource()))).queue(); // change getRepEmbed function here as well
            return;
        }

        List<MessageEmbed> messageEmbeds2 = Mapper.getSources().values().stream()
            .filter(model -> model.search(sourceString, source))
            .filter(model -> canalBool == null || canalBool == model.isCanalOfficial())
            .map(model -> model.getRepresentationEmbed(getOccurrencesByCompType(model.getSource())))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds2);
    }


    private HashMap<String, Integer> getOccurrencesByCompType(ComponentSource compSource) {
        HashMap<String, Integer> occurrences = new HashMap<>();
        occurrences.put("Abilities", getAbilitiesSources(compSource).size());
        occurrences.put("Action Cards", getActionCardsSources(compSource).size());
        occurrences.put("Agendas", getAgendasSources(compSource).size());
        occurrences.put("Attachments", getAttachmentsSources(compSource).size());
        //occurrences.put("Colors", get...(compSource).size());
        //occurrences.put("Combat Modifiers", get...(compSource).size());
        occurrences.put("Decks", getDecksSources(compSource).size());
        occurrences.put("Events", getEventsSources(compSource).size());
        occurrences.put("Explores", getExploresSources(compSource).size());
        occurrences.put("Factions", getFactionsSources(compSource).size());
        occurrences.put("Draft Errata", getDraftErratasSources(compSource).size());
        occurrences.put("Generic Cards", getGenericCardsSources(compSource).size());
        occurrences.put("Leaders", getLeadersSources(compSource).size());
        //occurrences.put("Map Templates", get...(compSource).size());
        occurrences.put("Promissory Notes", getPromissoryNotesSources(compSource).size());
        occurrences.put("Public Objectives", getPublicObjectivesSources(compSource).size());
        occurrences.put("Relics", getRelicsSources(compSource).size());
        occurrences.put("Secret Objectives", getSecretObjectivesSources(compSource).size());
        occurrences.put("Strategy Card Sets", getStrategyCardSetsSources(compSource).size());
        occurrences.put("Strategy Cards", getStrategyCardsSources(compSource).size());
        occurrences.put("Technologies", getTechnologiesSources(compSource).size());
        occurrences.put("Tokens", getTokensSources(compSource).size());
        occurrences.put("Units", getUnitsSources(compSource).size());
        occurrences.put("Planets", getPlanetsSources(compSource).size());
        occurrences.put("Tiles", getTilesSources(compSource).size());
        return occurrences;
    }

    // ##################################################
    // Transfer all this section to Mapper.java?
    private List<String> getAbilitiesSources(ComponentSource CompSource) {
        return Mapper.getAbilities().values().stream() // Collection<AbilityModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getActionCardsSources(ComponentSource CompSource) {
        return Mapper.getActionCards().values().stream() // Collection<ActionCardModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getAgendasSources(ComponentSource CompSource) {
        return Mapper.getAgendas().values().stream() // Collection<AgendaModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getAttachmentsSources(ComponentSource CompSource) {
        return Mapper.getAttachments().stream() // List<AttachmentModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    // colors not sourced
    // combat_modifiers not sourced
    private List<String> getDecksSources(ComponentSource CompSource) {
        return Mapper.getDecks().values().stream() // Collection<DeckModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getEventsSources(ComponentSource CompSource) {
        return Mapper.getEvents().values().stream() // Collection<EventModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getExploresSources(ComponentSource CompSource) {
        return Mapper.getExplores().values().stream() // Collection<ExploreModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getFactionsSources(ComponentSource CompSource) {
        return Mapper.getFactions().stream() // List<FactionModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getDraftErratasSources(ComponentSource CompSource) {
        return Mapper.getFrankenErrata().values().stream() // Collection<DraftErrataModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getGenericCardsSources(ComponentSource CompSource) {
        return Mapper.getGenericCards().values().stream() // Collection<GenericCardModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getLeadersSources(ComponentSource CompSource) {
        return Mapper.getLeaders().values().stream() // Collection<LeaderModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    // map_templates not sourced
    private List<String> getPromissoryNotesSources(ComponentSource CompSource) {
        return Mapper.getPromissoryNotes().values().stream() // Collection<PromissoryNoteModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getPublicObjectivesSources(ComponentSource CompSource) {
        return Mapper.getPublicObjectives().values().stream() // Collection<PublicObjectiveModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getRelicsSources(ComponentSource CompSource) {
        return Mapper.getRelics().values().stream() // Collection<RelicModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getSecretObjectivesSources(ComponentSource CompSource) {
        return Mapper.getSecretObjectives().values().stream() // Collection<SecretObjectiveModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getStrategyCardSetsSources(ComponentSource CompSource) {
        return Mapper.getStrategyCardSets().values().stream() // Collection<StrategyCardSetModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getStrategyCardsSources(ComponentSource CompSource) {
        return Mapper.getStrategyCards().values().stream() // Collection<StrategyCardModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getTechnologiesSources(ComponentSource CompSource) {
        return Mapper.getTechs().values().stream() // Collection<TechnologyModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getTokensSources(ComponentSource CompSource) {
        return Mapper.getTokens2().stream() // List<TokenModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getUnitsSources(ComponentSource CompSource) {
        return Mapper.getUnits().values().stream() // Collection<UnitModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getPlanetsSources(ComponentSource CompSource) {
        return TileHelper.getAllPlanetModels().stream() // Collection<PlanetModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    private List<String> getTilesSources(ComponentSource CompSource) {
        return TileHelper.getAllTileModels().stream() // Collection<TileModel> -> Stream<>
            .filter(model -> model.searchSource(CompSource))
            .map(model -> model.getSource().toString()).toList();
    }
    // ##################################################

    private void checkSources(SlashCommandInteractionEvent event) {
        List<String> abilitySources = getAbilitiesSources(null);
        List<String> actioncardSources = getActionCardsSources(null);
        List<String> agendaSources = getAgendasSources(null);
        List<String> attachmentSources = getAttachmentsSources(null);
        // colors not sourced
        // combat_modifiers not sourced
        List<String> deckSources = getDecksSources(null);
        List<String> eventSources = getEventsSources(null);
        List<String> exploreSources = getExploresSources(null);
        List<String> factionSources = getFactionsSources(null);
        List<String> drafterrataSources = getDraftErratasSources(null);
        List<String> genericcardSources = getGenericCardsSources(null);
        List<String> leaderSources = getLeadersSources(null);
        // map_templates not sourced
        List<String> promissorynoteSources = getPromissoryNotesSources(null);
        List<String> publicobjectiveSources = getPublicObjectivesSources(null);
        List<String> relicSources = getRelicsSources(null);
        List<String> secretobjectiveSources = getSecretObjectivesSources(null);
        List<String> strategycardsetSources = getStrategyCardSetsSources(null);
        List<String> strategycardSources = getStrategyCardsSources(null);
        List<String> technologySources = getTechnologiesSources(null);
        List<String> tokenSources = getTokensSources(null);
        List<String> unitSources = getUnitsSources(null);
        List<String> planetSources = getPlanetsSources(null);
        List<String> tileSources = getTilesSources(null);

        List<String> sources = Mapper.getSources().values().stream()
            .map(model -> model.getSource().toString()).toList();

        List<String> componentSources = Stream.of(abilitySources, actioncardSources, agendaSources, attachmentSources,
                deckSources, eventSources, exploreSources, factionSources, drafterrataSources, genericcardSources,
                leaderSources, promissorynoteSources, publicobjectiveSources, relicSources, secretobjectiveSources,
                strategycardsetSources, strategycardSources, technologySources, tokenSources, unitSources,
                planetSources, tileSources)
            .flatMap(i -> i.stream()).toList();

        Map<String, Long> uniqueComponentSources = componentSources.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting())) // Removes duplicates and counts occurrences
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) // Sorts by values DESC then untie with key ASC
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)); // Converts back to Map
        
        StringBuilder uniqueComponentSourcesTextList = new StringBuilder();

        uniqueComponentSourcesTextList.append("**/resources/ JSONs content:**\n");
        uniqueComponentSourcesTextList.append(uniqueComponentSources.size()).append(" sources: ").append(uniqueComponentSources.values().stream().mapToLong(d -> d).sum()).append(" elements\r\n");
        uniqueComponentSourcesTextList.append("- ").append(Joiner.on("\r\n- ").withKeyValueSeparator(": ").join(uniqueComponentSources)); // added guava class for "Joiner"
        
        uniqueComponentSourcesTextList.append("\n\n");
        uniqueComponentSourcesTextList.append("**Entries missing from sources.json:**");
        for (int i = 0; i < sources.size(); i++) {
            if (!uniqueComponentSources.containsKey(sources.get(i))) uniqueComponentSourcesTextList.append("\n- ").append(sources.get(i));
        }

        uniqueComponentSourcesTextList.append("\n\n");
        uniqueComponentSourcesTextList.append("**Implentation missing from /resources/ JSONs content:**");
        for (Map.Entry<String, Long> entry : uniqueComponentSources.entrySet()) {
            if (!sources.contains(entry.getKey())) uniqueComponentSourcesTextList.append("\n- ").append(entry.getKey());
        }

        //MessageHelper.sendMessageToChannel(event.getChannel(), uniqueComponentSourcesTextList);
        MessageHelper.sendMessageToThread(event.getChannel(), "Sources check", uniqueComponentSourcesTextList.toString());
    }

}
