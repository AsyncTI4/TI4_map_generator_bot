package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        assertEquals(26, payout);
    }

    @Test
    void pricesRoundOneWhiffFromInitialSnapshotOnly() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = snapshotCandidate(4, 1);

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(20, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void capsVeryLowOddsRoundOneSlamAfterSelectionBias() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = snapshotCandidate(4, 1);

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "sol");

        assertEquals(50, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesRoundOneSlamAfterOpponentAfbCanRemoveTargetFighters() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = slamAfbSnapshotCandidate();

        int yinPayout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "yin");
        int solPayout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "sol");

        assertEquals(12, yinPayout);
        assertEquals(50, solPayout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesRoundOneSlamFromOpponentNonDestroyerAfbUnits() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = zelianDreadnoughtAfbSnapshotCandidate();

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_SLAM, "yin");

        assertEquals(4, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesRoundOneSlamWithJolNarCommanderAfbRerollMisses() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity lockedCommanderCandidate = jolNarAfbRerollSnapshotCandidate(false);
        CombatCandidateEntity unlockedCommanderCandidate = jolNarAfbRerollSnapshotCandidate(true);

        int lockedPayout =
                service.offeredPayout(contest, lockedCommanderCandidate, CombatSideBetType.ROUND_ONE_SLAM, "yin");
        int unlockedPayout =
                service.offeredPayout(contest, unlockedCommanderCandidate, CombatSideBetType.ROUND_ONE_SLAM, "yin");

        assertEquals(4, lockedPayout);
        assertEquals(4, unlockedPayout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesAfbWhiffFromInitialSnapshotAfbUnitsOnly() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = afbSnapshotCandidate();

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.AFB_WHIFF, "sol");

        assertTrue(service.hasAfbUnits(candidate, "sol"));
        assertEquals(4, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void afbWhiffIsUnavailableWithoutInitialSnapshotAfbUnits() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = snapshotCandidate(4, 1);

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.AFB_WHIFF, "sol");

        assertFalse(service.hasAfbUnits(candidate, "sol"));
        assertEquals(4, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void roundOneSideBetsUseFixedPayoutWhenInitialSnapshotIsMissing() {
        CombatReplayContestEntity contest = oddsContest();
        CombatCandidateEntity candidate = candidate();

        int payout = service.offeredPayout(contest, candidate, CombatSideBetType.ROUND_ONE_WHIFF, "sol");

        assertEquals(20, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void pricesOneHpFromInitialSnapshotAndIgnoresCandidateHpFields() {
        CombatCandidateEntity candidate = snapshotCandidate(1, 1);
        candidate.setAttackerHp(30.0);
        candidate.setDefenderHp(30.0);

        int payout = service.offeredPayout(oddsContest(), candidate, CombatSideBetType.WINNER_ONE_HP, "sol");

        assertEquals(3, payout);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void resolvesLockedSideBetPayout() {
        CombatContestSideBetEntity locked = new CombatContestSideBetEntity();
        locked.setBetType(CombatSideBetType.ROUND_ONE_WHIFF);
        locked.setOfferedProfitPoints(17);

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

    private CombatCandidateEntity slamAfbSnapshotCandidate() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-side-bet-slam-afb-snapshot");
        Player sol = player(game, "sol");
        sol.removeOwnedUnitByID("destroyer");
        sol.addOwnedUnitByID("destroyer2");
        Player yin = player(game, "yin");
        Tile tile = tile(game, "18");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, sol.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, sol.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, sol.getColorID()), 4);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, yin.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, yin.getColorID()), 4);

        CombatCandidateEntity candidate = candidate(null, null);
        candidate.setTilePosition(tile.getPosition());
        candidate.setInitialRenderSnapshotJson(
                CombatReplayTileRenderer.captureInitialSnapshot(game, tile.getPosition()));
        return candidate;
    }

    private CombatCandidateEntity zelianDreadnoughtAfbSnapshotCandidate() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-side-bet-zelian-afb-snapshot");
        Player sol = player(game, "sol");
        unlockLeader(sol, "zeliancommander");
        Player yin = player(game, "yin");
        Tile tile = tile(game, "18");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Dreadnought, sol.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, yin.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, yin.getColorID()), 1);

        CombatCandidateEntity candidate = candidate(null, null);
        candidate.setTilePosition(tile.getPosition());
        candidate.setInitialRenderSnapshotJson(
                CombatReplayTileRenderer.captureInitialSnapshot(game, tile.getPosition()));
        return candidate;
    }

    private CombatCandidateEntity jolNarAfbRerollSnapshotCandidate(boolean commanderUnlocked) {
        Game game = new Game();
        game.newGameSetup();
        game.setName("pbd-side-bet-jolnar-afb-reroll-snapshot");
        Player jolnar = player(game, "jolnar");
        if (commanderUnlocked) {
            unlockLeader(jolnar, "jolnarcommander");
        }
        Player yin = player(game, "yin");
        Tile tile = tile(game, "18");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, jolnar.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, yin.getColorID()), 1);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Fighter, yin.getColorID()), 1);

        CombatCandidateEntity candidate = candidate(null, null);
        candidate.setAttackerFaction("jolnar");
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
        return new CombatReplayContestEntity();
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

    private void unlockLeader(Player player, String leaderId) {
        player.addLeader(leaderId);
        player.unsafeGetLeader(leaderId).setLocked(false);
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
