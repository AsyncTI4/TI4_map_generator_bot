package ti4.ai.trade;

import java.util.Set;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

/**
 * A table for trade tests: goal 10, round 3, the action phase on Nekro's turn, nobody scored. Nekro is the AI seat
 * doing the valuing and Sol is its partner (a human by default).
 *
 * <p>Nobody neighbours anybody until a test says so: the corners of the outer ring ({@link #NEKRO_SPOT},
 * {@link #SOL_SPOT}, {@link #FAR_SPOT}, {@link #FARTHER_SPOT}) are two systems apart from each other, and a ship
 * placed {@link #BESIDE_NEKRO} or {@link #BESIDE_SOL} makes its owner that seat's neighbour.
 */
final class TradeTable {

    static final String NEKRO_SPOT = "301";
    static final String BESIDE_NEKRO = "302";
    static final String SOL_SPOT = "304";
    static final String BESIDE_SOL = "305";
    static final String FAR_SPOT = "307";
    static final String FARTHER_SPOT = "310";
    static final String THIRD_AI_ID = "7100000555555555";
    static final String FOURTH_AI_ID = "7100000666666666";
    static final String SECOND_HUMAN_ID = "200000000000000002";
    private static final String EMPTY_SYSTEM = "46";
    private static final String CUSTODIANS = "Custodians";
    private static final int CUSTODIANS_ID = 0;

    final AiTestGame test;
    final Game game;
    final Player nekro;
    final Player sol;
    private long modified = 1;
    private int awards;

    private TradeTable(AiTestGame test) {
        this.test = test;
        game = test.game;
        nekro = test.nekro;
        sol = test.sol;
        game.setVp(10);
        game.setRound(3);
        test.aiIsActive("action");
        // The bot keeps the custodians token as revealed objective 0; reserving it here stops the first custom
        // objective a test scores (for points) from counting as the custodians.
        game.addCustomPO(CUSTODIANS, 0);
        game.setLastModifiedDate(modified);
    }

    static TradeTable withHumanSol() {
        return new TradeTable(new AiTestGame());
    }

    static TradeTable withAiSol() {
        return new TradeTable(AiTestGame.withSolAi());
    }

    Player aiSeat(String faction, String color) {
        return test.addSeat(THIRD_AI_ID, faction, color);
    }

    Player secondAiSeat(String faction, String color) {
        return test.addSeat(FOURTH_AI_ID, faction, color);
    }

    Player humanSeat(String faction, String color) {
        return test.addSeat(SECOND_HUMAN_ID, faction, color);
    }

    /** A destroyer of {@code player} in an empty system at {@code position}. */
    void presence(Player player, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) tile = test.place(EMPTY_SYSTEM, position);
        test.units(tile, "space", player, UnitType.Destroyer, 1);
        changed();
    }

    /** Nekro at {@link #NEKRO_SPOT} and Sol right beside it. */
    void nekroAndSolNeighbour() {
        presence(nekro, NEKRO_SPOT);
        presence(sol, BESIDE_NEKRO);
    }

    /** Neighbour sets are cached until the game is saved; a test that moves units "saves" with this. */
    void changed() {
        game.setLastModifiedDate(++modified);
    }

    /** Adds {@code points} victory points to what {@code player} already has. */
    void points(Player player, int points) {
        if (points > 0) {
            String award = "Test points " + (++awards);
            game.scorePublicObjective(player.getUserID(), game.addCustomPO(award, points));
        }
        changed();
    }

    void stock(Player player, int commodities, int tradeGoods) {
        player.setCommodities(commodities);
        player.setTg(tradeGoods);
    }

    void reveal(String objective) {
        game.getRevealedPublicObjectives()
                .put(objective, game.getRevealedPublicObjectives().size() + 1);
    }

    /** Sol's home system (Jord) at {@link #SOL_SPOT}, controlled, so Sol may score public objectives. */
    void solHome() {
        test.place("01", SOL_SPOT);
        sol.addPlanet("jord");
        changed();
    }

    /** Somebody took the custodians token, so agenda phases will happen. */
    void custodiansTaken(Player by) {
        game.scorePublicObjective(by.getUserID(), CUSTODIANS_ID);
    }

    AiTurnContext context() {
        return test.context();
    }

    AiTurnContext contextFor(Player seat) {
        return test.contextFor(seat, Set.of(), AiTestGame.NOW);
    }
}
