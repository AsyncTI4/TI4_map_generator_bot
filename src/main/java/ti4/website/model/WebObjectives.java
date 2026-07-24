package ti4.website.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Data;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.info.ListPlayerInfoService;

/*
 * Note on FoW: under fog you learn that *someone* scored an objective, not who. So
 * {@link #redactScorers} drops unidentified factions out of {@code scoredFactions} entirely and
 * replaces them with a bare count - sending the real faction strings and hiding them client-side
 * would leave the answer sitting in the payload. Numeric {@code factionProgress} is private too
 * (derived from hand/resources), so {@link #redactFactionProgress} strips it down to only the
 * viewer's own entry.
 */

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
        // How many scorers were dropped from scoredFactions because the viewer can't identify them
        // (see #redactScorers). Always 0 in the unfiltered view.
        private int unidentifiedScorerCount;
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

    // The redactions below mutate through allObjectives, which holds the same ObjectiveInfo
    // instances referenced by the stage1/stage2/custom lists - so updating it updates all of them.

    /**
     * Replaces scorers the viewer can't identify with a count, and drops them from the peeking list.
     * The frontend renders {@code unidentifiedScorerCount} anonymous tokens, which carry no faction
     * string at all - so there is nothing to correlate across objectives, and nothing recoverable
     * from the raw payload.
     */
    public static void redactScorers(WebObjectives objectives, Game game, Player viewer) {
        for (ObjectiveInfo info : objectives.allObjectives) {
            int before = info.scoredFactions.size();
            info.scoredFactions.removeIf(faction -> !canIdentify(game, viewer, faction));
            info.unidentifiedScorerCount = before - info.scoredFactions.size();
            info.peekingFactions.removeIf(faction -> !canIdentify(game, viewer, faction));
        }
    }

    private static boolean canIdentify(Game game, Player viewer, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        // Neutral forces are public, and canSeeStatsOfPlayer is false for any non-real player.
        // An unresolvable faction fails closed - better an extra anonymous token than a leak.
        return player != null && (player.isNeutral() || FoWHelper.canSeeStatsOfPlayer(game, player, viewer));
    }

    /** Strips numeric factionProgress down to just {@code viewer}'s own entry. */
    public static void redactFactionProgress(WebObjectives objectives, Player viewer) {
        String ownFaction = viewer.getFaction();
        for (ObjectiveInfo info : objectives.allObjectives) {
            Integer ownProgress = info.factionProgress.get(ownFaction);
            info.factionProgress.clear();
            if (ownProgress != null) {
                info.factionProgress.put(ownFaction, ownProgress);
            }
        }
    }

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
        boolean isRedTapeMode = game.isRedTapeMode();
        boolean isCivilizedSocietyMode = game.isCivilizedSocietyMode();

        // Process revealed Stage 1 objectives
        for (Map.Entry<String, Integer> entry : revealedObjectives.entrySet()) {
            String key = entry.getKey();
            if (stage1Objectives.containsKey(key)) {
                ObjectiveInfo objInfo = createObjectiveInfo(game, key, true, false, 1);
                if (objInfo != null) {
                    webObjectives.stage1Objectives.add(objInfo);
                }
            }
        }

        // Process unrevealed Stage 1 objectives
        List<String> unrevealed1 = game.getPublicObjectives1Peekable();
        boolean isRevealed = isRedTapeMode || isCivilizedSocietyMode;
        for (String key : unrevealed1) {
            ObjectiveInfo objInfo = createObjectiveInfo(game, key, isRevealed, isRedTapeMode, 1);
            if (objInfo != null) {
                webObjectives.stage1Objectives.add(objInfo);
            }
        }
    }

    private static void processStage2Objectives(Game game, WebObjectives webObjectives) {
        Map<String, Integer> revealedObjectives = game.getRevealedPublicObjectives();
        Map<String, String> stage2Objectives = Mapper.getPublicObjectivesStage2();
        boolean isRedTapeMode = game.isRedTapeMode();
        boolean isCivilizedSocietyMode = game.isCivilizedSocietyMode();

        // Process revealed Stage 2 objectives
        for (Map.Entry<String, Integer> entry : revealedObjectives.entrySet()) {
            String key = entry.getKey();
            if (stage2Objectives.containsKey(key)) {
                ObjectiveInfo objInfo = createObjectiveInfo(game, key, true, false, 2);
                if (objInfo != null) {
                    webObjectives.stage2Objectives.add(objInfo);
                }
            }
        }

        // Process unrevealed Stage 2 objectives
        List<String> unrevealed2 = game.getPublicObjectives2Peekable();
        boolean isRevealed = isRedTapeMode || isCivilizedSocietyMode;
        for (String key : unrevealed2) {
            ObjectiveInfo objInfo = createObjectiveInfo(game, key, isRevealed, isRedTapeMode, 2);
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
            webObjectives.customObjectives.add(objInfo);
        }
    }

    private static ObjectiveInfo createObjectiveInfo(
            Game game, String key, boolean revealed, boolean hasRedTape, int defaultPointValue) {
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
        List<String> scoredPlayerIDs =
                game.getScoredPublicObjectives().getOrDefault(objectiveKey, Collections.emptyList());

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
