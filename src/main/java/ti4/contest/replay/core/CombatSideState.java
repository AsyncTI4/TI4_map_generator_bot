package ti4.contest.replay.core;

import ti4.contest.replay.entities.CombatCandidateEntity;

/**
 * Provides one side's tracked combat flags and hides attacker/defender column branching.
 */
public record CombatSideState(
        int destroyerCount,
        boolean rolledAfb,
        boolean afbWhiff,
        boolean roundOneWhiff,
        boolean roundOneSlam,
        boolean playedMoraleBoost,
        boolean playedShieldsHolding) {

    public static CombatSideState forFaction(CombatCandidateEntity candidate, String faction) {
        if (candidate == null || faction == null) return null;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) return attacker(candidate);
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) return defender(candidate);
        return null;
    }

    public static boolean markRollFlags(
            CombatCandidateEntity candidate,
            String faction,
            boolean rolledAfb,
            boolean afbWhiff,
            boolean roundOneWhiff,
            boolean roundOneSlam) {
        if (candidate == null || faction == null) return false;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            candidate.setAttackerRolledAfb(Boolean.TRUE.equals(candidate.getAttackerRolledAfb()) || rolledAfb);
            candidate.setAttackerAfbWhiff(Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()) || afbWhiff);
            candidate.setAttackerRoundOneWhiff(
                    Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()) || roundOneWhiff);
            candidate.setAttackerRoundOneSlam(Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()) || roundOneSlam);
            return true;
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            candidate.setDefenderRolledAfb(Boolean.TRUE.equals(candidate.getDefenderRolledAfb()) || rolledAfb);
            candidate.setDefenderAfbWhiff(Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()) || afbWhiff);
            candidate.setDefenderRoundOneWhiff(
                    Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()) || roundOneWhiff);
            candidate.setDefenderRoundOneSlam(Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()) || roundOneSlam);
            return true;
        }
        return false;
    }

    public static boolean markEventFlags(
            CombatCandidateEntity candidate, String faction, boolean moraleBoost, boolean shieldsHolding) {
        if (candidate == null || faction == null) return false;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            candidate.setAttackerPlayedMoraleBoost(
                    Boolean.TRUE.equals(candidate.getAttackerPlayedMoraleBoost()) || moraleBoost);
            candidate.setAttackerPlayedShieldsHolding(
                    Boolean.TRUE.equals(candidate.getAttackerPlayedShieldsHolding()) || shieldsHolding);
            return true;
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            candidate.setDefenderPlayedMoraleBoost(
                    Boolean.TRUE.equals(candidate.getDefenderPlayedMoraleBoost()) || moraleBoost);
            candidate.setDefenderPlayedShieldsHolding(
                    Boolean.TRUE.equals(candidate.getDefenderPlayedShieldsHolding()) || shieldsHolding);
            return true;
        }
        return false;
    }

    private static CombatSideState attacker(CombatCandidateEntity candidate) {
        return new CombatSideState(
                safeInt(candidate.getAttackerDestroyerCount()),
                Boolean.TRUE.equals(candidate.getAttackerRolledAfb()),
                Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()),
                Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()),
                Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedMoraleBoost()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedShieldsHolding()));
    }

    private static CombatSideState defender(CombatCandidateEntity candidate) {
        return new CombatSideState(
                safeInt(candidate.getDefenderDestroyerCount()),
                Boolean.TRUE.equals(candidate.getDefenderRolledAfb()),
                Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()),
                Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()),
                Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedMoraleBoost()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedShieldsHolding()));
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
