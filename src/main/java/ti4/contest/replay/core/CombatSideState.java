package ti4.contest.replay.core;

import ti4.contest.replay.entities.CombatCandidateEntity;

/**
 * Provides one side's tracked combat flags and hides attacker/defender column branching.
 */
public record CombatSideState(
        int destroyerCount,
        boolean rolledAfb,
        boolean afbWhiff,
        boolean skippedAfb,
        boolean roundOneWhiff,
        boolean roundOneSlam,
        boolean playedMoraleBoost,
        boolean playedShieldsHolding,
        boolean playedDirectHit,
        boolean playedFighterPrototype) {

    public static CombatSideState forFaction(CombatCandidateEntity candidate, String faction) {
        if (candidate == null || faction == null) return null;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) return attacker(candidate);
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) return defender(candidate);
        return null;
    }

    public static void markRollFlags(
            CombatCandidateEntity candidate,
            String faction,
            boolean rolledAfb,
            boolean afbWhiff,
            boolean skippedAfb,
            boolean roundOneWhiff,
            boolean roundOneSlam) {
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            candidate.setAttackerRolledAfb(Boolean.TRUE.equals(candidate.getAttackerRolledAfb()) || rolledAfb);
            candidate.setAttackerAfbWhiff(Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()) || afbWhiff);
            candidate.setAttackerSkippedAfb(Boolean.TRUE.equals(candidate.getAttackerSkippedAfb()) || skippedAfb);
            candidate.setAttackerRoundOneWhiff(
                    Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()) || roundOneWhiff);
            candidate.setAttackerRoundOneSlam(Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()) || roundOneSlam);
            return;
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            candidate.setDefenderRolledAfb(Boolean.TRUE.equals(candidate.getDefenderRolledAfb()) || rolledAfb);
            candidate.setDefenderAfbWhiff(Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()) || afbWhiff);
            candidate.setDefenderSkippedAfb(Boolean.TRUE.equals(candidate.getDefenderSkippedAfb()) || skippedAfb);
            candidate.setDefenderRoundOneWhiff(
                    Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()) || roundOneWhiff);
            candidate.setDefenderRoundOneSlam(Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()) || roundOneSlam);
        }
    }

    public static void markEventFlag(
            CombatCandidateEntity candidate, String faction, CombatReplayTrackedEvent trackedEvent) {
        boolean attacker = faction.equalsIgnoreCase(candidate.getAttackerFaction());
        if (!attacker && !faction.equalsIgnoreCase(candidate.getDefenderFaction())) return;
        switch (trackedEvent) {
            case MORALE_BOOST -> {
                if (attacker) candidate.setAttackerPlayedMoraleBoost(true);
                else candidate.setDefenderPlayedMoraleBoost(true);
            }
            case SHIELDS_HOLDING -> {
                if (attacker) candidate.setAttackerPlayedShieldsHolding(true);
                else candidate.setDefenderPlayedShieldsHolding(true);
            }
            case DIRECT_HIT -> {
                if (attacker) candidate.setAttackerPlayedDirectHit(true);
                else candidate.setDefenderPlayedDirectHit(true);
            }
            case FIGHTER_PROTOTYPE -> {
                if (attacker) candidate.setAttackerPlayedFighterPrototype(true);
                else candidate.setDefenderPlayedFighterPrototype(true);
            }
            case ROUT, NONE -> {}
        }
    }

    private static CombatSideState attacker(CombatCandidateEntity candidate) {
        return new CombatSideState(
                safeInt(candidate.getAttackerDestroyerCount()),
                Boolean.TRUE.equals(candidate.getAttackerRolledAfb()),
                Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()),
                Boolean.TRUE.equals(candidate.getAttackerSkippedAfb()),
                Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()),
                Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedMoraleBoost()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedShieldsHolding()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedDirectHit()),
                Boolean.TRUE.equals(candidate.getAttackerPlayedFighterPrototype()));
    }

    private static CombatSideState defender(CombatCandidateEntity candidate) {
        return new CombatSideState(
                safeInt(candidate.getDefenderDestroyerCount()),
                Boolean.TRUE.equals(candidate.getDefenderRolledAfb()),
                Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()),
                Boolean.TRUE.equals(candidate.getDefenderSkippedAfb()),
                Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()),
                Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedMoraleBoost()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedShieldsHolding()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedDirectHit()),
                Boolean.TRUE.equals(candidate.getDefenderPlayedFighterPrototype()));
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
