package ti4.website.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.info.ListPlayerInfoService;

@Data
public class WebObjectives {

    @Data
    static class ObjectiveInfo {
        private String key;
        private String name;
        private int pointValue;
        private boolean revealed;
        private boolean isMultiScoring;
        private boolean hasRedTape;
        private List<String> scoredFactions;
        private List<String> peekingFactions;
        private Map<String, Integer> factionProgress;
        private int progressThreshold;

        ObjectiveInfo(
                String key,
                String name,
                int pointValue,
                boolean revealed,
                boolean isMultiScoring,
                boolean hasRedTape,
                List<String> scoredFactions,
                List<String> peekingFactions,
                Map<String, Integer> factionProgress,
                int progressThreshold) {
            this.key = key;
            this.name = name;
            this.pointValue = pointValue;
            this.revealed = revealed;
            this.isMultiScoring = isMultiScoring;
            this.hasRedTape = hasRedTape;
            this.scoredFactions = scoredFactions != null ? scoredFactions : new ArrayList<>();
            this.peekingFactions = peekingFactions != null ? peekingFactions : new ArrayList<>();
            this.factionProgress = factionProgress != null ? factionProgress : new HashMap<>();
            this.progressThreshold = progressThreshold;
        }
    }

    private List<ObjectiveInfo> stage1Objectives;
    private List<ObjectiveInfo> stage2Objectives;
    private List<ObjectiveInfo> customObjectives;
    private List<ObjectiveInfo> allObjectives;

    public static WebObjectives fromGame(Game game) {
        WebObjectives webObjectives = new WebObjectives();
        webObjectives.stage1Objectives =
                stageObjectives(game, Mapper.getPublicObjectivesStage1(), game.getPublicObjectives1Peekable());
        webObjectives.stage2Objectives =
                stageObjectives(game, Mapper.getPublicObjectivesStage2(), game.getPublicObjectives2Peekable());
        webObjectives.customObjectives = customObjectives(game);
        webObjectives.allObjectives = new ArrayList<>();
        webObjectives.allObjectives.addAll(webObjectives.stage1Objectives);
        webObjectives.allObjectives.addAll(webObjectives.stage2Objectives);
        webObjectives.allObjectives.addAll(webObjectives.customObjectives);
        return webObjectives;
    }

    public Optional<Integer> findFactionProgress(String objectiveKey, String faction) {
        return allObjectives.stream()
                .filter(objective ->
                        objective.isRevealed() && objective.getKey().equals(objectiveKey))
                .map(objective -> objective.getFactionProgress().get(faction))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static List<ObjectiveInfo> stageObjectives(
            Game game, Map<String, String> stageObjectives, List<String> unrevealed) {
        List<ObjectiveInfo> objectives = new ArrayList<>();
        for (String key : game.getRevealedPublicObjectives().keySet()) {
            if (stageObjectives.containsKey(key)) {
                addIfPresent(objectives, createObjectiveInfo(game, key, true, false));
            }
        }

        boolean isRedTapeMode = game.isRedTapeMode();
        boolean isRevealed = isRedTapeMode || game.isCivilizedSocietyMode();
        for (String key : unrevealed) {
            addIfPresent(objectives, createObjectiveInfo(game, key, isRevealed, isRedTapeMode));
        }
        return objectives;
    }

    private static void addIfPresent(List<ObjectiveInfo> objectives, ObjectiveInfo objectiveInfo) {
        if (objectiveInfo != null) {
            objectives.add(objectiveInfo);
        }
    }

    private static List<ObjectiveInfo> customObjectives(Game game) {
        List<ObjectiveInfo> objectives = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : game.getCustomPublicVP().entrySet()) {
            objectives.add(createCustomObjectiveInfo(game, entry.getKey(), entry.getValue()));
        }
        return objectives;
    }

    private static ObjectiveInfo createObjectiveInfo(Game game, String key, boolean revealed, boolean hasRedTape) {
        PublicObjectiveModel po = Mapper.getPublicObjective(key);
        if (po == null) {
            return null;
        }

        String displayKey = revealed ? key : "UNREVEALED_" + String.format("%04d", (int) (Math.random() * 10_000));
        String name = revealed ? po.getName() : "UNREVEALED";
        int pointValue = po.getPoints();

        boolean isMultiScoring =
                Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || game.isFowMode();

        List<String> scoredFactions = getScoredFactions(game, key);
        List<String> peekingFactions = getPeekingFactions(game, key);
        Map<String, Integer> factionProgress = getFactionProgress(game, key, revealed);
        int progressThreshold = revealed ? ListPlayerInfoService.getObjectiveThreshold(key, game) : 0;

        return new ObjectiveInfo(
                displayKey,
                name,
                pointValue,
                revealed,
                isMultiScoring,
                hasRedTape,
                scoredFactions,
                peekingFactions,
                factionProgress,
                progressThreshold);
    }

    private static ObjectiveInfo createCustomObjectiveInfo(Game game, String key, int pointValue) {
        boolean isMultiScoring =
                Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || game.isFowMode();

        List<String> scoredFactions = getScoredFactions(game, key);
        List<String> peekingFactions = new ArrayList<>(); // Custom objectives don't have peeking
        Map<String, Integer> factionProgress = getFactionProgress(game, key, true);
        int progressThreshold = ListPlayerInfoService.getObjectiveThreshold(key, game);

        return new ObjectiveInfo(
                key,
                key,
                pointValue,
                true,
                isMultiScoring,
                false,
                scoredFactions,
                peekingFactions,
                factionProgress,
                progressThreshold);
    }

    private static List<String> getScoredFactions(Game game, String objectiveKey) {
        return toFactions(game, game.getScoredPublicObjectives().getOrDefault(objectiveKey, Collections.emptyList()));
    }

    private static List<String> getPeekingFactions(Game game, String objectiveKey) {
        List<String> peekingPlayerIDs = new ArrayList<>();
        peekingPlayerIDs.addAll(game.getPublicObjectives1Peeked().getOrDefault(objectiveKey, Collections.emptyList()));
        peekingPlayerIDs.addAll(game.getPublicObjectives2Peeked().getOrDefault(objectiveKey, Collections.emptyList()));
        return toFactions(game, peekingPlayerIDs);
    }

    private static List<String> toFactions(Game game, List<String> playerIDs) {
        return playerIDs.stream()
                .map(playerID -> game.getPlayers().get(playerID))
                .filter(Objects::nonNull)
                .map(Player::getFaction)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static Map<String, Integer> getFactionProgress(Game game, String objectiveKey, boolean revealed) {
        Map<String, Integer> factionProgress = new HashMap<>();

        // Only calculate progress for revealed objectives
        if (!revealed) {
            return factionProgress;
        }

        for (Player player : game.getRealPlayers()) {
            if (player.getFaction() != null) {
                int progress = ListPlayerInfoService.getPlayerProgressOnObjective(objectiveKey, game, player);
                factionProgress.put(player.getFaction(), progress);
            }
        }

        return factionProgress;
    }
}
