package ti4.contest.replay.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ti4.contest.replay.entities.CombatObservationEntity;

/**
 * Maintains the rolling selection window used to decide which combats are replay-worthy.
 */
public class CombatReplaySelection {

    private final CombatContestSettings settings;
    private SelectionSnapshot selectionSnapshot;

    public CombatReplaySelection(CombatContestSettings settings) {
        this.settings = settings;
        selectionSnapshot = emptySnapshot();
    }

    public void refresh(List<CombatObservationEntity> window) {
        List<Double> fairnessValues = new ArrayList<>(window.size());
        List<Double> weakerStrengthValues = new ArrayList<>(window.size());
        for (CombatObservationEntity observation : window) {
            fairnessValues.add(observation.getFairnessRatio());
            weakerStrengthValues.add(weakerStrength(observation));
        }

        double jointScoreCutoff = computeCutoff(window, fairnessValues, weakerStrengthValues);
        List<SelectionObservationDebugView> observations = new ArrayList<>(window.size());
        for (CombatObservationEntity observation : window) {
            observations.add(toDebugView(observation, fairnessValues, weakerStrengthValues, jointScoreCutoff));
        }
        observations.sort(Comparator.comparing((SelectionObservationDebugView view) ->
                        view.observation().getStartedAt())
                .reversed());

        selectionSnapshot = new SelectionSnapshot(
                window.size(),
                List.copyOf(fairnessValues),
                List.copyOf(weakerStrengthValues),
                jointScoreCutoff,
                List.copyOf(observations));
    }

    public Evaluation evaluate(LazaxCombatSupport.SpaceCombatSnapshot snapshot) {
        double fairnessRatio = LazaxCombatSupport.computeFairnessRatio(
                snapshot.attackerStrength(),
                snapshot.defenderStrength(),
                snapshot.attackerHp(),
                snapshot.defenderHp(),
                snapshot.attackerExpectedHits(),
                snapshot.defenderExpectedHits());
        double weakerStrength = Math.min(snapshot.attackerStrength(), snapshot.defenderStrength());
        double jointScore = computeJointScore(fairnessRatio, weakerStrength);
        boolean eligible = settings.getCandidateSelection().getTargetCandidatesPerHour() > 0
                && selectionSnapshot.hasWindow()
                && jointScore >= selectionSnapshot.jointScoreCutoff();
        return new Evaluation(fairnessRatio, jointScore, eligible, selectionSnapshot.windowSize());
    }

    public SelectionDebugView debugView() {
        return new SelectionDebugView(
                settings.getCandidateSelection().getWindow().getLookbackMinutes(),
                settings.getCandidateSelection().getTargetCandidatesPerHour(),
                selectionSnapshot.windowSize(),
                selectionSnapshot.jointScoreCutoff(),
                average(selectionSnapshot.fairnessValues()),
                average(selectionSnapshot.weakerStrengthValues()),
                selectionSnapshot.observations());
    }

    private double computeJointScore(double fairnessRatio, double weakerStrength) {
        return percentileRank(selectionSnapshot.fairnessValues(), fairnessRatio)
                * percentileRank(selectionSnapshot.weakerStrengthValues(), weakerStrength);
    }

    private SelectionObservationDebugView toDebugView(
            CombatObservationEntity observation,
            List<Double> fairnessValues,
            List<Double> weakerStrengthValues,
            double jointScoreCutoff) {
        double observationWeakerStrength = weakerStrength(observation);
        double fairnessPercentile = percentileRank(fairnessValues, observation.getFairnessRatio());
        double weakerStrengthPercentile = percentileRank(weakerStrengthValues, observationWeakerStrength);
        double jointScore = fairnessPercentile * weakerStrengthPercentile;
        return new SelectionObservationDebugView(
                observation,
                observationWeakerStrength,
                fairnessPercentile,
                weakerStrengthPercentile,
                jointScore,
                settings.getCandidateSelection().getTargetCandidatesPerHour() > 0 && jointScore >= jointScoreCutoff);
    }

    private double computeCutoff(
            List<CombatObservationEntity> window, List<Double> fairnessValues, List<Double> weakerStrengthValues) {
        if (window.isEmpty()) return 1.0;
        int targetCandidatesPerHour = settings.getCandidateSelection().getTargetCandidatesPerHour();
        if (targetCandidatesPerHour <= 0) return 1.0;

        List<Double> jointScores = new ArrayList<>(window.size());
        for (CombatObservationEntity observation : window) {
            double fairnessPercentile = percentileRank(fairnessValues, observation.getFairnessRatio());
            double weakerStrengthPercentile = percentileRank(weakerStrengthValues, weakerStrength(observation));
            jointScores.add(fairnessPercentile * weakerStrengthPercentile);
        }
        jointScores.sort(Comparator.reverseOrder());

        int index = Math.clamp(jointScores.size() - 1, 0, targetCandidatesPerHour - 1);
        return jointScores.get(index);
    }

    private static double percentileRank(List<Double> values, double value) {
        if (values.isEmpty()) return 0.0;
        int count = 0;
        for (double candidate : values) {
            if (candidate <= value) count++;
        }
        return count / (double) values.size();
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total / values.size();
    }

    private static double weakerStrength(CombatObservationEntity observation) {
        return Math.min(observation.getAttackerStrength(), observation.getDefenderStrength());
    }

    private static SelectionSnapshot emptySnapshot() {
        return new SelectionSnapshot(0, List.of(), List.of(), 1.0, List.of());
    }

    private record SelectionSnapshot(
            int windowSize,
            List<Double> fairnessValues,
            List<Double> weakerStrengthValues,
            double jointScoreCutoff,
            List<SelectionObservationDebugView> observations) {
        private boolean hasWindow() {
            return windowSize > 0;
        }
    }

    public record Evaluation(double fairnessRatio, double jointScore, boolean eligible, int windowSize) {}

    public record SelectionDebugView(
            int lookbackMinutes,
            int targetCandidatesPerHour,
            int windowSize,
            double jointScoreCutoff,
            double averageFairnessRatio,
            double averageWeakerStrength,
            List<SelectionObservationDebugView> observations) {}

    public record SelectionObservationDebugView(
            CombatObservationEntity observation,
            double weakerStrength,
            double fairnessPercentile,
            double weakerStrengthPercentile,
            double jointScore,
            boolean eligibleAsCandidate) {}
}
