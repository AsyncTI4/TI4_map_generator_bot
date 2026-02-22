package ti4.spring.service.gamestats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;
import ti4.website.model.stats.GameStatsDashboardPayload;
import ti4.website.model.stats.PlayerStatsDashboardPayload;

@Service
@RequiredArgsConstructor
public class GameStatsDashboardPayloadPersistenceService {

    private final GameStatsDashboardPayloadRepository gameStatsDashboardPayloadRepository;

    public void persistAllGames() {
        List<String> activeGameNames = new ArrayList<>();
        int persistedRows = 0;

        for (ManagedGame managedGame : GameManager.getManagedGames()) {
            activeGameNames.add(managedGame.getName());
            try {
                GameStatsDashboardPayload payload = new GameStatsDashboardPayload(managedGame.getGame());
                GameStatsDashboardPayloadData row = toEntity(managedGame.getName(), payload);
                gameStatsDashboardPayloadRepository.deleteById(managedGame.getName());
                gameStatsDashboardPayloadRepository.save(row);
                persistedRows++;
            } catch (Exception e) {
                BotLogger.error(
                        String.format(
                                "Failed to persist GameStatsDashboardPayload for game: `%s`",
                                managedGame.getName()),
                        e);
            }
        }

        if (activeGameNames.isEmpty()) {
            gameStatsDashboardPayloadRepository.deleteAll();
        } else {
            gameStatsDashboardPayloadRepository.deleteByGameNameNotIn(activeGameNames);
        }

        BotLogger.info(String.format(
                "Persisted %,d game stats dashboard payload rows to SQLite.", persistedRows));
    }

    private GameStatsDashboardPayloadData toEntity(String gameName, GameStatsDashboardPayload payload) {
        GameStatsDashboardPayloadData row = new GameStatsDashboardPayloadData();
        row.setGameName(gameName);
        row.setAsyncGameId(payload.getAsyncGameID());
        row.setAsyncFunGameName(payload.getAsyncFunGameName());
        row.setHexSummary(payload.getHexSummary());
        row.setPok(payload.isPoK());
        row.setMapString(payload.getMapString());
        row.setPlatform(payload.getPlatform());
        row.setRound(payload.getRound());
        row.setScoreboard(payload.getScoreboard());
        row.setCreationEpochMilliseconds(payload.getCreationEpochMilliseconds());
        row.setEndedEpochMilliseconds(payload.getEndedEpochMilliseconds());
        row.setLastUpdatedEpochMilliseconds(payload.getLastUpdatedEpochMilliseconds());
        row.setSpeaker(payload.getSpeaker());
        row.setTimestamp(payload.getTimestamp());
        row.setEndedTimestamp(payload.getEndedTimestamp());
        row.setTurn(payload.getTurn());
        row.setCompleted(payload.isCompleted());
        row.setFractureInPlay(payload.isFractureInPlay());
        row.setHomebrew(payload.isHomebrew());
        row.setDiscordantStarsMode(payload.isDiscordantStarsMode());
        row.setAbsolMode(payload.isAbsolMode());
        row.setFrankenGame(payload.isFrankenGame());
        row.setAllianceMode(payload.isAllianceMode());
        row.setTiglGame(payload.isTiglGame());

        row.getLaws().addAll(payload.getLaws());
        row.getModes().addAll(payload.getModes());
        row.getWinners().addAll(payload.getWinners());
        row.getUnpickedStrategyCards().putAll(payload.getUnpickedStrategyCards());

        payload.getObjectives().forEach((category, objectiveNames) -> {
            for (String objectiveName : objectiveNames) {
                row.addObjective(category, objectiveName);
            }
        });

        for (PlayerStatsDashboardPayload playerPayload : payload.getPlayers()) {
            row.addPlayer(toPlayerEntity(playerPayload));
        }

        return row;
    }

    private GameStatsDashboardPlayerData toPlayerEntity(PlayerStatsDashboardPayload playerPayload) {
        GameStatsDashboardPlayerData playerRow = new GameStatsDashboardPlayerData();
        playerRow.setDiscordUserId(playerPayload.getDiscordUserID());
        playerRow.setDiscordUsername(playerPayload.getDiscordUsername());
        playerRow.setColorActual(playerPayload.getColorActual());

        Map<String, Integer> commandTokens = playerPayload.getCommandTokens();
        playerRow.setCommandTokenFleet(commandTokens.get("fleet"));
        playerRow.setCommandTokenStrategy(commandTokens.get("strategy"));
        playerRow.setCommandTokenTactics(commandTokens.get("tactics"));

        playerRow.setCommodities(playerPayload.getCommodities());
        playerRow.setMaxCommodities(playerPayload.getMaxCommodities());
        playerRow.setCustodianPoints(playerPayload.getCustodianPoints());
        playerRow.setFactionName(playerPayload.getFactionName());

        Map<String, Integer> handSummary = playerPayload.getHandSummary();
        playerRow.setHandSummarySecretObjectives(handSummary.get("Secret Objectives"));
        playerRow.setHandSummaryActions(handSummary.get("Actions"));
        playerRow.setHandSummaryPromissory(handSummary.get("Promissory"));

        var leaderPayload = playerPayload.getLeaders();
        playerRow.setLeaderHero(leaderPayload.getHero());
        playerRow.setLeaderCommander(leaderPayload.getCommander());

        Map<String, Object> planetTotals = playerPayload.getPlanetTotals();
        Map<String, Number> influence = mapValuesToNumbers(planetTotals.get("influence"));
        playerRow.setInfluenceAvailable(asInt(influence.get("avail")));
        playerRow.setInfluenceTotal(asInt(influence.get("total")));

        Map<String, Number> resources = mapValuesToNumbers(planetTotals.get("resources"));
        playerRow.setResourcesAvailable(asInt(resources.get("avail")));
        playerRow.setResourcesTotal(asInt(resources.get("total")));

        playerRow.setLegendaryCount(asInt((Number) planetTotals.get("legendary")));

        Map<String, Number> traits = mapValuesToNumbers(planetTotals.get("traits"));
        playerRow.setTraitsCultural(asInt(traits.get("cultural")));
        playerRow.setTraitsHazardous(asInt(traits.get("hazardous")));
        playerRow.setTraitsIndustrial(asInt(traits.get("industrial")));

        Map<String, Number> techs = mapValuesToNumbers(planetTotals.get("techs"));
        playerRow.setTechSpecialtyBlue(asInt(techs.get("blue")));
        playerRow.setTechSpecialtyGreen(asInt(techs.get("green")));
        playerRow.setTechSpecialtyRed(asInt(techs.get("red")));
        playerRow.setTechSpecialtyYellow(asInt(techs.get("yellow")));

        playerRow.setScore(playerPayload.getScore());
        playerRow.setTradeGoods(playerPayload.getTradeGoods());
        playerRow.setTurnOrder(playerPayload.getTurnOrder());
        playerRow.setTotalNumberOfTurns(playerPayload.getTotalNumberOfTurns());
        playerRow.setTotalTurnTime(playerPayload.getTotalTurnTime());
        playerRow.setExpectedHits(playerPayload.getExpectedHits());
        playerRow.setActualHits(playerPayload.getActualHits());
        playerRow.setEliminated(playerPayload.isEliminated());

        playerRow.getAlliances().addAll(playerPayload.getAlliances());
        playerRow.getLaws().addAll(playerPayload.getLaws());
        playerRow.getObjectives().addAll(playerPayload.getObjectives());
        playerRow.getRelicCards().addAll(playerPayload.getRelicCards());
        playerRow.getStrategyCards().addAll(playerPayload.getStrategyCards());
        playerRow.getTechnologies().addAll(playerPayload.getTechnologies());
        playerRow.getTeammateIds().addAll(playerPayload.getTeammateIDs());
        return playerRow;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Number> mapValuesToNumbers(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Number>) map : Map.of();
    }

    private static Integer asInt(Number number) {
        return number == null ? null : number.intValue();
    }
}
