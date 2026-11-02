package ti4.spring.service.gameevent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import tools.jackson.core.type.TypeReference;

@UtilityClass
class CompactPlayerState {

    private static final int FORMAT_VERSION = 3;
    private static final String SPEAKER = "speaker";
    private static final String REVEALED = "revealed";
    private static final String PLAYERS = "players";
    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {};

    record Snapshot(String full, String delta) {}

    static Snapshot snapshot(Game game) {
        Map<String, Object> current = current(game);
        String full = write(current);
        if (full.equals(game.getLastPlayerStateJson())) {
            return new Snapshot(full, null);
        }
        return new Snapshot(full, write(deltaOf(previousOf(game), current)));
    }

    private static Map<String, Object> current(Game game) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("v", FORMAT_VERSION);
        state.put(SPEAKER, speakerFaction(game));
        state.put(REVEALED, new ArrayList<>(game.getRevealedPublicObjectives().keySet()));
        Map<String, Object> players = new TreeMap<>();
        for (Player player : game.getRealPlayers()) {
            players.put(player.getFaction(), serializePlayer(game, player));
        }
        state.put(PLAYERS, players);
        return state;
    }

    private static Map<String, Object> previousOf(Game game) {
        String previous = game.getLastPlayerStateJson();
        if (StringUtils.isBlank(previous)) {
            return Map.of();
        }
        try {
            return JsonMapperManager.basic().readValue(previous, STATE_TYPE);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to read the previous player state snapshot.", e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deltaOf(Map<String, Object> previous, Map<String, Object> current) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("v", FORMAT_VERSION);
        for (String key : List.of(SPEAKER, REVEALED)) {
            if (!sameJson(previous.get(key), current.get(key))) {
                delta.put(key, current.get(key));
            }
        }
        Map<String, Object> previousPlayers = (Map<String, Object>) previous.getOrDefault(PLAYERS, Map.of());
        Map<String, Object> changedPlayers = new TreeMap<>();
        ((Map<String, Object>) current.get(PLAYERS)).forEach((faction, row) -> {
            if (!sameJson(previousPlayers.get(faction), row)) {
                changedPlayers.put(faction, row);
            }
        });
        if (!changedPlayers.isEmpty()) {
            delta.put(PLAYERS, changedPlayers);
        }
        return delta;
    }

    private static boolean sameJson(Object left, Object right) {
        return Objects.equals(write(left), write(right));
    }

    private static String write(Object value) {
        return JsonMapperManager.basic().writeValueAsString(value);
    }

    private static String speakerFaction(Game game) {
        Player speaker = game.getSpeaker();
        return speaker == null ? null : speaker.getFaction();
    }

    private static List<Object> serializePlayer(Game game, Player player) {
        List<Object> row = new ArrayList<>();
        row.add(player.getTotalVictoryPoints());
        row.add(player.getTg());
        row.add(player.getCommodities());
        row.add(player.getTacticalCC());
        row.add(player.getFleetCC());
        row.add(player.getStrategicCC());
        row.add(sorted(player.getSCs()));
        row.add(sorted(player.getFollowedSCs()));
        row.add(secondariesUsedThisRound(game, player));
        row.add(unlockedLeaders(player));
        row.add(sorted(player.getTechs()));
        row.add(sorted(player.getExhaustedTechs()));
        row.add(sorted(player.getRelics()));
        row.add(sorted(player.getFragments()));
        row.add(player.getAcCount());
        row.add(player.getSo());
        row.add(player.getPnCount());
        row.add(player.isPassed() ? 1 : 0);
        row.add(scoredObjectives(game, player));
        return row;
    }

    private static List<Integer> secondariesUsedThisRound(Game game, Player player) {
        String factionMarker = "_" + player.getFaction() + "_";
        return game.getSCList().stream()
                .filter(sc ->
                        (game.getStoredValue("followedSC" + sc + "_" + game.getRound()) + "_").contains(factionMarker))
                .sorted()
                .toList();
    }

    private static List<String> unlockedLeaders(Player player) {
        return player.getLeaders().stream()
                .filter(leader -> !leader.isLocked())
                .map(leader -> leader.getId() + (leader.isActive() ? ":A" : leader.isExhausted() ? ":E" : ":R"))
                .sorted()
                .toList();
    }

    private static List<String> scoredObjectives(Game game, Player player) {
        List<String> scored = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry :
                game.getScoredPublicObjectives().entrySet()) {
            if (entry.getValue().contains(player.getUserID())) {
                scored.add(entry.getKey());
            }
        }
        scored.addAll(player.getSecretsScored().keySet());
        return scored.stream().sorted().toList();
    }

    private static <T extends Comparable<T>> List<T> sorted(Collection<T> values) {
        return values.stream().sorted().toList();
    }
}
