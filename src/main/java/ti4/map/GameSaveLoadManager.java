package ti4.map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import ti4.commands.uncategorized.CardsInfo;
import ti4.draft.BagDraft;
import ti4.generator.PositionMapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
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

    public static final boolean loadFromJSON = false; // TEMPORARY FLAG THAT CAN BE REMOVED ONCE JSON SAVES ARE 100%
                                                      // WORKING

    public static void saveMaps() {
        // TODO: add last command time/last save time to cut down on saves
        GameManager.getInstance().getGameNameToGame().values().parallelStream()
                .forEach(game -> {
                    try {
                        saveMap(game, true, null);
                    } catch (Exception e) {
                        BotLogger.log("Error saving map: " + game.getName(), e);
                    }
                });
    }

    public static void saveMap(Game activeGame) {
        saveMap(activeGame, false, null);
    }

    public static void saveMap(Game activeGame, GenericInteractionCreateEvent event) {
        saveMap(activeGame, false, event);
    }

    public static void saveMap(Game activeGame, boolean keepModifiedDate,
            @Nullable GenericInteractionCreateEvent event) {
        // ADD COMMAND/BUTTON FOR UNDO INFORMATION
        if (event != null) {
            String username = event.getUser().getName();
            if (event instanceof SlashCommandInteractionEvent) {
                activeGame.setLatestCommand(
                        username + " used: " + ((CommandInteractionPayload) event).getCommandString());
            } else if (event instanceof ButtonInteractionEvent button) {
                if ((event.getMessageChannel() instanceof ThreadChannel
                        && event.getMessageChannel().getName().contains("Cards Info")) || activeGame.isFoWMode()
                        || button.getButton().getId().contains("anonDeclare")) {
                    activeGame.setLatestCommand(username + " pressed button: [CLASSIFIED]");
                } else {
                    activeGame.setLatestCommand(
                            username + " pressed button: " + ((ButtonInteraction) event).getButton().getId() + " -- "
                                    + ((ButtonInteraction) event).getButton().getLabel());
                }
            } else {
                activeGame.setLatestCommand("Last Command Unknown - Not a Slash Command or Button Press");
            }
        } else {
            if (keepModifiedDate && activeGame.isHasEnded()
                    && "Last Command Unknown - No Event Provided".equals(activeGame.getLatestCommand())) {
                System.out.println("Skipped Saving Map: " + activeGame.getName()
                        + " - Game has ended and has no changes since last save");
                return;
            }
            activeGame.setLatestCommand("Last Command Unknown - No Event Provided");
        }

        if (activeGame.isDiscordantStarsMode()) {
            try {
                DiscordantStarsHelper.checkGardenWorlds(activeGame);
                DiscordantStarsHelper.checkSigil(activeGame);
                DiscordantStarsHelper.checkOlradinMech(activeGame);
            } catch (Exception e) {
                BotLogger.log("Error doing extra Discordant Stars stuff", e);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(Storage.getMapsJSONStorage(activeGame.getName() + JSON),
                    activeGame);
        } catch (IOException e) {
            BotLogger.log(activeGame.getName() + ": IOException with JSON SAVER - Likely need to @JsonIgnore something",
                    e);
        } catch (Exception e) {
            BotLogger.log("JSON SAVER", e);
        }

        if (loadFromJSON)
            return; // DON'T SAVE OVER OLD TXT SAVES IF LOADING AND SAVING FROM JSON

        File mapFile = Storage.getMapImageStorage(activeGame.getName() + TXT);
        if (mapFile == null) {
            BotLogger.log("Could not save map, error creating save file");
            return;
        }

        try (FileWriter writer = new FileWriter(mapFile.getAbsoluteFile())) {
            Map<String, Tile> tileMap = activeGame.getTileMap();
            writer.write(activeGame.getOwnerID());
            writer.write(System.lineSeparator());
            writer.write(activeGame.getOwnerName());
            writer.write(System.lineSeparator());
            writer.write(activeGame.getName());
            writer.write(System.lineSeparator());
            saveMapInfo(writer, activeGame, keepModifiedDate);

            for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                Tile tile = tileEntry.getValue();
                saveTile(writer, tile);
            }
        } catch (IOException e) {
            BotLogger.log("Could not save map: " + activeGame.getName(), e);
        }

        mapFile = Storage.getMapImageStorage(activeGame.getName() + TXT);
        if (mapFile.exists()) {
            saveUndo(activeGame, mapFile);
        }
    }

    public static void undo(Game activeGame, GenericInteractionCreateEvent event) {
        File originalMapFile = Storage.getMapImageStorage(activeGame.getName() + Constants.TXT);
        if (originalMapFile != null) {
            File mapUndoDirectory = Storage.getMapUndoDirectory();
            if (mapUndoDirectory == null) {
                return;
            }
            if (!mapUndoDirectory.exists()) {
                return;
            }

            String mapName = activeGame.getName();
            String mapNameForUndoStart = mapName + "_";
            String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(mapNameForUndoStart));
            if (mapUndoFiles != null && mapUndoFiles.length > 0) {
                try {
                    List<Integer> numbers = Arrays.stream(mapUndoFiles)
                            .map(fileName -> fileName.replace(mapNameForUndoStart, ""))
                            .map(fileName -> fileName.replace(Constants.TXT, ""))
                            .map(Integer::parseInt).toList();
                    int maxNumber = numbers.isEmpty() ? 0
                            : numbers.stream().mapToInt(value -> value)
                                    .max().orElseThrow(NoSuchElementException::new);

                    File mapUndoStorage = Storage.getMapUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                    CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                    Files.copy(mapUndoStorage.toPath(), originalMapFile.toPath(), options);
                    mapUndoStorage.delete();
                    Game loadedGame = loadMap(originalMapFile);
                    for (Player p1 : loadedGame.getRealPlayers()) {
                        Player p2 = activeGame.getPlayerFromColorOrFaction(p1.getFaction());
                        if (p1.getAc() != p2.getAc() || p1.getSo() != p2.getSo()) {
                            CardsInfo.sendCardsInfo(loadedGame, p1);
                        }
                    }
                    GameManager.getInstance().deleteGame(activeGame.getName());
                    GameManager.getInstance().addGame(loadedGame);
                    StringBuilder sb = new StringBuilder("Rolled the game back, including this command:\n> `")
                            .append(maxNumber).append("` ");
                    if (loadedGame.getSavedChannel() instanceof ThreadChannel
                            && loadedGame.getSavedChannel().getName().contains("Cards Info")) {
                        sb.append("[CLASSIFIED]");
                    } else {
                        sb.append(loadedGame.getLatestCommand());
                    }
                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
                    } else {
                        ButtonHelper.findOrCreateThreadWithMessage(activeGame, mapName + "-undo-log", sb.toString());
                    }
                    try {
                        if (!loadedGame.getSavedButtons().isEmpty() && loadedGame.getSavedChannel() != null
                                && !activeGame.getCurrentPhase().contains("status")) {
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
                } catch (Exception e) {
                    BotLogger.log("Error trying to make undo copy for map: " + mapName, e);
                }
            }
        }
    }

    public static void reload(Game activeGame) {
        File originalMapFile = Storage.getMapImageStorage(activeGame.getName() + Constants.TXT);
        if (originalMapFile != null) {
            Game loadedGame = loadMap(originalMapFile);
            GameManager.getInstance().deleteGame(activeGame.getName());
            GameManager.getInstance().addGame(loadedGame);
        }
    }

    private static void saveUndo(Game activeGame, File originalMapFile) {
        File mapUndoDirectory = Storage.getMapUndoDirectory();
        if (mapUndoDirectory == null) {
            return;
        }
        if (!mapUndoDirectory.exists()) {
            mapUndoDirectory.mkdir();
        }

        String mapName = activeGame.getName();
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
                    File mapToDelete = Storage.getMapUndoStorage(mapName + "_" + minNumber + Constants.TXT);
                    mapToDelete.delete();
                }
                int maxNumber = numbers.isEmpty() ? 0
                        : numbers.stream().mapToInt(value -> value)
                                .max().orElseThrow(NoSuchElementException::new);
                maxNumber++;
                File mapUndoStorage = Storage.getMapUndoStorage(mapName + "_" + maxNumber + Constants.TXT);
                CopyOption[] options = { StandardCopyOption.REPLACE_EXISTING };
                Files.copy(originalMapFile.toPath(), mapUndoStorage.toPath(), options);
            } catch (Exception e) {
                BotLogger.log("Error trying to make undo copy for map: " + mapName, e);
            }
        }
    }

    private static void saveMapInfo(FileWriter writer, Game activeGame, boolean keepModifiedDate) throws IOException {
        writer.write(MAPINFO);
        writer.write(System.lineSeparator());

        writer.write(GAMEINFO);
        writer.write(System.lineSeparator());
        // game information
        writer.write(Constants.LATEST_COMMAND + " " + activeGame.getLatestCommand());
        writer.write(System.lineSeparator());
        writer.write(Constants.PHASE_OF_GAME + " " + activeGame.getCurrentPhase());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_OUTCOME_VOTED_FOR + " " + activeGame.getLatestOutcomeVotedFor());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_AFTER_MSG + " " + activeGame.getLatestAfterMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_WHEN_MSG + " " + activeGame.getLatestWhenMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_TRANSACTION_MSG + " " + activeGame.getLatestTransactionMsg());
        writer.write(System.lineSeparator());
        writer.write(Constants.LATEST_UPNEXT_MSG + " " + activeGame.getLatestUpNextMsg());
        writer.write(System.lineSeparator());

        writer.write(Constants.SO + " " + String.join(",", activeGame.getSecretObjectives()));
        writer.write(System.lineSeparator());

        writer.write(Constants.MESSAGEID_FOR_SABOS + " " + String.join(",", activeGame.getMessageIDsForSabo()));
        writer.write(System.lineSeparator());

        writer.write(Constants.AC + " " + String.join(",", activeGame.getActionCards()));
        writer.write(System.lineSeparator());

        writeCards(activeGame.getDiscardActionCards(), writer, Constants.AC_DISCARDED);
        writeCards(activeGame.getPurgedActionCards(), writer, Constants.AC_PURGED);

        writer.write(Constants.EXPLORE + " " + String.join(",", activeGame.getAllExplores()));
        writer.write(System.lineSeparator());

        writer.write(Constants.RELICS + " " + String.join(",", activeGame.getAllRelics()));
        writer.write(System.lineSeparator());

        writer.write(Constants.DISCARDED_EXPLORES + " " + String.join(",", activeGame.getAllExploreDiscard()));
        writer.write(System.lineSeparator());

        writer.write(Constants.SPEAKER + " " + activeGame.getSpeaker());
        writer.write(System.lineSeparator());

        writer.write(Constants.ACTIVE_PLAYER + " " + activeGame.getActivePlayerID());
        writer.write(System.lineSeparator());
        writer.write(Constants.ACTIVE_SYSTEM + " " + activeGame.getActiveSystem());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_ACTIVE_PLAYER_PING + " " + activeGame.getLastActivePlayerPing().getTime());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_TIME_GAMES_CHECKED + " " + activeGame.getLastTimeGamesChecked().getTime());
        writer.write(System.lineSeparator());

        writer.write(Constants.AUTO_PING + " " + activeGame.getAutoPingSpacer());
        writer.write(System.lineSeparator());

        writer.write(
                Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_AFTER + " " + activeGame.getPlayersWhoHitPersistentNoAfter());
        writer.write(System.lineSeparator());

        writer.write(
                Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_WHEN + " " + activeGame.getPlayersWhoHitPersistentNoWhen());
        writer.write(System.lineSeparator());

        writer.write(Constants.CURRENT_AGENDA_INFO + " " + activeGame.getCurrentAgendaInfo());
        writer.write(System.lineSeparator());
        writer.write(Constants.CURRENT_ACDRAWSTATUS_INFO + " " + activeGame.getACDrawStatusInfo());
        writer.write(System.lineSeparator());

        writer.write(Constants.LAST_ACTIVE_PLAYER_CHANGE + " " + activeGame.getLastActivePlayerChange().getTime());
        writer.write(System.lineSeparator());

        Map<Integer, Boolean> scPlayed = activeGame.getScPlayed();

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Boolean> entry : scPlayed.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_PLAYED + " " + sb);
        writer.write(System.lineSeparator());

        Map<String, String> agendaVoteInfo = activeGame.getCurrentAgendaVotes();
        StringBuilder sb2 = new StringBuilder();
        for (Map.Entry<String, String> entry : agendaVoteInfo.entrySet()) {
            sb2.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.AGENDA_VOTE_INFO + " " + sb2);
        writer.write(System.lineSeparator());

        Map<String, String> currentCheckingForAllReacts = activeGame.getMessagesThatICheckedForAllReacts();
        sb2 = new StringBuilder();
        for (Map.Entry<String, String> entry : currentCheckingForAllReacts.entrySet()) {
            sb2.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.CHECK_REACTS_INFO + " " + sb2);
        writer.write(System.lineSeparator());

        Map<String, Integer> displaced1System = activeGame.getCurrentMovedUnitsFrom1System();
        StringBuilder sb3 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : displaced1System.entrySet()) {
            sb3.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.DISPLACED_UNITS_SYSTEM + " " + sb3);
        writer.write(System.lineSeparator());

        Map<String, Integer> thalnosUnits = activeGame.getThalnosUnits();
        StringBuilder sb16 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : thalnosUnits.entrySet()) {
            sb16.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.THALNOS_UNITS + " " + sb16);
        writer.write(System.lineSeparator());

        Map<String, Integer> slashCommands = activeGame.getAllSlashCommandsUsed();
        StringBuilder sb10 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : slashCommands.entrySet()) {
            sb10.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.SLASH_COMMAND_STRING + " " + sb10);
        writer.write(System.lineSeparator());

        Map<String, Integer> acSabod = activeGame.getAllActionCardsSabod();
        StringBuilder sb11 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : acSabod.entrySet()) {
            sb11.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.ACS_SABOD + " " + sb11);
        writer.write(System.lineSeparator());

        Map<String, Integer> displacedActivation = activeGame.getMovedUnitsFromCurrentActivation();
        StringBuilder sb4 = new StringBuilder();
        for (Map.Entry<String, Integer> entry : displacedActivation.entrySet()) {
            sb4.append(entry.getKey()).append(",").append(entry.getValue()).append(":");
        }
        writer.write(Constants.DISPLACED_UNITS_ACTIVATION + " " + sb4);
        writer.write(System.lineSeparator());

        writer.write(Constants.AGENDAS + " " + String.join(",", activeGame.getAgendas()));
        writer.write(System.lineSeparator());

        writeCards(activeGame.getDiscardAgendas(), writer, Constants.DISCARDED_AGENDAS);
        writeCards(activeGame.getSentAgendas(), writer, Constants.SENT_AGENDAS);
        writeCards(activeGame.getLaws(), writer, Constants.LAW);
        writeCards(activeGame.getEventsInEffect(), writer, Constants.EVENTS_IN_EFFECT);

        writer.write(Constants.EVENTS + " " + String.join(",", activeGame.getEvents()));
        writer.write(System.lineSeparator());
        writeCards(activeGame.getDiscardedEvents(), writer, Constants.DISCARDED_EVENTS);

        sb = new StringBuilder();
        for (Map.Entry<String, String> entry : activeGame.getLawsInfo().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.LAW_INFO + " " + sb);
        writer.write(System.lineSeparator());

        sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : activeGame.getScTradeGoods().entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(Constants.SC_TRADE_GOODS + " " + sb);
        writer.write(System.lineSeparator());

        writeCards(activeGame.getRevealedPublicObjectives(), writer, Constants.REVEALED_PO);
        writeCards(activeGame.getCustomPublicVP(), writer, Constants.CUSTOM_PO_VP);
        writer.write(Constants.PO1 + " " + String.join(",", activeGame.getPublicObjectives1()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO2 + " " + String.join(",", activeGame.getPublicObjectives2()));
        writer.write(System.lineSeparator());
        writer.write(Constants.SO_TO_PO + " " + String.join(",", activeGame.getSoToPoList()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PURGED_PN + " " + String.join(",", activeGame.getPurgedPN()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO1PEAKABLE + " " + String.join(",", activeGame.getPublicObjectives1Peakable()));
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_BUTTONS + " " + String.join(",", activeGame.getSavedButtons()));
        writer.write(System.lineSeparator());
        writer.write(Constants.PO2PEAKABLE + " " + String.join(",", activeGame.getPublicObjectives2Peakable()));
        writer.write(System.lineSeparator());

        DisplayType displayTypeForced = activeGame.getDisplayTypeForced();
        if (displayTypeForced != null) {
            writer.write(Constants.DISPLAY_TYPE + " " + displayTypeForced.getValue());
            writer.write(System.lineSeparator());
        }

        writer.write(Constants.SC_COUNT_FOR_MAP + " " + activeGame.getStrategyCardsPerPlayer());
        writer.write(System.lineSeparator());

        writer.write(Constants.PLAYER_COUNT_FOR_MAP + " " + activeGame.getPlayerCountForMap());
        writer.write(System.lineSeparator());

        writer.write(Constants.VP_COUNT + " " + activeGame.getVp());
        writer.write(System.lineSeparator());
        writer.write(Constants.MAX_SO_COUNT + " " + activeGame.getMaxSOCountPerPlayer());
        writer.write(System.lineSeparator());

        StringBuilder sb1 = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : activeGame.getScoredPublicObjectives().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            sb1.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.SCORED_PO + " " + sb1);
        writer.write(System.lineSeparator());

        StringBuilder adjacentTiles = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : activeGame.getCustomAdjacentTiles().entrySet()) {
            String userIds = String.join("-", entry.getValue());
            adjacentTiles.append(entry.getKey()).append(",").append(userIds).append(";");
        }
        writer.write(Constants.CUSTOM_ADJACENT_TILES + " " + adjacentTiles);
        writer.write(System.lineSeparator());
        writer.write(Constants.REVERSE_SPEAKER_ORDER + " " + activeGame.isReverseSpeakerOrder());
        writer.write(System.lineSeparator());

        StringBuilder adjacencyOverrides = new StringBuilder();
        for (Map.Entry<Pair<String, Integer>, String> entry : activeGame.getAdjacentTileOverrides().entrySet()) {
            adjacencyOverrides.append(entry.getKey().getLeft()).append("-");
            adjacencyOverrides.append(entry.getKey().getRight()).append("-");
            adjacencyOverrides.append(entry.getValue()).append(";");
        }
        writer.write(Constants.ADJACENCY_OVERRIDES + " " + adjacencyOverrides);
        writer.write(System.lineSeparator());

        writer.write(Constants.CREATION_DATE + " " + activeGame.getCreationDate());
        writer.write(System.lineSeparator());
        long time = keepModifiedDate ? activeGame.getLastModifiedDate() : new Date().getTime();
        activeGame.setLastModifiedDate(time);
        writer.write(Constants.LAST_MODIFIED_DATE + " " + time);
        writer.write(System.lineSeparator());
        writer.write(Constants.ENDED_DATE + " " + activeGame.getEndedDate());
        writer.write(System.lineSeparator());
        writer.write(Constants.ROUND + " " + activeGame.getRound());
        writer.write(System.lineSeparator());
        writer.write(Constants.BUTTON_PRESS_COUNT + " " + activeGame.getButtonPressCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.SLASH_COMMAND_COUNT + " " + activeGame.getSlashCommandsRunCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.GAME_CUSTOM_NAME + " " + activeGame.getCustomName());
        writer.write(System.lineSeparator());

        writer.write(Constants.TABLE_TALK_CHANNEL + " " + activeGame.getTableTalkChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.MAIN_GAME_CHANNEL + " " + activeGame.getMainGameChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_CHANNEL + " " + activeGame.getSavedChannelID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SAVED_MESSAGE + " " + activeGame.getSavedMessage());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_MAP_CHANNEL + " " + activeGame.getBotMapUpdatesThreadID());
        writer.write(System.lineSeparator());

        // GAME MODES
        writer.write(Constants.TIGL_GAME + " " + activeGame.isCompetitiveTIGLGame());
        writer.write(System.lineSeparator());
        writer.write(Constants.COMMUNITY_MODE + " " + activeGame.isCommunityMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.ALLIANCE_MODE + " " + activeGame.isAllianceMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.FOW_MODE + " " + activeGame.isFoWMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.NAALU_AGENT + " " + activeGame.getNaaluAgent());
        writer.write(System.lineSeparator());
        writer.write(Constants.L1_HERO + " " + activeGame.getL1Hero());
        writer.write(System.lineSeparator());
        writer.write(Constants.NOMAD_COIN + " " + activeGame.getNomadCoin());
        writer.write(System.lineSeparator());
        writer.write(Constants.UNDO_BUTTON + " " + activeGame.getUndoButton());
        writer.write(System.lineSeparator());
        writer.write(Constants.FAST_SC_FOLLOW + " " + activeGame.isFastSCFollowMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.QUEUE_SO + " " + activeGame.getQueueSO());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_BUBBLES + " " + activeGame.getShowBubbles());
        writer.write(System.lineSeparator());
        writer.write(Constants.HOMEBREW_MODE + " " + activeGame.isHomeBrew());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_GEARS + " " + activeGame.getShowGears());
        writer.write(System.lineSeparator());
        writer.write(Constants.PURGED_FRAGMENTS + " " + activeGame.getNumberOfPurgedFragments());
        writer.write(System.lineSeparator());
        writer.write(Constants.TEMPORARY_PING_DISABLE + " " + activeGame.getTemporaryPingDisable());
        writer.write(System.lineSeparator());
        writer.write(Constants.DOMINUS_ORB + " " + activeGame.getDominusOrbStatus());
        writer.write(System.lineSeparator());
        writer.write(Constants.COMPONENT_ACTION + " " + activeGame.getComponentAction());
        writer.write(System.lineSeparator());
        writer.write(Constants.JUST_PLAYED_COMPONENT_AC + " " + activeGame.getJustPlayedComponentAC());
        writer.write(System.lineSeparator());
        writer.write(Constants.ACTIVATION_COUNT + " " + activeGame.getActivationCount());
        writer.write(System.lineSeparator());
        writer.write(Constants.BASE_GAME_MODE + " " + activeGame.isBaseGameMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.LIGHT_FOG_MODE + " " + activeGame.isLightFogMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.RED_TAPE_MODE + " " + activeGame.isRedTapeMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.STRAT_PINGS + " " + activeGame.isStratPings());
        writer.write(System.lineSeparator());
        writer.write(Constants.ABSOL_MODE + " " + activeGame.isAbsolMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.MILTYMOD_MODE + " " + activeGame.isMiltyModMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.TEXT_SIZE + " " + activeGame.getTextSize());
        writer.write(System.lineSeparator());
        writer.write(Constants.DISCORDANT_STARS_MODE + " " + activeGame.isDiscordantStarsMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.VERBOSITY + " " + activeGame.getOutputVerbosity());
        writer.write(System.lineSeparator());
        writer.write(Constants.BETA_TEST_MODE + " " + activeGame.isTestBetaFeaturesMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_FULL_COMPONENT_TEXT + " " + activeGame.isShowFullComponentTextEmbeds());
        writer.write(System.lineSeparator());
        writer.write(Constants.HACK_ELECTION_STATUS + " " + activeGame.getHackElectionStatus());
        writer.write(System.lineSeparator());
        writer.write(Constants.CC_N_PLASTIC_LIMIT + " " + activeGame.getCCNPlasticLimit());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_FACTION_REACTS + " " + activeGame.getBotFactionReacts());
        writer.write(System.lineSeparator());
        writer.write(Constants.HAS_HAD_A_STATUS_PHASE + " " + activeGame.getHasHadAStatusPhase());
        writer.write(System.lineSeparator());
        writer.write(Constants.BOT_SHUSHING + " " + activeGame.getBotShushing());
        writer.write(System.lineSeparator());
        writer.write(Constants.HOMEBREW_SC_MODE + " " + activeGame.isHomeBrewSCMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.INJECT_RULES_LINKS + " " + activeGame.isInjectRulesLinks());
        writer.write(System.lineSeparator());
        writer.write(Constants.SPIN_MODE + " " + activeGame.isSpinMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_UNIT_TAGS + " " + activeGame.isShowUnitTags());
        writer.write(System.lineSeparator());

        writer.write(Constants.AC_DECK_ID + " " + activeGame.getAcDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.SO_DECK_ID + " " + activeGame.getSoDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.STAGE_1_PUBLIC_DECK_ID + " " + activeGame.getStage1PublicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.STAGE_2_PUBLIC_DECK_ID + " " + activeGame.getStage2PublicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.TECH_DECK_ID + " " + activeGame.getTechnologyDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.RELIC_DECK_ID + " " + activeGame.getRelicDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.AGENDA_DECK_ID + " " + activeGame.getAgendaDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.EVENT_DECK_ID + " " + activeGame.getEventDeckID());
        writer.write(System.lineSeparator());
        writer.write(Constants.EXPLORATION_DECK_ID + " " + activeGame.getExplorationDeckID());
        writer.write(System.lineSeparator());

        writer.write(Constants.BAG_DRAFT + " "
                + (activeGame.getActiveBagDraft() == null ? "" : activeGame.getActiveBagDraft().getSaveString()));
        writer.write(System.lineSeparator());

        writer.write(Constants.STRATEGY_CARD_SET + " " + activeGame.getScSetID());
        writer.write(System.lineSeparator());

        ObjectMapper mapper = new ObjectMapper();
        String anomaliesJson = mapper.writeValueAsString(activeGame.getBorderAnomalies()); // much easier than manually
                                                                                           // (de)serialising
        writer.write(Constants.BORDER_ANOMALIES + " " + anomaliesJson);
        writer.write(System.lineSeparator());

        writer.write(Constants.GAME_HAS_ENDED + " " + activeGame.isHasEnded());
        writer.write(System.lineSeparator());

        writer.write(Constants.IMAGE_GEN_COUNT + " " + activeGame.getMapImageGenerationCount());
        writer.write(System.lineSeparator());

        writer.write(Constants.RUN_DATA_MIGRATIONS + " " + String.join(",", activeGame.getRunMigrations()));
        writer.write(System.lineSeparator());

        writer.write(ENDGAMEINFO);
        writer.write(System.lineSeparator());

        // Player information
        writer.write(PLAYERINFO);
        writer.write(System.lineSeparator());
        Map<String, Player> players = activeGame.getPlayers();
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
            writer.write(Constants.FACTION_DISPLAY_NAME + " " + player.getDisplayName());
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
            writer.write(Constants.TEAMMATE_IDS + " " + String.join(",", player.getTeamMateIDs()));
            writer.write(System.lineSeparator());
            writer.write(Constants.TECH_EXHAUSTED + " " + String.join(",", player.getExhaustedTechs()));
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
            writer.write(Constants.PERSONAL_PING_INTERVAL + " " + player.getPersonalPingInterval());
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
                    units.append(entry.getKey().outputForSave()).append(",").append(entry.getValue()).append(";");
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
            writer.write(Constants.STRATEGY_CARD + " "
                    + String.join(",", player.getSCs().stream().map(String::valueOf).toList()));
            writer.write(System.lineSeparator());
            writer.write(Constants.FOLLOWED_SC + " "
                    + String.join(",", player.getFollowedSCs().stream().map(String::valueOf).toList()));
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
                fogOfWarSystems.append(label == null || "".equals(label) ? "." : label);
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

            writer.write(ENDPLAYER);
            writer.write(System.lineSeparator());
        }

        writer.write(ENDPLAYERINFO);
        writer.write(System.lineSeparator());

        writer.write(ENDMAPINFO);
        writer.write(System.lineSeparator());
    }

    private static void writeCards(Map<String, Integer> cardList, FileWriter writer, String saveID) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : cardList.entrySet()) {
            sb.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        writer.write(saveID + " " + sb);
        writer.write(System.lineSeparator());
    }

    private static void writeCardsStrings(Map<String, String> cardList, FileWriter writer, String saveID)
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

    private static File[] readAllMapFiles() {
        File folder = Storage.getMapImageDirectory();
        if (!folder.exists()) {
            try {
                if (folder.createNewFile()) {
                    folder = Storage.getMapImageDirectory();
                }
            } catch (IOException e) {
                BotLogger.log("Could not create folder for maps", e);
            }

        }
        return folder.listFiles();
    }

    private static File[] readAllMapJSONFiles() {
        File folder = Storage.getMapsJSONDirectory();
        if (!folder.exists()) {
            try {
                if (folder.createNewFile()) {
                    folder = Storage.getMapImageDirectory();
                }
            } catch (IOException e) {
                BotLogger.log("Could not create folder for maps", e);
            }

        }
        return folder.listFiles();
    }

    private static boolean isTxtExtention(File file) {
        return file.getAbsolutePath().endsWith(TXT);
    }

    private static boolean isJSONExtention(File file) {
        return file.getAbsolutePath().endsWith(JSON);
    }

    public static boolean deleteMap(String mapName) {
        File mapStorage = Storage.getMapStorage(mapName + TXT);
        if (mapStorage == null) {
            return false;
        }
        File deletedMapStorage = Storage.getDeletedMapStorage(mapName + "_" + System.currentTimeMillis() + TXT);
        return mapStorage.renameTo(deletedMapStorage);
    }

    public static void loadMaps() {
        Map<String, Game> mapList = new HashMap<>();
        if (loadFromJSON) {
            File[] jsonfiles = readAllMapJSONFiles();
            if (jsonfiles != null) {
                for (File file : jsonfiles) {
                    if (isJSONExtention(file)) {
                        try {
                            Game activeGame = loadMapJSON(file);
                            if (activeGame != null) {
                                mapList.put(activeGame.getName(), activeGame);
                            }
                        } catch (Exception e) {
                            BotLogger.log("Could not load JSON game:" + file, e);
                        }
                    }
                }
            }
        } else {
            File[] txtFiles = readAllMapFiles();
            if (txtFiles != null) {
                for (File file : txtFiles) {
                    if (isTxtExtention(file)) {
                        try {
                            Game activeGame = loadMap(file);
                            if (activeGame != null && activeGame.getName() != null) {
                                mapList.put(activeGame.getName(), activeGame);
                            }
                        } catch (Exception e) {
                            BotLogger.log("Could not load TXT game:" + file, e);
                        }
                    }
                }
            }
        }

        GameManager.getInstance().setGameNameToGame(mapList);
    }

    @Nullable
    private static Game loadMapJSON(File mapFile) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new SimpleModule().addKeyDeserializer(Pair.class, new MapPairKeyDeserializer()));
        try {
            return mapper.readValue(mapFile, Game.class);
        } catch (Exception e) {
            BotLogger.log(mapFile.getName() + "JSON FAILED TO LOAD", e);
            // System.out.println(mapFile.getAbsolutePath());
            // System.out.println(ExceptionUtils.getStackTrace(e));
        }

        return null;
    }

    public static Game loadMapJSONString(String mapFile) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new SimpleModule().addKeyDeserializer(Pair.class, new MapPairKeyDeserializer()));
        try {
            return mapper.readValue(mapFile, Game.class);
        } catch (Exception e) {
            BotLogger.log("JSON STRING FAILED TO LOAD", e);
            // System.out.println(mapFile.getAbsolutePath());
            // System.out.println(ExceptionUtils.getStackTrace(e));
        }

        return null;
    }

    @Nullable
    public static Game loadMap(File mapFile) {
        if (mapFile != null) {
            Game activeGame = new Game();
            try (Scanner myReader = new Scanner(mapFile)) {
                activeGame.setOwnerID(myReader.nextLine());
                activeGame.setOwnerName(myReader.nextLine());
                activeGame.setName(myReader.nextLine());
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    if (MAPINFO.equals(data)) {
                        continue;
                    }
                    if (ENDMAPINFO.equals(data)) {
                        break;
                    }

                    while (myReader.hasNextLine()) {
                        data = myReader.nextLine();
                        if (GAMEINFO.equals(data)) {
                            continue;
                        }
                        if (ENDGAMEINFO.equals(data)) {
                            break;
                        }
                        try {
                            readGameInfo(activeGame, data);
                        } catch (Exception e) {
                            BotLogger.log("Data is bad: " + activeGame.getName(), e);
                        }
                    }

                    while (myReader.hasNextLine()) {
                        String tmpData = myReader.nextLine();
                        if (PLAYERINFO.equals(tmpData)) {
                            continue;
                        }
                        if (ENDPLAYERINFO.equals(tmpData)) {
                            break;
                        }
                        Player player = null;
                        while (myReader.hasNextLine()) {
                            data = tmpData != null ? tmpData : myReader.nextLine();
                            tmpData = null;
                            if (PLAYER.equals(data)) {

                                player = activeGame.addPlayerLoad(myReader.nextLine(), myReader.nextLine());
                                continue;
                            }
                            if (ENDPLAYER.equals(data)) {
                                break;
                            }
                            readPlayerInfo(player, data, activeGame);
                        }
                    }
                }
                Map<String, Tile> tileMap = new HashMap<>();
                try {
                    while (myReader.hasNextLine()) {
                        String tileData = myReader.nextLine();
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
                            BotLogger.log("Error loading Map: `" + activeGame.getName() + "` -> Tile is null: `"
                                    + tileData + "` - tile will be skipped - check save file");
                        }

                        while (myReader.hasNextLine()) {
                            String tmpData = myReader.nextLine();
                            if (UNITHOLDER.equals(tmpData)) {
                                continue;
                            }
                            if (ENDUNITHOLDER.equals(tmpData)) {
                                break;
                            }
                            String spaceHolder = null;
                            while (myReader.hasNextLine()) {
                                String data = tmpData != null ? tmpData : myReader.nextLine();
                                tmpData = null;
                                if (UNITS.equals(data)) {
                                    spaceHolder = myReader.nextLine().toLowerCase();
                                    if (tile != null) {
                                        if (Constants.MIRAGE.equals(spaceHolder)) {
                                            Helper.addMirageToTile(tile);
                                        } else if (!tile.isSpaceHolderValid(spaceHolder)) {
                                            BotLogger.log(activeGame.getName() + ": Not valid space holder detected: "
                                                    + spaceHolder);
                                        }
                                    }
                                    continue;
                                }
                                if (ENDUNITS.equals(data)) {
                                    break;
                                }
                                readUnit(tile, data, spaceHolder);
                            }

                            while (myReader.hasNextLine()) {
                                String data = myReader.nextLine();
                                if (UNITDAMAGE.equals(data)) {
                                    continue;
                                }
                                if (ENDUNITDAMAGE.equals(data)) {
                                    break;
                                }
                                readUnitDamage(tile, data, spaceHolder);
                            }

                            while (myReader.hasNextLine()) {
                                String data = myReader.nextLine();
                                if (PLANET_TOKENS.equals(data)) {
                                    continue;
                                }
                                if (PLANET_ENDTOKENS.equals(data)) {
                                    break;
                                }
                                readPlanetTokens(tile, data, spaceHolder);
                            }
                        }

                        while (myReader.hasNextLine()) {
                            String data = myReader.nextLine();
                            if (TOKENS.equals(data)) {
                                continue;
                            }
                            if (ENDTOKENS.equals(data)) {
                                break;
                            }
                            readTokens(tile, data);
                        }
                    }
                } catch (Exception e) {
                    BotLogger.log("Data read error: " + mapFile.getName(), e);
                }
                activeGame.setTileMap(tileMap);
            } catch (FileNotFoundException e) {
                BotLogger.log("File not found to read map data: " + mapFile.getName(), e);
            } catch (Exception e) {
                BotLogger.log("Data read error: " + mapFile.getName(), e);
            }

            activeGame.endGameIfOld();
            return activeGame;
        } else {
            BotLogger.log("Could not save map, error creating save file");
        }
        return null;
    }

    private static void readGameInfo(Game activeGame, String data) {
        String[] tokenizer = data.split(" ", 2);
        if (tokenizer.length == 2) {
            String identification = tokenizer[0];
            String info = tokenizer[1];
            switch (identification) {
                case Constants.LATEST_COMMAND -> activeGame.setLatestCommand(info);
                case Constants.LATEST_OUTCOME_VOTED_FOR -> activeGame.setLatestOutcomeVotedFor(info);
                case Constants.LATEST_AFTER_MSG -> activeGame.setLatestAfterMsg(info);
                case Constants.LATEST_WHEN_MSG -> activeGame.setLatestWhenMsg(info);
                case Constants.LATEST_TRANSACTION_MSG -> activeGame.setLatestTransactionMsg(info);
                case Constants.PHASE_OF_GAME -> activeGame.setCurrentPhase(info);
                case Constants.LATEST_UPNEXT_MSG -> activeGame.setLatestUpNextMsg(info);
                case Constants.SO -> activeGame.setSecretObjectives(getCardList(info));
                case Constants.MESSAGEID_FOR_SABOS -> activeGame.setMessageIDForSabo(getCardList(info));
                case Constants.AC -> activeGame.setActionCards(getCardList(info));
                case Constants.PO1 -> activeGame.setPublicObjectives1(getCardList(info));
                case Constants.PO2 -> activeGame.setPublicObjectives2(getCardList(info));
                case Constants.PO1PEAKABLE -> activeGame.setPublicObjectives1Peakable(getCardList(info));
                case Constants.SAVED_BUTTONS -> activeGame.setSavedButtons(getCardList(info));
                case Constants.PO2PEAKABLE -> activeGame.setPublicObjectives2Peakable(getCardList(info));
                case Constants.SO_TO_PO -> activeGame.setSoToPoList(getCardList(info));
                case Constants.PURGED_PN -> activeGame.setPurgedPNs(getCardList(info));
                case Constants.REVEALED_PO -> activeGame.setRevealedPublicObjectives(getParsedCards(info));
                case Constants.CUSTOM_PO_VP -> activeGame.setCustomPublicVP(getParsedCards(info));
                case Constants.SCORED_PO -> activeGame.setScoredPublicObjectives(getParsedCardsForScoredPO(info));
                case Constants.AC_DECK_ID -> activeGame.setAcDeckID(info);
                case Constants.SO_DECK_ID -> activeGame.setSoDeckID(info);
                case Constants.STAGE_1_PUBLIC_DECK_ID -> activeGame.setStage1PublicDeckID(info);
                case Constants.STAGE_2_PUBLIC_DECK_ID -> activeGame.setStage2PublicDeckID(info);
                case Constants.TECH_DECK_ID -> activeGame.setTechnologyDeckID(info);
                case Constants.RELIC_DECK_ID -> activeGame.setRelicDeckID(info);
                case Constants.AGENDA_DECK_ID -> activeGame.setAgendaDeckID(info);
                case Constants.EVENT_DECK_ID -> activeGame.setEventDeckID(info);
                case Constants.EXPLORATION_DECK_ID -> activeGame.setExplorationDeckID(info);
                case Constants.STRATEGY_CARD_SET -> activeGame.setScSetID(info);
                case Constants.CUSTOM_ADJACENT_TILES -> {
                    Map<String, List<String>> adjacentTiles = getParsedCardsForScoredPO(info);
                    Map<String, List<String>> adjacentTilesMigrated = new LinkedHashMap<>();
                    for (Map.Entry<String, List<String>> entry : adjacentTiles.entrySet()) {
                        String key = entry.getKey();
                        List<String> migrated = new ArrayList<>(entry.getValue());
                        adjacentTilesMigrated.put(key, migrated);
                    }

                    activeGame.setCustomAdjacentTiles(adjacentTilesMigrated);
                }
                case Constants.BORDER_ANOMALIES -> {
                    if ("[]".equals(info))
                        break;
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        activeGame.setBorderAnomalies(mapper.readValue(info, new TypeReference<>() {
                        }));
                    } catch (Exception e) {
                        BotLogger.log("Error reading border anomalies from save file!", e);
                    }
                }
                case Constants.ADJACENCY_OVERRIDES -> {
                    try {
                        activeGame.setAdjacentTileOverride(getParsedAdjacencyOverrides(info));
                    } catch (Exception e) {
                        BotLogger.log("Failed to load adjacency overrides", e);
                    }
                }
                case Constants.REVERSE_SPEAKER_ORDER -> activeGame.setReverseSpeakerOrder("true".equals(info));
                case Constants.AGENDAS -> activeGame.setAgendas(getCardList(info));
                case Constants.AC_DISCARDED -> activeGame.setDiscardActionCards(getParsedCards(info));
                case Constants.AC_PURGED -> activeGame.setPurgedActionCards(getParsedCards(info));
                case Constants.DISCARDED_AGENDAS -> activeGame.setDiscardAgendas(getParsedCards(info));
                case Constants.SENT_AGENDAS -> activeGame.setSentAgendas(getParsedCards(info));
                case Constants.LAW -> activeGame.setLaws(getParsedCards(info));
                case Constants.EVENTS -> activeGame.setEvents(getCardList(info));
                case Constants.EVENTS_IN_EFFECT -> activeGame.setEventsInEffect(getParsedCards(info));
                case Constants.DISCARDED_EVENTS -> activeGame.setDiscardedEvents(getParsedCards(info));
                case Constants.EXPLORE -> activeGame.setExploreDeck(getCardList(info));
                case Constants.RELICS -> activeGame.setRelics(getCardList(info));
                case Constants.DISCARDED_EXPLORES -> activeGame.setExploreDiscard(getCardList(info));
                case Constants.LAW_INFO -> {
                    StringTokenizer actionCardToken = new StringTokenizer(info, ";");
                    Map<String, String> cards = new LinkedHashMap<>();
                    while (actionCardToken.hasMoreTokens()) {
                        StringTokenizer cardInfo = new StringTokenizer(actionCardToken.nextToken(), ",");
                        String id = cardInfo.nextToken();
                        String value = cardInfo.nextToken();
                        cards.put(id, value);
                    }
                    activeGame.setLawsInfo(cards);
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
                    activeGame.setScTradeGoods(scTradeGoods);
                }
                case Constants.SPEAKER -> activeGame.setSpeaker(info);
                case Constants.ACTIVE_PLAYER -> activeGame.setActivePlayer(info);
                case Constants.ACTIVE_SYSTEM -> activeGame.setActiveSystem(info);
                case Constants.LAST_ACTIVE_PLAYER_PING -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastPing = new Date(millis);
                        activeGame.setLastActivePlayerPing(lastPing);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.LAST_TIME_GAMES_CHECKED -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastGameCheck = new Date(millis);
                        activeGame.setLastTimeGamesChecked(lastGameCheck);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.AUTO_PING -> {
                    try {
                        int pnghrs = Integer.parseInt(info);
                        activeGame.setAutoPing(pnghrs != 0);
                        activeGame.setAutoPingSpacer(pnghrs);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.CURRENT_AGENDA_INFO -> {
                    try {
                        activeGame.setCurrentAgendaInfo(info);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.CURRENT_ACDRAWSTATUS_INFO -> {
                    try {
                        activeGame.setACDrawStatusInfo(info);
                    } catch (Exception e) {
                        // do nothing
                    }
                }

                case Constants.LAST_ACTIVE_PLAYER_CHANGE -> {
                    try {
                        long millis = Long.parseLong(info);
                        Date lastChange = new Date(millis);
                        activeGame.setLastActivePlayerChange(lastChange);
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                case Constants.PLAYER_COUNT_FOR_MAP -> {
                    try {
                        int playerCount = Integer.parseInt(info);
                        if (playerCount >= 1 && playerCount <= 30) {
                            activeGame.setPlayerCountForMap(playerCount);
                        } else {
                            activeGame.setPlayerCountForMap(6);
                        }
                    } catch (Exception e) {
                        activeGame.setPlayerCountForMap(6);
                    }
                }
                case Constants.SC_COUNT_FOR_MAP -> {
                    try {
                        int scCount = Integer.parseInt(info);
                        if (scCount >= 1 && scCount <= 8) {
                            activeGame.setStrategyCardsPerPlayer(scCount);
                        } else {
                            activeGame.setStrategyCardsPerPlayer(1);
                        }
                    } catch (Exception e) {
                        activeGame.setStrategyCardsPerPlayer(1);
                    }
                }
                case Constants.ACTIVATION_COUNT -> {
                    try {
                        int activationCount = Integer.parseInt(info);
                        activeGame.setActivationCount(activationCount);
                    } catch (Exception e) {
                        activeGame.setActivationCount(0);
                    }
                }
                case Constants.VP_COUNT -> {
                    try {
                        int vpCount = Integer.parseInt(info);
                        activeGame.setVp(vpCount);
                    } catch (Exception e) {
                        activeGame.setVp(10);
                    }
                }
                case Constants.MAX_SO_COUNT -> {
                    try {
                        int soCount = Integer.parseInt(info);
                        activeGame.setMaxSOCountPerPlayer(soCount);
                    } catch (Exception e) {
                        activeGame.setVp(3);
                    }
                }
                case Constants.DISPLAY_TYPE -> {
                    if (info.equals(DisplayType.stats.getValue())) {
                        activeGame.setDisplayTypeForced(DisplayType.stats);
                    } else if (info.equals(DisplayType.map.getValue())) {
                        activeGame.setDisplayTypeForced(DisplayType.map);
                    } else if (info.equals(DisplayType.all.getValue())) {
                        activeGame.setDisplayTypeForced(DisplayType.all);
                    }
                }
                case Constants.SC_PLAYED -> {
                    StringTokenizer scPlayed = new StringTokenizer(info, ";");
                    while (scPlayed.hasMoreTokens()) {
                        StringTokenizer dataInfo = new StringTokenizer(scPlayed.nextToken(), ",");
                        Integer scID = Integer.parseInt(dataInfo.nextToken());
                        Boolean status = Boolean.parseBoolean(dataInfo.nextToken());
                        activeGame.setSCPlayed(scID, status);
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
                            activeGame.setCurrentAgendaVote(outcome, voteInfo);
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
                            activeGame.setCurrentReacts(outcome, voteInfo);
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
                            activeGame.setSpecificCurrentMovedUnitsFrom1System(outcome, Integer.parseInt(voteInfo));
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
                            activeGame.setSpecificThalnosUnit(outcome, Integer.parseInt(voteInfo));
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
                            activeGame.setSpecificSlashCommandCount(commandName, Integer.parseInt(commandCount));
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
                            activeGame.setSpecificActionCardSaboCount(outcome, Integer.parseInt(voteInfo));
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
                            activeGame.setSpecificCurrentMovedUnitsFrom1TacticalAction(outcome,
                                    Integer.parseInt(voteInfo));
                        }
                    }
                }
                case Constants.GAME_CUSTOM_NAME -> activeGame.setCustomName(info);
                case Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_AFTER ->
                    activeGame.setPlayersWhoHitPersistentNoAfter(info);
                case Constants.PLAYERS_WHO_HIT_PERSISTENT_NO_WHEN -> activeGame.setPlayersWhoHitPersistentNoWhen(info);
                case Constants.TABLE_TALK_CHANNEL -> activeGame.setTableTalkChannelID(info);
                case Constants.MAIN_GAME_CHANNEL -> activeGame.setMainGameChannelID(info);
                case Constants.SAVED_CHANNEL -> activeGame.setSavedChannelID(info);
                case Constants.SAVED_MESSAGE -> activeGame.setSavedMessage(info);
                case Constants.BOT_MAP_CHANNEL -> activeGame.setBotMapUpdatesThreadID(info);

                // GAME MODES
                case Constants.TIGL_GAME -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setCompetitiveTIGLGame(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HACK_ELECTION_STATUS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setHackElectionStatus(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.CC_N_PLASTIC_LIMIT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setCCNPlasticLimit(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BOT_FACTION_REACTS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setBotFactionReactions(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HAS_HAD_A_STATUS_PHASE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setHasHadAStatusPhase(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BOT_SHUSHING -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setShushing(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.COMMUNITY_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setCommunityMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.ALLIANCE_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setAllianceMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.FOW_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setFoWMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.NAALU_AGENT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setNaaluAgent(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.L1_HERO -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setL1Hero(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.NOMAD_COIN -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setNomadCoin(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.UNDO_BUTTON -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setUndoButton(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.FAST_SC_FOLLOW -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setFastSCFollowMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.QUEUE_SO -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setQueueSO(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_BUBBLES -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setShowBubbles(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_GEARS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setShowGears(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HOMEBREW_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setHomeBrew(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.PURGED_FRAGMENTS -> {
                    try {
                        int value = Integer.parseInt(info);
                        activeGame.setNumberOfPurgedFragments(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.TEMPORARY_PING_DISABLE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setTemporaryPingDisable(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.DOMINUS_ORB -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setDominusOrb(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.COMPONENT_ACTION -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setComponentAction(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.JUST_PLAYED_COMPONENT_AC -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setJustPlayedComponentAC(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BASE_GAME_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setBaseGameMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.LIGHT_FOG_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setLightFogMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.RED_TAPE_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setRedTapeMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.HOMEBREW_SC_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setHomeBrewSCMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.INJECT_RULES_LINKS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setInjectRulesLinks(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SPIN_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setSpinMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_UNIT_TAGS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setShowUnitTags(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.STRAT_PINGS -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setStratPings(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.TEXT_SIZE -> {
                    try {
                        activeGame.setTextSize(info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.ABSOL_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setAbsolMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.MILTYMOD_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setMiltyModMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.DISCORDANT_STARS_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setDiscordantStarsMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.VERBOSITY -> {
                    try {
                        activeGame.setOutputVerbosity(info);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.BETA_TEST_MODE -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setTestBetaFeaturesMode(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.SHOW_FULL_COMPONENT_TEXT -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setShowFullComponentTextEmbeds(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.GAME_HAS_ENDED -> {
                    try {
                        boolean value = Boolean.parseBoolean(info);
                        activeGame.setHasEnded(value);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.CREATION_DATE -> activeGame.setCreationDate(info);
                case Constants.ROUND -> {
                    try {
                        activeGame.setRound(Integer.parseInt(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse round number", exception);
                    }
                }
                case Constants.BUTTON_PRESS_COUNT -> {
                    try {
                        activeGame.setButtonPressCount(Integer.parseInt(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse button press count", exception);
                    }
                }
                case Constants.LAST_MODIFIED_DATE -> {
                    try {
                        activeGame.setLastModifiedDate(Long.parseLong(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse last modified date", exception);
                    }
                }
                case Constants.ENDED_DATE -> {
                    try {
                        activeGame.setEndedDate(Long.parseLong(info));
                    } catch (Exception exception) {
                        BotLogger.log("Could not parse ended date", exception);
                    }
                }
                case Constants.IMAGE_GEN_COUNT -> {
                    try {
                        int count = Integer.parseInt(info);
                        activeGame.setMapImageGenerationCount(count);
                    } catch (Exception e) {
                        // Do nothing
                    }
                }
                case Constants.RUN_DATA_MIGRATIONS -> {
                    StringTokenizer migrationInfo = new StringTokenizer(info, ",");

                    while (migrationInfo.hasMoreTokens()) {
                        String migration = migrationInfo.nextToken();
                        activeGame.addMigration(migration);
                    }
                }
                case Constants.BAG_DRAFT -> {
                    try {
                        activeGame.setBagDraft(BagDraft.GenerateDraft(info, activeGame));
                    } catch (Exception e) {
                        // Do nothing
                    }
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

    private static void readPlayerInfo(Player player, String data, Game activeGame) {
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        if (tokenizer.countTokens() == 2) {
            data = tokenizer.nextToken();
            switch (data) {
                case Constants.FACTION -> player.setFaction(tokenizer.nextToken());
                case Constants.FACTION_EMOJI -> player.setFactionEmoji(tokenizer.nextToken());
                case Constants.FACTION_DISPLAY_NAME -> player.setDisplayName(tokenizer.nextToken());
                case Constants.COLOR -> player.setColor(tokenizer.nextToken());
                case Constants.DECAL_SET -> player.setDecalSet(tokenizer.nextToken());
                case Constants.STATS_ANCHOR_LOCATION -> player.setPlayerStatsAnchorPosition(tokenizer.nextToken());
                case Constants.ALLIANCE_MEMBERS -> player.setAllianceMembers(tokenizer.nextToken());
                case Constants.AFK_HOURS -> player.setHoursThatPlayerIsAFK(tokenizer.nextToken());
                case Constants.ROLE_FOR_COMMUNITY -> player.setRoleIDForCommunity(tokenizer.nextToken());
                case Constants.PLAYER_PRIVATE_CHANNEL -> player.setPrivateChannelID(tokenizer.nextToken());
                case Constants.TACTICAL -> player.setTacticalCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.FLEET -> player.setFleetCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.STRATEGY -> player.setStrategicCC(Integer.parseInt(tokenizer.nextToken()));
                case Constants.TG -> player.setTg(Integer.parseInt(tokenizer.nextToken()));
                case Constants.ACTUAL_HITS -> player.setActualHits(Integer.parseInt(tokenizer.nextToken()));
                case Constants.EXPECTED_HITS_TIMES_10 ->
                    player.setExpectedHitsTimes10(Integer.parseInt(tokenizer.nextToken()));
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
                case Constants.STRATEGY_CARD -> player.setSCs(new LinkedHashSet<>(
                        getCardList(tokenizer.nextToken()).stream().map(Integer::valueOf).collect(Collectors.toSet())));
                case Constants.FOLLOWED_SC -> player.setFollowedSCs(new HashSet<>(
                        getCardList(tokenizer.nextToken()).stream().map(Integer::valueOf).collect(Collectors.toSet())));
                case Constants.COMMODITIES_TOTAL -> player.setCommoditiesTotal(Integer.parseInt(tokenizer.nextToken()));
                case Constants.COMMODITIES -> player.setCommodities(Integer.parseInt(tokenizer.nextToken()));
                case Constants.PERSONAL_PING_INTERVAL ->
                    player.setPersonalPingInterval(Integer.parseInt(tokenizer.nextToken()));
                case Constants.STASIS_INFANTRY -> player.setStasisInfantry(Integer.parseInt(tokenizer.nextToken()));
                case Constants.AUTO_SABO_PASS_MEDIAN ->
                    player.setAutoSaboPassMedian(Integer.parseInt(tokenizer.nextToken()));
                case Constants.CAPTURE -> {
                    UnitHolder unitHolder = player.getNomboxTile().getUnitHolders().get(Constants.SPACE);
                    StringTokenizer unitTokens = new StringTokenizer(tokenizer.nextToken(), ";");
                    while (unitTokens.hasMoreTokens()) {
                        StringTokenizer unitInfo = new StringTokenizer(unitTokens.nextToken(), ",");
                        String id = unitInfo.nextToken();
                        UnitKey unitKey = Units.parseID(id);
                        Integer number = Integer.parseInt(unitInfo.nextToken());
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
                        if (activeGame.isAbsolMode() && id.endsWith("_ps") && !id.startsWith("absol_"))
                            id = "absol_" + id;
                        // END MIGRATE
                        Integer index = Integer.parseInt(pnInfo.nextToken());
                        player.setPromissoryNote(id, index);
                    }
                }
                case Constants.PROMISSORY_NOTES_OWNED ->
                    player.setPromissoryNotesOwned(new HashSet<>(Helper.getSetFromCSV(tokenizer.nextToken())));
                case Constants.PROMISSORY_NOTES_PLAY_AREA ->
                    player.setPromissoryNotesInPlayArea(getCardList(tokenizer.nextToken()));
                case Constants.UNITS_OWNED ->
                    player.setUnitsOwned(new HashSet<>(Helper.getSetFromCSV(tokenizer.nextToken())));
                case Constants.PLANETS -> player.setPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_EXHAUSTED -> player.setExhaustedPlanets(getCardList(tokenizer.nextToken()));
                case Constants.PLANETS_ABILITY_EXHAUSTED ->
                    player.setExhaustedPlanetsAbilities(getCardList(tokenizer.nextToken()));
                case Constants.TECH -> player.setTechs(getCardList(tokenizer.nextToken()));
                case Constants.SPENT_THINGS -> player.setSpentThings(getCardList(tokenizer.nextToken()));
                case Constants.TEAMMATE_IDS -> player.setTeamMateIDs(getCardList(tokenizer.nextToken()));
                case Constants.FACTION_TECH -> player.setFactionTechs(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_BAG -> player.loadCurrentDraftBag(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_QUEUE -> player.loadItemsToDraft(getCardList(tokenizer.nextToken()));
                case Constants.DRAFT_HAND -> player.loadDraftHand(getCardList(tokenizer.nextToken()));
                case Constants.ABILITIES -> player.setAbilities(new HashSet<>(getCardList(tokenizer.nextToken())));
                case Constants.TECH_EXHAUSTED -> player.setExhaustedTechs(getCardList(tokenizer.nextToken()));
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
                case Constants.READY_TO_PASS_BAG ->
                    player.setReadyToPassBag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.TEN_MIN_REMINDER ->
                    player.setWhetherPlayerShouldBeTenMinReminded(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.PREFERS_DISTANCE ->
                    player.setPreferenceForDistanceBasedTacticalActions(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.AUTO_PASS_WHENS_N_AFTERS ->
                    player.setAutoPassWhensAfters(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.SEARCH_WARRANT -> player.setSearchWarrant(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.DUMMY -> player.setDummy(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_CFRAG ->
                    player.setHasFoundCulFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_HFRAG ->
                    player.setHasFoundHazFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_IFRAG ->
                    player.setHasFoundIndFrag(Boolean.parseBoolean(tokenizer.nextToken()));
                case Constants.BENTOR_HAS_FOUND_UFRAG ->
                    player.setHasFoundUnkFrag(Boolean.parseBoolean(tokenizer.nextToken()));
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
        if (tile == null)
            return;
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

    private static void readTokens(Tile tile, String data) {
        if (tile == null)
            return;
        StringTokenizer tokenizer = new StringTokenizer(data, " ");
        // tile.setUnit(tokenizer.nextToken(), tokenizer.nextToken());
        // todo implement token read
    }

}
