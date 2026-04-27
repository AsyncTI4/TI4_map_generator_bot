package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

class CombatReplaySideBetPayoutServiceTest extends BaseTi4Test {

    private final CombatContestSettings settings = new CombatContestSettings();
    private final CombatCandidateEventRepository eventRepository = mock(CombatCandidateEventRepository.class);
    private final CombatReplaySideBetPayoutService service = new CombatReplaySideBetPayoutService(settings);

    @Test
    void usesFixedPayoutForExistingContestWithoutOddsModel() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();

        int payout = service.offeredPayout(contest, candidate(30, 30), CombatSideBetType.WINNER_ONE_HP, "sol");

        assertEquals(35, payout);
    }

    @Test
    void pricesRoundOneWhiffFromInitialSnapshotOnly() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = snapshotCandidate(4, 1);

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(4, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void capsVeryLowOddsRoundOneSlamFromInitialSnapshotAtMaxPayout() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = snapshotCandidate(4, 1);

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "sol");

        assertEquals(100, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesAfbWhiffFromInitialSnapshotAfbUnitsOnly() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = afbSnapshotCandidate();

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.AFB_WHIFF, "sol");

        assertEquals(6, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void roundOneSideBetsUseFixedPayoutWhenInitialSnapshotIsMissing() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = candidate();

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(10, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesOneHpFromInitialSnapshotAndIgnoresCandidateHpFields() {
        CombatCandidateEntity candidate = snapshotCandidate(1, 1);
        candidate.setAttackerHp(30.0);
        candidate.setDefenderHp(30.0);

        int payout = service.offeredPayout(oddsContest(), candidate, CombatSideBetType.WINNER_ONE_HP, "sol");

        assertEquals(6, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void resolvesOldRowsWithLegacyFixedPayoutAndNewRowsWithLockedSnapshot() {
        CombatContestSideBetEntity legacy = new CombatContestSideBetEntity();
        legacy.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);

        CombatContestSideBetEntity locked = new CombatContestSideBetEntity();
        locked.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);
        locked.setOfferedProfitPoints(17);

        assertEquals(10, service.resolvedProfitPoints(legacy));
        assertEquals(17, service.resolvedProfitPoints(locked));
    }

    private CombatCandidateEntity candidate() {
        return candidate(null, null);
    }

    private CombatCandidateEntity candidate(Integer attackerHp, Integer defenderHp) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(100L);
        candidate.setAttackerFaction("sol");
        candidate.setDefenderFaction("yin");
        candidate.setAttackerHp(attackerHp == null ? null : attackerHp.doubleValue());
        candidate.setDefenderHp(defenderHp == null ? null : defenderHp.doubleValue());
        return candidate;
    }

    private CombatCandidateEntity snapshotCandidate(int solCarriers, int yinCarriers) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-side-bet-snapshot");
        Player sol = player(game, "sol");
        Player yin = player(game, "yin");
        Tile tile = tile(game, "18");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, sol.getColorID()), solCarriers);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, yin.getColorID()), yinCarriers);

        CombatCandidateEntity candidate = candidate(null, null);
        candidate.setTilePosition(tile.getPosition());
        candidate.setInitialRenderSnapshotJson(
                CombatReplayTileRenderer.captureInitialSnapshot(game, tile.getPosition()));
        return candidate;
    }

    private CombatCandidateEntity afbSnapshotCandidate() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-side-bet-afb-snapshot");
        Player sol = player(game, "sol");
        sol.removeOwnedUnitByID("destroyer");
        sol.addOwnedUnitByID("destroyer2");
        Player yin = player(game, "yin");
        Tile tile = tile(game, "18");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, sol.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, sol.getColorID()), 3);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, yin.getColorID()), 1);

        CombatCandidateEntity candidate = candidate(null, null);
        candidate.setTilePosition(tile.getPosition());
        candidate.setInitialRenderSnapshotJson(
                CombatReplayTileRenderer.captureInitialSnapshot(game, tile.getPosition()));
        return candidate;
    }

    private CombatReplayContestEntity oddsContest() {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setSideBetPayoutModel(CombatReplaySideBetPayoutService.ODDS_V1);
        return contest;
    }

    private Player player(Game game, String faction) {
        FactionModel model = Mapper.getFaction(faction);
        Player player = game.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(game, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(PlayerColorService.getPreferredColor(player));
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.setCommoditiesBase(model.getCommodities());
        player.setFactionTechs(model.getFactionTech());
        return player;
    }

    private Tile tile(Game game, String tileId) {
        Tile tile = new Tile(tileId, getNextPosition(game));
        game.setTile(tile);
        game.setActiveSystem(tile.getPosition());
        return tile;
    }

    private String getNextPosition(Game game) {
        for (String position : PositionMapper.getTilePositions()) {
            if (game.getTileByPosition(position) == null) return position;
        }
        return null;
    }
}
