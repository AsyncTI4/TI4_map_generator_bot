package ti4.ai.actioncards;

import ti4.ai.AiTestGame;
import ti4.ai.tactical.Stall;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

/**
 * A table for action card tests: goal 10, round 3, the action phase on Nekro's turn, nobody scored. Nekro is the AI
 * seat that plays or cancels, Sol is a human, and Nekro's home system is in place so it may score public objectives.
 */
final class CardTable {

    static final String SOL_SPOT = "304";
    static final String THIRD_ID = "200000000000000002";
    private static final String CUSTODIANS = "Custodians";
    private static final int STRATEGY_CARDS = 8;

    final AiTestGame test;
    final Game game;
    final Player nekro;
    final Player sol;
    private int awards;

    CardTable() {
        test = new AiTestGame();
        game = test.game;
        nekro = test.nekro;
        sol = test.sol;
        game.setVp(10);
        game.setRound(3);
        test.aiIsActive("action");
        test.nekroHome();
        game.addCustomPO(CUSTODIANS, 0);
        for (int card = 1; card <= STRATEGY_CARDS; card++) game.setScTradeGood(card, 0);
    }

    // What a component action is worth on its own while another player still has a turn to take.
    double stall() {
        return Stall.actionValue(game, nekro);
    }

    Player third(String faction, String color) {
        return test.addSeat(THIRD_ID, faction, color);
    }

    void points(Player player, int points) {
        if (points <= 0) return;
        String award = "Test points " + (++awards);
        game.scorePublicObjective(player.getUserID(), game.addCustomPO(award, points));
    }

    void reveal(String objective) {
        game.getRevealedPublicObjectives()
                .put(objective, game.getRevealedPublicObjectives().size() + 1);
    }

    void solHome() {
        test.place("01", SOL_SPOT);
        sol.addPlanet("jord");
    }

    Tile solHomeWithADock() {
        solHome();
        Tile home = game.getTileByPosition(SOL_SPOT);
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        return home;
    }

    Tile system(String tileId, String position) {
        return test.place(tileId, position);
    }

    void ships(Player player, Tile tile, UnitType type, int count) {
        test.units(tile, "space", player, type, count);
    }

    void cards(Player player, String... aliases) {
        int index = 100;
        for (String alias : aliases) player.setActionCard(alias, ++index);
    }
}
