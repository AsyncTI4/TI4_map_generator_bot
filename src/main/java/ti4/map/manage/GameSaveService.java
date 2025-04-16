package ti4.map.manage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.omegaPhase.PriorityTrackHelper;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
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
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
class GameSaveService {

    public static boolean save(Game game, String reason) {
        return GameFileLockManager.wrapWithWriteLock(game.getName(), () -> {
            game.setLatestCommand(Objects.requireNonNullElse(reason, "Command Unknown"));
            return save(game);
        });
    }

    private static boolean save(Game game) {
        TransientGameInfoUpdater.update(game);
        //Ugly fix to update seen tiles data for fog since doing it in 
        //MapGenerator/TileGenerator won't save changes anymore
        if (game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                FoWHelper.updateFog(game, player);
            }
        }

        File gameFile = Storage.getGameFile(game.getName() + Constants.TXT);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(gameFile))) {
            Map<String, Tile> tileMap = game.getTileMap();
            writer.write(game.getOwnerID());
            writer.write(System.lineSeparator());
            writer.write(game.getOwnerName());
            writer.write(System.lineSeparator());
            writer.write(game.getName());
            writer.write(System.lineSeparator());
            saveGameInfo(writer, game);

            for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
                Tile tile = tileEntry.getValue();
                saveTile(writer, tile);
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Could not save map: " + game.getName(), e);
            return false;
        }

        GameUndoService.createUndoCopy(game.getName());
        return true;
    }

    private static void saveGameInfo(Writer writer, Game game) throws IOException {
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

        writer.write(Constants.SO + " " + String.join(",", game.getSecretObjectives()));
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
        writer.write(Constants.ACTIVE_SYSTEM + " " + game.getCurrentActiveSystem());
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
        writer.write(Constants.PINGED_SYSTEMS + " " + String.join(",", game.getListOfTilesPinged()));
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
        long time = System.currentTimeMillis();
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
        for (Map.Entry<FOWOption, Boolean> entry : game.getFowOptions().entrySet()) {
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
        writer.write(Constants.CPTI_EXPLORE_MODE + " " + game.isCptiExploreMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.RED_TAPE_MODE + " " + game.isRedTapeMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.OMEGA_PHASE_MODE + " " + game.isOmegaPhaseMode());
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
        writer.write(Constants.SPIN_MODE + " " + game.getSpinMode());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_UNIT_TAGS + " " + game.isShowUnitTags());
        writer.write(System.lineSeparator());
        writer.write(Constants.SHOW_OWNED_PNS_IN_PLAYER_AREA + " " + game.isShowOwnedPNsInPlayerArea());
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

        MiltyDraftManager manager = game.getMiltyDraftManagerUnsafe();
        if (manager != null) {
            writer.write(Constants.MILTY_DRAFT_MANAGER + " " + manager.superSaveMessage());
            writer.write(System.lineSeparator());
        } else {
            writer.write(Constants.MILTY_DRAFT_MANAGER + " " + game.getMiltyDraftString());
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

            writer.write(Constants.AUTO_PASS_WHENS_N_AFTERS + " " + player.isAutoPassOnWhensAfters());
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
            writer.write(Constants.BENTOR_HAS_FOUND_CFRAG + " " + player.isHasFoundCulFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_HFRAG + " " + player.isHasFoundHazFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_IFRAG + " " + player.isHasFoundIndFrag());
            writer.write(System.lineSeparator());
            writer.write(Constants.BENTOR_HAS_FOUND_UFRAG + " " + player.isHasFoundUnkFrag());
            writer.write(System.lineSeparator());

            // LANEFIR ATS Armaments count
            writer.write(Constants.LANEFIR_ATS_COUNT + " " + player.getAtsCount());
            writer.write(System.lineSeparator());

            writer.write(Constants.PILLAGE_COUNT + " " + player.getPillageCounter());
            writer.write(System.lineSeparator());

            writer.write(Constants.SARWEEN_COUNT + " " + player.getSarweenCounter());
            writer.write(System.lineSeparator());

            writer.write(Constants.PATH_TOKEN_COUNT + " " + player.getPathTokenCounter());
            writer.write(System.lineSeparator());

            writer.write(Constants.HARVEST_COUNT + " " + player.getHarvestCounter());
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

            writer.write(Constants.TURN_COUNT + " " + player.getInRoundTurnCount());
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

            writer.write(Constants.NUMBER_OF_TURNS + " " + player.getNumberOfTurns());
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

            if (player.hasPriorityPosition()) {
                writer.write(Constants.PRIORITY_TRACK + " " + player.getPriorityPosition());
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

    private static void writeCardsStrings(Map<String, String> cardList, Writer writer, String saveID) throws IOException {
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
            BotLogger.error("Error trying to save peeked public objective(s): " + constant, e);
        }
    }

    public static boolean delete(String gameName) {
        return GameFileLockManager.wrapWithWriteLock(gameName, () -> {
            File mapStorage = Storage.getGameFile(gameName + Constants.TXT);
            if (!mapStorage.exists()) {
                return false;
            }
            File deletedMapStorage = Storage.getDeletedGame(gameName + "_" + System.currentTimeMillis() + Constants.TXT);
            return mapStorage.renameTo(deletedMapStorage);
        });
    }
}
