package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.ai.agenda.AgendaOutcomes;
import ti4.game.Game;
import ti4.game.Player;

/**
 * Drive the Debate, read from public state at every AI decision: for each election the table resolved, the seats that
 * held the secret, the votes each of them cast against the votes the winning outcome got, who was elected, and whether
 * the secret was scored. The scoring window is the agenda-end phase after the agenda resolves, until the next reveal
 * clears the resolved outcome, so an election that elects a holder who never scores shows up as "elected not scored".
 */
final class DebateTracker {

    private static final String DRIVE_THE_DEBATE = "dtd";
    private static final String AGENDA_PHASE_PREFIX = "agenda";

    private static final class Election {
        final String info;
        final int round;
        final Set<String> holders = new LinkedHashSet<>();
        final Set<String> scored = new LinkedHashSet<>();
        Map<String, String> votes = new LinkedHashMap<>();
        String outcome = "";

        Election(String info, int round) {
            this.info = info;
            this.round = round;
        }
    }

    private final List<String> lines = new ArrayList<>();
    private Election open;
    private int elections;
    private int heldElections;
    private int holderElected;
    private int holderScored;
    private int electedNotScored;
    private boolean ended;

    synchronized void observe(Game game) {
        String phase = game.getPhaseOfGame();
        if (phase == null || !phase.toLowerCase().startsWith(AGENDA_PHASE_PREFIX) || !isElection(game)) return;
        String info = game.getCurrentAgendaInfo();
        if (open != null && !open.info.equals(info)) close(game);
        if (open == null) open = new Election(info, game.getRound());
        for (Player seat : game.getRealPlayers()) {
            if (seat.getSecretsUnscored().containsKey(DRIVE_THE_DEBATE)) open.holders.add(seat.getFaction());
            if (seat.getSecretsScored().containsKey(DRIVE_THE_DEBATE)) open.scored.add(seat.getFaction());
        }
        if (!game.getCurrentAgendaVotes().isEmpty()) open.votes = new LinkedHashMap<>(game.getCurrentAgendaVotes());
        String resolved = game.getStoredValue("resolvedAgendaOutcome");
        if (!resolved.isBlank()) open.outcome = resolved;
    }

    synchronized List<String> lines(Game game) {
        if (!ended) {
            ended = true;
            if (open != null) close(game);
            lines.add(String.format(
                    "DriveTheDebate %s: %d elections, %d with a holder, holder elected %d, scored %d, elected not scored %d",
                    game.getName(), elections, heldElections, holderElected, holderScored, electedNotScored));
        }
        return List.copyOf(lines);
    }

    private static boolean isElection(Game game) {
        List<String> outcomes = AgendaOutcomes.of(game);
        return !outcomes.isEmpty() && !outcomes.contains("for");
    }

    private void close(Game game) {
        Election election = open;
        open = null;
        elections++;
        if (election.holders.isEmpty()) return;
        heldElections++;
        for (String faction : election.holders) {
            Player holder = game.getPlayerFromColorOrFaction(faction);
            boolean elected = holder != null && electsHolder(election.outcome, holder);
            boolean scored = election.scored.contains(faction);
            if (elected) holderElected++;
            if (scored) holderScored++;
            if (elected && !scored) electedNotScored++;
            lines.add(String.format(
                    "DriveTheDebate %s r%d %s: holder %s cast %d, winner %s got %d, elected %s, scored %s",
                    game.getName(),
                    election.round,
                    election.info,
                    faction,
                    holder == null ? 0 : cast(election.votes, holder),
                    election.outcome.isBlank() ? "none" : election.outcome,
                    tally(election.votes, election.outcome),
                    elected,
                    scored));
        }
    }

    private static boolean electsHolder(String outcome, Player holder) {
        return !outcome.isBlank()
                && (outcome.equalsIgnoreCase(holder.getFaction())
                        || outcome.equalsIgnoreCase(holder.getColor())
                        || holder.getPlanets().contains(outcome));
    }

    private static int cast(Map<String, String> votes, Player holder) {
        int total = 0;
        for (String entry : votes.values()) {
            for (String piece : entry.split(";")) {
                int split = piece.indexOf('_');
                String voter = split > 0 ? piece.substring(0, split) : "";
                if (voter.equalsIgnoreCase(holder.getFaction()) || voter.equalsIgnoreCase(holder.getColor())) {
                    total += number(piece.substring(split + 1));
                }
            }
        }
        return total;
    }

    private static int tally(Map<String, String> votes, String outcome) {
        String entry = votes.get(outcome);
        if (entry == null) return 0;
        int total = 0;
        for (String piece : entry.split(";")) {
            int split = piece.indexOf('_');
            if (split > 0) total += number(piece.substring(split + 1));
        }
        return total;
    }

    private static int number(String text) {
        return text.chars().allMatch(Character::isDigit) && !text.isEmpty() ? Integer.parseInt(text) : 0;
    }
}
