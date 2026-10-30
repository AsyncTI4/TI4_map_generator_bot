package ti4.ai.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;

/**
 * What one self-play game did about Mecatol Rex and the custodians, read from public state at every AI decision:
 * the round the custodians token was first seen gone, who took it and how, how often Mecatol Rex changed hands
 * afterwards, and the Politics, speaker and Leadership chain around round 1 and 2.
 */
final class MecatolTracker {

    static final int NEVER = 0;

    record Summary(
            int custodiansRound,
            String taker,
            int takerInitiative,
            boolean takerWasSpeaker,
            boolean takerFirstAction,
            String politicsRound1,
            String speakerRound2,
            String leadershipRound2,
            int controlChanges) {

        static final Summary NOTHING = new Summary(NEVER, "", -1, false, false, "", "", "", 0);

        boolean taken() {
            return custodiansRound != NEVER;
        }

        boolean takerWentFirst() {
            return taken() && takerInitiative <= 1;
        }

        boolean politicsSpeakerLeadershipChain() {
            return !politicsRound1.isEmpty()
                    && politicsRound1.equals(speakerRound2)
                    && politicsRound1.equals(leadershipRound2);
        }
    }

    private boolean tokenSeen;
    private int custodiansRound = NEVER;
    private String taker = "";
    private int takerInitiative = -1;
    private boolean takerWasSpeaker;
    private String controller = "";
    private int controlChanges;
    private String politicsRound1 = "";
    private String speakerRound2 = "";
    private String leadershipRound2 = "";
    private final Map<String, Integer> actionsStarted = new HashMap<>();
    private final Map<String, Integer> mecatolActionIndex = new HashMap<>();

    synchronized void observe(Game game) {
        observeChain(game);
        observeCustodians(game);
    }

    synchronized void tacticalAction(Game game, Player seat, Optional<String> planTarget) {
        String key = game.getRound() + "|" + seat.getFaction();
        int index = actionsStarted.merge(key, 1, Integer::sum);
        Tile mecatol = game.getMecatolTile();
        if (mecatol != null && planTarget.filter(mecatol.getPosition()::equals).isPresent()) {
            mecatolActionIndex.putIfAbsent(key, index);
        }
    }

    synchronized Summary summary() {
        boolean first = taken() && mecatolActionIndex.getOrDefault(custodiansRound + "|" + taker, 0) == 1;
        return new Summary(
                custodiansRound,
                taker,
                takerInitiative,
                takerWasSpeaker,
                first,
                politicsRound1,
                speakerRound2,
                leadershipRound2,
                controlChanges);
    }

    private boolean taken() {
        return custodiansRound != NEVER;
    }

    private void observeChain(Game game) {
        if (game.getRound() == 1 && politicsRound1.isEmpty()) {
            politicsRound1 = holderOf(game, StrategyCard.POLITICS);
        }
        if (game.getRound() == 2) {
            if (speakerRound2.isEmpty() && game.getSpeaker() != null) {
                speakerRound2 = game.getSpeaker().getFaction();
            }
            if (leadershipRound2.isEmpty()) leadershipRound2 = holderOf(game, StrategyCard.LEADERSHIP);
        }
    }

    private static String holderOf(Game game, StrategyCard card) {
        for (Player player : game.getRealPlayers()) {
            for (int initiative : player.getSCs()) {
                if (StrategyCard.of(game, initiative) == card) return player.getFaction();
            }
        }
        return "";
    }

    private void observeCustodians(Game game) {
        Planet rex = mecatolPlanet(game);
        if (rex == null) return;
        boolean custodians = rex.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG);
        if (custodians) {
            tokenSeen = true;
            return;
        }
        if (!tokenSeen) return;
        Player holder = game.getPlayerThatControlsPlanet(rex.getName());
        String holderName = holder == null ? "" : holder.getFaction();
        if (!taken()) {
            custodiansRound = game.getRound();
            takerInitiative = holder == null ? -1 : holder.getInitiative();
            takerWasSpeaker = holder != null && holder.isSpeaker();
            taker = holderName;
            controller = holderName;
            return;
        }
        if (taker.isEmpty() && holder != null) {
            taker = holderName;
            takerInitiative = holder.getInitiative();
            takerWasSpeaker = holder.isSpeaker();
            controller = holderName;
            return;
        }
        if (!holderName.isEmpty() && !holderName.equals(controller)) {
            controlChanges++;
            controller = holderName;
        }
    }

    private static Planet mecatolPlanet(Game game) {
        Tile tile = game.getMecatolTile();
        if (tile == null) return null;
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> game.mecatols().contains(planet.getName()))
                .findFirst()
                .orElse(null);
    }
}
