package ti4.commands.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.SourceModel;

/* When no arguments list all sources appearing in all of data (related to game only))
 * First argument is Source, and if completed, lists all component types with the number for this source
 */
// TO DO: implement searchSource for FrankenErrataModel, StrategyCardSetModel, TokenModel

class SearchSources extends Subcommand {

    private static final String CHECK_SOURCES = "sources_check";
    private static final String CANAL = "canal_official";

    public SearchSources() {
        super(Constants.SEARCH_SOURCES, "List all sources the bot has");
        addOptions(
            new OptionData(OptionType.STRING, Constants.SOURCE, "Limit results to a specific source.").setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, CANAL, "unspecified for all sources, True for 'official' only, False for 'community' only"),
            new OptionData(OptionType.BOOLEAN, CHECK_SOURCES, "True: Cancel search, counts elements in json files per source, and compare to sources file"));
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

        List<MessageEmbed> messageEmbeds = Mapper.getSources().values().stream()
            .filter(model -> model.search(sourceString, source))
            .filter(model -> canalBool == null || canalBool == model.isCanalOfficial())
            .sorted((r1, r2) -> { // should sort by canal ('official' before 'community') then subcanal (null values before non-null values, but all 'official' should have null subcanal and all 'community' should have non null subcanal) then source
                if (r1.getCanal().equals(r2.getCanal())) {
                    if (r1.getSubcanal() == null && r2.getSubcanal() == null) return r1.getSource().compareTo(r2.getSource());
                    if (r1.getSubcanal() == null) return -1;
                    if (r2.getSubcanal() == null) return 1;
                    if (r1.getSubcanal().equals(r2.getSubcanal())) return r1.getSource().compareTo(r2.getSource());
                    return r1.getSubcanal().compareTo(r2.getSubcanal());
                }
                return -r1.getCanal().compareTo(r2.getName());
            })
            //.sorted((r1, r2) -> (r1.getCanal()+r1.getSubcanal()).compareTo((r2.getCanal()+r2.getSubcanal()))) // raise a bug for null values
            .map(model -> model.getRepresentationEmbed(getOccurrencesByCompType(model.getSource())))
            .toList();
        SearchHelper.sendSearchEmbedsToEventChannel(event, messageEmbeds);
    }

    /**
     * Used in '/search sources',
     * Gives a Map with the number of occurrences in each \resources\ .json file for a specific source
     * @param compSource
     * @return
     */
    private HashMap<String, Integer> getOccurrencesByCompType(ComponentSource compSource) {
        HashMap<String, Integer> occurrences = new HashMap<>();
        occurrences.put("Abilities", Mapper.getAbilitiesSources(compSource).size());
        occurrences.put("Action Cards", Mapper.getActionCardsSources(compSource).size());
        occurrences.put("Agendas", Mapper.getAgendasSources(compSource).size());
        occurrences.put("Attachments", Mapper.getAttachmentsSources(compSource).size());
        //occurrences.put("Colors", get...(compSource).size());
        //occurrences.put("Combat Modifiers", get...(compSource).size());
        occurrences.put("Decks", Mapper.getDecksSources(compSource).size());
        occurrences.put("Events", Mapper.getEventsSources(compSource).size());
        occurrences.put("Explores", Mapper.getExploresSources(compSource).size());
        occurrences.put("Factions", Mapper.getFactionsSources(compSource).size());
        occurrences.put("Draft Errata", Mapper.getDraftErratasSources(compSource).size()); // Draft Errata is related to files in \data\franken_errata\*
        occurrences.put("Generic Cards", Mapper.getGenericCardsSources(compSource).size());
        occurrences.put("Leaders", Mapper.getLeadersSources(compSource).size());
        //occurrences.put("Map Templates", get...(compSource).size());
        occurrences.put("Promissory Notes", Mapper.getPromissoryNotesSources(compSource).size());
        occurrences.put("Public Objectives", Mapper.getPublicObjectivesSources(compSource).size());
        occurrences.put("Relics", Mapper.getRelicsSources(compSource).size());
        occurrences.put("Secret Objectives", Mapper.getSecretObjectivesSources(compSource).size());
        occurrences.put("Strategy Card Sets", Mapper.getStrategyCardSetsSources(compSource).size());
        occurrences.put("Strategy Cards", Mapper.getStrategyCardsSources(compSource).size());
        occurrences.put("Technologies", Mapper.getTechnologiesSources(compSource).size());
        occurrences.put("Tokens", Mapper.getTokensSources(compSource).size());
        occurrences.put("Units", Mapper.getUnitsSources(compSource).size());
        occurrences.put("Planets", Mapper.getPlanetsSources(compSource).size());
        occurrences.put("Tiles", Mapper.getTilesSources(compSource).size());
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
        List<String> abilitySources = Mapper.getAbilitiesSources(null);
        List<String> actioncardSources = Mapper.getActionCardsSources(null);
        List<String> agendaSources = Mapper.getAgendasSources(null);
        List<String> attachmentSources = Mapper.getAttachmentsSources(null);
        // colors not sourced
        // combat_modifiers not sourced
        List<String> deckSources = Mapper.getDecksSources(null);
        List<String> eventSources = Mapper.getEventsSources(null);
        List<String> exploreSources = Mapper.getExploresSources(null);
        List<String> factionSources = Mapper.getFactionsSources(null);
        List<String> drafterrataSources = Mapper.getDraftErratasSources(null);
        List<String> genericcardSources = Mapper.getGenericCardsSources(null);
        List<String> leaderSources = Mapper.getLeadersSources(null);
        // map_templates not sourced
        List<String> promissorynoteSources = Mapper.getPromissoryNotesSources(null);
        List<String> publicobjectiveSources = Mapper.getPublicObjectivesSources(null);
        List<String> relicSources = Mapper.getRelicsSources(null);
        List<String> secretobjectiveSources = Mapper.getSecretObjectivesSources(null);
        List<String> strategycardsetSources = Mapper.getStrategyCardSetsSources(null);
        List<String> strategycardSources = Mapper.getStrategyCardsSources(null);
        List<String> technologySources = Mapper.getTechnologiesSources(null);
        List<String> tokenSources = Mapper.getTokensSources(null);
        List<String> unitSources = Mapper.getUnitsSources(null);
        List<String> planetSources = Mapper.getPlanetsSources(null);
        List<String> tileSources = Mapper.getTilesSources(null);

        List<String> componentSources = Stream.of(
            abilitySources, actioncardSources, agendaSources, attachmentSources, deckSources, eventSources,
            exploreSources, factionSources, drafterrataSources, genericcardSources, leaderSources, promissorynoteSources,
            publicobjectiveSources, relicSources, secretobjectiveSources, strategycardsetSources, strategycardSources,
            technologySources, tokenSources, unitSources, planetSources, tileSources //
        ).flatMap(i -> i.stream()).toList();

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
        uniqueComponentSourcesTextList.append(uniqueComponentSources.size()).append(" sources: ").append(uniqueComponentSources.values().stream().mapToLong(d -> d).sum()).append(" elements");
        for (var component : uniqueComponentSources.entrySet()) {
            uniqueComponentSourcesTextList.append("\n> - ").append(component.getKey()).append(": ").append(component.getValue());
        }
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

}
