package ti4.map;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.uncategorized.CardsInfo;
import ti4.draft.BagDraft;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.json.ObjectMapperFactory;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.TemporaryCombatModifierModel;

public class GameSaveLoadManager {

    public static final String TXT = ".txt";
    public static final String JSON = ".json";
    public static final String TILE = "-tile-";
    public static final String UNITS = "-units-";
    public static final String UNITHOLDER = "-unitholder-";
    public static final String ENDUNITHOLDER = "-endunitholder-";
    public static final String ENDUNITS = "-endunits-";
    public static final String ENDUNITDAMAGE = "-endunitdamage-";
    public static final String UNITDAMAGE = "-unitdamage-";
    public static final String ENDTILE = "-endtile-";
    public static final String TOKENS = "-tokens-";
    public static final String ENDTOKENS = "-endtokens-";
    public static final String PLANET_TOKENS = "-planettokens-";
    public static final String PLANET_ENDTOKENS = "-planetendtokens-";
    public static final String MAPINFO = "-mapinfo-";
    public static final String ENDMAPINFO = "-endmapinfo-";
    public static final String GAMEINFO = "-gameinfo-";
    public static final String ENDGAMEINFO = "-endgameinfo-";
    public static final String PLAYERINFO = "-playerinfo-";
    public static final String ENDPLAYERINFO = "-endplayerinfo-";
    public static final String PLAYER = "-player-";
    public static final String ENDPLAYER = "-endplayer-";

    // Log the save times for each map for benchmarking
    private static final List<Long> saveTimes = new ArrayList<>();
    private static long txtTime = 0L;
    private static long undoTime = 0L;

    public static void saveMaps() {
        // TODO: Make sure all commands and buttons and such actually save the game
        AtomicInteger savedGamesCount = new AtomicInteger();
        AtomicInteger skippedGamesCount = new AtomicInteger();
        long loadTime = GameManager.getInstance().getLoadTime();
        GameManager.getInstance().getGameNameToGame().values().parallelStream().forEach(game -> {
            try {
                long time = game.getLastModifiedDate();
                if (time > loadTime) {
                    saveGame(game, true, "Bot Reload");
                    savedGamesCount.getAndIncrement();
                } else {
                    skippedGamesCount.getAndIncrement();
                }
            } catch (Exception e) {
                BotLogger.log("Error saving game: " + game.getName(), e);
            }
        });

        BotLogger.logWithTimestamp("**__Saved `" + savedGamesCount.get() + "` games.__**");
        BotLogger.logWithTimestamp("**__Skipped saving `" + skippedGamesCount.get() + "` games.__**");

        boolean debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
        if (debug && !saveTimes.isEmpty()) {
            long tot = 0;
            for (long time : saveTimes)
                tot += time;

            String sb = "Map save time stats:\n```fix" + "\n" + debugString("        total:", tot, tot) +
                "\n" + debugString("          txt:", txtTime, tot) +
                "\n" + debugString("    undo file:", undoTime, tot) +
                "\n" + debugString("  other stuff:", tot - txtTime - undoTime, tot) +
                "\n```";
            BotLogger.logWithTimestamp(sb);
        }
    }

    private static String debugString(String prefix, long time, long total) {
        return prefix + Helper.getTimeRepresentationNanoSeconds(time) + String.format(" (%2.2f%%)", (double) time / (double) total * 100.0);
    }

    public static void saveGame(Game game, String reason) {
        saveGame(game, false, reason);
    }

    public static void saveGame(Game game, GenericInteractionCreateEvent event) {
        saveGame(game, false, event);
    }

    public static void saveGame(Game game, boolean keepModifiedDate, @Nullable GenericInteractionCreateEvent event) {
        String reason = null;
        if (event != null) {
            String username = event.getUser().getName();
            switch (event) {
                case SlashCommandInteractionEvent slash -> reason = username + " used: " + slash.getCommandString();
                case ButtonInteractionEvent button -> {
                    boolean thread = button.getMessageChannel() instanceof ThreadChannel;
                    boolean cardThread = thread && button.getMessageChannel().getName().contains("Cards Info-");
                    boolean draftThread = thread && button.getMessageChannel().getName().contains("Draft Bag-");
                    if (cardThread || draftThread || game.isFowMode() || button.getButton().getId().contains("anonDeclare") || button.getButton().getId().contains("requestAllFollow")) {
                        reason = username + " pressed button: [CLASSIFIED]";
                    } else {
                        reason = username + " pressed button: " + button.getButton().getId() + " -- " + button.getButton().getLabel();
                    }
                }
                case StringSelectInteractionEvent selectMenu -> reason = username + " used string selection: " + selectMenu.getComponentId();
                case ModalInteractionEvent modal -> reason = username + " used modal: " + modal.getModalId();
                default -> reason = "Last Command Unknown - No Event Provided";
            }
        }
        saveGame(game, keepModifiedDate, reason);
    }

    public static void saveGame(Game game, boolean keepModifiedDate, String saveReason) {
        long saveStart = System.nanoTime();
        game.setLatestCommand(Objects.requireNonNullElse(saveReason, "Last Command Unknown - No Event Provided"));
        try {
            ButtonHelperFactionSpecific.checkIihqAttachment(game);
            DiscordantStarsHelper.checkGardenWorlds(game);
            DiscordantStarsHelper.checkSigil(game);
            DiscordantStarsHelper.checkOlradinMech(game);
        } catch (Exception e) {
            BotLogger.log("Error adding transient attachment tokens for game " + game.getName(), e);
        }

        File mapFile = Storage.getGameFile(game.getName() + TXT);

        long txtStart = System.nanoTime();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mapFile.getAbsoluteFile()))) {
            Map<String, Tile> tileMap = game.getTileMap();
            writer.write(game.getOwnerID());
            writer.write(System.lineSeparator());
            writer.write(game.getOwnerName());
            writer.write(System.lineSeparator());
            writer.write(game.getName());
            writer.write(System.lineSeparator());
            saveGameInfo(writer, game, keepModifiedDate);

            for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                Tile tile = tileEntry.getValue();
                saveTile(writer, tile);
            }
        } catch (IOException e) {
            BotLogger.log("Could not save map: " + game.getName(), e);
        }
        txtTime += System.nanoTime() - txtStart;
        long undoStart = System.nanoTime();
        mapFile = Storage.getGameFile(game.getName() + TXT);
        if (mapFile.exists()) {
            saveUndo(game, mapFile);
        }
        undoTime += System.nanoTime() - undoStart;

        long saveTime = System.nanoTime() - saveStart;
        saveTimes.add(saveTime);
    }

    public static void undo(Game game, GenericInteractionCreateEvent event) {
        File originalMapFile = Storage.getGameFile(game.getName() + Constants.TXT);
        if (originalMapFile.exists()) {
            File mapUndoDirectory = Storage.getGameUndoDirectory();
            if (!mapUndoDirectory.exists()) {
                return;
            }

            String mapName = game.getName();
            String mapNameForUndoStart = mapName + "_";
            String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
            if (mapUndoFiles != null && mapUndoFiles.length > 0) {
                try {
                    List<Integer> numbers = Arrays.stream(mapUndoFiles)
                        .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                        .map(fileName -> fileName.replace(Constants.TXT, ""))
                        .map(Integer::parseInt).toList();
                    int maxNumber = numbers.isEmpty() ? 0 : numbers.stream().mapToInt(value -> value).max().orElseThrow(NoSuchElementException::new);
                    File mapUndoStorage = Storage.getGameUndoStorage(mapName + "_" + (maxNumber - 1) + Constants.TXT);
                    File mapUndoStorage2 = Storage.getGameUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                    CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                    Files.copy(mapUndoStorage2.toPath(), originalMapFile.toPath(), options);
                    Game loadedGame = loadGame(originalMapFile);
                    try {
                        if (!loadedGame.getSavedButtons().isEmpty() && loadedGame.getSavedChannel() != null
                            && !game.getPhaseOfGame().contains("status")) {
                            // MessageHelper.sendMessageToChannel(loadedGame.getSavedChannel(), "Attempting
                            // to regenerate buttons:");
                            MessageHelper.sendMessageToChannelWithButtons(loadedGame.getSavedChannel(),
                                loadedGame.getSavedMessage(), ButtonHelper.getSavedButtons(loadedGame));
                        } else {
                            System.out.println("Boop" + loadedGame.getSavedButtons().size());
                        }
                    } catch (Exception e) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Had trouble getting the saved buttons, sorry");
                    }
                    StringBuilder sb = new StringBuilder("Rolled the game back, including this command:\n> `")
                        .append(maxNumber).append("` ");
                    if (loadedGame.getSavedChannel() instanceof ThreadChannel
                        && loadedGame.getSavedChannel().getName().contains("Cards Info")) {
                        sb.append("[CLASSIFIED]");
                    } else {
                        sb.append(loadedGame.getLatestCommand());
                    }
                    Files.copy(mapUndoStorage.toPath(), originalMapFile.toPath(), options);
                    mapUndoStorage2.delete();
                    loadedGame = loadGame(originalMapFile);
                    if (loadedGame == null) throw new Exception("Failed to load undo copy");

                    for (Player p1 : loadedGame.getRealPlayers()) {
                        Player p2 = game.getPlayerFromColorOrFaction(p1.getFaction());
                        if (p1.getAc() != p2.getAc() || p1.getSo() != p2.getSo()) {
                            CardsInfo.sendCardsInfo(loadedGame, p1);
                        }
                    }
                    GameManager.getInstance().deleteGame(game.getName());
                    GameManager.getInstance().addGame(loadedGame);

                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
                    } else {
                        ButtonHelper.findOrCreateThreadWithMessage(game, mapName + "-undo-log", sb.toString());
                    }

                } catch (Exception e) {
                    BotLogger.log("Error trying to make undo copy for map: " + mapName, e);
                }
            }
        }
    }

    public static void reload(Game game) {
        File originalMapFile = Storage.getGameFile(game.getName() + Constants.TXT);
        if (originalMapFile.exists()) {
            Game loadedGame = loadGame(originalMapFile);
            if (loadedGame != null) {
                GameManager.getInstance().deleteGame(game.getName());
                GameManager.getInstance().addGame(loadedGame);
            }
        }
    }

    private static void saveUndo(Game game, File originalMapFile) {
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        if (!mapUndoDirectory.exists()) {
            mapUndoDirectory.mkdir();
        }

        String mapName = game.getName();
        String mapNameForUndoStart = mapName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
        if (mapUndoFiles != null) {
            try {
                List<Integer> numbers = Arrays.stream(mapUndoFiles)
                    .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                    .map(fileName -> fileName.replace(Constants.TXT, ""))
                    .map(Integer::parseInt).toList();
                if (numbers.size() == 50) {
                    int minNumber = numbers.stream().mapToInt(value -> value)
                        .min().orElseThrow(NoSuchElementException::new);
                    File mapToDelete = Storage.getGameUndoStorage(mapName + "_" + minNumber + Constants.TXT);
                    mapToDelete.delete();
                }
                int maxNumber = numbers.isEmpty() ? 0
                    : numbers.stream().mapToInt(value -> value)
                        .max().orElseThrow(NoSuchElementException::new);
                maxNumber++;
                File mapUndoStorage = Storage.getGameUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                Files.copy(originalMapFile.toPath(), mapUndoStorage.toPath(), options);
            } catch (Exception e) {
                BotLogger.log("Error trying to make undo copy for map: " + mapName, e);
            }
        }
    }

    private static void saveGameInfo(Writer writer, Game game, boolean keepModifiedDate) throws IOException {
        writer.write(MAPINFO);
        writer.write(System.lineSeparator());

        writer.write(GAMEINFO);
        writer.write(System.lineSeparator());
        // game information
        writer.write(Constants.LATEST_COMMAND + " " + game.getLatestCommand());
        writer.write(System.lineSeparator());
        writer.write(Constants.PHASE_OF_GAME + " " + game.getPhaseOfGame());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_OUTCOME_VOTED_FOR + " " + game.getLatestOutcomeVotedFor());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_AFTER_MSG + " " + game.getLatestAfterMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_WHEN_MSG + " " + game.getLatestWhenMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_TRANSACTION_MSG + " " + game.getLatestTransactionMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_UPNEXT_MSG + " " + game.getLatestUpNextMsg());
        writer.write(System.lineSeparator());

        writer.write(Constants.SO + " " + String.join(",", game.getSecretObjectives()));
        writer.write(System.lineSeparator());

        writer.write(Constants.MESSAGEID_FOR_SABOS + " " + String.join(",", game.getMessageIDsForSabo()));
        writer.write(System.lineSeparator());

        writer.write(Constants.AC + " " + String.join(",", game.getActionCards()));
        writer.write(System.lineSeparator());

        writeCards(game.getDiscardActionCards(), writer, Constants.AC_DISCARDED);
        writeCards(game.getPurgedActionCards(), writer, Constants.AC_PURGED);

        writer.write(Constants.EXPLORE + " " + String.join(",", game.getAllExplores()));
        writer.write(System.lineSeparator());

        writer.write(Constants.RELICS + " " + String.join(",", game.getAllRelics()));
        writer.write(System.lineSeparator());

        writer.write(Constants.DISCARDED_EXPLORES + " " + String.join(",", game.getAllExploreDiscard()));
        writer.write(System.lineSeparator());

        writer.write(Constants.SPEAKER + " " + game.getSpeakerUserID());
        writer.write(System.lineSeparator());

        writer.write(Constants.ACTIVE_PLAYER + " " + game.getActivePlayerID());
        writer.write(System.lineSeparator());
        writer.write(Constants.ACTIVE_SYSTEM + " " + game.getActiveSystem());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_ACTIVE_PLAYER_PING + " " + game.getLastActivePlayerPing().getTime());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_TIME_GAMES_CHECKED + " " + game.getLastTimeGamesChecked().getTime());
        writer.write(System.lineSeparator());

        writer.write(Constants.AUTO_PING + " " + game.getAutoPingSpacer());
        writer.write(System.lineSeparator());

        writer.write(Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_AFTER + " " + game.getPlayersWhoHitPersistentNoAfter());
        writer.write(System.lineSeparator());

        writer.write(Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_WHEN + " " + game.getPlayersWhoHitPersistentNoWhen());
        writer.write(System.lineSeparator());

        writer.write(Constants.CURRENT_AGENDA_INFO + " " + game.getCurrentAgendaInfo());
        writer.write(System.lineSeparator());
        writer.write(Constants.CURRENT_ACDRAWSTATUS_INFO + " " + game.getCurrentACDrawStatusInfo());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_ACTIVE_PLAYER_CHANGE + " " + game.getLastActivePlayerChange().getTime());
        writer.write(System.lineSeparator());

        Map<Integer, Boolean> scPlayed = game.getScPlayed();

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Boolean> entry : scPlayed.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_PLAYED + " " + sb);
        writer.write(System.lineSeparator());

        Map<String, String> agendaVoteInfo = game.getCurrentAgendaVotes();
        StringBuilder sb2 = new StringBuilder();
        for (Map.Entry<String, String> entry : agendaVoteInfo.entrySet()) {
            sb2.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.AGENDA_VOTE_INFO + " " + sb2);
        writer.write(System.lineSeparator());

        Map<String, String> currentCheckingForAllReacts = game.getMessagesThatICheckedForAllReacts();
        sb2 = new StringBuilder();
        for (Map.Entry<String, String> entry : currentCheckingForAllReacts.entrySet()) {
            sb2.append(entry.getKey()).append(",").append(entry.getValue().replace("\n", ". ")).append(":");
        }
        writer.write(Constants.CHECK_REACTS_INFO + " " + sb2);
        writer.write(System.lineSeparator());

        Map<String, Integer> displaced1System = game.getCurrentMovedUnitsFrom1System();
        StringBuilder sb3 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : displaced1System.entrySet()) {
            sb3.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.DISPLACED_UNITS_SYSTEM + " " + sb3);
        writer.write(System.lineSeparator());

        Map<String, Integer> thalnosUnits = game.getThalnosUnits();
        StringBuilder sb16 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : thalnosUnits.entrySet()) {
            sb16.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.THALNOS_UNITS + " " + sb16);
        writer.write(System.lineSeparator());

        Map<String, Integer> slashCommands = game.getAllSlashCommandsUsed();
        StringBuilder sb10 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : slashCommands.entrySet()) {
            sb10.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.SLASH_COMMAND_STRING + " " + sb10);
        writer.write(System.lineSeparator());

        Map<String, Integer> acSabod = game.getAllActionCardsSabod();
        StringBuilder sb11 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : acSabod.entrySet()) {
            sb11.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.ACS_SABOD + " " + sb11);
        writer.write(System.lineSeparator());

        Map<String, Integer> displacedActivation = game.getMovedUnitsFromCurrentActivation();
        StringBuilder sb4 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : displacedActivation.entrySet()) {
            sb4.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.DISPLACED_UNITS_ACTIVATION + " " + sb4);
        writer.write(System.lineSeparator());

        writer.write(Constants.AGENDAS + " " + String.join(",", game.getAgendas()));
        writer.write(System.lineSeparator());

        writeCards(game.getDiscardAgendas(), writer, Constants.DISCARDED_AGENDAS);
        writeCards(game.getSentAgendas(), writer, Constants.SENT_AGENDAS);
        writeCards(game.getLaws(), writer, Constants.LAW);
        writeCards(game.getEventsInEffect(), writer, Constants.EVENTS_IN_EFFECT);

        writer.write(Constants.EVENTS + " " + String.join(",", game.getEvents()));
        writer.write(System.lineSeparator());
        writeCards(game.getDiscardedEvents(), writer, Constants.DISCARDED_EVENTS);

        sb = new StringBuilder();
        for (Map.Entry<String, String> entry : game.getLawsInfo().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.LAW_INFO + " " + sb);
        writer.write(System.lineSeparator());

        sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : game.getScTradeGoods().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_TRADE_GOODS + " " + sb);
        writer.write(System.lineSeparator());

        writeCards(game.getRevealedPublicObjectives(), writer, Constants.REVEALED_PO);
        writeCards(game.getCustomPublicVP(), writer, Constants.CUSTOM_PO_VP);
        writer.write(Constants.PO1 + " " + String.join(",", game.getPublicObjectives1()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO2 + " " + String.join(",", game.getPublicObjectives2()));
        writer.write(System.lineSeparator());
        writer.write(Constants.SO_TO_PO + " " + String.join(",", game.getSoToPoList()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PURGED_PN + " " + String.join(",", game.getPurgedPN()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO1PEAKABLE + " " + String.join(",", game.getPublicObjectives1Peakable()));
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_BUTTONS + " " + String.join(",", game.getSavedButtons()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO2PEAKABLE + " " + String.join(",", game.getPublicObjectives2Peakable()));
        writer.write(System.lineSeparator());

        savePeekedPublicObjectives(writer, Constants.PO1PEEKED, game.getPublicObjectives1Peeked());
        savePeekedPublicObjectives(writer, Constants.PO2PEEKED, game.getPublicObjectives2Peeked());

        DisplayType displayTypeForced = game.getDisplayTypeForced();
        if (displayTypeForced != null) {
            writer.write(Constants.DISPLAY_TYPE + " " + displayTypeForced.getValue());
            writer.write(System.lineSeparator());
        }

        writer.write(Constants.SC_COUNT_FOR_MAP + " " + game.getStrategyCardsPerPlayer());
        writer.write(System.lineSeparator());

        writer.write(Constants.PLAYER_COUNT_FOR_MAP + " " + game.getPlayerCountForMap());
        writer.write(System.lineSeparator());

        writer.write(Constants.VP_COUNT + " " + game.getVp());
        writer.write(System.lineSeparator());
        writer.write(Constants.MAX_SO_COUNT + " " + game.getMaxSOCountPerPlayer());
        writer.write(System.lineSeparator());

        StringBuilder sb1 = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : game.getScoredPublicObjectives().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            sb1.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.SCORED_PO + " " + sb1);
        writer.write(System.lineSeparator());

        StringBuilder adjacentTiles = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : game.getCustomAdjacentTiles().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            adjacentTiles.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.CUSTOM_ADJACENT_TILES + " " + adjacentTiles);
        writer.write(System.lineSeparator());
        writer.write(Constants.REVERSE_SPEAKER_ORDER + " " + game.isReverseSpeakerOrder());
        writer.write(System.lineSeparator());

        StringBuilder adjacencyOverrides = new StringBuilder();
        for (Map.Entry<Pair<String, Integer>, String> entry : game.getAdjacentTileOverrides().entrySet()) {
            adjacencyOverrides.append(entry.getKey().getLeft()).append("-");
            adjacencyOverrides.append(entry.getKey().getRight()).append("-");
            adjacencyOverrides.append(entry.getValue()).append(";");
        }
        writer.write(Constants.ADJACENCY_OVERRIDES + " " + adjacencyOverrides);
        writer.write(System.lineSeparator());

        writer.write(Constants.CREATION_DATE + " " + game.getCreationDate());
        writer.write(System.lineSeparator());
        writer.write(Constants.STARTED_DATE + " " + game.getStartedDate());
        writer.write(System.lineSeparator());
        long time = keepModifiedDate ? game.getLastModifiedDate() : new Date().getTime();
        game.setLastModifiedDate(time);
        writer.write(Constants.LAST_MODIFIED_DATE + " " + time);
        writer.write(System.lineSeparator());
        writer.write(Constants.ENDED_DATE + " " + game.getEndedDate());
        writer.write(System.lineSeparator());
        writer.write(Constants.ROUND + " " + game.getRound());
        writer.write(System.lineSeparator());
        writer.write(Constants.BUTTON_PRESS_COUNT + " " + game.getButtonPressCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.SLASH_COMMAND_COUNT + " " + game.getSlashCommandsRunCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.GAME_CUSTOM_NAME + " " + game.getCustomName());
        writer.write(System.lineSeparator());

        writer.write(Constants.TABLE_TALK_CHANNEL + " " + game.getTableTalkChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.MAIN_GAME_CHANNEL + " " + game.getMainChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_CHANNEL + " " + game.getSavedChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_MESSAGE + " " + game.getSavedMessage());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_MAP_CHANNEL + " " + game.getBotMapUpdatesThreadID());
        writer.write(System.lineSeparator());
        writer.write(Constants.BAG_DRAFT_STATUS_MESSAGE_ID + " " + game.getBagDraftStatusMessageID());
        writer.write(System.lineSeparator());
        writer.write(Constants.GAME_LAUNCH_THREAD_ID + " " + game.getLaunchPostThreadID());
        writer.write(System.lineSeparator());

        // GAME MODES
        writer.write(Constants.TIGL_GAME + " " + game.isCompetitiveTIGLGame());
        writer.write(System.lineSeparator());
        writer.write(Constants.COMMUNITY_MODE + " " + game.isCommunityMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.ALLIANCE_MODE + " " + game.isAllianceMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.FOW_MODE + " " + game.isFowMode());
        writer.write(System.lineSeparator());
        StringBuilder fowOptions = new StringBuilder();
        for (Map.Entry<String, String> entry : game.getFowOptions().entrySet()) {
            fowOptions.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.FOW_OPTIONS + " " + fowOptions);
        writer.write(System.lineSeparator());
        writer.write(Constants.NAALU_AGENT + " " + game.isNaaluAgent());
        writer.write(System.lineSeparator());
        writer.write(Constants.L1_HERO + " " + game.isL1Hero());
        writer.write(System.lineSeparator());
        writer.write(Constants.NOMAD_COIN + " " + game.isNomadCoin());
        writer.write(System.lineSeparator());
        writer.write(Constants.UNDO_BUTTON + " " + game.isUndoButtonOffered());
        writer.write(System.lineSeparator());
        writer.write(Constants.FAST_SC_FOLLOW + " " + game.isFastSCFollowMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.QUEUE_SO + " " + game.isQueueSO());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_BUBBLES + " " + game.isShowBubbles());
        writer.write(System.lineSeparator());
        writer.write(Constants.TRANSACTION_METHOD + " " + game.isNewTransactionMethod());
        writer.write(System.lineSeparator());
        writer.write(Constants.HOMEBREW_MODE + " " + game.isHomebrew());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_GEARS + " " + game.isShowGears());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_BANNERS + " " + game.isShowBanners());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_HEX_BORDERS + " " + game.getHexBorderStyle());
        writer.write(System.lineSeparator());
        writer.write(Constants.PURGED_FRAGMENTS + " " + game.getNumberOfPurgedFragments());
        writer.write(System.lineSeparator());
        writer.write(Constants.TEMPORARY_PING_DISABLE + " " + game.isTemporaryPingDisable());
        writer.write(System.lineSeparator());
        writer.write(Constants.DOMINUS_ORB + " " + game.isDominusOrb());
        writer.write(System.lineSeparator());
        writer.write(Constants.COMPONENT_ACTION + " " + game.isComponentAction());
        writer.write(System.lineSeparator());
        writer.write(Constants.JUST_PLAYED_COMPONENT_AC + " " + game.isJustPlayedComponentAC());
        writer.write(System.lineSeparator());
        writer.write(Constants.ACTIVATION_COUNT + " " + game.getActivationCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.BASE_GAME_MODE + " " + game.isBaseGameMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.LIGHT_FOG_MODE + " " + game.isLightFogMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.RED_TAPE_MODE + " " + game.isRedTapeMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.STRAT_PINGS + " " + game.isStratPings());
        writer.write(System.lineSeparator());
        writer.write(Constants.ABSOL_MODE + " " + game.isAbsolMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.VOTC_MODE + " " + game.isVotcMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.MILTYMOD_MODE + " " + game.isMiltyModMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.PROMISES_PROMISES + " " + game.isPromisesPromisesMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.FLAGSHIPPING + " " + game.isFlagshippingMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_MAP_SETUP + " " + game.isShowMapSetup());
        writer.write(System.lineSeparator());
        writer.write(Constants.TEXT_SIZE + " " + game.getTextSize());
        writer.write(System.lineSeparator());
        writer.write(Constants.DISCORDANT_STARS_MODE + " " + game.isDiscordantStarsMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.VERBOSITY + " " + game.getOutputVerbosity());
        writer.write(System.lineSeparator());
        writer.write(Constants.BETA_TEST_MODE + " " + game.isTestBetaFeaturesMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.AGE_OF_EXPLORATION_MODE + " " + game.isAgeOfExplorationMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.MINOR_FACTIONS_MODE + " " + game.isMinorFactionsMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_FULL_COMPONENT_TEXT + " " + game.isShowFullComponentTextEmbeds());
        writer.write(System.lineSeparator());
        writer.write(Constants.HACK_ELECTION_STATUS + " " + game.isHasHackElectionBeenPlayed());
        writer.write(System.lineSeparator());
        writer.write(Constants.CC_N_PLASTIC_LIMIT + " " + game.isCcNPlasticLimit());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_FACTION_REACTS + " " + game.isBotFactionReacts());
        writer.write(System.lineSeparator());
        writer.write(Constants.HAS_HAD_A_STATUS_PHASE + " " + game.isHasHadAStatusPhase());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_SHUSHING + " " + game.isBotShushing());
        writer.write(System.lineSeparator());
        writer.write(Constants.HOMEBREW_SC_MODE + " " + game.isHomebrewSCMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.INJECT_RULES_LINKS + " " + game.isInjectRulesLinks());
        writer.write(System.lineSeparator());
        writer.write(Constants.SPIN_MODE + " " + game.isSpinMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_UNIT_TAGS + " " + game.isShowUnitTags());
        writer.write(System.lineSeparator());

        writer.write(Constants.AC_DECK_ID + " " + game.getAcDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SO_DECK_ID + " " + game.getSoDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.STAGE_1_PUBLIC_DECK_ID + " " + game.getStage1PublicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.STAGE_2_PUBLIC_DECK_ID + " " + game.getStage2PublicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.MAP_TEMPLATE + " " + game.getMapTemplateID());
        writer.write(System.lineSeparator());
        writer.write(Constants.TECH_DECK_ID + " " + game.getTechnologyDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.RELIC_DECK_ID + " " + game.getRelicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.AGENDA_DECK_ID + " " + game.getAgendaDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.EVENT_DECK_ID + " " + game.getEventDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.EXPLORATION_DECK_ID + " " + game.getExplorationDeckID());
        writer.write(System.lineSeparator());

        writer.write(Constants.BAG_DRAFT + " " + (game.getActiveBagDraft() == null ? "" : game.getActiveBagDraft().getSaveString()));
        writer.write(System.lineSeparator());

        writer.write(Constants.GAME_TAGS + " " + String.join(",", game.getTags()));
        writer.write(System.lineSeparator());

        MiltyDraftManager manager = game.getMiltyDraftManager();
        if (manager != null) {
            writer.write(Constants.MILTY_DRAFT_MANAGER + " " + manager.superSaveMessage());
            writer.write(System.lineSeparator());
        }

        MiltySettings miltySettings = game.getMiltySettingsUnsafe();
        if (miltySettings != null) {
            writer.write(Constants.MILTY_DRAFT_SETTINGS + " " + miltySettings.json());
            writer.write(System.lineSeparator());
        } else if (game.getMiltyJson() != null) {
            // default to the already stored value, if we failed to read it previously
            writer.write(Constants.MILTY_DRAFT_SETTINGS + " " + game.getMiltyJson());
            writer.write(System.lineSeparator());
        }

        writer.write(Constants.STRATEGY_CARD_SET + " " + game.getScSetID());
        writer.write(System.lineSeparator());

        ObjectMapper mapper = ObjectMapperFactory.build();
        String anomaliesJson = mapper.writeValueAsString(game.getBorderAnomalies()); // much easier than manually (de)serialising
        writer.write(Constants.BORDER_ANOMALIES + " " + anomaliesJson);
        writer.write(System.lineSeparator());

        writer.write(Constants.GAME_HAS_ENDED + " " + game.isHasEnded());
        writer.write(System.lineSeparator());

        writer.write(Constants.IMAGE_GEN_COUNT + " " + game.getMapImageGenerationCount());
        writer.write(System.lineSeparator());

        game.getPlayersWithGMRole(); //init gmIds
        writer.write(Constants.FOW_GM_IDS + " " + String.join(",", game.getFogOfWarGMIDs()));
        writer.write(System.lineSeparator());

        writer.write(Constants.RUN_DATA_MIGRATIONS + " " + String.join(",", game.getRunMigrations()));
        writer.write(System.lineSeparator());

        if (game.getMinimumTIGLRankAtGameStart() != null) {
            writer.write(Constants.TIGL_RANK + " " + game.getMinimumTIGLRankAtGameStart());
            writer.write(System.lineSeparator());
        }

        // writer.write("historicalStatsDashboardJsons " + )
        // writer.write(System.lineSeparator());

        writer.write(ENDGAMEINFO);
        writer.write(System.lineSeparator());

        // Player information
        writer.write(PLAYERINFO);
        writer.write(System.lineSeparator());
        Map<String, Player> players = game.getPlayers();
        for (Map.Entry<String, Player> playerEntry : players.entrySet()) {
            writer.write(PLAYER);
            writer.write(System.lineSeparator());

            Player player = playerEntry.getValue();
            writer.write(player.getUserID());
            writer.write(System.lineSeparator());
            writer.write(player.getUserName());
            writer.write(System.lineSeparator());

            writer.write(Constants.FACTION + " " + player.getFaction());
            writer.write(System.lineSeparator());
            writer.write(Constants.FACTION_EMOJI + " " + player.getFactionEmojiRaw());
            writer.write(System.lineSeparator());
            String displayName = player.getDisplayName() != null ? player.getDisplayName().replace(" ", "_") : "null";
            writer.write(Constants.FACTION_DISPLAY_NAME + " " + displayName);
            writer.write(System.lineSeparator());
            // TODO Remove when no longer relevant
            String playerColor = player.getColor();
            if (player.getFaction() == null || "null".equals(player.getFaction())) {
                playerColor = "null";
            }
            writer.write(Constants.COLOR + " " + playerColor);
            writer.write(System.lineSeparator());

            writer.write(Constants.DECAL_SET + " " + player.getDecalSet());
            writer.write(System.lineSeparator());

            writer.write(Constants.STATS_ANCHOR_LOCATION + " " + player.getPlayerStatsAnchorPosition());
            writer.write(System.lineSeparator());

            writer.write(Constants.HS_TILE_POSITION + " " + player.getHomeSystemPosition());
            writer.write(System.lineSeparator());

            writer.write(Constants.ALLIANCE_MEMBERS + " " + player.getAllianceMembers());
            writer.write(System.lineSeparator());

            writer.write(Constants.AFK_HOURS + " " + player.getHoursThatPlayerIsAFK());
            writer.write(System.lineSeparator());

            writer.write(Constants.ROLE_FOR_COMMUNITY + " " + player.getRoleIDForCommunity());
            writer.write(System.lineSeparator());

            writer.write(Constants.PLAYER_PRIVATE_CHANNEL + " " + player.getPrivateChannelID());
            writer.write(System.lineSeparator());

            String fogColor = player.getFogFilter() == null ? "" : player.getFogFilter();
            writer.write(Constants.FOG_FILTER + " " + fogColor);
            writer.write(System.lineSeparator());

            writer.write(Constants.PASSED + " " + player.isPassed());
            writer.write(System.lineSeparator());

            writer.write(Constants.READY_TO_PASS_BAG + " " + player.isReadyToPassBag());
            writer.write(System.lineSeparator());

            writer.write(Constants.TEN_MIN_REMINDER + " " + player.shouldPlayerBeTenMinReminded());
            writer.write(System.lineSeparator());

            writer.write(Constants.PREFERS_DISTANCE + " " + player.doesPlayerPreferDistanceBasedTacticalActions());
            writer.write(System.lineSeparator());

            writer.write(Constants.AUTO_PASS_WHENS_N_AFTERS + " " + player.doesPlayerAutoPassOnWhensAfters());
            writer.write(System.lineSeparator());

            writer.write(Constants.SEARCH_WARRANT + " " + player.isSearchWarrant());
            writer.write(System.lineSeparator());

            writer.write(Constants.DUMMY + " " + player.isDummy());
            writer.write(System.lineSeparator());

            writer.write(Constants.ELIMINATED + " " + player.isEliminated());
            writer.write(System.lineSeparator());

            writer.write(Constants.NOTEPAD + " " + player.getNotes());
            writer.write(System.lineSeparator());

            // BENTOR Ancient Blueprints
            writer.write(Constants.BENTOR_HAS_FOUND_CFRAG + " " + player.hasFoundCulFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_HFRAG + " " + player.hasFoundHazFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_IFRAG + " " + player.hasFoundIndFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_UFRAG + " " + player.hasFoundUnkFrag());
            writer.write(System.lineSeparator());

            // LANEFIR ATS Armaments count
            writer.write(Constants.LANEFIR_ATS_COUNT + " " + player.getAtsCount());
            writer.write(System.lineSeparator());

            writeCards(player.getActionCards(), writer, Constants.AC);
            writeCards(player.getEvents(), writer, Constants.EVENTS);
            writeCards(player.getPromissoryNotes(), writer, Constants.PROMISSORY_NOTES);

            writer.write(Constants.PROMISSORY_NOTES_OWNED + " " + String.join(",", player.getPromissoryNotesOwned()));
            writer.write(System.lineSeparator());

            writer.write(Constants.PROMISSORY_NOTES_PLAY_AREA + " "
                + String.join(",", player.getPromissoryNotesInPlayArea()));
            writer.write(System.lineSeparator());

            writer.write(Constants.UNITS_OWNED + " " + String.join(",", player.getUnitsOwned()));
            writer.write(System.lineSeparator());

            writeCards(player.getTrapCards(), writer, Constants.LIZHO_TRAP_CARDS);
            writeCardsStrings(player.getTrapCardsPlanets(), writer, Constants.LIZHO_TRAP_PLANETS);

            writer.write(Constants.FRAGMENTS + " " + String.join(",", player.getFragments()));
            writer.write(System.lineSeparator());

            writer.write(Constants.RELICS + " " + String.join(",", player.getRelics()));
            writer.write(System.lineSeparator());

            writer.write(Constants.EXHAUSTED_RELICS + " " + String.join(",", player.getExhaustedRelics()));
            writer.write(System.lineSeparator());

            writer.write(Constants.MAHACT_CC + " " + String.join(",", player.getMahactCC()));
            writer.write(System.lineSeparator());

            writer.write(Constants.DRAFT_BAG + " " + player.getCurrentDraftBag().toStoreString());
            writer.write(System.lineSeparator());

            writer.write(Constants.DRAFT_HAND + " " + player.getDraftHand().toStoreString());
            writer.write(System.lineSeparator());

            writer.write(Constants.DRAFT_QUEUE + " " + player.getDraftQueue().toStoreString());
            writer.write(System.lineSeparator());

            writer.write(Constants.FACTION_TECH + " " + String.join(",", player.getFactionTechs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TECH + " " + String.join(",", player.getTechs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.SPENT_THINGS + " " + String.join(",", player.getSpentThingsThisWindow()));
            writer.write(System.lineSeparator());
            writer.write(Constants.BOMBARD_UNITS + " " + String.join(",", player.getBombardUnits()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TRANSACTION_ITEMS + " " + String.join(",", player.getTransactionItems()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TEAMMATE_IDS + " " + String.join(",", player.getTeamMateIDs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TECH_EXHAUSTED + " " + String.join(",", player.getExhaustedTechs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TECH_PURGED + " " + String.join(",", player.getPurgedTechs()));
            writer.write(System.lineSeparator());

            writer.write(Constants.PLANETS + " " + String.join(",", player.getPlanets()));
            writer.write(System.lineSeparator());
            writer.write(Constants.PLANETS_EXHAUSTED + " " + String.join(",", player.getExhaustedPlanets()));
            writer.write(System.lineSeparator());
            writer.write(Constants.PLANETS_ABILITY_EXHAUSTED + " "
                + String.join(",", player.getExhaustedPlanetsAbilities()));
            writer.write(System.lineSeparator());

            writer.write(Constants.TACTICAL + " " + player.getTacticalCC());
            writer.write(System.lineSeparator());
            writer.write(Constants.FLEET + " " + player.getFleetCC());
            writer.write(System.lineSeparator());
            writer.write(Constants.STRATEGY + " " + player.getStrategicCC());
            writer.write(System.lineSeparator());

            writer.write(Constants.ABILITIES + " " + String.join(",", player.getAbilities()));
            writer.write(System.lineSeparator());

            writer.write(Constants.TG + " " + player.getTg());
            writer.write(System.lineSeparator());

            writer.write(Constants.EXPECTED_HITS_TIMES_10 + " " + player.getExpectedHitsTimes10());
            writer.write(System.lineSeparator());

            writer.write(Constants.TOTAL_EXPENSES + " " + player.getTotalExpenses());
            writer.write(System.lineSeparator());

            writer.write(Constants.TURN_COUNT + " " + player.getTurnCount());
            writer.write(System.lineSeparator());

            writer.write(Constants.ACTUAL_HITS + " " + player.getActualHits());
            writer.write(System.lineSeparator());

            writer.write(Constants.DEBT + " " + getStringRepresentationOfMap(player.getDebtTokens()));
            writer.write(System.lineSeparator());

            writer.write(Constants.COMMODITIES + " " + player.getCommodities());
            writer.write(System.lineSeparator());
            writer.write(Constants.COMMODITIES_TOTAL + " " + player.getCommoditiesTotal());
            writer.write(System.lineSeparator());
            writer.write(Constants.STASIS_INFANTRY + " " + player.getStasisInfantry());
            writer.write(System.lineSeparator());
            writer.write(Constants.AUTO_SABO_PASS_MEDIAN + " " + player.getAutoSaboPassMedian());
            writer.write(System.lineSeparator());

            UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
            StringBuilder units = new StringBuilder();
            if (unitHolder != null) {
                for (Map.Entry<UnitKey, Integer> entry : unitHolder.getUnits().entrySet()) {
                    if (Mapper.isValidColor(entry.getKey().getColor())) {
                        units.append(entry.getKey().outputForSave()).append(",").append(entry.getValue()).append(";");
                    }
                }
            }
            writer.write(Constants.CAPTURE + " " + units);
            writer.write(System.lineSeparator());

            writer.write(Constants.UNIT_CAP + " " + getStringRepresentationOfMap(player.getUnitCaps()));
            writer.write(System.lineSeparator());

            writer.write(Constants.SO + " " + getStringRepresentationOfMap(player.getSecrets()));
            writer.write(System.lineSeparator());
            writer.write(
                Constants.PRODUCED_UNITS + " " + getStringRepresentationOfMap(player.getCurrentProducedUnits()));
            writer.write(System.lineSeparator());
            writer.write(Constants.SO_SCORED + " " + getStringRepresentationOfMap(player.getSecretsScored()));
            writer.write(System.lineSeparator());

            writer.write(Constants.NUMBER_OF_TURNS + " " + player.getNumberTurns());
            writer.write(System.lineSeparator());
            writer.write(Constants.TOTAL_TURN_TIME + " " + player.getTotalTurnTime());
            writer.write(System.lineSeparator());
            writer.write(Constants.STRATEGY_CARD + " " + String.join(",", player.getSCs().stream().map(String::valueOf).toList()));
            writer.write(System.lineSeparator());
            writer.write(Constants.FOLLOWED_SC + " " + String.join(",", player.getFollowedSCs().stream().map(String::valueOf).toList()));
            writer.write(System.lineSeparator());

            StringBuilder leaderInfo = new StringBuilder();
            if (player.getLeaders().isEmpty())
                leaderInfo.append("none");
            for (Leader leader : player.getLeaders()) {
                leaderInfo.append(leader.getId());
                leaderInfo.append(",");
                leaderInfo.append(leader.getType());
                leaderInfo.append(",");
                leaderInfo.append(leader.getTgCount());
                leaderInfo.append(",");
                leaderInfo.append(leader.isExhausted());
                leaderInfo.append(",");
                leaderInfo.append(leader.isLocked());
                leaderInfo.append(",");
                leaderInfo.append(leader.isActive());
                leaderInfo.append(";");
            }
            writer.write(Constants.LEADERS + " " + leaderInfo);
            writer.write(System.lineSeparator());

            StringBuilder fogOfWarSystems = new StringBuilder();
            Map<String, String> fow_systems = player.getFogTiles();
            Map<String, String> fow_labels = player.getFogLabels();
            for (String key : fow_systems.keySet()) {
                String system = fow_systems.get(key);
                String label = fow_labels.get(key);
                if (label != null)
                    label = label.replaceAll(" ", "—"); // replace spaces with em dash
                fogOfWarSystems.append(key);
                fogOfWarSystems.append(",");
                fogOfWarSystems.append(system);
                fogOfWarSystems.append(",");
                fogOfWarSystems.append(label == null || label.isEmpty() ? "." : label);
                fogOfWarSystems.append(";");
            }
            writer.write(Constants.FOW_SYSTEMS + " " + fogOfWarSystems);
            writer.write(System.lineSeparator());

            writer.write(Constants.CARDS_INFO_THREAD_CHANNEL_ID + " " + player.getCardsInfoThreadID());
            writer.write(System.lineSeparator());

            writer.write(Constants.DRAFT_BAG_INFO_THREAD_CHANNEL_ID + " " + player.getBagInfoThreadID());
            writer.write(System.lineSeparator());

            List<String> newTempCombatMods = new ArrayList<>();
            for (TemporaryCombatModifierModel mod : player.getNewTempCombatModifiers()) {
                if (mod == null || mod.getModifier() == null) {
                    continue;
                }
                newTempCombatMods.add(mod.getSaveString());
            }
            writer.write(Constants.PLAYER_NEW_TEMP_MODS + " " + String.join("|", newTempCombatMods));
            writer.write(System.lineSeparator());

            List<String> tempCombatMods = new ArrayList<>();
            for (TemporaryCombatModifierModel mod : player.getTempCombatModifiers()) {
                tempCombatMods.add(mod.getSaveString());
            }
            writer.write(Constants.PLAYER_TEMP_MODS + " " + String.join("|", tempCombatMods));
            writer.write(System.lineSeparator());

            if (player.getPlayerTIGLRankAtGameStart() != null) {
                writer.write(Constants.TIGL_RANK + " " + player.getPlayerTIGLRankAtGameStart());
                writer.write(System.lineSeparator());
            }

            writer.write(ENDPLAYER);
            writer.write(System.lineSeparator());
        }

        writer.write(ENDPLAYERINFO);
        writer.write(System.lineSeparator());

        writer.write(ENDMAPINFO);
        writer.write(System.lineSeparator());
    }

    private static void writeCards(Map<String, Integer> cardList, Writer writer, String saveID) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : cardList.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(saveID + " " + sb);
        writer.write(System.lineSeparator());
    }

    private static void writeCardsStrings(Map<String, String> cardList, Writer writer, String saveID)
        throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cardList.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(saveID + " " + sb);
        writer.write(System.lineSeparator());
    }

    private static String getStringRepresentationOfMap(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        return sb.toString();
    }

    private static void saveTile(Writer writer, Tile tile) throws IOException {
        writer.write(TILE);
        writer.write(System.lineSeparator());
        writer.write(tile.getTileID() + " " + tile.getPosition());
        writer.write(System.lineSeparator());
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        writer.write(UNITHOLDER);
        writer.write(System.lineSeparator());
        for (UnitHolder unitHolder : unitHolders.values()) {
            writer.write(UNITS);
            writer.write(System.lineSeparator());
            writer.write(unitHolder.getName());
            writer.write(System.lineSeparator());
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            for (Map.Entry<UnitKey, Integer> entry : units.entrySet()) {
                if (entry.getKey() != null) {
                    writer.write(entry.getKey().outputForSave() + " " + entry.getValue());
                    writer.write(System.lineSeparator());
                }
            }
            writer.write(ENDUNITS);
            writer.write(System.lineSeparator());

            writer.write(UNITDAMAGE);
            writer.write(System.lineSeparator());
            Map<UnitKey, Integer> unitDamage = unitHolder.getUnitDamage();
            for (Map.Entry<UnitKey, Integer> entry : unitDamage.entrySet()) {
                writer.write(entry.getKey().outputForSave() + " " + entry.getValue());
                writer.write(System.lineSeparator());
            }
            writer.write(ENDUNITDAMAGE);
            writer.write(System.lineSeparator());

            writer.write(PLANET_TOKENS);
            writer.write(System.lineSeparator());
            for (String ccID : unitHolder.getCCList()) {
                writer.write(ccID);
                writer.write(System.lineSeparator());
            }

            for (String controlID : unitHolder.getControlList()) {
                writer.write(controlID);
                writer.write(System.lineSeparator());
            }

            for (String tokenID : unitHolder.getTokenList()) {
                writer.write(tokenID);
                writer.write(System.lineSeparator());
            }
            writer.write(PLANET_ENDTOKENS);
            writer.write(System.lineSeparator());
        }
        writer.write(ENDUNITHOLDER);
        writer.write(System.lineSeparator());

        writer.write(TOKENS);
        writer.write(System.lineSeparator());

        writer.write(ENDTOKENS);
        writer.write(System.lineSeparator());
        writer.write(ENDTILE);
        writer.write(System.lineSeparator());
    }

    public static boolean deleteGame(String mapName) {
        File mapStorage = Storage.getGameFile(mapName + TXT);
        if (!mapStorage.exists()) {
            return false;
        }
        File deletedMapStorage = Storage.getDeletedGame(mapName + "_" + System.currentTimeMillis() + TXT);
        return mapStorage.renameTo(deletedMapStorage);
    }

    public static void loadGame() {
        long loadStart = System.nanoTime();
        try (Stream<Path> pathStream = Files.list(Storage.getGamesDirectory().toPath())) {
            pathStream.parallel()
                .filter(path -> path.toString().toLowerCase().endsWith(".txt"))
                .forEach(path -> {
                    File file = path.toFile();
                    try {
                        Game game = loadGame(file);
                        if (game == null || game.getName() == null) {
                            BotLogger.log("Could not load game. Game or game name is null: " + file.getName());
                            return;
                        }
                        if (file.getName().contains("reference") || Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(new Date().getTime())) < 60 || game.isCustodiansScored()) {
                            GameManager.getInstance().addGame(game);
                        }
                    } catch (Exception e) {
                        BotLogger.log("Could not load game: " + file.getName(), e);
                    }
                });
        } catch (IOException e) {
            BotLogger.log("Exception occurred while streaming map directory.", e);
        }
        long loadTime = System.nanoTime() - loadStart;
        BotLogger.logWithTimestamp(debugString("Time to load `" + GameManager.getInstance().getGameNameToGame().size() + "` games: ", loadTime, loadTime));
    }

    @Nullable
    public static Game loadGame(File mapFile) {
        if (mapFile == null || !mapFile.exists()) {
            BotLogger.log("Could not load map, map file does not exist: " + (mapFile == null ? "null file" : mapFile.getAbsolutePath()));
            return null;
        }
        Game game = new Game();
        boolean fatalError = false;
        try {
            Iterator<String> gameFileLines = Files.readAllLines(mapFile.toPath(), Charset.defaultCharset()).listIterator();
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
                        BotLogger.log("Data is bad: " + game.getName(), e);
                        fatalError = true;
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
                            player = game.addPlayerLoad(gameFileLines.next(), gameFileLines.next());
                            continue;
                        }
                        if (ENDPLAYER.equals(data)) {
                            break;
                        }
                        readPlayerInfo(player, data, game);
                    }
                }
            }
            Map<String, Tile> tileMap = getTileMap(gameFileLines, game, mapFile);
            if (tileMap == null || fatalError) {
                BotLogger.log("Encountered fatal error loading game " + game.getName() + ". Load aborted.");
                return null;
            }
            game.setTileMap(tileMap);
            game.endGameIfOld();
            return game;
        } catch (Exception e) {
            BotLogger.log("Data read error: " + mapFile.getName(), e);
        }

        return null;
    }

    private static Map<String, Tile> getTileMap(Iterator<String> gameFileLines, Game game, File mapFile) {
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
                    String spaceHolder = null;
                    while (gameFileLines.hasNext()) {
                        String data = tmpData != null ? tmpData : gameFileLines.next();
                        tmpData = null;
                        if (UNITS.equals(data)) {
                            spaceHolder = gameFileLines.next().toLowerCase();
                            if (tile != null) {
                                if (Constants.MIRAGE.equals(spaceHolder)) {
                                    Helper.addMirageToTile(tile);
                                } else if (!tile.isSpaceHolderValid(spaceHolder)) {
                                    BotLogger.log(game.getName() + ": Not valid space holder detected: " + spaceHolder);
                                }
                            }
                            continue;
                        }
                        if (ENDUNITS.equals(data)) {
                            break;
                        }
                        readUnit(tile, data, spaceHolder);
                    }

                    while (gameFileLines.hasNext()) {
                        String data = gameFileLines.next();
                        if (UNITDAMAGE.equals(data)) {
                            continue;
                        }
                        if (ENDUNITDAMAGE.equals(data)) {
                            break;
                        }
                        readUnitDamage(tile, data, spaceHolder);
                    }

                    while (gameFileLines.hasNext()) {
                        String data = gameFileLines.next();
                        if (PLANET_TOKENS.equals(data)) {
                            continue;
                        }
                        if (PLANET_ENDTOKENS.equals(data)) {
                            break;
                        }
                        readPlanetTokens(tile, data, spaceHolder);
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
            BotLogger.log("Data read error: " + mapFile.getName(), e);
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
                case Constants.LATEST_AFTER_MSG -> game.setLatestAfterMsg(info);
                case Constants.LATEST_WHEN_MSG -> game.setLatestWhenMsg(info);
                case Constants.LATEST_TRANSACTION_MSG -> game.setLatestTransactionMsg(info);
                case Constants.PHASE_OF_GAME -> game.setPhaseOfGame(info);
                case Constants.LATEST_UPNEXT_MSG -> game.setLatestUpNextMsg(info);
                case Constants.SO -> game.setSecretObjectives(getCardList(info));
                case Constants.MESSAGEID_FOR_SABOS -> game.setMessageIDForSabo(getCardList(info));
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
                case Constants.RELIC_DECK_ID -> game.setRelicDeckID(info);
                case Constants.AGENDA_DECK_ID -> game.setAgendaDeckID(info);
                case Constants.EVENT_DECK_ID -> game.setEventDeckID(info);
                case Constants.EXPLORATION_DECK_ID -> game.setExplorationDeckID(info);
                case Constants.STRATEGY_CARD_SET -> {
                    if (Mapper.isValidStrategyCardSet(info)) {
                        game.setScSetID(info);
                    } else {
                        // BotLogger.log("Invalid strategy card set ID found: `" + scSetID + "` Game: `" + game.getName() + "`");
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
                case Constants.LAST_ACTIVE_PLAYER_PING -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastPing = new Date(millis);
                        game.setLastActivePlayerPing(lastPing);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.LAST_TIME_GAMES_CHECKED -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastGameCheck = new Date(millis);
                        game.setLastTimeGamesChecked(lastGameCheck);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.AUTO_PING -> {
                    try {
                        int pnghrs = Integer.parseInt(info);
                        game.setAutoPing(pnghrs != 0);
                        game.setAutoPingSpacer(pnghrs);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.CURRENT_AGENDA_INFO -> {
                    try {
                        game.setCurrentAgendaInfo(info);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.CURRENT_ACDRAWSTATUS_INFO -> {
                    try {
                        game.setCurrentACDrawStatusInfo(info);
                    } catch (Exception e) {
                        // do nothing
                    }
                }

                case Constants.LAST_ACTIVE_PLAYER_CHANGE -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastChange = new Date(millis);
                        game.setLastActivePlayerChange(lastChange);
                    } catch (Exception e) {
                        // do nothing
                    }
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
                        game.setFowOption(optionName, optionValue);
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
                case Constants.BAG_DRAFT_STATUS_MESSAGE_ID -> game.setBagDraftStatusMessageID(info);
                case Constants.GAME_LAUNCH_THREAD_ID -> game.setLaunchPostThreadID(info);

                // GAME MODES
                case Constants.TIGL_GAME -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setCompetitiveTIGLGame(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HACK_ELECTION_STATUS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setHasHackElectionBeenPlayed(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.CC_N_PLASTIC_LIMIT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setCcNPlasticLimit(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BOT_FACTION_REACTS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setBotFactionReacts(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HAS_HAD_A_STATUS_PHASE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setHasHadAStatusPhase(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BOT_SHUSHING -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setBotShushing(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.COMMUNITY_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setCommunityMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.ALLIANCE_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setAllianceMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.FOW_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setFowMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case "fow_hide_names" -> { // TODO REMOVE THIS AFTER ONE SAVE/LOAD GAMES
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        if (value)
                            game.setFowOption("hide_player_names", info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.NAALU_AGENT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setNaaluAgent(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.L1_HERO -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setL1Hero(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.NOMAD_COIN -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setNomadCoin(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.UNDO_BUTTON -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setUndoButtonOffered(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.FAST_SC_FOLLOW -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setFastSCFollowMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.QUEUE_SO -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setQueueSO(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_BUBBLES -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setShowBubbles(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.TRANSACTION_METHOD -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setNewTransactionMethod(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_GEARS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setShowGears(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_BANNERS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setShowBanners(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_HEX_BORDERS -> game.setHexBorderStyle(info);
                case Constants.HOMEBREW_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setHomebrew(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.PURGED_FRAGMENTS -> {
                    try {
                        int value = Integer.parseInt(info);
                        game.setNumberOfPurgedFragments(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.TEMPORARY_PING_DISABLE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setTemporaryPingDisable(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.DOMINUS_ORB -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setDominusOrb(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.COMPONENT_ACTION -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setComponentAction(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.JUST_PLAYED_COMPONENT_AC -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setJustPlayedComponentAC(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BASE_GAME_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setBaseGameMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.LIGHT_FOG_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setLightFogMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.RED_TAPE_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setRedTapeMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HOMEBREW_SC_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setHomebrewSCMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.INJECT_RULES_LINKS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setInjectRulesLinks(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SPIN_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setSpinMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_UNIT_TAGS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setShowUnitTags(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.STRAT_PINGS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setStratPings(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.TEXT_SIZE -> {
                    try {
                        game.setTextSize(info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.ABSOL_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setAbsolMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.VOTC_MODE, "cryypter_mode" -> { //TODO: Remove "cryypter_mode" option if found in prod after Nov 2024
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setVotcMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.PROMISES_PROMISES -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setPromisesPromisesMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.FLAGSHIPPING -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setFlagshippingMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.MILTYMOD_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setMiltyModMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_MAP_SETUP -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setMiltyModMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.DISCORDANT_STARS_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setDiscordantStarsMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.VERBOSITY -> {
                    try {
                        game.setOutputVerbosity(info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BETA_TEST_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setTestBetaFeaturesMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.AGE_OF_EXPLORATION_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setAgeOfExplorationMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.MINOR_FACTIONS_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setMinorFactionsMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_FULL_COMPONENT_TEXT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setShowFullComponentTextEmbeds(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.GAME_HAS_ENDED -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        game.setHasEnded(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
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
                    } catch (Exception e) {
                        // Do nothing
                    }
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
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.MILTY_DRAFT_MANAGER -> {
                    try {
                        MiltyDraftManager manager = game.getMiltyDraftManager();
                        manager.init(game);
                        manager.loadSuperSaveString(game, info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.MILTY_DRAFT_SETTINGS -> game.setMiltyJson(info); // We will parse this later
                case Constants.GAME_TAGS -> game.setTags(getCardList(info));
                case Constants.TIGL_RANK -> {
                    TIGLRank rank = TIGLRank.fromString(info);
                    game.setMinimumTIGLRankAtGameStart(rank);
                }
            }
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
                case Constants.FACTION -> player.setFaction(tokenizer.nextToken());
                case Constants.FACTION_EMOJI -> player.setFactionEmoji(tokenizer.nextToken());
                case Constants.FACTION_DISPLAY_NAME -> player.setDisplayName(tokenizer.nextToken().replace("_", " "));
                case Constants.COLOR -> player.setColor(tokenizer.nextToken());
                case Constants.DECAL_SET -> player.setDecalSet(tokenizer.nextToken());
                case Constants.STATS_ANCHOR_LOCATION -> player.setPlayerStatsAnchorPosition(tokenizer.nextToken());
                case Constants.HS_TILE_POSITION -> player.setHomeSystemPosition(tokenizer.nextToken());
                case Constants.ALLIANCE_MEMBERS -> player.setAllianceMembers(tokenizer.nextToken());
                case Constants.AFK_HOURS -> player.setHoursThatPlayerIsAFK(tokenizer.nextToken());
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
                case Constants.TURN_COUNT -> player.setTurnCount(Integer.parseInt(tokenizer.nextToken()));
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
                        UnitKey unitKey = Units.parseID(id);
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
                case Constants.PLANETS -> player.setPlanets(getCardList(tokenizer.nextToken().replace("exhausted", "").replace("refreshed", "")));
                case Constants.PLANETS_EXHAUSTED -> player.setExhaustedPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_ABILITY_EXHAUSTED -> player.setExhaustedPlanetsAbilities(getCardList(tokenizer.nextToken()));
                case Constants.TECH -> player.setTechs(getCardList(tokenizer.nextToken()));
                case Constants.SPENT_THINGS -> player.setSpentThings(getCardList(tokenizer.nextToken()));
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

                case Constants.NUMBER_OF_TURNS -> player.setNumberTurns(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TOTAL_TURN_TIME -> player.setTotalTurnTime(Long.parseLong(tokenizer.nextToken()));
                case Constants.FOG_FILTER -> {
                    String filter = tokenizer.nextToken();
                    player.setFogFilter(filter);
                }
                case Constants.PASSED -> player.setPassed(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.READY_TO_PASS_BAG -> player.setReadyToPassBag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.TEN_MIN_REMINDER -> player.setWhetherPlayerShouldBeTenMinReminded(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.PREFERS_DISTANCE -> player.setPreferenceForDistanceBasedTacticalActions(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.AUTO_PASS_WHENS_N_AFTERS -> player.setAutoPassWhensAfters(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.SEARCH_WARRANT -> player.setSearchWarrant(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.DUMMY -> player.setDummy(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_CFRAG -> player.setHasFoundCulFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_HFRAG -> player.setHasFoundHazFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_IFRAG -> player.setHasFoundIndFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_UFRAG -> player.setHasFoundUnkFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.LANEFIR_ATS_COUNT -> player.setAtsCount(Integer.parseInt(tokenizer.nextToken()));
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
                    TIGLRank rank = TIGLRank.fromString(rankID);
                    player.setPlayerTIGLRankAtGameStart(rank);
                }
            }
        }
    }

    private static Tile readTile(String tileData) {
        try {
            StringTokenizer tokenizer = new StringTokenizer(tileData, " ");
            String tileID = tokenizer.nextToken();
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

    private static void savePeekedPublicObjectives(Writer writer, final String constant, Map<String, List<String>> peekedPOs) {
        try {
            writer.write(constant + " ");

            for (String po : peekedPOs.keySet()) {
                writer.write(po + ":");

                for (String playerID : peekedPOs.get(po)) {
                    writer.write(playerID + ",");
                }

                writer.write(";");
            }

            writer.write(System.lineSeparator());
        } catch (Exception e) {
            BotLogger.log("Error trying to save peeked public objective(s): " + constant, e);
        }
    }

    private static Map<String, List<String>> loadPeekedPublicObjectives(String data) {
        Map<String, List<String>> peekedPublicObjectives = new LinkedHashMap<>();

        if (data.isEmpty()) {
            return peekedPublicObjectives;
        }

        Pattern pattern = Pattern.compile("(?>([a-z_]+):((?>\\d+,)+);)");
        Matcher matcher = pattern.matcher(data);

        while (matcher.find()) {
            String po = matcher.group(1);
            List<String> playerIDs = new ArrayList<>(Arrays.asList(matcher.group(2).split(",")));
            peekedPublicObjectives.put(po, playerIDs);
        }

        return peekedPublicObjectives;
    }
}
