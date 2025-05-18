package ti4.commands.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.servlet.BaseHolder.Source;

import com.google.gwt.thirdparty.guava.common.base.Joiner;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
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
        Needs (all sources from \resources\ jsons) & (all sources from sources.json)
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
            .sorted((r1, r2) -> { // should sort by canal ('official' before 'community') then subcanal (null values before non-null values, but all 'official' should have null subcanal and all 'community' should have non null subcanal)
                if(r1.getCanal().equals(r2.getCanal()))
                    if (r1.getSubcanal() == null && r2.getSubcanal() == null) return 0;
                    else if (r1.getSubcanal() == null) return -1;
                    else if (r2.getSubcanal() == null) return 1;
                    else return r1.getSubcanal().compareTo(r2.getSubcanal());
                else return -r1.getCanal().compareTo(r2.getName());
            })
            //.sorted((r1, r2) -> (r1.getCanal()+r1.getSubcanal()).compareTo((r2.getCanal()+r2.getSubcanal()))) // raise a bug for null values
            .map(model -> model.getRepresentationEmbed(getOccurrencesByCompType(model.getSource())))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds2);
    }

    /**
     * Used in '/search sources',
     * Gives a Map with the number of occurrences in each \resources\ .json file for a specific source
     * @param compSource
     * @return
     */
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
        occurrences.put("Draft Errata", getDraftErratasSources(compSource).size()); // Draft Errata is related to files in \data\franken_errata\*
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

    /**
     * Used in '/search sources', and bypasses the standard execution it,
     * 1. List all distinct sources in \resources\ .json files (except sources.json) and count their occurrences,
     * 2. List sources from that previous list that have no match in sources.json (missing entries in sources.json),
     * 3. List sources from sources.json that have no match in the first list (missing entries in \resources\ .json files (except sources.json), or wrong entry in sources.json)
     * @param event
     */
    private void checkSources(SlashCommandInteractionEvent event) {

        // Sources from \resources\ .json files (excluding sources.json)
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

        List<String> componentSources = Stream.of(abilitySources, actioncardSources, agendaSources, attachmentSources,
                deckSources, eventSources, exploreSources, factionSources, drafterrataSources, genericcardSources,
                leaderSources, promissorynoteSources, publicobjectiveSources, relicSources, secretobjectiveSources,
                strategycardsetSources, strategycardSources, technologySources, tokenSources, unitSources,
                planetSources, tileSources)
            .flatMap(i -> i.stream()).toList();

        Map<String, Long> uniqueComponentSources = componentSources.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting())) // Groups by distinct value and counts initial occurrences
            .entrySet().stream().sorted(Map.Entry.comparingByKey()).sorted(Collections.reverseOrder(Map.Entry.comparingByValue())) // Sorts by values DESC then untie with key ASC
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)); // Converts back to Map

        // Sources from sources.json file
        List<String> sources = Mapper.getSources().values().stream()
            .map(model -> model.getSource().toString()).toList();

        // Sources from Source.java Enum file
        List<String> enumSources = Arrays.asList(ComponentSource.values()).stream().map(entry -> entry.toString()).toList();

        // Build answer
        StringBuilder uniqueComponentSourcesTextList = new StringBuilder();

        uniqueComponentSourcesTextList.append("**All \\resources\\ JSON content:**\n");
        uniqueComponentSourcesTextList.append(uniqueComponentSources.size()).append(" sources: ").append(uniqueComponentSources.values().stream().mapToLong(d -> d).sum()).append(" elements\r\n");
        uniqueComponentSourcesTextList.append("- ").append(Joiner.on("\r\n- ").withKeyValueSeparator(": ").join(uniqueComponentSources)); // added guava class for "Joiner"
        // TO DO: find a way to add emoji to list given by previous instruction
        
        uniqueComponentSourcesTextList.append("\n\n");

        uniqueComponentSourcesTextList.append("**Implementation missing in \\resources\\ JSON content:**\n");
        uniqueComponentSourcesTextList.append("Compared to sources.json\n");
        for (int i = 0; i < sources.size(); i++) {
            if (!uniqueComponentSources.containsKey(sources.get(i))) uniqueComponentSourcesTextList.append("- ").append(sources.get(i)).append("\n");
        }
        uniqueComponentSourcesTextList.append("Compared to Source.java Enum\n");
        for (int i = 0; i < enumSources.size(); i++) {
            if (!uniqueComponentSources.containsKey(enumSources.get(i))) uniqueComponentSourcesTextList.append("- ").append(enumSources.get(i)).append("\n");
        }

        uniqueComponentSourcesTextList.append("\n");

        uniqueComponentSourcesTextList.append("**Entries missing in sources.json:**\n");
        uniqueComponentSourcesTextList.append("Compared to \\resources\\ JSON content\n");
        for (Map.Entry<String, Long> entry : uniqueComponentSources.entrySet()) {
            if (!sources.contains(entry.getKey())) uniqueComponentSourcesTextList.append("- ").append(entry.getKey()).append("\n");
        }
        uniqueComponentSourcesTextList.append("Compared to Source.java Enum\n");
        for (int i = 0; i < enumSources.size(); i++) {
            if (!sources.contains(enumSources.get(i))) uniqueComponentSourcesTextList.append("- ").append(enumSources.get(i)).append("\n");
        }

        uniqueComponentSourcesTextList.append("\n");

        uniqueComponentSourcesTextList.append("**Entries missing in Source.java Enum:**\n");
        uniqueComponentSourcesTextList.append("Compared to \\resources\\ JSON content\n");
        for (Map.Entry<String, Long> entry : uniqueComponentSources.entrySet()) {
            if (!enumSources.contains(entry.getKey())) uniqueComponentSourcesTextList.append("- ").append(entry.getKey()).append("\n");
        }
        uniqueComponentSourcesTextList.append("Compared to sources.json\n");
        for (int i = 0; i < sources.size(); i++) {
            if (!enumSources.contains(sources.get(i))) uniqueComponentSourcesTextList.append("- ").append(sources.get(i)).append("\n");
        }

        // Send answer
        MessageHelper.sendMessageToThread(event.getChannel(), "Sources check", uniqueComponentSourcesTextList.toString());
    }


    // ##################################################
    // Transfer all this section to Mapper.java?
    // ##################################################
    // All those functions get the list sources in a \resources\ .json file for a specific source, or list all sources when no specific source

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
        return Mapper.getTokens().stream() // List<TokenModel> -> Stream<>
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

}
