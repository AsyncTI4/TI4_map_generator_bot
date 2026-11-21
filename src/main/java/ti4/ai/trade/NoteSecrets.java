package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiMemory;
import ti4.ai.eval.GameHorizon;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

@UtilityClass
class NoteSecrets {

    static final String STRENGTHEN_BONDS = "sb";
    static final String BETRAY_A_FRIEND = "baf";
    static final int MAX_PRICE = 9;
    private static final double BONDS_SHARE = 0.9;
    private static final double BETRAY_SHARE = 0.4;
    private static final int LAST_EARLY_ROUND = 2;
    private static final int EARLY_STEP = 1;
    private static final int MIDDLE_STEP = 2;
    private static final int LATE_STEP = 3;
    private static final String ASKS_KEY = "noteSecretAsks|";

    static double value(Game game, Player seat, String alias) {
        Player owner = game.getPNOwner(alias);
        if (owner == null || Standings.same(owner, seat) || !landsInPlayArea(alias)) return 0;
        double share = (chasesBonds(seat) ? BONDS_SHARE : 0) + (chasesBetrayal(seat, owner) ? BETRAY_SHARE : 0);
        return share == 0 ? 0 : share * ScoringStakes.scoreValue(game, seat, 1);
    }

    static boolean chases(Game game, Player seat, String alias) {
        return value(game, seat, alias) > 0;
    }

    static int firstAsk(Game game, Player seat, Player owner, String alias) {
        double fair = NotesForTrade.giveCost(game, owner, seat, alias);
        return (int) Math.clamp(Math.ceil(fair), 1, MAX_PRICE);
    }

    static int askFloor(AiMemory memory, Game game, Player seat, Player owner, String alias) {
        return Math.min(MAX_PRICE, firstAsk(game, seat, owner, alias) + step(game) * asks(memory, owner));
    }

    static void asked(AiMemory memory, Player owner) {
        memory.put(asksKey(owner), String.valueOf(asks(memory, owner) + 1));
    }

    static int step(Game game) {
        if (GameHorizon.endInSight(game)) return LATE_STEP;
        return game.getRound() <= LAST_EARLY_ROUND ? EARLY_STEP : MIDDLE_STEP;
    }

    private static int asks(AiMemory memory, Player owner) {
        return memory.get(asksKey(owner)).map(Integer::parseInt).orElse(0);
    }

    private static String asksKey(Player owner) {
        return ASKS_KEY + owner.getFaction();
    }

    private static boolean chasesBonds(Player seat) {
        return seat.getSecretsUnscored().containsKey(STRENGTHEN_BONDS)
                && seat.getPromissoryNotesInPlayArea().stream().noneMatch(note -> !seat.ownsPromissoryNote(note));
    }

    private static boolean chasesBetrayal(Player seat, Player owner) {
        return seat.getSecretsUnscored().containsKey(BETRAY_A_FRIEND)
                && seat.getNeighbouringPlayers(true).contains(owner)
                && seat.getPromissoryNotesInPlayArea().stream().noneMatch(owner::ownsPromissoryNote);
    }

    private static boolean landsInPlayArea(String alias) {
        if (alias.endsWith(NoteGiving.THRONE) || alias.endsWith(NoteGiving.ALLIANCE)) return true;
        PromissoryNoteModel model = Mapper.getPromissoryNote(alias);
        return model != null && model.getPlayArea();
    }
}
