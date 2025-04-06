package ti4.map.manage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.draft.BagDraft;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.omegaPhase.PriorityTrackHelper;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.json.ObjectMapperFactory;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import static ti4.map.manage.GamePersistenceKeys.ENDGAMEINFO;
import static ti4.map.manage.GamePersistenceKeys.ENDMAPINFO;
import static ti4.map.manage.GamePersistenceKeys.ENDPLAYER;
import static ti4.map.manage.GamePersistenceKeys.ENDPLAYERINFO;
import static ti4.map.manage.GamePersistenceKeys.ENDTILE;
import static ti4.map.manage.GamePersistenceKeys.ENDTOKENS;
import static ti4.map.manage.GamePersistenceKeys.ENDUNITDAMAGE;
import static ti4.map.manage.GamePersistenceKeys.ENDUNITHOLDER;
import static ti4.map.manage.GamePersistenceKeys.ENDUNITS;
import static ti4.map.manage.GamePersistenceKeys.GAMEINFO;
import static ti4.map.manage.GamePersistenceKeys.MAPINFO;
import static ti4.map.manage.GamePersistenceKeys.PLANET_ENDTOKENS;
import static ti4.map.manage.GamePersistenceKeys.PLANET_TOKENS;
import static ti4.map.manage.GamePersistenceKeys.PLAYER;
import static ti4.map.manage.GamePersistenceKeys.PLAYERINFO;
import static ti4.map.manage.GamePersistenceKeys.TILE;
import static ti4.map.manage.GamePersistenceKeys.TOKENS;
import static ti4.map.manage.GamePersistenceKeys.UNITDAMAGE;
import static ti4.map.manage.GamePersistenceKeys.UNITHOLDER;
import static ti4.map.manage.GamePersistenceKeys.UNITS;
import ti4.message.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
class GameLoadService {

    private static final Pattern PEEKED_OBJECTIVE_PATTERN = Pattern.compile("(?>([a-z_]+):((?>\\d+,)+);)");

    public static List<ManagedGame> loadManagedGames() {
        try (Stream<Path> pathStream = Files.list(Storage.getGamesDirectory().toPath())) {
            return pathStream.parallel()
                .filter(path -> path.toString().toLowerCase().endsWith(".txt"))
                .map(path -> {
                    File file = path.toFile();
                    try {
                        Game game = readGame(file);
                        if (game == null || game.getName() == null) {
                            BotLogger.log("Could not load game. Game or game name is null: " + file.getName());
                            return null;
                        }
                        return new ManagedGame(game);
                    } catch (Exception e) {
                        BotLogger.log("Could not load game: " + file.getName(), e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
        } catch (IOException e) {
            BotLogger.log("Exception occurred while getting all game names.", e);
        }
        return Collections.emptyList();
    }

    @Nullable
    public static Game load(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            File gameFile = Storage.getGameFile(gameName + Constants.TXT);
            if (!gameFile.exists()) {
                return null;
            }
            return readGame(gameFile);
        });
    }

    @Nullable
    private static Game readGame(@NotNull File gameFile) {
        if (!gameFile.exists()) {
            BotLogger.log("Could not load map, map file does not exist: " + gameFile.getAbsolutePath());
            return null;
        }
        try {
            Game game = new Game();
            Iterator<String> gameFileLines = Files.readAllLines(gameFile.toPath(), Charset.defaultCharset()).listIterator();
            game.setOwnerID(gameFileLines.next());
            game.setOwnerName(gameFileLines.next());
            game.setName(gameFileLines.next());
            while (gameFileLines.hasNext()) {
                String data = gameFileLines.next();
                if (MAPINFO.equals(data)) {
                    continue;
                }
                if (ENDMAPINFO.equals(data)) {
                    break;
                }

                while (gameFileLines.hasNext()) {
                    data = gameFileLines.next();
                    if (GAMEINFO.equals(data)) {
                        continue;
                    }
                    if (ENDGAMEINFO.equals(data)) {
                        break;
                    }
                    try {
                        readGameInfo(game, data);
                    } catch (Exception e) {
                        BotLogger.log("Encountered fatal error loading game " + game.getName() + ". Load aborted.", e);
                        return null;
                    }
                }

                while (gameFileLines.hasNext()) {
                    String tmpData = gameFileLines.next();
                    if (PLAYERINFO.equals(tmpData)) {
                        continue;
                    }
                    if (ENDPLAYERINFO.equals(tmpData)) {
                        break;
                    }
                    Player player = null;
                    while (gameFileLines.hasNext()) {
                        data = tmpData != null ? tmpData : gameFileLines.next();
                        tmpData = null;
                        if (PLAYER.equals(data)) {
                            player = game.addPlayer(gameFileLines.next(), gameFileLines.next());
                            continue;
                        }
                        if (ENDPLAYER.equals(data)) {
                            break;
                        }
                        readPlayerInfo(player, data, game);
                    }
                }
            }
            Map<String, Tile> tileMap = getTileMap(gameFileLines, game, gameFile);
            if (tileMap == null) {
                BotLogger.log("Encountered fatal error loading game " + game.getName() + ". Load aborted.");
                return null;
            }
            game.setTileMap(tileMap);
            TransientGameInfoUpdater.update(game);
            return game;
        } catch (Exception e) {
            BotLogger.log("Data read error: " + gameFile.getName(), e);
            return null;
        }
    }

    private static Map<String, Tile> getTileMap(Iterator<String> gameFileLines, Game game, File gameFile) {
        Map<String, Tile> tileMap = new HashMap<>();
        try {
            while (gameFileLines.hasNext()) {
                String tileData = gameFileLines.next();
                if (TILE.equals(tileData)) {
                    continue;
                }
                if (ENDTILE.equals(tileData)) {
                    continue;
                }
                if (tileData.isEmpty()) {
                    continue;
                }
                Tile tile = readTile(tileData);
                if (tile != null) {
                    tileMap.put(tile.getPosition(), tile);
                } else {
                    BotLogger.log("Error loading Map: `" + game.getName() + "` -> Tile is null: `" + tileData + "` - tile will be skipped - check save file");
                }

                while (gameFileLines.hasNext()) {
                    String tmpData = gameFileLines.next();
                    if (UNITHOLDER.equals(tmpData)) {
                        continue;
                    }
                    if (ENDUNITHOLDER.equals(tmpData)) {
                        break;
                    }
                    String unitHolderName = null;
                    while (gameFileLines.hasNext()) {
                        String data = tmpData != null ? tmpData : gameFileLines.next();
                        tmpData = null;
                        if (UNITS.equals(data)) {
                            unitHolderName = gameFileLines.next().toLowerCase();
                            if (tile != null) {
                                if (Constants.MIRAGE.equals(unitHolderName)) {
                                    Helper.addMirageToTile(tile);
                                } else if (!tile.isSpaceHolderValid(unitHolderName)) {
                                    BotLogger.log(game.getName() + ": Not valid unitholder detected: " + unitHolderName);
                                }
                            }
                            continue;
                        }
                        if (ENDUNITS.equals(data)) {
                            break;
                        }
                        readUnit(tile, data, unitHolderName);
                    }

                    while (gameFileLines.hasNext()) {
                        String data = gameFileLines.next();
                        if (UNITDAMAGE.equals(data)) {
                            continue;
                        }
                        if (ENDUNITDAMAGE.equals(data)) {
                            break;
                        }
                        readUnitDamage(tile, data, unitHolderName);
                    }

                    while (gameFileLines.hasNext()) {
                        String data = gameFileLines.next();
                        if (PLANET_TOKENS.equals(data)) {
                            continue;
                        }
                        if (PLANET_ENDTOKENS.equals(data)) {
                            break;
                        }
                        readPlanetTokens(tile, data, unitHolderName);
                    }
                }

                while (gameFileLines.hasNext()) {
                    String data = gameFileLines.next();
                    if (TOKENS.equals(data)) {
                        continue;
                    }
                    if (ENDTOKENS.equals(data)) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("Data read error: " + gameFile.getName(), e);
            return null;
        }
        return tileMap;
    }

    private static void readGameInfo(Game game, String data) {
        String[] tokenizer = data.split(" ", 2);
        if (tokenizer.length == 2) {
            String identification = tokenizer[0];
            String info = tokenizer[1];
            switch (identification) {
                case Constants.LATEST_COMMAND -> game.setLatestCommand(info);
                case Constants.LATEST_OUTCOME_VOTED_FOR -> game.setLatestOutcomeVotedFor(info);
                case Constants.PHASE_OF_GAME -> game.setPhaseOfGame(info);
                case Constants.SO -> game.setSecretObjectives(getCardList(info));
                case Constants.AC -> game.setActionCards(getCardList(info));
                case Constants.PO1 -> game.setPublicObjectives1(getCardList(info));
                case Constants.PO2 -> game.setPublicObjectives2(getCardList(info));
                case Constants.PO1PEAKABLE -> game.setPublicObjectives1Peakable(getCardList(info));
                case Constants.SAVED_BUTTONS -> game.setSavedButtons(getCardList(info));
                case Constants.PO2PEAKABLE -> game.setPublicObjectives2Peakable(getCardList(info));
                case Constants.PO1PEEKED -> game.setPublicObjectives1Peeked(loadPeekedPublicObjectives(info));
                case Constants.PO2PEEKED -> game.setPublicObjectives2Peeked(loadPeekedPublicObjectives(info));
                case Constants.SO_TO_PO -> game.setSoToPoList(getCardList(info));
                case Constants.PURGED_PN -> game.setPurgedPNs(getCardList(info));
                case Constants.REVEALED_PO -> game.setRevealedPublicObjectives(getParsedCards(info));
                case Constants.CUSTOM_PO_VP -> game.setCustomPublicVP(getParsedCards(info));
                case Constants.SCORED_PO -> game.setScoredPublicObjectives(getParsedCardsForScoredPO(info));
                case Constants.AC_DECK_ID -> game.setAcDeckID(info);
                case Constants.SO_DECK_ID -> game.setSoDeckID(info);
                case Constants.STAGE_1_PUBLIC_DECK_ID -> game.setStage1PublicDeckID(info);
                case Constants.STAGE_2_PUBLIC_DECK_ID -> game.setStage2PublicDeckID(info);
                case Constants.MAP_TEMPLATE -> game.setMapTemplateID(info);
                case Constants.TECH_DECK_ID -> game.setTechnologyDeckID(info);
                case Constants.PINGED_SYSTEMS -> game.setListOfTilesPinged(getCardList(info));
                case Constants.RELIC_DECK_ID -> game.setRelicDeckID(info);
                case Constants.AGENDA_DECK_ID -> game.setAgendaDeckID(info);
                case Constants.EVENT_DECK_ID -> game.setEventDeckID(info);
                case Constants.EXPLORATION_DECK_ID -> game.setExplorationDeckID(info);
                case Constants.STRATEGY_CARD_SET -> {
                    if (Mapper.isValidStrategyCardSet(info)) {
                        game.setScSetID(info);
                    } else {
                        game.setScSetID("pok");
                    }
                }
                case Constants.CUSTOM_ADJACENT_TILES -> {
                    Map<String, List<String>> adjacentTiles = getParsedCardsForScoredPO(info);
                    Map<String, List<String>> adjacentTilesMigrated = new LinkedHashMap<>();
                    for (Map.Entry<String, List<String>> entry : adjacentTiles.entrySet()) {
                        String key = entry.getKey();
                        List<String> migrated = new ArrayList<>(entry.getValue());
                        adjacentTilesMigrated.put(key, migrated);
                    }

                    game.setCustomAdjacentTiles(adjacentTilesMigrated);
                }
                case Constants.BORDER_ANOMALIES -> {
                    if ("[]".equals(info))
                        break;
                    ObjectMapper mapper = ObjectMapperFactory.build();
                    try {
                        JavaType reference = mapper.getTypeFactory().constructParametricType(List.class, BorderAnomalyHolder.class);
                        game.setBorderAnomalies(mapper.readValue(info, reference));
                    } catch (Exception e) {
                        BotLogger.log("Error reading border anomalies from save file!", e);
                    }
                }
                case Constants.ADJACENCY_OVERRIDES -> {
                    try {
                        game.setAdjacentTileOverride(getParsedAdjacencyOverrides(info));
                    } catch (Exception e) {
                        BotLogger.log("Failed to load adjacency overrides", e);
                    }
                }
                case Constants.REVERSE_SPEAKER_ORDER -> game.setReverseSpeakerOrder("true".equals(info));
                case Constants.AGENDAS -> game.setAgendas(getCardList(info));
                case Constants.AC_DISCARDED -> game.setDiscardActionCards(getParsedCards(info));
                case Constants.AC_PURGED -> game.setPurgedActionCards(getParsedCards(info));
                case Constants.DISCARDED_AGENDAS -> game.setDiscardAgendas(getParsedCards(info));
                case Constants.SENT_AGENDAS -> game.setSentAgendas(getParsedCards(info));
                case Constants.LAW -> game.setLaws(getParsedCards(info));
                case Constants.EVENTS -> game.setEvents(getCardList(info));
                case Constants.EVENTS_IN_EFFECT -> game.setEventsInEffect(getParsedCards(info));
                case Constants.DISCARDED_EVENTS -> game.setDiscardedEvents(getParsedCards(info));
                case Constants.EXPLORE -> game.setExploreDeck(getCardList(info));
                case Constants.RELICS -> game.setRelics(getCardList(info));
                case Constants.DISCARDED_EXPLORES -> game.setExploreDiscard(getCardList(info));
                case Constants.LAW_INFO -> {
                    StringTokenizer actionCardToken = new StringTokenizer(info, ";");
                    Map<String, String> cards = new LinkedHashMap<>();
                    while (actionCardToken.hasMoreTokens()) {
                        StringTokenizer cardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
                        String id = cardInfo.nextToken();
                        String value = cardInfo.nextToken();
                        cards.put(id, value);
                    }
                    game.setLawsInfo(cards);
                }
                case Constants.SC_TRADE_GOODS -> {
                    StringTokenizer scTokenizer = new StringTokenizer(info, ";");
                    Map<Integer, Integer> scTradeGoods = new LinkedHashMap<>();
                    while (scTokenizer.hasMoreTokens()) {
                        StringTokenizer cardInfo = new StringTokenizer(scTokenizer.nextToken(), ",");
                        Integer id = Integer.parseInt(cardInfo.nextToken());
                        Integer value = Integer.parseInt(cardInfo.nextToken());
                        scTradeGoods.put(id, value);
                    }
                    game.setScTradeGoods(scTradeGoods);
                }
                case Constants.SPEAKER -> game.setSpeakerUserID(info);
                case Constants.ACTIVE_PLAYER -> game.setActivePlayerID(info);
                case Constants.ACTIVE_SYSTEM -> game.setActiveSystem(info);
                case Constants.AUTO_PING -> {
                    try {
                        int pnghrs = Integer.parseInt(info);
                        game.setAutoPing(pnghrs != 0);
                        game.setAutoPingSpacer(pnghrs);
                    } catch (Exception e) {}
                }
                case Constants.CURRENT_AGENDA_INFO -> {
                    try {
                        game.setCurrentAgendaInfo(info);
                    } catch (Exception e) {}
                }
                case Constants.CURRENT_ACDRAWSTATUS_INFO -> {
                    try {
                        game.setCurrentACDrawStatusInfo(info);
                    } catch (Exception e) {}
                }

                case Constants.LAST_ACTIVE_PLAYER_CHANGE -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastChange = new Date(millis);
                        game.setLastActivePlayerChange(lastChange);
                    } catch (Exception e) {}
                }
                case Constants.PLAYER_COUNT_FOR_MAP -> {
                    try {
                        int playerCount = Integer.parseInt(info);
                        if (playerCount >= 1 && playerCount <= 30) {
                            game.setPlayerCountForMap(playerCount);
                        } else {
                            game.setPlayerCountForMap(6);
                        }
                    } catch (Exception e) {
                        game.setPlayerCountForMap(6);
                    }
                }
                case Constants.SC_COUNT_FOR_MAP -> {
                    try {
                        int scCount = Integer.parseInt(info);
                        if (scCount >= 1 && scCount <= 8) {
                            game.setStrategyCardsPerPlayer(scCount);
                        } else {
                            game.setStrategyCardsPerPlayer(1);
                        }
                    } catch (Exception e) {
                        game.setStrategyCardsPerPlayer(1);
                    }
                }
                case Constants.ACTIVATION_COUNT -> {
                    try {
                        int activationCount = Integer.parseInt(info);
                        game.setActivationCount(activationCount);
                    } catch (Exception e) {
                        game.setActivationCount(0);
                    }
                }
                case Constants.VP_COUNT -> {
                    try {
                        int vpCount = Integer.parseInt(info);
                        game.setVp(vpCount);
                    } catch (Exception e) {
                        game.setVp(10);
                    }
                }
                case Constants.MAX_SO_COUNT -> {
                    try {
                        int soCount = Integer.parseInt(info);
                        game.setMaxSOCountPerPlayer(soCount);
                    } catch (Exception e) {
                        game.setVp(3);
                    }
                }
                case Constants.DISPLAY_TYPE -> {
                    if (info.equals(DisplayType.stats.getValue())) {
                        game.setDisplayTypeForced(DisplayType.stats);
                    } else if (info.equals(DisplayType.map.getValue())) {
                        game.setDisplayTypeForced(DisplayType.map);
                    } else if (info.equals(DisplayType.all.getValue())) {
                        game.setDisplayTypeForced(DisplayType.all);
                    }
                }
                case Constants.SC_PLAYED -> {
                    StringTokenizer scPlayed = new StringTokenizer(info, ";");
                    while (scPlayed.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(scPlayed.nextToken(), ",");
                        Integer scID = Integer.parseInt(dataInfo.nextToken());
                        Boolean status = Boolean.parseBoolean(dataInfo.nextToken());
                        game.setSCPlayed(scID, status);
                    }
                }
                case Constants.AGENDA_VOTE_INFO -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setCurrentAgendaVote(outcome, voteInfo);
                        }
                    }
                }
                case Constants.CHECK_REACTS_INFO -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setStoredValue(outcome, voteInfo);
                        }
                    }
                }
                case Constants.DISPLACED_UNITS_SYSTEM -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setSpecificCurrentMovedUnitsFrom1System(outcome, Integer.parseInt(voteInfo));
                        }
                    }
                }
                case Constants.THALNOS_UNITS -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setSpecificThalnosUnit(outcome, Integer.parseInt(voteInfo));
                        }
                    }
                }
                case Constants.SLASH_COMMAND_STRING -> {
                    StringTokenizer commandCounts = new StringTokenizer(info, ":");
                    while (commandCounts.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(commandCounts.nextToken(), ",");
                        String commandName = null;
                        String commandCount;
                        if (dataInfo.hasMoreTokens()) {
                            commandName = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            commandCount = dataInfo.nextToken();
                            game.setSpecificSlashCommandCount(commandName, Integer.parseInt(commandCount));
                        }
                    }
                }
                case Constants.ACS_SABOD -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setSpecificActionCardSaboCount(outcome, Integer.parseInt(voteInfo));
                        }
                    }
                }
                case Constants.DISPLACED_UNITS_ACTIVATION -> {
                    StringTokenizer vote_info = new StringTokenizer(info, ":");
                    while (vote_info.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(vote_info.nextToken(), ",");
                        String outcome = null;
                        String voteInfo;
                        if (dataInfo.hasMoreTokens()) {
                            outcome = dataInfo.nextToken();
                        }
                        if (dataInfo.hasMoreTokens()) {
                            voteInfo = dataInfo.nextToken();
                            game.setSpecificCurrentMovedUnitsFrom1TacticalAction(outcome,
                                Integer.parseInt(voteInfo));
                        }
                    }
                }
                case Constants.FOW_OPTIONS -> {
                    StringTokenizer fowOptions = new StringTokenizer(info, ";");
                    while (fowOptions.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(fowOptions.nextToken(), ",");
                        String optionName = dataInfo.nextToken();
                        String optionValue = dataInfo.nextToken();
                        game.setFowOption(FOWOption.fromString(optionName), Boolean.parseBoolean(optionValue));
                    }
                }
                case Constants.GAME_CUSTOM_NAME -> game.setCustomName(info);
                case Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_AFTER -> game.setPlayersWhoHitPersistentNoAfter(info);
                case Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_WHEN -> game.setPlayersWhoHitPersistentNoWhen(info);
                case Constants.TABLE_TALK_CHANNEL -> game.setTableTalkChannelID(info);
                case Constants.MAIN_GAME_CHANNEL -> game.setMainChannelID(info);
                case Constants.SAVED_CHANNEL -> game.setSavedChannelID(info);
                case Constants.SAVED_MESSAGE -> game.setSavedMessage(info);
                case Constants.BOT_MAP_CHANNEL -> game.setBotMapUpdatesThreadID(info);
                case Constants.GAME_LAUNCH_THREAD_ID -> game.setLaunchPostThreadID(info);

                // GAME MODES / SETTINGS
                case Constants.TIGL_GAME -> game.setCompetitiveTIGLGame(loadBooleanOrDefault(info, false));
                case Constants.HACK_ELECTION_STATUS -> game.setHasHackElectionBeenPlayed(loadBooleanOrDefault(info, false));
                case Constants.CC_N_PLASTIC_LIMIT -> game.setCcNPlasticLimit(loadBooleanOrDefault(info, false));
                case Constants.BOT_FACTION_REACTS -> game.setBotFactionReacts(loadBooleanOrDefault(info, false));
                case Constants.HAS_HAD_A_STATUS_PHASE -> game.setHasHadAStatusPhase(loadBooleanOrDefault(info, false));
                case Constants.BOT_SHUSHING -> game.setBotShushing(loadBooleanOrDefault(info, false));
                case Constants.COMMUNITY_MODE -> game.setCommunityMode(loadBooleanOrDefault(info, false));
                case Constants.ALLIANCE_MODE -> game.setAllianceMode(loadBooleanOrDefault(info, false));
                case Constants.FOW_MODE -> game.setFowMode(loadBooleanOrDefault(info, false));
                case Constants.NAALU_AGENT -> game.setNaaluAgent(loadBooleanOrDefault(info, false));
                case Constants.L1_HERO -> game.setL1Hero(loadBooleanOrDefault(info, false));
                case Constants.NOMAD_COIN -> game.setNomadCoin(loadBooleanOrDefault(info, false));
                case Constants.FAST_SC_FOLLOW -> game.setFastSCFollowMode(loadBooleanOrDefault(info, false));
                case Constants.QUEUE_SO -> game.setQueueSO(loadBooleanOrDefault(info, false));
                case Constants.SHOW_BUBBLES -> game.setShowBubbles(loadBooleanOrDefault(info, false));
                case Constants.TRANSACTION_METHOD -> game.setNewTransactionMethod(loadBooleanOrDefault(info, false));
                case Constants.SHOW_GEARS -> game.setShowGears(loadBooleanOrDefault(info, false));
                case Constants.SHOW_BANNERS -> game.setShowBanners(loadBooleanOrDefault(info, false));
                case Constants.SHOW_HEX_BORDERS -> game.setHexBorderStyle(info);
                case Constants.HOMEBREW_MODE -> game.setHomebrew(loadBooleanOrDefault(info, false));

                // OTHER GAME STATE
                case Constants.PURGED_FRAGMENTS -> {
                    try {
                        int value = Integer.parseInt(info);
                        game.setNumberOfPurgedFragments(value);
                    } catch (Exception e) {}
                }
                case Constants.TEMPORARY_PING_DISABLE -> game.setTemporaryPingDisable(loadBooleanOrDefault(info, false));
                case Constants.DOMINUS_ORB -> game.setDominusOrb(loadBooleanOrDefault(info, false));
                case Constants.COMPONENT_ACTION -> game.setComponentAction(loadBooleanOrDefault(info, false));
                case Constants.JUST_PLAYED_COMPONENT_AC -> game.setJustPlayedComponentAC(loadBooleanOrDefault(info, false));
                case Constants.BASE_GAME_MODE -> game.setBaseGameMode(loadBooleanOrDefault(info, false));
                case Constants.LIGHT_FOG_MODE -> game.setLightFogMode(loadBooleanOrDefault(info, false));
                case Constants.CPTI_EXPLORE_MODE -> game.setCptiExploreMode(loadBooleanOrDefault(info, false));
                case Constants.RED_TAPE_MODE -> game.setRedTapeMode(loadBooleanOrDefault(info, false));
                case Constants.HOMEBREW_SC_MODE -> game.setHomebrewSCMode(loadBooleanOrDefault(info, false));
                case Constants.INJECT_RULES_LINKS -> game.setInjectRulesLinks(loadBooleanOrDefault(info, false));
                case Constants.SPIN_MODE -> {
                    try {
                        String value = "false".equalsIgnoreCase(info) ? "OFF" : info;
                        game.setSpinMode(value);
                    } catch (Exception e) {}
                }
                case Constants.SHOW_UNIT_TAGS -> game.setShowUnitTags(loadBooleanOrDefault(info, false));
                case Constants.SHOW_OWNED_PNS_IN_PLAYER_AREA -> game.setShowOwnedPNsInPlayerArea(loadBooleanOrDefault(info, false));
                case Constants.STRAT_PINGS -> game.setStratPings(loadBooleanOrDefault(info, false));
                case Constants.TEXT_SIZE -> {
                    try {
                        game.setTextSize(info);
                    } catch (Exception e) {}
                }
                case Constants.ABSOL_MODE -> game.setAbsolMode(loadBooleanOrDefault(info, false));
                case Constants.PROMISES_PROMISES -> game.setPromisesPromisesMode(loadBooleanOrDefault(info, false));
                case Constants.FLAGSHIPPING -> game.setFlagshippingMode(loadBooleanOrDefault(info, false));
                case Constants.MILTYMOD_MODE -> game.setMiltyModMode(loadBooleanOrDefault(info, false));
                case Constants.SHOW_MAP_SETUP -> game.setShowMapSetup(loadBooleanOrDefault(info, false));
                case Constants.DISCORDANT_STARS_MODE -> game.setDiscordantStarsMode(loadBooleanOrDefault(info, false));
                case Constants.UNCHARTED_SPACE_STUFF -> game.setUnchartedSpaceStuff(loadBooleanOrDefault(info, false));
                case Constants.VERBOSITY -> {
                    try {
                        game.setOutputVerbosity(info);
                    } catch (Exception e) {}
                }
                case Constants.BETA_TEST_MODE -> game.setTestBetaFeaturesMode(loadBooleanOrDefault(info, false));
                case Constants.AGE_OF_EXPLORATION_MODE -> game.setAgeOfExplorationMode(loadBooleanOrDefault(info, false));
                case Constants.MINOR_FACTIONS_MODE -> game.setMinorFactionsMode(loadBooleanOrDefault(info, false));
                case Constants.SHOW_FULL_COMPONENT_TEXT -> game.setShowFullComponentTextEmbeds(loadBooleanOrDefault(info, false));
                case Constants.GAME_HAS_ENDED -> game.setHasEnded(loadBooleanOrDefault(info, false));
                case Constants.CREATION_DATE -> game.setCreationDate(info);
                case Constants.ROUND -> {
                    try {
                        game.setRound(Integer.parseInt(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse round number", exception);
                    }
                }
                case Constants.BUTTON_PRESS_COUNT -> {
                    try {
                        game.setButtonPressCount(Integer.parseInt(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse button press count", exception);
                    }
                }
                case Constants.STARTED_DATE -> {
                    try {
                        game.setStartedDate(Long.parseLong(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse started date", exception);
                    }
                }
                case Constants.LAST_MODIFIED_DATE -> {
                    try {
                        game.setLastModifiedDate(Long.parseLong(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse last modified date", exception);
                    }
                }
                case Constants.ENDED_DATE -> {
                    try {
                        game.setEndedDate(Long.parseLong(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse ended date", exception);
                    }
                }
                case Constants.IMAGE_GEN_COUNT -> {
                    try {
                        int count = Integer.parseInt(info);
                        game.setMapImageGenerationCount(count);
                    } catch (Exception e) {}
                }
                case Constants.FOW_GM_IDS -> game.setFogOfWarGMIDs(Helper.getListFromCSV(info));
                case Constants.RUN_DATA_MIGRATIONS -> {
                    StringTokenizer migrationInfo = new StringTokenizer(info, ",");

                    while (migrationInfo.hasMoreTokens()) {
                        String migration = migrationInfo.nextToken();
                        game.addMigration(migration);
                    }
                }
                case Constants.BAG_DRAFT -> {
                    try {
                        game.setBagDraft(BagDraft.GenerateDraft(info, game));
                    } catch (Exception e) {}
                }
                case Constants.MILTY_DRAFT_MANAGER -> game.setMiltyDraftString(info); // We will parse this later
                case Constants.MILTY_DRAFT_SETTINGS -> game.setMiltyJson(info); // We will parse this later
                case Constants.GAME_TAGS -> game.setTags(getCardList(info));
                case Constants.TIGL_RANK -> {
                    TIGLHelper.TIGLRank rank = TIGLHelper.TIGLRank.fromString(info);
                    game.setMinimumTIGLRankAtGameStart(rank);
                }
            }
        }
    }

    private static boolean loadBooleanOrDefault(String info, boolean def) {
        try {
            return Boolean.parseBoolean(info);
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> getCardList(String tokenizer) {
        StringTokenizer cards = new StringTokenizer(tokenizer, ",");
        List<String> cardList = new ArrayList<>();
        while (cards.hasMoreTokens()) {
            cardList.add(cards.nextToken());
        }
        return cardList;
    }

    private static Map<String, Integer> getParsedCards(String tokenizer) {
        StringTokenizer actionCardToken = new StringTokenizer(tokenizer, ";");
        Map<String, Integer> cards = new LinkedHashMap<>();
        while (actionCardToken.hasMoreTokens()) {
            StringTokenizer cardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
            String id = cardInfo.nextToken();
            Integer index = Integer.parseInt(cardInfo.nextToken());
            cards.put(id, index);
        }
        return cards;
    }

    private static Map<String, List<String>> getParsedCardsForScoredPO(String tokenizer) {
        StringTokenizer po = new StringTokenizer(tokenizer, ";");
        Map<String, List<String>> scoredPOs = new LinkedHashMap<>();
        while (po.hasMoreTokens()) {
            StringTokenizer poInfo = new StringTokenizer(po.nextToken(), ",");
            String id = poInfo.nextToken();

            if (poInfo.hasMoreTokens()) {
                StringTokenizer userIDs = new StringTokenizer(poInfo.nextToken(), "-");
                List<String> userIDList = new ArrayList<>();
                while (userIDs.hasMoreTokens()) {
                    userIDList.add(userIDs.nextToken());
                }
                scoredPOs.put(id, userIDList);
            }
        }
        return scoredPOs;
    }

    private static Map<Pair<String, Integer>, String> getParsedAdjacencyOverrides(String tokenizer) {
        StringTokenizer override = new StringTokenizer(tokenizer, ";");
        Map<Pair<String, Integer>, String> overrides = new LinkedHashMap<>();
        while (override.hasMoreTokens()) {
            String[] overrideInfo = override.nextToken().split("-");
            String primaryTile = overrideInfo[0];
            String direction = overrideInfo[1];
            String secondaryTile = overrideInfo[2];

            Pair<String, Integer> primary = new ImmutablePair<>(primaryTile, Integer.parseInt(direction));
            overrides.put(primary, secondaryTile);
        }
        return overrides;
    }

    private static void readPlayerInfo(Player player, String data, Game game) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        if (tokenizer.countTokens() == 2) {
            data = tokenizer.nextToken();
            switch (data) {
                case Constants.FACTION -> player.setFaction(game, tokenizer.nextToken());
                case Constants.FACTION_EMOJI -> player.setFactionEmoji(tokenizer.nextToken());
                case Constants.FACTION_DISPLAY_NAME -> player.setDisplayName(tokenizer.nextToken().replace("_", " "));
                case Constants.COLOR -> player.setColor(tokenizer.nextToken());
                case Constants.DECAL_SET -> player.setDecalSet(tokenizer.nextToken());
                case Constants.STATS_ANCHOR_LOCATION -> player.setPlayerStatsAnchorPosition(tokenizer.nextToken());
                case Constants.HS_TILE_POSITION -> player.setHomeSystemPosition(tokenizer.nextToken());
                case Constants.ALLIANCE_MEMBERS -> player.setAllianceMembers(tokenizer.nextToken());
                case Constants.ROLE_FOR_COMMUNITY -> player.setRoleIDForCommunity(tokenizer.nextToken());
                case Constants.PLAYER_PRIVATE_CHANNEL -> player.setPrivateChannelID(tokenizer.nextToken());
                case Constants.NOTEPAD -> player.setNotes(tokenizer.nextToken());
                case Constants.TACTICAL -> player.setTacticalCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.FLEET -> player.setFleetCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.STRATEGY -> player.setStrategicCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TG -> player.setTg(Integer.parseInt(tokenizer.nextToken()));
                case Constants.ACTUAL_HITS -> player.setActualHits(Integer.parseInt(tokenizer.nextToken()));
                case Constants.EXPECTED_HITS_TIMES_10 -> player.setExpectedHitsTimes10(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TOTAL_EXPENSES -> player.setTotalExpenses(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TURN_COUNT -> player.setInRoundTurnCount(Integer.parseInt(tokenizer.nextToken()));
                case Constants.DEBT -> {
                    StringTokenizer debtToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    Map<String, Integer> debtTokens = new LinkedHashMap<>();
                    while (debtToken.hasMoreTokens()) {
                        StringTokenizer debtInfo = new StringTokenizer(debtToken.nextToken(), ",");
                        String color = debtInfo.nextToken();
                        Integer count = Integer.parseInt(debtInfo.nextToken());
                        debtTokens.put(color, count);
                    }
                    player.setDebtTokens(debtTokens);
                }
                case Constants.STRATEGY_CARD -> player.setSCs(new LinkedHashSet<>(getCardList(tokenizer.nextToken()).stream().map(Integer::valueOf).collect(Collectors.toSet())));
                case Constants.FOLLOWED_SC -> player.setFollowedSCs(new HashSet<>(getCardList(tokenizer.nextToken()).stream().map(Integer::valueOf).collect(Collectors.toSet())));
                case Constants.COMMODITIES_TOTAL -> player.setCommoditiesTotal(Integer.parseInt(tokenizer.nextToken()));
                case Constants.COMMODITIES -> player.setCommodities(Integer.parseInt(tokenizer.nextToken()));
                case Constants.STASIS_INFANTRY -> player.setStasisInfantry(Integer.parseInt(tokenizer.nextToken()));
                case Constants.AUTO_SABO_PASS_MEDIAN -> player.setAutoSaboPassMedian(Integer.parseInt(tokenizer.nextToken()));
                case Constants.CAPTURE -> {
                    UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                    StringTokenizer unitTokens = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (unitTokens.hasMoreTokens()) {
                        StringTokenizer unitInfo = new StringTokenizer(unitTokens.nextToken(), ",");
                        String id = unitInfo.nextToken();
                        Units.UnitKey unitKey = Units.parseID(id);
                        Integer number = Integer.parseInt(unitInfo.nextToken());
                        if (unitKey != null)
                            unitHolder.addUnit(unitKey, number);
                    }
                }
                case Constants.AC -> {
                    StringTokenizer actionCardToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (actionCardToken.hasMoreTokens()) {
                        StringTokenizer actionCardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
                        String id = actionCardInfo.nextToken();
                        Integer index = Integer.parseInt(actionCardInfo.nextToken());
                        player.setActionCard(id, index);
                    }
                }
                case Constants.EVENTS -> {
                    StringTokenizer eventToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (eventToken.hasMoreTokens()) {
                        StringTokenizer eventInfo = new StringTokenizer(eventToken.nextToken(), ",");
                        String id = eventInfo.nextToken();
                        Integer index = Integer.parseInt(eventInfo.nextToken());
                        player.setEvent(id, index);
                    }
                }
                case Constants.LIZHO_TRAP_CARDS -> {
                    StringTokenizer trapCardToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (trapCardToken.hasMoreTokens()) {
                        StringTokenizer trapCardInfo = new StringTokenizer(trapCardToken.nextToken(), ",");
                        String id = trapCardInfo.nextToken();
                        Integer index = Integer.parseInt(trapCardInfo.nextToken());
                        player.setTrapCard(id, index);
                    }
                }
                case Constants.LIZHO_TRAP_PLANETS -> {
                    StringTokenizer trapCardToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (trapCardToken.hasMoreTokens()) {
                        StringTokenizer trapCardInfo = new StringTokenizer(trapCardToken.nextToken(), ",");
                        String id = trapCardInfo.nextToken();
                        String planet = trapCardInfo.nextToken();
                        player.setTrapCardPlanet(id, planet);
                    }
                }
                case Constants.PROMISSORY_NOTES -> {
                    StringTokenizer pnToken = new StringTokenizer(tokenizer.nextToken(), ";");
                    player.clearPromissoryNotes();
                    while (pnToken.hasMoreTokens()) {
                        StringTokenizer pnInfo = new StringTokenizer(pnToken.nextToken(), ",");
                        String id = pnInfo.nextToken();
                        // MIGRATE ABSOL'S PS
                        if (game.isAbsolMode() && id.endsWith("_ps") && !id.startsWith("absol_"))
                            id = "absol_" + id;
                        // END MIGRATE
                        Integer index = Integer.parseInt(pnInfo.nextToken());
                        player.setPromissoryNote(id, index);
                    }
                }
                case Constants.PROMISSORY_NOTES_OWNED -> player.setPromissoryNotesOwned(new HashSet<>(Helper.getSetFromCSV(tokenizer.nextToken())));
                case Constants.PROMISSORY_NOTES_PLAY_AREA -> player.setPromissoryNotesInPlayArea(getCardList(tokenizer.nextToken()));
                case Constants.UNITS_OWNED -> player.setUnitsOwned(new HashSet<>(Helper.getSetFromCSV(tokenizer.nextToken())));
                case Constants.PLANETS -> player.setPlanets(getCardList(tokenizer.nextToken().replace("exhausted", "").replace("refreshed", "").replace("blaheo", "biaheo")));
                case Constants.PLANETS_EXHAUSTED -> player.setExhaustedPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_ABILITY_EXHAUSTED -> player.setExhaustedPlanetsAbilities(getCardList(tokenizer.nextToken()));
                case Constants.TECH -> player.setTechs(getCardList(tokenizer.nextToken()));
                case Constants.SPENT_THINGS -> player.setSpentThingsThisWindow(getCardList(tokenizer.nextToken()));
                case Constants.BOMBARD_UNITS -> player.setBombardUnits(getCardList(tokenizer.nextToken()));
                case Constants.TRANSACTION_ITEMS -> player.setTransactionItems(getCardList(tokenizer.nextToken()));
                case Constants.TEAMMATE_IDS -> player.setTeamMateIDs(getCardList(tokenizer.nextToken()));
                case Constants.FACTION_TECH -> player.setFactionTechs(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_BAG -> player.loadCurrentDraftBag(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_QUEUE -> player.loadItemsToDraft(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_HAND -> player.loadDraftHand(getCardList(tokenizer.nextToken()));
                case Constants.ABILITIES -> player.setAbilities(new HashSet<>(getCardList(tokenizer.nextToken())));
                case Constants.TECH_EXHAUSTED -> player.setExhaustedTechs(getCardList(tokenizer.nextToken()));
                case Constants.TECH_PURGED -> player.setPurgedTechs(getCardList(tokenizer.nextToken()));
                case Constants.RELICS -> player.setRelics(getCardList(tokenizer.nextToken()));
                case Constants.EXHAUSTED_RELICS -> player.setExhaustedRelics(getCardList(tokenizer.nextToken()));
                case Constants.MAHACT_CC -> player.setMahactCC(getCardList(tokenizer.nextToken()));
                case Constants.LEADERS -> {
                    String nextToken = tokenizer.nextToken();
                    if ("none".equals(nextToken)) {
                        player.setLeaders(new ArrayList<>());
                        break;
                    }
                    StringTokenizer leaderInfos = new StringTokenizer(nextToken, ";");
                    try {
                        List<Leader> leaderList = new ArrayList<>();
                        while (leaderInfos.hasMoreTokens()) {
                            String[] split = leaderInfos.nextToken().split(",");
                            Leader leader = new Leader(split[0]);
                            // leader.setType(Integer.parseInt(split[1])); // type is set in constructor
                            // based on ID
                            leader.setTgCount(Integer.parseInt(split[2]));
                            leader.setExhausted(Boolean.parseBoolean(split[3]));
                            leader.setLocked(Boolean.parseBoolean(split[4]));
                            if (split.length == 6) {
                                leader.setActive(Boolean.parseBoolean(split[5]));
                            }
                            leaderList.add(leader);
                        }
                        player.setLeaders(leaderList);
                    } catch (Exception e) {
                        BotLogger.log("Could not parse leaders loading map", e);
                    }
                }
                case Constants.FOW_SYSTEMS -> {
                    try {
                        StringTokenizer fow_systems = new StringTokenizer(tokenizer.nextToken(), ";");
                        while (fow_systems.hasMoreTokens()) {
                            String[] system = fow_systems.nextToken().split(",");
                            String position = system[0];
                            String tileID = system[1];
                            String label = system[2];
                            if (label != null)
                                label = label.replaceAll("—", " "); // replace em dash with spaces
                            player.addFogTile(tileID, position, label);
                        }
                    } catch (Exception e) {
                        BotLogger.log("Could not parse fog of war systems for player when loading the map: "
                            + player.getColor(), e);
                    }
                }
                case Constants.SO_SCORED -> {
                    StringTokenizer secrets = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (secrets.hasMoreTokens()) {
                        StringTokenizer secretInfo = new StringTokenizer(secrets.nextToken(), ",");
                        String id = secretInfo.nextToken();
                        Integer index = Integer.parseInt(secretInfo.nextToken());
                        player.setSecretScored(id, index);
                    }
                }
                case Constants.SO -> {
                    StringTokenizer secrets = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (secrets.hasMoreTokens()) {
                        StringTokenizer secretInfo = new StringTokenizer(secrets.nextToken(), ",");
                        String id = secretInfo.nextToken();
                        Integer index = Integer.parseInt(secretInfo.nextToken());
                        player.setSecret(id, index);
                    }
                }
                case Constants.PRODUCED_UNITS -> {
                    StringTokenizer secrets = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (secrets.hasMoreTokens()) {
                        StringTokenizer secretInfo = new StringTokenizer(secrets.nextToken(), ",");
                        String id = secretInfo.nextToken();
                        int amount = Integer.parseInt(secretInfo.nextToken());
                        player.setProducedUnit(id, amount);
                    }
                }
                case Constants.UNIT_CAP -> {
                    StringTokenizer unitcaps = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (unitcaps.hasMoreTokens()) {
                        StringTokenizer unitcap = new StringTokenizer(unitcaps.nextToken(), ",");
                        String id = unitcap.nextToken();
                        int cap = Integer.parseInt(unitcap.nextToken());
                        player.setUnitCap(id, cap);
                    }
                }

                case Constants.FRAGMENTS -> {
                    StringTokenizer fragments = new StringTokenizer(tokenizer.nextToken(), ",");
                    while (fragments.hasMoreTokens()) {
                        player.addFragment(fragments.nextToken());
                    }
                }

                case Constants.NUMBER_OF_TURNS -> player.setNumberOfTurns(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TOTAL_TURN_TIME -> player.setTotalTurnTime(Long.parseLong(tokenizer.nextToken()));
                case Constants.FOG_FILTER -> {
                    String filter = tokenizer.nextToken();
                    player.setFogFilter(filter);
                }
                case Constants.PASSED -> player.setPassed(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.READY_TO_PASS_BAG -> player.setReadyToPassBag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.AUTO_PASS_WHENS_N_AFTERS -> player.setAutoPassOnWhensAfters(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.SEARCH_WARRANT -> player.setSearchWarrant(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.DUMMY -> player.setDummy(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_CFRAG -> player.setHasFoundCulFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_HFRAG -> player.setHasFoundHazFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_IFRAG -> player.setHasFoundIndFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_UFRAG -> player.setHasFoundUnkFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.LANEFIR_ATS_COUNT -> player.setAtsCount(Integer.parseInt(tokenizer.nextToken()));
                case Constants.SARWEEN_COUNT -> player.setSarweenCounter(Integer.parseInt(tokenizer.nextToken()));
                case Constants.PILLAGE_COUNT -> player.setPillageCounter(Integer.parseInt(tokenizer.nextToken()));
                case Constants.CARDS_INFO_THREAD_CHANNEL_ID -> player.setCardsInfoThreadID(tokenizer.nextToken());
                case Constants.DRAFT_BAG_INFO_THREAD_CHANNEL_ID -> player.setBagInfoThreadID(tokenizer.nextToken());
                case Constants.PLAYER_NEW_TEMP_MODS -> {
                    StringTokenizer mods = new StringTokenizer(tokenizer.nextToken(), "|");
                    while (mods.hasMoreTokens()) {
                        player.addNewTempCombatMod(new TemporaryCombatModifierModel(mods.nextToken()));
                    }
                }
                case Constants.PLAYER_TEMP_MODS -> {
                    StringTokenizer mods = new StringTokenizer(tokenizer.nextToken(), "|");
                    while (mods.hasMoreTokens()) {
                        player.addTempCombatMod(new TemporaryCombatModifierModel(mods.nextToken()));
                    }
                }
                case Constants.ELIMINATED -> player.setEliminated(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.TIGL_RANK -> {
                    String rankID = tokenizer.nextToken();
                    TIGLHelper.TIGLRank rank = TIGLHelper.TIGLRank.fromString(rankID);
                    player.setPlayerTIGLRankAtGameStart(rank);
                }
                case Constants.PRIORITY_TRACK -> player.setPriorityPosition(Integer.parseInt(tokenizer.nextToken()));
            }
        }
    }

    private static Tile readTile(String tileData) {
        try {
            StringTokenizer tokenizer = new StringTokenizer(tileData, " ");
            String tileID = AliasHandler.resolveTile(tokenizer.nextToken());
            String position = tokenizer.nextToken();
            if (!PositionMapper.isTilePositionValid(position))
                return null;
            return new Tile(tileID, position);
        } catch (Exception e) {
            BotLogger.log("Error reading tileData: `" + tileData + "`", e);
        }
        return null;
    }

    private static void readUnit(Tile tile, String data, String spaceHolder) {
        if (tile == null)
            return;
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        tile.addUnit(spaceHolder, Units.parseID(tokenizer.nextToken()), tokenizer.nextToken());
    }

    private static void readUnitDamage(Tile tile, String data, String spaceHolder) {
        if (tile == null)
            return;
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        tile.addUnitDamage(spaceHolder, Units.parseID(tokenizer.nextToken()), tokenizer.nextToken());
    }

    private static void readPlanetTokens(Tile tile, String data, String unitHolderName) {
        if (tile == null) return;
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        if (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.startsWith(Constants.COMMAND) || token.startsWith(Constants.SWEEP)) {
                tile.addCC(token);
            } else if (token.startsWith(Constants.CONTROL)) {
                tile.addControl(token, unitHolderName);
            } else {
                tile.addToken(token, unitHolderName);
            }
        }
    }

    private static Map<String, List<String>> loadPeekedPublicObjectives(String data) {
        Map<String, List<String>> peekedPublicObjectives = new LinkedHashMap<>();

        if (data.isEmpty()) {
            return peekedPublicObjectives;
        }

        Matcher matcher = PEEKED_OBJECTIVE_PATTERN.matcher(data);

        while (matcher.find()) {
            String po = matcher.group(1);
            List<String> playerIDs = new ArrayList<>(Arrays.asList(matcher.group(2).split(",")));
            peekedPublicObjectives.put(po, playerIDs);
        }

        return peekedPublicObjectives;
    }
}
