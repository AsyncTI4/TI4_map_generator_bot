package ti4.website;

import java.util.*;
import java.util.stream.Collectors;

import lombok.Data;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.PublicObjectiveModel;

@Data
public class WebObjectives {

    @Data
    public static class ObjectiveInfo {
        private String key;
        private String name;
        private int pointValue;
        private boolean revealed;
        private boolean isMultiScoring;
        private List<String> scoredFactions;
        private List<String> peekingFactions;

        public ObjectiveInfo(String key, String name, int pointValue,
                           boolean revealed, boolean isMultiScoring,
                           List<String> scoredFactions, List<String> peekingFactions) {
            this.key = key;
            this.name = name;
            this.pointValue = pointValue;
            this.revealed = revealed;
            this.isMultiScoring = isMultiScoring;
            this.scoredFactions = scoredFactions != null ? scoredFactions : new ArrayList<>();
            this.peekingFactions = peekingFactions != null ? peekingFactions : new ArrayList<>();
        }
    }

    private List<ObjectiveInfo> stage1Objectives;
    private List<ObjectiveInfo> stage2Objectives;
    private List<ObjectiveInfo> customObjectives;
    private List<ObjectiveInfo> allObjectives;

    public static WebObjectives fromGame(Game game) {
        WebObjectives webObjectives = new WebObjectives();

        // Initialize lists
        webObjectives.stage1Objectives = new ArrayList<>();
        webObjectives.stage2Objectives = new ArrayList<>();
        webObjectives.customObjectives = new ArrayList<>();
        webObjectives.allObjectives = new ArrayList<>();

        // Process Stage 1 objectives (1VP)
        processStage1Objectives(game, webObjectives);

        // Process Stage 2 objectives (2VP)
        processStage2Objectives(game, webObjectives);

        // Process custom objectives (mutiny, custodian, imperial rider, etc.)
        processCustomObjectives(game, webObjectives);

        // Combine all objectives
        webObjectives.allObjectives.addAll(webObjectives.stage1Objectives);
        webObjectives.allObjectives.addAll(webObjectives.stage2Objectives);
        webObjectives.allObjectives.addAll(webObjectives.customObjectives);

        return webObjectives;
    }

    private static void processStage1Objectives(Game game, WebObjectives webObjectives) {
        Map<String, Integer> revealedObjectives = game.getRevealedPublicObjectives();
        Map<String, String> stage1Objectives = Mapper.getPublicObjectivesStage1();

        // Process revealed Stage 1 objectives
        for (Map.Entry<String, Integer> entry : revealedObjectives.entrySet()) {
            String key = entry.getKey();
            if (stage1Objectives.containsKey(key)) {
                ObjectiveInfo objInfo = createObjectiveInfo(game, key, true, 1);
                if (objInfo != null) {
                    webObjectives.stage1Objectives.add(objInfo);
                }
            }
        }

        // Process unrevealed Stage 1 objectives
        List<String> unrevealed1 = game.getPublicObjectives1Peakable();
        for (String key : unrevealed1) {
            ObjectiveInfo objInfo = createObjectiveInfo(game, key, false, 1);
            if (objInfo != null) {
                webObjectives.stage1Objectives.add(objInfo);
            }
        }
    }

    private static void processStage2Objectives(Game game, WebObjectives webObjectives) {
        Map<String, Integer> revealedObjectives = game.getRevealedPublicObjectives();
        Map<String, String> stage2Objectives = Mapper.getPublicObjectivesStage2();

        // Process revealed Stage 2 objectives
        for (Map.Entry<String, Integer> entry : revealedObjectives.entrySet()) {
            String key = entry.getKey();
            if (stage2Objectives.containsKey(key)) {
                ObjectiveInfo objInfo = createObjectiveInfo(game, key, true, 2);
                if (objInfo != null) {
                    webObjectives.stage2Objectives.add(objInfo);
                }
            }
        }

        // Process unrevealed Stage 2 objectives
        List<String> unrevealed2 = game.getPublicObjectives2Peakable();
        for (String key : unrevealed2) {
            ObjectiveInfo objInfo = createObjectiveInfo(game, key, false, 2);
            if (objInfo != null) {
                webObjectives.stage2Objectives.add(objInfo);
            }
        }
    }

    private static void processCustomObjectives(Game game, WebObjectives webObjectives) {
        Map<String, Integer> customPublicVP = game.getCustomPublicVP();

        for (Map.Entry<String, Integer> entry : customPublicVP.entrySet()) {
            String key = entry.getKey();
            int pointValue = entry.getValue();

            ObjectiveInfo objInfo = createCustomObjectiveInfo(game, key, pointValue);
            if (objInfo != null) {
                webObjectives.customObjectives.add(objInfo);
            }
        }
    }

    private static ObjectiveInfo createObjectiveInfo(Game game, String key, boolean revealed, int defaultPointValue) {
        PublicObjectiveModel po = Mapper.getPublicObjective(key);
        if (po == null) {
            return null;
        }

        String displayKey = revealed ? key : "UNREVEALED_" + String.format("%04d", (int)(Math.random() * 10000));
        String name = revealed ? po.getName() : "UNREVEALED";
        int pointValue = po.getPoints();

        boolean isMultiScoring = Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || game.isFowMode();

        List<String> scoredFactions = getScoredFactions(game, key);
        List<String> peekingFactions = getPeekingFactions(game, key);

        return new ObjectiveInfo(displayKey, name, pointValue, revealed, isMultiScoring, scoredFactions, peekingFactions);
    }

    private static ObjectiveInfo createCustomObjectiveInfo(Game game, String key, int pointValue) {
        boolean isMultiScoring = Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || game.isFowMode();

        List<String> scoredFactions = getScoredFactions(game, key);
        List<String> peekingFactions = new ArrayList<>(); // Custom objectives don't have peeking

        return new ObjectiveInfo(key, key, pointValue, true, isMultiScoring, scoredFactions, peekingFactions);
    }

    private static List<String> getScoredFactions(Game game, String objectiveKey) {
        List<String> scoredPlayerIDs = game.getScoredPublicObjectives().getOrDefault(objectiveKey, Collections.emptyList());

        return scoredPlayerIDs.stream()
            .map(playerID -> {
                Player player = game.getPlayers().get(playerID);
                return player != null ? player.getFaction() : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static List<String> getPeekingFactions(Game game, String objectiveKey) {
        List<String> peekingPlayerIDs = new ArrayList<>();

        // Check Stage 1 peeked objectives
        if (game.getPublicObjectives1Peeked().containsKey(objectiveKey)) {
            peekingPlayerIDs.addAll(game.getPublicObjectives1Peeked().get(objectiveKey));
        }

        // Check Stage 2 peeked objectives
        if (game.getPublicObjectives2Peeked().containsKey(objectiveKey)) {
            peekingPlayerIDs.addAll(game.getPublicObjectives2Peeked().get(objectiveKey));
        }

        return peekingPlayerIDs.stream()
            .map(playerID -> {
                Player player = game.getPlayers().get(playerID);
                return player != null ? player.getFaction() : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}