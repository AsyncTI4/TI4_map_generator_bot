package ti4.model;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
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
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.model.DeckModel.DeckType;
import ti4.model.GenericCardModel.CardType;
import ti4.model.Source.ComponentSource;
import ti4.model.WormholeModel.Wormhole;
import ti4.service.emoji.CardEmojis;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@UtilityClass
public class Mapper {

    private static volatile Properties decals = new Properties();
    private static volatile Properties general = new Properties();
    private static volatile Properties hyperlaneAdjacencies = new Properties();
    private static volatile Properties specialCase = new Properties();
    private static volatile Properties tokensFromProperties = new Properties();

    // TODO: Finish moving all files over from properties to json
    private static volatile Map<String, AbilityModel> abilities = new HashMap<>();
    private static volatile Map<String, RuleModel> rules = new HashMap<>();
    private static volatile Map<String, ActionCardModel> actionCards = new HashMap<>();
    private static volatile Map<String, AgendaModel> agendas = new HashMap<>();
    private static volatile Map<String, AttachmentModel> attachments = new HashMap<>();
    private static volatile Map<String, BorderAnomalyModel> borderAnomalies = new HashMap<>();
    private static volatile Map<String, ColorModel> colors = new HashMap<>();
    private static volatile Map<String, CombatModifierModel> combatModifiers = new HashMap<>();
    private static volatile Map<String, DeckModel> decks = new HashMap<>();
    private static volatile Map<String, EventModel> events = new HashMap<>();
    private static volatile Map<String, ExploreModel> explores = new HashMap<>();
    private static volatile Map<String, FactionModel> factions = new HashMap<>();

    @Getter
    private static volatile Map<String, DraftErrataModel> frankenErrata = new HashMap<>();

    private static volatile Map<String, GenericCardModel> genericCards = new HashMap<>();
    private static volatile Map<String, HydromorphicCardModel> hydromorphicCards = new HashMap<>();
    private static volatile Map<String, LeaderModel> leaders = new HashMap<>();
    private static volatile Map<String, MapTemplateModel> mapTemplates = new HashMap<>();

    @Getter
    private static volatile Map<String, PromissoryNoteModel> promissoryNotes = new HashMap<>();

    private static volatile Map<String, PublicObjectiveModel> publicObjectives = new HashMap<>();
    private static volatile Map<String, RelicModel> relics = new HashMap<>();
    private static volatile Map<String, SpaceTokenModel> spaceTokens = new HashMap<>();
    private static volatile Map<String, SecretObjectiveModel> secretObjectives = new HashMap<>();
    private static volatile Map<String, SourceModel> sources = new HashMap<>();
    private static volatile Map<String, StrategyCardSetModel> strategyCardSets = new HashMap<>();
    private static volatile Map<String, StrategyCardModel> strategyCards = new HashMap<>();
    private static volatile Map<String, TechnologyModel> technologies = new HashMap<>();
    private static volatile Map<String, TokenModel> tokens = new HashMap<>();
    // ids + image paths of tokens flagged isFowVision; precomputed so per-tile fog checks avoid getTokenKey's scan
    private static volatile Set<String> fowVisionTokenIds = new HashSet<>();
    private static volatile Map<String, List<Wormhole>> wormholesByImagePath = new HashMap<>();
    private static volatile Map<Wormhole, String> imagePathByWormhole = new HashMap<>();
    private static volatile Set<String> mergeableWormholeImagePaths = new HashSet<>();
    private static volatile Map<String, GalacticEventModel> galacticevents = new HashMap<>();

    @Getter
    private static volatile Map<String, UnitModel> units = new HashMap<>();

    @Getter
    private static volatile Map<String, BreakthroughModel> breakthroughs = new HashMap<>();

    private static volatile Map<String, ColorModel> colorsByAnyName = new HashMap<>();
    private static volatile Set<String> unitAsyncIds = Set.of();

    private static final JsonMapper jsonMapper = JsonMapperManager.basic()
            .rebuild()
            .addModule(new SimpleModule().addDeserializer(Color.class, new ColorDeserializer()))
            .build();

    public static void init() {
        try {
            loadData();
        } catch (Exception e) {
            BotLogger.error("Could not load data", e);
        }
    }

    static void loadData() throws Exception {
        // must be first for validating later models
        factions = importJsonObjectsFromFolder("factions", FactionModel.class);

        abilities = importJsonObjectsFromFolder("abilities", AbilityModel.class);
        actionCards = importJsonObjectsFromFolder("action_cards", ActionCardModel.class);
        agendas = importJsonObjectsFromFolder("agendas", AgendaModel.class);
        attachments = importJsonObjectsFromFolder("attachments", AttachmentModel.class);
        borderAnomalies = importJsonObjectsFromFolder("border_anomalies", BorderAnomalyModel.class);
        breakthroughs = importJsonObjectsFromFolder("breakthroughs", BreakthroughModel.class);
        colors = importJsonObjectsFromFolder("colors", ColorModel.class);
        combatModifiers = importJsonObjectsFromFolder("combat_modifiers", CombatModifierModel.class);
        decks = importJsonObjectsFromFolder("decks", DeckModel.class);
        events = importJsonObjectsFromFolder("events", EventModel.class);
        explores = importJsonObjectsFromFolder("explores", ExploreModel.class);
        genericCards = importJsonObjectsFromFolder("genericcards", GenericCardModel.class);
        hydromorphicCards = importJsonObjectsFromFolder("hydromorphic_cards", HydromorphicCardModel.class);
        leaders = importJsonObjectsFromFolder("leaders", LeaderModel.class);
        mapTemplates = importJsonObjectsFromFolder("map_templates", MapTemplateModel.class);
        promissoryNotes =
                withCopiesForAllColors(importJsonObjectsFromFolder("promissory_notes", PromissoryNoteModel.class));
        publicObjectives = importJsonObjectsFromFolder("public_objectives", PublicObjectiveModel.class);
        relics = importJsonObjectsFromFolder("relics", RelicModel.class);
        rules = importJsonObjectsFromFolder("rules", RuleModel.class);
        secretObjectives =
                withCopiesForAllColors(importJsonObjectsFromFolder("secret_objectives", SecretObjectiveModel.class));
        sources = importJsonObjectsFromFolder("sources", SourceModel.class);
        strategyCardSets = importJsonObjectsFromFolder("strategy_card_sets", StrategyCardSetModel.class);
        strategyCards = importJsonObjectsFromFolder("strategy_cards", StrategyCardModel.class);
        technologies = importJsonObjectsFromFolder("technologies", TechnologyModel.class);
        galacticevents = importJsonObjectsFromFolder("galactic_events", GalacticEventModel.class);

        tokens = importJsonObjectsFromFolder("tokens", TokenModel.class);
        indexFowVisionTokens();
        indexTokenWormholes();
        spaceTokens = importJsonObjectsFromFolder("tokens", SpaceTokenModel.class);
        units = importJsonObjectsFromFolder("units", UnitModel.class);
        frankenErrata = importJsonObjectsFromFolder("franken_errata", DraftErrataModel.class);
        decals = readData("decals.properties");
        general = readData("general.properties");
        hyperlaneAdjacencies = readData("hyperlanes.properties");
        specialCase = readData("special_case.properties");
        tokensFromProperties = readData("tokens.properties");

        colorsByAnyName = indexColorsByAnyName(colors);
        unitAsyncIds = Collections.unmodifiableSet(
                units.values().stream().map(UnitModel::getAsyncId).collect(Collectors.toSet()));
    }

    private static Properties readData(String propertyFileName) throws IOException {
        Properties properties = new Properties();
        String propFile = ResourceHelper.getDataFile(propertyFileName);
        if (propFile != null) {
            try (InputStream input = new FileInputStream(propFile)) {
                properties.load(input);
            } catch (IOException e) {
                BotLogger.error("Could not read .property file: " + propertyFileName, e);
                throw e;
            }
        }
        return properties;
    }

    private static <T extends ModelInterface> Map<String, T> importJsonObjectsFromFolder(
            String jsonFolderName, Class<T> target) {
        String folderPath = ResourceHelper.getDataFolder(jsonFolderName);
        Map<String, T> objectMap = new HashMap<>();

        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                continue;
            }
            try {
                importJsonObjects(jsonFolderName + File.separator + file.getName(), objectMap, target);
            } catch (Exception e) {
                BotLogger.error("Could not import JSON Objects from file: " + jsonFolderName + "/" + file.getName(), e);
            }
        }
        return objectMap;
    }

    public static List<SpaceTokenModel> getSpaceTokens() {
        return new ArrayList<>(spaceTokens.values());
    }

    public static SpaceTokenModel getSpaceToken(String tokenID) {
        return spaceTokens.getOrDefault(tokenID, null);
    }

    public static SpaceTokenModel getSpaceTokenFromTokenIdOrFileName(String tokenIdOrFileName) {
        for (SpaceTokenModel model : spaceTokens.values()) {
            if (model.getFileName().equals(tokenIdOrFileName)
                    || model.getAlias().equals(tokenIdOrFileName)) {
                return model;
            }
        }
        return null;
    }

    public static Map<String, GenericCardModel> getPlots() {
        Map<String, GenericCardModel> plots = getGenericCards().entrySet().stream()
                .filter(card -> card.getValue().getCardType() == CardType.plot)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        return new HashMap<>(plots);
    }

    public static GenericCardModel getPlot(String plotID) {
        return getPlots().get(plotID);
    }

    private static <T extends ModelInterface> void importJsonObjects(
            String jsonFileName, Map<String, T> objectMap, Class<T> target) throws Exception {
        List<T> allObjects = new ArrayList<>();
        String filePath = ResourceHelper.getDataFile(jsonFileName);
        JavaType type = jsonMapper.getTypeFactory().constructCollectionType(ArrayList.class, target);

        if (filePath != null) {
            try {
                InputStream input = new FileInputStream(filePath);
                allObjects = jsonMapper.readValue(input, type);
            } catch (Exception e) {
                BotLogger.error("Could not import JSON Objects from file: " + jsonFileName, e);
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

    private static <T extends ColorableModelInterface<T>> Map<String, T> withCopiesForAllColors(Map<String, T> loaded) {
        Map<String, T> objectMap = new HashMap<>(loaded);
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
        return objectMap;
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

    public static Map<String, GalacticEventModel> getGalacticEvents() {
        return new HashMap<>(galacticevents);
    }

    public static GalacticEventModel getGalacticEvent(String id) {
        return galacticevents.get(id);
    }

    public static Map<String, RuleModel> getRules() {
        return new HashMap<>(rules);
    }

    public static RuleModel getRule(String ruleID) {
        return getRules().getOrDefault(ruleID, null);
    }

    public static boolean isValidRule(String ruleID) {
        return getRule(ruleID) != null;
    }

    public static boolean isValidGalacticEvent(String scenarioID) {
        return getGalacticEvents().containsKey(scenarioID);
    }

    public static List<String> getAbilitiesSources(ComponentSource compSource) {
        return getAbilities().values().stream()
                .filter(model -> model.searchSource(compSource))
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
        if (isBlank(id)) {
            throw new IllegalArgumentException("AC id cannot be blank.");
        }
        id = id.replace("extra1", "");
        id = id.replace("extra2", "");
        if (!actionCards.containsKey(id)) {
            BotLogger.critical("Action card not found: " + id);
            return null;
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

    public static Map<String, HydromorphicCardModel> getHydromorphicCards() {
        return new HashMap<>(hydromorphicCards);
    }

    public static HydromorphicCardModel getHydromorphicCard(String id) {
        return hydromorphicCards.get(id);
    }

    public static List<String> getActionCardsSources(ComponentSource compSource) {
        return getActionCards().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getAgendasSources(ComponentSource compSource) {
        return getAgendas().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    @Nullable
    public static String getAgendaTitleNoCap(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) return null;
        return agendaModel.getName();
    }

    public static Map<String, String> getAgendaJustNames() {
        Map<String, String> agendaList = new HashMap<>();
        for (AgendaModel agenda : agendas.values()) {
            agendaList.put(agenda.getAlias(), agenda.getName());
        }
        return agendaList;
    }

    @Nullable
    public static String getAgendaText(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) return null;
        return agendaModel.getMapText();
    }

    public static String getAgendaType(String id) {
        AgendaModel agendaModel = agendas.get(id);
        if (agendaModel == null) {
            return "1";
        }
        return agendaModel.displayElectedFaction() ? "0" : "1";
    }

    @Nullable
    public static String getAgendaForOnly(String id) {
        AgendaModel agenda = agendas.get(id);
        if (agenda == null) return null;

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

    public static boolean isValidAttachment(String id) {
        return attachments.containsKey(id);
    }

    public static List<String> getAttachmentsSources(ComponentSource compSource) {
        return getAttachments().values().stream()
                .filter(model -> model.searchSource(compSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static AttachmentModel getAttachmentInfo(String id) {
        AttachmentModel model = attachments.get(id);
        if (model != null) return model;
        id = id.replace("attachment_", "").replace(".png", "");
        if ("diversifiedresearchfacilitystat".equals(id)
                && attachments.containsKey("diversifiedresearchfacility_stat")) {
            return attachments.get("diversifiedresearchfacility_stat");
        }
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
        return colorsByAnyName.get(color);
    }

    private static Map<String, ColorModel> indexColorsByAnyName(Map<String, ColorModel> colorsByAlias) {
        Map<String, ColorModel> index = new HashMap<>();
        for (ColorModel color : colorsByAlias.values()) {
            index.putIfAbsent(color.getAlias(), color);
            index.putIfAbsent(color.getName(), color);
            for (String alias : color.getAliases()) index.putIfAbsent(alias, color);
        }
        return index;
    }

    public static boolean isValidColor(String color) {
        if (colors.containsKey(color)) return true;
        for (ColorModel col : colors.values()) {
            if (col.getName().equals(color)) return true;
            if (col.getAliases().contains(color)) return true;
        }
        return false;
    }

    // no source field in colors data, missing 'private List<String> getColorsSources(ComponentSource compSource)'

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

    public static String getColorDisplayName(String color) {
        ColorModel colorModel = getColor(color);
        if (colorModel == null) return null;
        return colorModel.getDisplayName();
    }

    // ####################
    // Combat modifiers

    public static Map<String, CombatModifierModel> getCombatModifiers() {
        return new HashMap<>(combatModifiers);
    }

    // no source field in combat_modifiers data, missing 'private List<String> getCombatModifiersSources(ComponentSource
    // compSource)'

    // ####################
    // Decks

    public static Map<String, DeckModel> getDecks() {
        return new HashMap<>(decks);
    }

    public static DeckModel getDeck(String deckID) {
        return getDecks().get(deckID);
    }

    public static DeckModel getDeck(String deckID, Game game) {
        if (game == null || (getDecks().get(deckID) != null)) return getDeck(deckID);

        switch (deckID) {
            case "relic" -> {
                return getDynamicRelicDeck(game);
            }
            case "ac" -> {
                return getDynamicACDeck(game);
            }
            case "explore" -> {
                return getDynamicExploreDeck(game);
            }
            default -> {
                return null;
            }
        }
    }

    private static DeckModel getDynamicExploreDeck(Game game) {
        DeckModel deck = new DeckModel();
        deck.setType(DeckType.EXPLORE);
        deck.setName("Dynamic Explore Deck");
        deck.setAlias("explore");
        deck.setDescription("A dynamic explore deck for the game.");
        List<String> cards = new ArrayList<>();
        for (String explore : getExplores().keySet()) {
            ExploreModel exploreModel = getExplore(explore);
            if (exploreModel.getSource().isPok() && game.isProphecyOfKings()) {
                cards.add(explore);
            }
            if (exploreModel.getSource() == ComponentSource.uncharted_space && game.isUnchartedSpaceStuff()) {
                cards.add(explore);
            }
            if (exploreModel.getSource() == ComponentSource.blue_reverie && game.isBlueReverieMode()) {
                cards.add(explore);
            }
        }
        deck.setCardIDs(cards);
        return deck;
    }

    private static DeckModel getDynamicRelicDeck(Game game) {
        DeckModel deck = new DeckModel();
        deck.setType(DeckType.RELIC);
        deck.setName("Dynamic Relic Deck");
        deck.setAlias("relic");
        deck.setDescription("A dynamic relic deck for the game.");
        List<String> cards = new ArrayList<>();
        for (String relic : getRelics().keySet()) {
            RelicModel relicModel = getRelic(relic);
            if (relicModel.isFakeRelic()) {
                continue;
            }
            if (relicModel.getSource().isPok() && game.isProphecyOfKings() && !game.isAbsolMode()) {
                cards.add(relic);
            }
            if (relicModel.getSource() == ComponentSource.absol && game.isAbsolMode()) {
                cards.add(relic);
            }
            if (relicModel.getSource() == ComponentSource.thunders_edge && game.isThundersEdge()) {
                cards.add(relic);
            }
            if (relicModel.getSource() == ComponentSource.uncharted_space && game.isUnchartedSpaceStuff()) {
                cards.add(relic);
            }
            if (relicModel.getSource() == ComponentSource.blue_reverie && game.isBlueReverieMode()) {
                cards.add(relic);
            }
        }
        deck.setCardIDs(cards);
        return deck;
    }

    private static DeckModel getDynamicACDeck(Game game) {
        // Implementation for dynamic AC deck
        DeckModel deck = new DeckModel();
        deck.setType(DeckType.ACTION_CARD);
        deck.setName("Dynamic AC Deck");
        deck.setAlias("ac");
        deck.setDescription("A dynamic AC deck for the game.");
        List<String> cards = new ArrayList<>();
        for (String actionCard : getActionCards().keySet()) {
            ActionCardModel actionCardModel = getActionCard(actionCard);
            if (actionCardModel.getSource().isPok()
                    && game.isProphecyOfKings()
                    && (!game.isAcd2()
                            || getDeck("action_deck_2_te").getNewDeck().contains(actionCard))
                    && !game.isTwilightsFallMode()) {
                cards.add(actionCard);
            }
            if (actionCardModel.getSource() == ComponentSource.action_deck_2 && game.isAcd2()) {
                cards.add(actionCard);
            }
            if (actionCardModel.getSource() == ComponentSource.thunders_edge
                    && game.isThundersEdge()
                    && !game.isAcd2()
                    && !game.isTwilightsFallMode()) {
                cards.add(actionCard);
            }
            if (actionCardModel.getSource() == ComponentSource.uncharted_space && game.isUnchartedSpaceStuff()) {
                cards.add(actionCard);
            }
            if (actionCardModel.getSource() == ComponentSource.blue_reverie && game.isBlueReverieMode()) {
                cards.add(actionCard);
            }
        }
        deck.setCardIDs(cards);
        return deck;
    }

    public static boolean isValidDeck(String deckID) {
        return getDecks().containsKey(deckID);
    }

    public static List<String> getDecksSources(ComponentSource compSource) {
        return getDecks().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getEventsSources(ComponentSource compSource) {
        return getEvents().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getExploresSources(ComponentSource compSource) {
        return getExplores().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getFactionsSources(ComponentSource compSource) {
        return getFactionsValues().stream()
                .filter(model -> model.searchSource(compSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    public static List<String> getFactionIDs() {
        return factions.keySet().stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
    }

    // ####################
    // Franken Errata

    public static DraftErrataModel getFrankenErrata(String alias) {
        return frankenErrata.getOrDefault(alias, null);
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

    public static List<String> getGenericCardsSources(ComponentSource compSource) {
        return getGenericCards().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getLeadersSources(ComponentSource compSource) {
        return getLeaders().values().stream()
                .filter(model -> model.searchSource(compSource))
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
    // compSource)'

    public static List<MapTemplateModel> getMapTemplatesForPlayerCount(int players) {
        return new ArrayList<>(mapTemplates.values())
                .stream()
                        .filter(template -> template.getPlayerCount() == players)
                        .toList();
    }

    public static MapTemplateModel getDefaultNucleusTemplate(int playerCount) {
        List<MapTemplateModel> templates = getMapTemplatesForPlayerCount(playerCount);
        for (MapTemplateModel model : templates) {
            if (model.isNucleusTemplate() && model.getPlayerCount() == playerCount) {
                return model;
            }
        }
        return null;
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

    public static PromissoryNoteModel getPromissoryNote(String id) {
        return promissoryNotes.get(id);
    }

    public static boolean isValidPromissoryNote(String id) {
        return promissoryNotes.containsKey(id);
    }

    public static List<String> getPromissoryNotesSources(ComponentSource compSource) {
        return promissoryNotes.values().stream()
                .filter(model -> model.searchSource(compSource))
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
                    if (pn.getAlias().endsWith("_ps") && "agendas_absol".equals(game.getAgendaDeckID())) {
                        // Absol's agenda deck is active: only its own Political Secret variant is dealt.
                        if (pn.getSource() == ComponentSource.absol) {
                            pnList.add(pn.getAlias());
                        }
                        continue;
                    }
                    if (pn.getAlias().startsWith("wekkerabsol_") && !"g14".equals(game.getName())) {
                        continue;
                    }
                    if (pn.getHomebrewReplacesID().isPresent()) {
                        // Any other homebrew replacement PN is opt-in only; nothing has turned it on yet.
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

    public static List<String> getPublicObjectivesSources(ComponentSource compSource) {
        return getPublicObjectives().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getRelicsSources(ComponentSource compSource) {
        return getRelics().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getSecretObjectivesSources(ComponentSource compSource) {
        return getSecretObjectives().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    // no point in having 'private List<String> getSourcesSources(ComponentSource compSource)'

    // ####################
    // Strategy Cards Sets

    public static Map<String, StrategyCardSetModel> getStrategyCardSets() {
        return new HashMap<>(strategyCardSets);
    }

    public static boolean isValidStrategyCardSet(String strategyCardSetID) {
        return strategyCardSets.containsKey(strategyCardSetID);
    }

    public static List<String> getStrategyCardSetsSources(ComponentSource compSource) {
        return getStrategyCardSets().values().stream()
                .filter(model -> model.searchSource(compSource)) // searchSource not implemented
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

    public static List<String> getStrategyCardsSources(ComponentSource compSource) {
        return getStrategyCards().values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static List<String> getTechnologiesSources(ComponentSource compSource) {
        return technologies.values().stream()
                .filter(model -> model.searchSource(compSource))
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

    public static BorderAnomalyModel getBorderAnomaly(String id) {
        if (id == null) return null;
        return borderAnomalies.get(id);
    }

    public static BorderAnomalyModel resolveBorderAnomaly(String input) {
        if (input == null) return null;
        BorderAnomalyModel exact = borderAnomalies.get(input);
        if (exact != null) return exact;
        return borderAnomalies.values().stream()
                .filter(model -> model.matches(input))
                .findFirst()
                .orElse(null);
    }

    public static List<BorderAnomalyModel> getBorderAnomalies() {
        return borderAnomalies.values().stream()
                .sorted(Comparator.comparing(BorderAnomalyModel::getName))
                .toList();
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

    private static void indexFowVisionTokens() {
        Set<String> visionTokenIds = new HashSet<>();
        for (TokenModel token : tokens.values()) {
            if (!Boolean.TRUE.equals(token.getIsFowVision())) continue;
            visionTokenIds.add(token.getId());
            if (token.getImagePath() != null) visionTokenIds.add(token.getImagePath());
        }
        fowVisionTokenIds = visionTokenIds;
    }

    /** True if the id or image path belongs to a token flagged {@code isFowVision}. */
    public static boolean isFowVisionToken(String tokenId) {
        return tokenId != null && fowVisionTokenIds.contains(tokenId);
    }

    private static void indexTokenWormholes() {
        Map<String, List<Wormhole>> byImagePath = new HashMap<>();
        Map<Wormhole, String> byWormhole = new HashMap<>();
        Set<String> mergeable = new HashSet<>();
        List<TokenModel> tokensById = tokens.values().stream()
                .sorted(Comparator.comparing(TokenModel::getId))
                .toList();
        for (TokenModel token : tokensById) {
            List<Wormhole> wormholes = nonNullWormholes(token.getWormholes());
            if (wormholes.isEmpty()) continue;
            byImagePath.put(token.getImagePath(), wormholes);
            if (wormholes.size() == 1) byWormhole.putIfAbsent(wormholes.getFirst(), token.getImagePath());
            boolean mergeableToken = wormholes.size() == 1 && !Boolean.FALSE.equals(token.getWormholeIconMerge());
            if (mergeableToken) mergeable.add(token.getImagePath());
            for (String legacyImagePath :
                    Optional.ofNullable(token.getLegacyImagePaths()).orElse(List.of())) {
                byImagePath.put(legacyImagePath, wormholes);
                if (mergeableToken) mergeable.add(legacyImagePath);
            }
        }
        for (AttachmentModel attachment : attachments.values()) {
            List<Wormhole> wormholes = nonNullWormholes(attachment.getWormholes());
            if (!wormholes.isEmpty()) byImagePath.put(attachment.getImagePath(), wormholes);
        }
        wormholesByImagePath = byImagePath;
        imagePathByWormhole = byWormhole;
        mergeableWormholeImagePaths = mergeable;
    }

    private static List<Wormhole> nonNullWormholes(List<Wormhole> wormholes) {
        if (wormholes == null) return List.of();
        return wormholes.stream().filter(Objects::nonNull).toList();
    }

    @Nullable
    public static String getWormholeTokenImagePath(Wormhole wormhole) {
        return imagePathByWormhole.get(wormhole);
    }

    public static boolean isMergeableWormholeToken(String tokenFileName) {
        return tokenFileName != null && mergeableWormholeImagePaths.contains(tokenFileName);
    }

    public static List<Wormhole> getTokenWormholes(String tokenFileName) {
        if (tokenFileName == null) return List.of();
        return wormholesByImagePath.getOrDefault(tokenFileName, List.of());
    }

    public static boolean isValidToken(String id) {
        return getTokensFromProperties().contains(id);
    }

    public static List<String> getTokensSources(ComponentSource compSource) {
        return getTokens().values().stream()
                .filter(model -> model.searchSource(compSource)) // searchSource not implemented
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

    // ####################
    // Units

    public static UnitModel getUnit(String unitID) {
        return units.get(unitID);
    }

    public static boolean isValidUnit(String unitID) {
        return units.containsKey(unitID);
    }

    public static List<String> getUnitsSources(ComponentSource compSource) {
        return units.values().stream()
                .filter(model -> model.searchSource(compSource))
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
        return units.values().stream().anyMatch(unit -> Objects.equals(asyncUnitID, unit.getAsyncId()));
    }

    public static Set<String> getUnitIDList() {
        return unitAsyncIds;
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

    public static List<String> getPlanetsSources(ComponentSource compSource) {
        return TileHelper.getAllPlanetModels().stream()
                .filter(model -> model.searchSource(compSource))
                .map(model -> model.getSource().toString())
                .toList();
    }

    // ####################
    // Tiles

    @Nullable
    public static String getTileRepresentation(String tileID) {
        TileModel tile = TileHelper.getTileById(tileID);
        return tile == null ? null : tile.getNameNullSafe();
    }

    public static String getTileID(String tileID) {
        if (TileHelper.getTileById(tileID) == null) {
            return null;
        }
        return TileHelper.getTileById(tileID).getImagePath();
    }

    public static List<String> getTilesSources(ComponentSource compSource) {
        return TileHelper.getAllTileModels().stream() // Collection<TileModel> -> Stream<>
                .filter(model -> model.searchSource(compSource))
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
                .map(TileModel::getId)
                .toList();
    }

    // Wormholes

    public static Set<Wormhole> getWormholes(String tileID) {
        Set<Wormhole> wormholes = EnumSet.noneOf(Wormhole.class);
        TileModel tile = tileID == null ? null : TileHelper.getTileById(tileID);
        if (tile != null && tile.getWormholes() != null) {
            tile.getWormholes().stream().filter(Objects::nonNull).forEach(wormholes::add);
        }
        return wormholes;
    }

    private static class ColorDeserializer extends ValueDeserializer<Color> {
        @Override
        public Color deserialize(JsonParser p, DeserializationContext c) {
            JsonNode n = p.readValueAsTree();

            // Handle "#RRGGBB" or "0xRRGGBB"
            if (n.isTextual()) {
                return Color.decode(n.asText());
            }

            // Handle ARGB integer
            if (n.isNumber()) {
                return new Color(n.asInt(), true);
            }

            // Handle { "red" : ###, "green": ###, "blue": ###, "alpha": ### }
            if (n.isObject() && n.has("red")) {
                int r = n.get("red").asInt();
                int g = n.get("green").asInt();
                int b = n.get("blue").asInt();
                int a = n.has("alpha") ? n.get("alpha").asInt() : 255;
                return new Color(r, g, b, a);
            }

            throw new RuntimeException("Unable to deserialize color: " + n);
        }
    }
}
