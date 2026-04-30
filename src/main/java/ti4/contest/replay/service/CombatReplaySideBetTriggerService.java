package ti4.contest.replay.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatReplayTrackedEvent;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.CombatSideState;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.game.Player;
import ti4.service.combat.CombatRollType;

/**
 * Converts tracked combat moments into replay announcements for side bets that just hit.
 */
@Service
public class CombatReplaySideBetTriggerService {

    public List<SideBetTriggerAnnouncement> fromRoll(
            CombatCandidateEntity candidate,
            Player player,
            CombatRollType rollType,
            boolean whiff,
            boolean slam,
            int round) {
        if (candidate == null
                || player == null
                || rollType == null
                || !Boolean.TRUE.equals(candidate.getSideBetCompatible())) {
            return List.of();
        }

        List<SideBetTriggerAnnouncement> announcements = new ArrayList<>();
        CombatSideState state = CombatSideState.forFaction(candidate, player.getFaction());
        int destroyerCount = state == null ? 0 : state.destroyerCount();
        if (rollType == CombatRollType.AFB && whiff && CombatSideBetType.AFB_WHIFF.isAvailable(destroyerCount)) {
            announcements.add(announcement(player, "AFB Whiff!"));
        }
        if (rollType == CombatRollType.combatround
                && round == 1
                && isAfbSkippedAvailable(candidate, player.getFaction())
                && state != null
                && state.skippedAfb()) {
            announcements.add(announcement(player, "Skipped AFB!"));
        }
        if (rollType == CombatRollType.combatround && round == 1 && whiff) {
            announcements.add(announcement(player, "Round 1 Whiff!"));
        }
        if (rollType == CombatRollType.combatround && round == 1 && slam) {
            announcements.add(announcement(player, "Slam!"));
        }
        return announcements;
    }

    public List<SideBetTriggerAnnouncement> fromTrackedEvent(
            CombatCandidateEntity candidate, Player player, CombatReplayTrackedEvent trackedEvent) {
        if (candidate == null
                || player == null
                || trackedEvent == null
                || !Boolean.TRUE.equals(candidate.getSideBetCompatible())) {
            return List.of();
        }
        return switch (trackedEvent) {
            case MORALE_BOOST -> List.of(announcement(player, "Morale Boost!"));
            case SHIELDS_HOLDING -> List.of(announcement(player, "Shields Holding!"));
            case DIRECT_HIT -> List.of(announcement(player, "Direct Hit!"));
            case FIGHTER_PROTOTYPE -> List.of(announcement(player, "Fighter Prototype!"));
            case ROUT, NONE -> List.of();
        };
    }

    public List<SideBetTriggerAnnouncement> fromResolution(CombatCandidateEntity candidate, Player winner) {
        if (candidate == null
                || winner == null
                || !Boolean.TRUE.equals(candidate.getSideBetCompatible())
                || !Boolean.TRUE.equals(candidate.getWinnerOneHpRemaining())) {
            return List.of();
        }
        return List.of(announcement(winner, "Wins on 1 HP!"));
    }

    private SideBetTriggerAnnouncement announcement(Player player, String triggerLabel) {
        return new SideBetTriggerAnnouncement(
                player.getFaction(), "### " + player.getFactionEmoji() + " " + triggerLabel);
    }

    private boolean isAfbSkippedAvailable(CombatCandidateEntity candidate, String faction) {
        CombatSideState state = CombatSideState.forFaction(candidate, faction);
        if (state == null || !CombatSideBetType.AFB_SKIPPED.isAvailable(state.destroyerCount())) return false;
        return !(state.destroyerCount() == 1 && opponentHasAssaultCannon(candidate, faction));
    }

    private boolean opponentHasAssaultCannon(CombatCandidateEntity candidate, String faction) {
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            return Boolean.TRUE.equals(candidate.getDefenderHasAssaultCannon());
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            return Boolean.TRUE.equals(candidate.getAttackerHasAssaultCannon());
        }
        return false;
    }

    public record SideBetTriggerAnnouncement(String faction, String message) {}
}
