package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ti4.ai.brain.AiDecision;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.service.map.FractureService;

/**
 * What one self-play game did about the Thunder's Edge expedition: which seat claimed which slice in which round,
 * how many times the Fracture was rolled for and when it entered, how many ingress tokens were placed, and who got
 * Thunder's Edge.
 */
final class ExpeditionTracker {

    private static final String PICK = "pick ";
    private static final String EXPEDITION = "expedition";
    private static final String ROLL = "roll for The Fracture";
    private static final String INGRESS = "place an ingress token near itself";

    record Claim(int round, String faction, String kind) {}

    record CardPick(int round, String card, boolean expeditionUse, boolean expeditionMain) {}

    record Summary(
            List<Claim> claims,
            List<CardPick> picks,
            int rolls,
            int fractureRound,
            int ingressTokens,
            int ingressPresses,
            int thundersEdgeRound,
            String mostSlices,
            String infantryOwner) {

        static final Summary NOTHING = new Summary(List.of(), List.of(), 0, 0, 0, 0, 0, "", "");

        boolean fractureEntered() {
            return fractureRound > 0;
        }

        boolean thundersEdgePlaced() {
            return thundersEdgeRound > 0;
        }
    }

    private final List<Claim> claims = new ArrayList<>();
    private final List<CardPick> picks = new ArrayList<>();
    private int rolls;
    private int fractureRound;
    private int ingressPresses;
    private int thundersEdgeRound;
    private String infantryOwner = "";

    synchronized void observe(Game game, AiDecision decision) {
        int round = game.getRound();
        for (Map.Entry<String, String> claim :
                game.getExpeditions().getExpeditionFactions().entrySet()) {
            if (claim.getValue() != null
                    && claims.stream().noneMatch(seen -> seen.kind().equals(claim.getKey()))) {
                claims.add(new Claim(round, claim.getValue(), claim.getKey()));
            }
        }
        if (fractureRound == 0 && FractureService.isFractureInPlay(game)) fractureRound = round;
        if (thundersEdgeRound == 0 && game.getTileFromPlanet(Constants.THUNDERSEDGE) != null) {
            thundersEdgeRound = round;
        }
        if (thundersEdgeRound > 0 && infantryOwner.isEmpty()) infantryOwner = infantryOn(game);
        if (decision instanceof AiDecision.Press press) {
            if (ROLL.equals(press.reason())) rolls++;
            if (INGRESS.equals(press.reason())) ingressPresses++;
            notePick(round, press.reason());
        }
    }

    // A pick's reason is "pick Card: use (+value), use (+value)" with the uses from the largest down, so the first use
    // is the main one.
    private void notePick(int round, String reason) {
        if (!reason.startsWith(PICK)) return;
        int colon = reason.indexOf(':');
        if (colon < 0) return;
        String card = reason.substring(PICK.length(), colon);
        String uses = reason.substring(colon + 1);
        String main = uses.split(", ")[0];
        picks.add(new CardPick(round, card, uses.contains(EXPEDITION), main.contains(EXPEDITION)));
    }

    synchronized Summary summary(Game game) {
        if (game == null) return Summary.NOTHING;
        long tokens = game.getTileMap().values().stream()
                .map(Tile::getSpaceUnitHolder)
                .filter(space -> space.getTokenList().contains(Constants.TOKEN_INGRESS))
                .count();
        return new Summary(
                List.copyOf(claims),
                List.copyOf(picks),
                rolls,
                fractureRound,
                (int) tokens,
                ingressPresses,
                thundersEdgeRound,
                mostSlices(),
                infantryOwner);
    }

    private String mostSlices() {
        Map<String, Long> counts = claims.stream()
                .collect(
                        java.util.stream.Collectors.groupingBy(Claim::faction, java.util.stream.Collectors.counting()));
        long most = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() == most)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.joining("+"));
    }

    private static String infantryOn(Game game) {
        Tile tile = game.getTileFromPlanet(Constants.THUNDERSEDGE);
        if (tile == null) return "";
        UnitHolder planet = tile.getUnitHolders().get(Constants.THUNDERSEDGE);
        return game.getRealPlayers().stream()
                .filter(player -> planet != null && BoardView.groundForces(planet, player) > 0)
                .map(Player::getFaction)
                .findFirst()
                .orElse("");
    }
}
