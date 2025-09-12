package ti4.image;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.model.AbilityModel;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.AttachmentModel;
import ti4.model.BreakthroughModel;
import ti4.model.ColorModel;
import ti4.model.ColorableModelInterface;
import ti4.model.CombatModifierModel;
import ti4.model.DeckModel;
import ti4.model.DraftErrataModel;
import ti4.model.EventModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.GenericCardModel;
import ti4.model.GenericCardModel.CardType;
import ti4.model.LeaderModel;
import ti4.model.MapTemplateModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;
import ti4.model.SourceModel;
import ti4.model.StrategyCardModel;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.model.TokenModel;
import ti4.model.UnitModel;
import ti4.model.WormholeModel;
import ti4.service.emoji.CardEmojis;

public class Mapper {

    // private static final Properties colors = new Properties();
    private static final Properties decals = new Properties();
    private static final Properties general = new Properties();
    private static final Properties hyperlaneAdjacencies = new Properties();
    private static final Properties specialCase = new Properties();
    private static final Properties tokensFromProperties = new Properties();

    // TODO: Finish moving all files over from properties to json
    private static final Map<String, AbilityModel> abilities = new HashMap<>();
    private static final Map<String, ActionCardModel> actionCards = new HashMap<>();
    private static final Map<String, AgendaModel> agendas = new HashMap<>();
    private static final Map<String, AttachmentModel> attachments = new HashMap<>();
    private static final Map<String, ColorModel> colors = new HashMap<>();
    private static final Map<String, CombatModifierModel> combatModifiers = new HashMap<>();
    private static final Map<String, DeckModel> decks = new HashMap<>();
    private static final Map<String, EventModel> events = new HashMap<>();
    private static final Map<String, ExploreModel> explores = new HashMap<>();
    private static final Map<String, FactionModel> factions = new HashMap<>();
    private static final Map<String, DraftErrataModel> frankenErrata = new HashMap<>();
    private static final Map<String, GenericCardModel> genericCards = new HashMap<>();
    private static final Map<String, LeaderModel> leaders = new HashMap<>();
    private static final Map<String, MapTemplateModel> mapTemplates = new HashMap<>();
    private static final Map<String, PromissoryNoteModel> promissoryNotes = new HashMap<>();
    private static final Map<String, PublicObjectiveModel> publicObjectives = new HashMap<>();
    private static final Map<String, RelicModel> relics = new HashMap<>();
    private static final Map<String, SecretObjectiveModel> secretObjectives = new HashMap<>();
    private static final Map<String, SourceModel> sources = new HashMap<>();
    private static final Map<String, StrategyCardSetModel> strategyCardSets = new HashMap<>();
    private static final Map<String, StrategyCardModel> strategyCards = new HashMap<>();
    private static final Map<String, TechnologyModel> technologies = new HashMap<>();
    private static final Map<String, TokenModel> tokens = new HashMap<>();
    private static final Map<String, UnitModel> units = new HashMap<>();
    private static final Map<String, BreakthroughModel> breakthroughs = new HashMap<>();

    private static final Cache<String, ColorModel> colorToColorModelCache =
            Caffeine.newBuilder().maximumSize(1000).build();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void init() {
        objectMapper.registerModule(new SimpleModule().addDeserializer(Color.class, new ColorDeserializer()));
        try {
            loadData();
        } catch (Exception e) {
            BotLogger.error("Could not load data", e);
        }
    }

    static void loadData() throws Exception {
        // must be first for validating later models
        importJsonObjectsFromFolder("factions", factions, FactionModel.class);

        importJsonObjectsFromFolder("abilities", abilities, AbilityModel.class);
        importJsonObjectsFromFolder("action_cards", actionCards, ActionCardModel.class);
        importJsonObjectsFromFolder("agendas", agendas, AgendaModel.class);
        importJsonObjectsFromFolder("attachments", attachments, AttachmentModel.class);
        importJsonObjectsFromFolder("breakthroughs", breakthroughs, BreakthroughModel.class);
        importJsonObjectsFromFolder("colors", colors, ColorModel.class);
        importJsonObjectsFromFolder("combat_modifiers", combatModifiers, CombatModifierModel.class);
        importJsonObjectsFromFolder("decks", decks, DeckModel.class);
        importJsonObjectsFromFolder("events", events, EventModel.class);
        importJsonObjectsFromFolder("explores", explores, ExploreModel.class);
        importJsonObjectsFromFolder("franken_errata", frankenErrata, DraftErrataModel.class);
        importJsonObjectsFromFolder("genericcards", genericCards, GenericCardModel.class);
        importJsonObjectsFromFolder("leaders", leaders, LeaderModel.class);
        importJsonObjectsFromFolder("map_templates", mapTemplates, MapTemplateModel.class);
        importJsonObjectsFromFolder("promissory_notes", promissoryNotes, PromissoryNoteModel.class);
        importJsonObjectsFromFolder("public_objectives", publicObjectives, PublicObjectiveModel.class);
        importJsonObjectsFromFolder("relics", relics, RelicModel.class);
        importJsonObjectsFromFolder("secret_objectives", secretObjectives, SecretObjectiveModel.class);
        importJsonObjectsFromFolder("sources", sources, SourceModel.class);
        importJsonObjectsFromFolder("strategy_card_sets", strategyCardSets, StrategyCardSetModel.class);
        importJsonObjectsFromFolder("strategy_cards", strategyCards, StrategyCardModel.class);
        importJsonObjectsFromFolder("technologies", technologies, TechnologyModel.class);

        importJsonObjectsFromFolder("tokens", tokens, TokenModel.class);
        importJsonObjectsFromFolder("units", units, UnitModel.class);
        readData("decals.properties", decals);
        readData("general.properties", general);
        readData("hyperlanes.properties", hyperlaneAdjacencies);
        readData("special_case.properties", specialCase);
        readData("tokens.properties", tokensFromProperties);

        duplicateObjectsForAllColors(promissoryNotes);
        duplicateObjectsForAllColors(secretObjectives);
    }

    private static void readData(String propertyFileName, Properties properties) throws IOException {
        properties.clear();
        String propFile = ResourceHelper.getInstance().getDataFile(propertyFileName);
        if (propFile != null) {
            try (InputStream input = new FileInputStream(propFile)) {
                properties.load(input);
            } catch (IOException e) {
                BotLogger.error("Could not read .property file: " + propertyFileName, e);
                throw e;
            }
        }
    }

    private static <T extends ModelInterface> void importJsonObjectsFromFolder(
            String jsonFolderName, Map<String, T> objectMap, Class<T> target) throws InvalidFormatException {
        String folderPath = ResourceHelper.getInstance().getDataFolder(jsonFolderName);
        objectMap.clear(); // Added to prevent duplicates when running Mapper.init() over and over with *ModelTest
        // classes

        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }
            try {
                importJsonObjects(jsonFolderName + File.separator + file.getName(), objectMap, target);
            } catch (InvalidFormatException e) {
                BotLogger.error("JSON File may be formatted incorrectly: " + jsonFolderName + "/" + file.getName(), e);
                throw e;
            } catch (Exception e) {
                BotLogger.error("Could not import JSON Objects from File: " + jsonFolderName + "/" + file.getName(), e);
            }
        }
    }

    private static <T extends ModelInterface> void importJsonObjects(
            String jsonFileName, Map<String, T> objectMap, Class<T> target) throws Exception {
        List<T> allObjects = new ArrayList<>();
        String filePath = ResourceHelper.getInstance().getDataFile(jsonFileName);
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, target);

        if (filePath != null) {
            try {
                InputStream input = new FileInputStream(filePath);
                allObjects = objectMapper.readValue(input, type);
            } catch (Exception e) {
                BotLogger.error("Could not import JSON Objects from File: " + jsonFileName, e);
                throw e;
            }
        }

        List<String> badObjects = new ArrayList<>();
        for (T obj : allObjects) {
            if (objectMap.containsKey(obj.getAlias())) { // duplicate found
                BotLogger.warning("Duplicate **" + target.getSimpleName() + "** found: " + obj.getAlias());
            }
            objectMap.put(obj.getAlias(), obj);
            if (!obj.isValid()) {
                badObjects.add(obj.getAlias());
            }
        }
        if (!badObjects.isEmpty())
            BotLogger.warning("The following **" + target.getSimpleName() + "** are improperly formatted:\n> "
                    + String.join("\n> ", badObjects));
    }

    private static <T extends ColorableModelInterface<T>> void duplicateObjectsForAllColors(Map<String, T> objectMap) {
        String mostRecentObject = "none";
        try {
            List<ColorModel> colorsToCreate = getColors();
            List<T> newObjects = new ArrayList<>();
            for (T obj : objectMap.values()) {
                mostRecentObject = obj.getAlias();
                if (obj.isColorable()) {
                    for (ColorModel color : colorsToCreate) {
                        T newObj = obj.duplicateAndSetColor(color);
                        newObjects.add(newObj);
                    }
                }
            }
            for (T obj : newObjects) {
                objectMap.put(obj.getAlias(), obj);
            }
        } catch (Exception e) {
            BotLogger.error("Failed duplicating colors: " + mostRecentObject, e);
        }
    }

    // End of initializers and file readers
    // ####################

    public static String getRelatedName(String relatedID, String relatedType) {
        String displayName = "";
        switch (relatedType) {
            case Constants.AGENDA -> {
                AgendaModel agenda = getAgenda(relatedID);
                displayName = CardEmojis.Agenda + " " + agenda.getName();
            }
            case Constants.AC -> {
                ActionCardModel actionCard = getActionCard(relatedID);
                displayName = actionCard.getRepresentation();
            }
            case Constants.PROMISSORY_NOTES -> {
                PromissoryNoteModel pn = getPromissoryNote(relatedID);
                displayName = CardEmojis.PN + " " + pn.getName() + ": " + pn.getText();
            }
            case Constants.TECH -> displayName = getTech(relatedID).getRepresentation(true);
            case Constants.RELIC -> displayName = getRelic(relatedID).getSimpleRepresentation();
            case Constants.ABILITY -> displayName = getAbility(relatedID).getRepresentation();
            case Constants.UNIT -> {
                UnitModel unit = getUnit(relatedID);
                displayName = unit.getUnitEmoji() + " " + unit.getName();
                if (unit.getAbility().isPresent()) displayName += " - *" + unit.getAbility() + "*";
            }
            case Constants.LEADER -> displayName = getLeader(relatedID).getRepresentation(true, true, false);
            default -> {}
        }
        return displayName;
    }

    // ####################
    // Abilities

    public static Map<String, AbilityModel> getAbilities() {
        return new HashMap<>(abilities);
    }

    public static AbilityModel getAbility(String abilityID) {
        return abilities.get(abilityID);
    }

    public static boolean isValidAbility(String abilityID) {
        return abilities.containsKey(abilityID);
    }

    public static List<String> getAbilitiesSources(ComponentSource CompSource) {
        return getAbilities().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static AbilityModel getAbilityOrReplacement(String abilityID, Game game) {
        if (game != null && game.isMiltyModMode()) {
            for (AbilityModel replace : getAbilities().values()) {
                if (replace.getSource() != ComponentSource.miltymod) continue;

                boolean replacesAbility =
                        replace.getHomebrewReplacesID().map(abilityID::equals).orElse(false);
                if (replacesAbility) {
                    return replace;
                }
            }
        }
        return getAbility(abilityID);
    }

    // ####################
    // Action Cards

    public static Map<String, ActionCardModel> getActionCards() {
        return new HashMap<>(actionCards);
    }

    public static Map<String, ActionCardModel> getActionCards(String extra) {
        Map<String, ActionCardModel> acList = new HashMap<>();
        for (Map.Entry<String, ActionCardModel> entry : actionCards.entrySet()) {
            acList.put(entry.getKey() + extra, entry.getValue());
        }
        return acList;
    }

    public static ActionCardModel getActionCard(String id) {
        if (id != null) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
        }
        return actionCards.get(id);
    }

    public static boolean isValidActionCard(String id) {
        if (id != null) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
        }
        return actionCards.containsKey(id);
    }

    public static List<String> getActionCardsSources(ComponentSource CompSource) {
        return getActionCards().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static Map<String, String> getACJustNames() {
        Map<String, String> acNameList = new HashMap<>();
        for (Map.Entry<String, ActionCardModel> entry : actionCards.entrySet()) {
            acNameList.put(entry.getKey(), entry.getValue().getName());
        }
        return acNameList;
    }
    // breakthroughs

    public static Map<String, BreakthroughModel> getBreakthroughs() {
        return breakthroughs;
    }

    public static BreakthroughModel getBreakthrough(String id) {
        return breakthroughs.get(id);
    }

    public static boolean isValidBreakthrough(String id) {
        return breakthroughs.containsKey(id);
    }

    // ####################
    // Agendas

    public static Map<String, AgendaModel> getAgendas() {
        return new HashMap<>(agendas);
    }

    public static AgendaModel getAgenda(String id) {
        return agendas.get(id);
    }

    public static boolean isValidAgenda(String agendaID) {
        return getAgendas().containsKey(agendaID);
    }

    public static List<String> getAgendasSources(ComponentSource CompSource) {
        return getAgendas().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    @Nullable
    public static String getAgendaTitle(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) {
            return null;
        }
        return agendaModel.getName().toUpperCase();
    }

    public static String getAgendaTitleNoCap(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) {
            return null;
        }
        return agendaModel.getName();
    }

    public static Map<String, String> getAgendaJustNames() {
        Map<String, String> agendaList = new HashMap<>();
        for (AgendaModel agenda : agendas.values()) {
            agendaList.put(agenda.getAlias(), agenda.getName());
        }
        return agendaList;
    }

    public static Map<String, String> getAgendaJustNames(Game game) {
        Map<String, String> agendaList = new HashMap<>();
        for (AgendaModel agenda : agendas.values()) {
            if (game.isAbsolMode() && agenda.getAlias().contains("absol_")) {
                agendaList.put(agenda.getAlias(), agenda.getName());
            }
            if (!game.isAbsolMode() && !agenda.getAlias().contains("absol_")) {
                agendaList.put(agenda.getAlias(), agenda.getName());
            }
        }
        return agendaList;
    }

    @Nullable
    public static String getAgendaText(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) {
            return null;
        }
        return agendaModel.getMapText();
    }

    public static String getAgendaType(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) {
            return "1";
        }
        return agendaModel.displayElectedFaction() ? "0" : "1";
    }

    public static String getAgendaForOnly(String id) {
        AgendaModel agenda = agendas.get(id);
        StringBuilder sb = new StringBuilder();
        sb.append(agenda.getName()).append(";");
        sb.append(agenda.getType()).append(";");
        if (agenda.getTarget().contains("For/Against")) {
            sb.append(agenda.getText1());
        } else {
            sb.append(agenda.getTarget()).append(";");
            sb.append(agenda.getText1());
            if (!agenda.getText2().isEmpty()) {
                sb.append(";").append(agenda.getText2());
            }
        }
        return sb.toString();
    }

    // ####################
    // Attachments

    public static Map<String, AttachmentModel> getAttachments() {
        return new HashMap<>(attachments);
    }

    public static List<AttachmentModel> getAttachmentsValues() {
        return new ArrayList<>(attachments.values());
    }

    public static boolean isValidAttachment(String id) {
        return attachments.containsKey(id);
    }

    public static List<String> getAttachmentsSources(ComponentSource CompSource) {
        return getAttachments().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static AttachmentModel getAttachmentInfo(String id) {
        AttachmentModel model = attachments.get(id);
        if (model != null) return model;
        id = id.replace("attachment_", "").replace(".png", "");
        if (attachments.get(id) == null) {
            id = "lloyd_" + id;
        }
        return attachments.get(id);
    }

    public static String getAttachmentImagePath(String tokenID) {
        AttachmentModel model = getAttachmentInfo(tokenID);
        if (model == null) return null;
        return model.getImagePath();
    }

    // ####################
    // Colors

    public static List<ColorModel> getColors() {
        return new ArrayList<>(colors.values());
    }

    public static ColorModel getColor(String color) {
        if (color == null || "null".equals(color)) return null;
        return colorToColorModelCache.get(color, c -> {
            for (ColorModel col : colors.values()) {
                if (col.getAlias().equals(color)) return col;
                if (col.getName().equals(color)) return col;
                if (col.getAliases().contains(color)) return col;
            }
            return null;
        });
    }

    public static boolean isValidColor(String color) {
        if (colors.containsKey(color)) return true;
        for (ColorModel col : colors.values()) {
            if (col.getName().equals(color)) return true;
            if (col.getAliases().contains(color)) return true;
        }
        return false;
    }

    // no source field in colors data, missing 'private List<String> getColorsSources(ComponentSource CompSource)'

    public static List<String> getColorNames() {
        return new ArrayList<>(colors.values().stream().map(ColorModel::getName).toList());
    }

    public static String getColorID(String color) {
        ColorModel colorModel = getColor(color);
        if (colorModel == null) return null;
        return colorModel.getAlias();
    }

    public static String getColorName(String color) {
        ColorModel colorModel = getColor(color);
        if (colorModel == null) return null;
        return colorModel.getName();
    }

    // ####################
    // Combat modifiers

    public static Map<String, CombatModifierModel> getCombatModifiers() {
        return new HashMap<>(combatModifiers);
    }

    // no source field in combat_modifiers data, missing 'private List<String> getCombatModifiersSources(ComponentSource
    // CompSource)'

    // ####################
    // Decks

    public static Map<String, DeckModel> getDecks() {
        return new HashMap<>(decks);
    }

    public static DeckModel getDeck(String deckID) {
        return getDecks().get(deckID);
    }

    public static boolean isValidDeck(String deckID) {
        return getDecks().containsKey(deckID);
    }

    public static List<String> getDecksSources(ComponentSource CompSource) {
        return getDecks().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static List<String> getShuffledDeck(String deckID) {
        return getDeck(deckID).getNewShuffledDeck();
    }

    // ####################
    // Events

    public static Map<String, EventModel> getEvents() {
        return new HashMap<>(events);
    }

    public static EventModel getEvent(String id) {
        return events.get(id);
    }

    public static boolean isValidEvent(String eventID) {
        return getEvents().containsKey(eventID);
    }

    public static List<String> getEventsSources(ComponentSource CompSource) {
        return getEvents().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Explores

    public static Map<String, ExploreModel> getExplores() {
        return new HashMap<>(explores);
    }

    public static ExploreModel getExplore(String exploreId) {
        exploreId = exploreId.replace("extra1", "");
        exploreId = exploreId.replace("extra2", "");
        return explores.get(exploreId);
    }

    public static boolean isValidExplore(String exploreID) {
        return explores.containsKey(exploreID);
    }

    public static List<String> getExploresSources(ComponentSource CompSource) {
        return getExplores().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Factions

    public static Map<String, FactionModel> getFactions() {
        return new HashMap<>(factions);
    }

    public static List<FactionModel> getFactionsValues() {
        return factions.values().stream()
                .sorted(Comparator.comparing(
                        f -> f.getFactionName().toLowerCase().replace("the ", "")))
                .collect(Collectors.toList());
    }

    public static FactionModel getFaction(String factionID) {
        return factions.get(factionID);
    }

    public static boolean isValidFaction(String faction) {
        return factions.containsKey(faction);
    }

    public static List<String> getFactionsSources(ComponentSource CompSource) {
        return getFactionsValues().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static List<String> getFactionIDs() {
        return factions.keySet().stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
    }

    // ####################
    // Franken Errata

    public static Map<String, DraftErrataModel> getFrankenErrata() {
        return frankenErrata;
    }

    public static List<String> getDraftErratasSources(ComponentSource CompSource) {
        return frankenErrata.values().stream()
                .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Generic Cards (Li-Zho Traps)

    public static Map<String, GenericCardModel> getGenericCards() {
        return new HashMap<>(genericCards);
    }

    public static Map<String, GenericCardModel> getTraps() {
        Map<String, GenericCardModel> plots = getGenericCards().entrySet().stream()
                .filter(card -> card.getValue().getCardType() == CardType.trap)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        return new HashMap<>(plots);
    }

    public static GenericCardModel getTrap(String plotID) {
        return getTraps().get(plotID);
    }

    public static List<String> getGenericCardsSources(ComponentSource CompSource) {
        return getGenericCards().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Leaders

    public static Map<String, LeaderModel> getLeaders() {
        return new HashMap<>(leaders);
    }

    public static LeaderModel getLeader(String leaderID) {
        return leaders.get(leaderID);
    }

    public static boolean isValidLeader(String leaderID) {
        return leaders.containsKey(leaderID);
    }

    public static List<String> getLeadersSources(ComponentSource CompSource) {
        return getLeaders().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Map Templates

    public static List<MapTemplateModel> getMapTemplates() {
        return new ArrayList<>(mapTemplates.values());
    }

    public static MapTemplateModel getMapTemplate(String id) {
        return mapTemplates.getOrDefault(id, null);
    }

    public static boolean isValidMapTemplate(String id) {
        if (id == null) return false;
        return mapTemplates.containsKey(id);
    }

    // no source field in map_templates data, missing 'private List<String> getMapTemplatesSources(ComponentSource
    // CompSource)'

    public static List<MapTemplateModel> getMapTemplatesForPlayerCount(int players) {
        return new ArrayList<>(mapTemplates.values())
                .stream()
                        .filter(template -> template.getPlayerCount() == players)
                        .toList();
    }

    public static MapTemplateModel getDefaultMapTemplateForPlayerCount(int players) {
        MapTemplateModel mapTemplate = null;
        List<MapTemplateModel> templates = getMapTemplatesForPlayerCount(players);
        if (templates.isEmpty()) {
            return null;
        } else if (templates.size() == 1) {
            mapTemplate = templates.getFirst();
        } else {
            String defaultMapTemplate =
                    switch (players) {
                        case 3 -> "3pHyperlanes";
                        case 4 -> "4pHyperlanes";
                        case 5 -> "5pHyperlanes";
                        case 6 -> "6pStandard";
                        case 7 -> "7pHyperlanes";
                        case 8 -> "8pHyperlanes";
                        default -> null;
                    };
            if (defaultMapTemplate == null) {
                mapTemplate = templates.getFirst(); // just get whatever template lol
            } else {
                for (MapTemplateModel model : templates) {
                    if (model.getAlias().equals(defaultMapTemplate)) {
                        return model;
                    }
                }
            }
        }
        return mapTemplate;
    }

    // ####################
    // Promissory Notes

    public static Map<String, PromissoryNoteModel> getPromissoryNotes() {
        return promissoryNotes;
    }

    public static PromissoryNoteModel getPromissoryNote(String id) {
        return promissoryNotes.get(id);
    }

    public static boolean isValidPromissoryNote(String id) {
        return promissoryNotes.containsKey(id);
    }

    public static List<String> getPromissoryNotesSources(ComponentSource CompSource) {
        return promissoryNotes.values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static List<String> getAllPromissoryNoteIDs() {
        return new ArrayList<>(promissoryNotes.keySet());
    }

    public static List<String> getColorPromissoryNoteIDs(Game game, String color) {
        List<String> pnList = new ArrayList<>();
        color = AliasHandler.resolveColor(color);
        if (isValidColor(color)) {
            for (PromissoryNoteModel pn : promissoryNotes.values()) {
                if (pn.getColor().isPresent() && color.equals(pn.getColor().get())) {
                    if ("agendas_absol".equals(game.getAgendaDeckID())
                            && pn.getAlias().endsWith("_ps")
                            && pn.getSource() != ComponentSource.absol) {
                        continue;
                    }
                    if (!"agendas_absol".equals(game.getAgendaDeckID())
                            && pn.getAlias().endsWith("_ps")
                            && pn.getSource() == ComponentSource.absol) {
                        continue;
                    }
                    if (pn.getAlias().startsWith("wekkerabsol_") && !"g14".equals(game.getName())) {
                        continue;
                    }
                    pnList.add(pn.getAlias());
                }
            }
        }
        return pnList;
    }

    // ####################
    // Public Objectives

    public static Map<String, PublicObjectiveModel> getPublicObjectives() {
        return new HashMap<>(publicObjectives);
    }

    @NotNull
    private static Map<String, String> getPublicObjectives(int requiredStage) {
        Map<String, String> poList = new HashMap<>();
        for (Map.Entry<String, PublicObjectiveModel> entry : publicObjectives.entrySet()) {
            PublicObjectiveModel po = entry.getValue();
            if (requiredStage == po.getPoints()) {
                poList.put(entry.getKey(), po.getName());
            }
        }
        return poList;
    }

    public static PublicObjectiveModel getPublicObjective(String id) {
        return publicObjectives.get(id);
    }

    public static boolean isValidPublicObjective(String id) {
        return publicObjectives.containsKey(id);
    }

    public static List<String> getPublicObjectivesSources(ComponentSource CompSource) {
        return getPublicObjectives().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static Map<String, String> getPublicObjectivesStage1() {
        return getPublicObjectives(1);
    }

    public static Map<String, String> getPublicObjectivesStage2() {
        return getPublicObjectives(2);
    }

    // ####################
    // Relics

    public static Map<String, RelicModel> getRelics() {
        return new HashMap<>(relics);
    }

    public static RelicModel getRelic(String id) {
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
        return relics.get(id);
    }

    public static boolean isValidRelic(String relicID) {
        return relics.containsKey(relicID);
    }

    public static List<String> getRelicsSources(ComponentSource CompSource) {
        return getRelics().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Secret Objectives

    public static Map<String, SecretObjectiveModel> getSecretObjectives() {
        return new HashMap<>(secretObjectives);
    }

    public static SecretObjectiveModel getSecretObjective(String id) {
        if (id != null) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
        }
        return secretObjectives.get(id);
    }

    public static boolean isValidSecretObjective(String id) {
        if (id != null) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
        }
        return secretObjectives.containsKey(id);
    }

    public static List<String> getSecretObjectivesSources(ComponentSource CompSource) {
        return getSecretObjectives().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static Map<String, String> getSecretObjectivesJustNames() {
        Map<String, String> soList = new HashMap<>();
        for (Map.Entry<String, SecretObjectiveModel> entry : secretObjectives.entrySet()) {
            soList.put(entry.getKey(), entry.getValue().getName());
        }
        return soList;
    }

    public static Map<String, String> getSecretObjectivesJustNamesAndSource() {
        Map<String, String> soList = new HashMap<>();
        for (Map.Entry<String, SecretObjectiveModel> entry : secretObjectives.entrySet()) {
            soList.put(
                    entry.getKey(),
                    entry.getValue().getName() + " (" + entry.getValue().getSource() + ")");
        }
        return soList;
    }

    // ####################
    // Sources

    public static Map<String, SourceModel> getSources() {
        return new HashMap<>(sources);
    }

    public static SourceModel getSource(String sourceID) {
        return sources.get(sourceID);
    }

    public static boolean isValidSource(String sourceID) {
        return sources.containsKey(sourceID);
    }

    // no point in having 'private List<String> getSourcesSources(ComponentSource CompSource)'

    // ####################
    // Strategy Cards Sets

    public static Map<String, StrategyCardSetModel> getStrategyCardSets() {
        return new HashMap<>(strategyCardSets);
    }

    public static boolean isValidStrategyCardSet(String strategyCardSetID) {
        return strategyCardSets.containsKey(strategyCardSetID);
    }

    public static List<String> getStrategyCardSetsSources(ComponentSource CompSource) {
        return getStrategyCardSets().values().stream()
                .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Strategy Cards

    public static Map<String, StrategyCardModel> getStrategyCards() {
        return new HashMap<>(strategyCards);
    }

    public static StrategyCardModel getStrategyCard(String strategyCardID) {
        return strategyCards.get(strategyCardID);
    }

    public static boolean isValidStrategyCard(String strategyCardID) {
        return strategyCards.containsKey(strategyCardID);
    }

    public static List<String> getStrategyCardsSources(ComponentSource CompSource) {
        return getStrategyCards().values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Techs

    public static Map<String, TechnologyModel> getTechs() {
        return technologies;
    }

    public static TechnologyModel getTech(String id) {
        return technologies.get(id);
    }

    public static boolean isValidTech(String id) {
        return technologies.containsKey(id);
    }

    public static List<String> getTechnologiesSources(ComponentSource CompSource) {
        return technologies.values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    private static <T> Map<String, T> getGenericHomebrewReplaceMap(
            List<T> models, Function<T, Optional<String>> getHomebrewID) {
        return new HashMap<>(models.stream()
                .filter(model -> getHomebrewID.apply(model).isPresent())
                .collect(Collectors.toMap(m -> getHomebrewID.apply(m).get(), Function.identity())));
    }

    public static Map<String, TechnologyModel> getHomebrewTechReplaceMap(String deckID) {
        List<TechnologyModel> models =
                getDeck(deckID).getNewDeck().stream().map(Mapper::getTech).toList();
        return getGenericHomebrewReplaceMap(models, TechnologyModel::getHomebrewReplacesID);
    }

    // ####################
    // Tokens & Tokens from .properties

    public static Map<String, TokenModel> getTokens() {
        return new HashMap<>(tokens);
    }

    public static List<TokenModel> getTokensValues() {
        return new ArrayList<>(tokens.values());
    }

    public static List<String> getTokensFromProperties() {
        return Stream.of(attachments.keySet(), tokensFromProperties.keySet(), tokens.keySet())
                .flatMap(Collection::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    public static TokenModel getToken(String id) {
        return tokens.get(getTokenKey(id));
    }

    public static boolean isValidToken(String id) {
        return getTokensFromProperties().contains(id);
    }

    public static List<String> getTokensSources(ComponentSource CompSource) {
        return getTokens().values().stream()
                .filter(model -> model.searchSource(CompSource)) // searchSource not implemented
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static Map<String, String> getTokensToName() {
        Map<String, String> tokensToName = new HashMap<>();
        for (Map.Entry<String, AttachmentModel> attachment : attachments.entrySet()) {
            String key = attachment.getKey();
            String value = attachment.getValue().getImagePath();
            tokensToName.put(value, key);
        }

        for (Map.Entry<Object, Object> tokens : tokensFromProperties.entrySet()) {
            String key = (String) tokens.getKey();
            String value = (String) tokens.getValue();
            tokensToName.put(value, key);
        }
        return tokensToName;
    }

    public static String getTokenIDFromTokenPath(String tokenPath) {
        return getTokensToName().get(tokenPath);
    }

    public static String getTokenID(String tokenID) {

        return tokensFromProperties.getProperty(tokenID);
    }

    public static String getTokenKey(String tokenID) {
        for (Entry<String, TokenModel> token : tokens.entrySet()) {
            String key = token.getKey();
            String val = token.getValue().getImagePath();
            if (tokenID.equalsIgnoreCase(val) || tokenID.equalsIgnoreCase(key)) return key;
        }
        System.out.println("Could not resolve token: " + tokenID);
        return tokenID;
    }

    // Color Tokens

    public static String getCCID(String color) {
        return "command_" + getColorID(color) + ".png";
    }

    public static String getFleetCCID(String color) {
        return "fleet_" + getColorID(color) + ".png";
    }

    public static String getControlID(String color) {
        return "control_" + getColorID(color) + ".png";
    }

    public static String getPeekMarkerID(String color) {
        return "peak_" + getColorID(color) + ".png";
    }

    public static String getSweepID(String color) {
        return "sweep_" + getColorID(color) + ".png";
    }

    @Nullable
    public static String getCCPath(String ccID) {
        return ResourceHelper.getInstance().getCCFile(ccID);
    }

    @Nullable
    public static String getPeekMarkerPath(String markerID) {
        return ResourceHelper.getInstance().getPeekMarkerFile(markerID);
    }

    @Nullable
    public static String getTokenPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null || !(new File(tokenPath).exists())) {
            tokenPath = ResourceHelper.getInstance().getTokenFile(tokenID);
            if (tokenPath == null) {
                BotLogger.warning("Could not find token path: " + tokenID);
                return null;
            }
        }
        return tokenPath;
    }

    // ####################
    // Units

    public static Map<String, UnitModel> getUnits() {
        return units;
    }

    public static UnitModel getUnit(String unitID) {
        return units.get(unitID);
    }

    public static boolean isValidUnit(String unitID) {
        return units.containsKey(unitID);
    }

    public static List<String> getUnitsSources(ComponentSource CompSource) {
        return units.values().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static List<String> getUnitSourcesDistinct() {
        return units.values().stream()
                .map(unit -> unit.getSource().toString())
                .distinct()
                .sorted()
                .toList();
    }

    public static UnitModel getUnitModelByTechUpgrade(String techID) {
        return units.values().stream()
                .filter(unitModel -> techID.equals(unitModel.getRequiredTechId().orElse("")))
                .findFirst()
                .orElse(null);
    }

    public static UnitKey getUnitKey(String unitID, String colorID) {
        if (!isValidAsyncUnitID(unitID)) return null;
        String actuallyColorID = getColorID(colorID) == null ? colorID : getColorID(colorID);
        return Units.getUnitKey(unitID, actuallyColorID);
    }

    private static boolean isValidAsyncUnitID(String asyncUnitID) {
        return getUnitIDList().contains(asyncUnitID);
    }

    public static Set<String> getUnitIDList() {
        return units.values().stream().map(UnitModel::getAsyncId).collect(Collectors.toSet());
    }

    public static String getUnitBaseTypeFromAsyncID(String asyncID) {
        return units.values().stream()
                .filter(unitModel -> asyncID.equals(unitModel.getAsyncId()))
                .map(UnitModel::getBaseType)
                .findFirst()
                .orElse(null);
    }

    // ####################
    // Decals from .properties

    public static Set<String> getDecals() {
        return decals.keySet().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toSet());
    }

    public static boolean isValidDecalSet(String decalID) {
        if (decalID == null || "null".equals(decalID)) return false;
        return decals.containsKey(decalID);
    }

    public static String getDecalName(String decalID) {
        if (decalID == null || "null".equals(decalID)) return null;
        return decals.getProperty(decalID);
    }

    // ####################
    // General from .properties

    public static String getGeneralFileName(String id) {
        return general.getProperty(id);
    }

    // ####################
    // Hyperlane adjacencies from .properties

    public static String getHyperlaneData(String tileID) {
        return hyperlaneAdjacencies.getProperty(tileID);
    }

    public static String getHyperlaneTileId(String hyperlaneData) {
        return hyperlaneAdjacencies.stringPropertyNames().stream()
                .filter(key -> hyperlaneData.equals(hyperlaneAdjacencies.getProperty(key)))
                .findFirst()
                .orElse(null);
    }

    // ####################
    // Special cases from .properties

    public static String getSpecialCaseValues(String id) {
        String property = specialCase.getProperty(id);
        return property != null ? property : "";
    }

    // ####################
    // Tokens from .properties
    // --> See section Tokens above

    // ####################
    // Planets

    public static Map<String, String> getPlanetRepresentations() {
        return TileHelper.getAllPlanetModels().stream()
                .collect(Collectors.toMap(PlanetModel::getId, PlanetModel::getNameNullSafe));
    }

    public static PlanetModel getPlanet(String id) {
        return TileHelper.getPlanetById(id);
    }

    public static boolean isValidPlanet(String id) {
        return AliasHandler.getPlanetKeyList().contains(id);
    }

    public static List<String> getPlanetsSources(ComponentSource CompSource) {
        return TileHelper.getAllPlanetModels().stream()
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Tiles

    public static Map<String, String> getTileRepresentations() {
        return TileHelper.getAllTileModels().stream()
                .collect(Collectors.toMap(TileModel::getId, TileModel::getNameNullSafe));
    }

    public static String getTileID(String tileID) {
        if (TileHelper.getTileById(tileID) == null) {
            return null;
        }
        return TileHelper.getTileById(tileID).getImagePath();
    }

    public static List<String> getTilesSources(ComponentSource CompSource) {
        return TileHelper.getAllTileModels().stream() // Collection<TileModel> -> Stream<>
                .filter(model -> model.searchSource(CompSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // Frontiers

    public static List<String> getFrontierTileIds() {
        List<String> exclusionList = List.of("Hyperlane", "", "Mallice (Locked)");
        return TileHelper.getAllTileModels().stream()
                .filter(tileModel -> !exclusionList.contains(tileModel.getNameNullSafe()))
                .filter(tileModel -> !TileHelper.isDraftTile(tileModel))
                .filter(tileModel -> !tileModel.isHyperlane())
                .filter(TileModel::isEmpty)
                .map(TileModel::getId)
                .toList();
    }

    // Wormholes

    public static Set<String> getWormholes(String tileID) {
        if (tileID == null
                || TileHelper.getTileById(tileID) == null
                || TileHelper.getTileById(tileID).getWormholes() == null) {
            return new HashSet<>();
        }
        return TileHelper.getTileById(tileID).getWormholes().stream()
                .filter(Objects::nonNull)
                .map(WormholeModel.Wormhole::toString)
                .collect(Collectors.toSet());
    }

    public static Set<String> getWormholesTiles(String wormholeID) {
        WormholeModel wormholeModel = new WormholeModel();
        WormholeModel.Wormhole wormhole = wormholeModel.getWormholeFromString(wormholeID);
        if (wormhole == null) {
            return new HashSet<>();
        }

        return TileHelper.getAllTileModels().stream()
                .filter(tileModel -> tileModel.getWormholes() != null
                        && tileModel.getWormholes().contains(wormhole))
                .map(TileModel::getId)
                .collect(Collectors.toSet());
    }

    private static class ColorDeserializer extends JsonDeserializer<Color> {
        @Override
        public Color deserialize(JsonParser p, DeserializationContext c) throws IOException {
            JsonNode n = p.getCodec().readTree(p);
            // "#RRGGBB" or "0xRRGGBB"
            if (n.isTextual()) return Color.decode(n.asText());
            // ARGB
            if (n.isInt()) return new Color(n.asInt(), true);
            // { "red" : ###, "green": ###, "blue": ###, "alpha": ### } where alpha is optional
            if (n.has("red")) {
                int r = n.get("red").asInt(),
                        g = n.get("green").asInt(),
                        b = n.get("blue").asInt();
                int a = n.has("alpha") ? n.get("alpha").asInt() : 255;
                return new Color(r, g, b, a);
            }
            throw c.weirdStringException(n.toString(), Color.class, "Unsupported color format");
        }
    }
}
