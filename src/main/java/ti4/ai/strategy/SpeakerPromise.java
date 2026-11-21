package ti4.ai.strategy;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiGameWrites;
import ti4.game.Game;
import ti4.game.Player;

record SpeakerPromise(
        String seller,
        String buyer,
        int saleRound,
        int price,
        Status status,
        long offeredAt,
        long expiresAt,
        int wanted) {

    enum Status {
        OFFERED,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    static final int NOT_DECLARED = 0;
    private static final String KEY = "aiSpeakerSale";
    private static final String FIELD = "|";
    private static final int FIELDS = 8;
    private static final String SAVE_REASON = "AI speaker sale";
    private static final String STRATEGY = "strategy";

    static Optional<SpeakerPromise> read(Game game) {
        String[] fields = StringUtils.splitPreserveAllTokens(game.getStoredValue(KEY), FIELD);
        if (fields == null || fields.length != FIELDS) return Optional.empty();
        try {
            return Optional.of(new SpeakerPromise(
                    fields[0],
                    fields[1],
                    Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3]),
                    Status.valueOf(fields[4]),
                    Long.parseLong(fields[5]),
                    Long.parseLong(fields[6]),
                    Integer.parseInt(fields[7])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    void save(Game game) {
        game.setStoredValue(KEY, encode());
    }

    void persist(Game game) {
        AiGameWrites.persist(game, SAVE_REASON, this::save);
    }

    SpeakerPromise with(Status next) {
        return new SpeakerPromise(seller, buyer, saleRound, price, next, offeredAt, expiresAt, wanted);
    }

    SpeakerPromise withWanted(int initiative) {
        return new SpeakerPromise(seller, buyer, saleRound, price, status, offeredAt, expiresAt, initiative);
    }

    boolean soldThisRound(Game game, String faction) {
        return seller.equals(faction) && saleRound == game.getRound();
    }

    boolean bindsThisStrategyPhase(Game game) {
        return status == Status.ACCEPTED
                && game.getRound() == pickRound()
                && STRATEGY.equalsIgnoreCase(game.getPhaseOfGame());
    }

    int pickRound() {
        return saleRound + 1;
    }

    boolean brokenBy(Game game, Player promisee, Player promiser) {
        return status == Status.ACCEPTED
                && declared()
                && seller.equals(promisee.getFaction())
                && buyer.equals(promiser.getFaction())
                && game.getRound() == pickRound()
                && promiser.getSCs().contains(wanted);
    }

    boolean declared() {
        return wanted != NOT_DECLARED;
    }

    private String encode() {
        return String.join(
                FIELD,
                seller,
                buyer,
                String.valueOf(saleRound),
                String.valueOf(price),
                status.name(),
                String.valueOf(offeredAt),
                String.valueOf(expiresAt),
                String.valueOf(wanted));
    }
}
