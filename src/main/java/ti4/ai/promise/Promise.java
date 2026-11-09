package ti4.ai.promise;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public record Promise(
        String id,
        Term term,
        String promiser,
        String promisee,
        String proposer,
        int round,
        String agenda,
        String target,
        int votes,
        int price,
        Status status,
        long offeredAt,
        long expiresAt,
        List<String> guarded,
        List<String> held) {

    public enum Term {
        VOTE_FOR,
        NO_ATTACK,
        STAY_OUT
    }

    public enum Status {
        OFFERED,
        ACCEPTED,
        DECLINED,
        EXPIRED,
        KEPT,
        BROKEN,
        VOID
    }

    private static final String FIELD = "|";
    private static final String POSITIONS = "+";
    private static final int FIELDS = 15;
    private static final String UNSAFE = FIELD + POSITIONS + PromiseBook.RECORDS;
    private static final String SAVE_DELIMITERS = ":,";

    public Promise {
        guarded = List.copyOf(guarded);
        held = List.copyOf(held);
    }

    public static Promise offer(
            Game game,
            Term term,
            Player promiser,
            Player promisee,
            Player proposer,
            String agenda,
            String target,
            int votes,
            int price,
            long now,
            long expiresAt) {
        return new Promise(
                Long.toString(now, Character.MAX_RADIX) + proposer.getColor(),
                term,
                promiser.getFaction(),
                promisee.getFaction(),
                proposer.getFaction(),
                game.getRound(),
                agenda,
                target,
                votes,
                price,
                Status.OFFERED,
                now,
                expiresAt,
                List.of(),
                List.of());
    }

    public Promise with(Status next) {
        return new Promise(
                id, term, promiser, promisee, proposer, round, agenda, target, votes, price, next, offeredAt, expiresAt,
                guarded, held);
    }

    public Promise withGuarded(List<String> positions) {
        return new Promise(
                id, term, promiser, promisee, proposer, round, agenda, target, votes, price, status, offeredAt,
                expiresAt, positions, held);
    }

    public boolean storable() {
        return StringUtils.containsNone(target + agenda, UNSAFE) && StringUtils.containsNone(encode(), SAVE_DELIMITERS);
    }

    public Promise accepted(Game game) {
        Player by = game.getPlayerFromColorOrFaction(promiser);
        Player to = game.getPlayerFromColorOrFaction(promisee);
        List<String> watched = by == null || to == null ? List.of() : watchedPositions(game, by, to);
        List<String> own = by == null || to == null || term == Term.VOTE_FOR ? List.of() : presence(game, by, to);
        return new Promise(
                id,
                term,
                promiser,
                promisee,
                proposer,
                round,
                agenda,
                target,
                votes,
                price,
                Status.ACCEPTED,
                offeredAt,
                expiresAt,
                watched,
                own);
    }

    public boolean binds() {
        return status == Status.ACCEPTED
                || (status == Status.OFFERED && proposer.equals(promiser) && term != Term.VOTE_FOR);
    }

    public boolean open() {
        return status == Status.OFFERED;
    }

    public boolean isPromiser(Player player) {
        return player != null && promiser.equals(player.getFaction());
    }

    public boolean isPromisee(Player player) {
        return player != null && promisee.equals(player.getFaction());
    }

    public boolean thisRound(Game game) {
        return round == game.getRound();
    }

    public boolean forbidsActivating(Game game, Tile tile) {
        if (tile == null) return false;
        return switch (term) {
            case VOTE_FOR -> false;
            case STAY_OUT -> target.equals(tile.getPosition());
            case NO_ATTACK -> {
                Player victim = game.getPlayerFromColorOrFaction(promisee);
                yield victim != null && Presence.at(victim, tile);
            }
        };
    }

    public Optional<Player> promiserIn(Game game) {
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(promiser));
    }

    public Optional<Player> promiseeIn(Game game) {
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(promisee));
    }

    private List<String> watchedPositions(Game game, Player by, Player to) {
        return switch (term) {
            case VOTE_FOR -> List.of();
            case STAY_OUT -> {
                Tile tile = game.getTileByPosition(target);
                yield tile == null || tile.hasPlayerCC(by) ? List.of() : List.of(target);
            }
            case NO_ATTACK -> presence(game, to, by);
        };
    }

    private static List<String> presence(Game game, Player owner, Player activator) {
        return game.getTileMap().values().stream()
                .filter(tile -> Presence.at(owner, tile))
                .filter(tile -> !tile.hasPlayerCC(activator))
                .map(Tile::getPosition)
                .sorted()
                .toList();
    }

    String encode() {
        return String.join(
                FIELD,
                id,
                term.name(),
                promiser,
                promisee,
                proposer,
                String.valueOf(round),
                agenda,
                target,
                String.valueOf(votes),
                String.valueOf(price),
                status.name(),
                String.valueOf(offeredAt),
                String.valueOf(expiresAt),
                String.join(POSITIONS, guarded),
                String.join(POSITIONS, held));
    }

    static Optional<Promise> decode(String encoded) {
        String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
        if (fields == null || fields.length != FIELDS) return Optional.empty();
        try {
            return Optional.of(new Promise(
                    fields[0],
                    Term.valueOf(fields[1]),
                    fields[2],
                    fields[3],
                    fields[4],
                    Integer.parseInt(fields[5]),
                    fields[6],
                    fields[7],
                    Integer.parseInt(fields[8]),
                    Integer.parseInt(fields[9]),
                    Status.valueOf(fields[10]),
                    Long.parseLong(fields[11]),
                    Long.parseLong(fields[12]),
                    positions(fields[13]),
                    positions(fields[14])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<String> positions(String joined) {
        return Arrays.stream(StringUtils.split(joined, POSITIONS)).toList();
    }
}
