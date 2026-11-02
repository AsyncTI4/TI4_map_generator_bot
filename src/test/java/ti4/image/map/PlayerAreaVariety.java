package ti4.image.map;

import java.util.List;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;

/**
 * Golden-harness mutation that switches on player-area blocks the save corpus rarely or never reaches: homebrew token
 * supplies, stasis cards, blueprints, plots, counters, teammates, debt overflow and a unicode debt pool icon, relic
 * special cases, search warrant and leader trade goods. It only uses Player and Game setters, so the same mutation
 * renders on the pre-rewrite commit for the baseline. Values are chosen to stay inside each block's token arrays.
 */
final class PlayerAreaVariety {

    private PlayerAreaVariety() {}

    static void apply(Game game) {
        List<Player> players = game.getRealPlayers();
        Player first = players.get(0);
        Player second = players.get(1);
        Player third = players.get(2);
        Player fourth = players.get(3);

        first.addAbility("sting_of_the_hive");
        first.addAbility("reflections_of_the_void");
        first.addAbility("dream_nexus");
        first.addAbility("ancient_blueprints");
        first.setHasFoundCulFrag(true);
        first.setHasFoundUnkFrag(true);
        first.addTech("inf2");
        first.setStasisInfantry(7);
        first.setStasisFighters(23);
        first.setHonorCounter(2);
        first.setDishonorCounter(1);
        first.addRelic("emelpar");
        first.addRelic("absol_quantumcore");
        first.addExhaustedRelic("absol_quantumcore");

        second.addAbility("awaken");
        second.addAbility("galvanize");
        second.addAbility("plotsplots");
        second.setPlotCard("plot_one", 1);
        second.setPlotCard("plot_two", 2);
        second.setPlotCardFaction("plot_one", third.getFaction());
        second.setSteelbalanceCounter(3);
        second.setStarbalanceCounter(1);
        second.getTeamMateIDs().add(third.getUserID());
        second.getTeamMateIDs().add(fourth.getUserID());
        second.addDebtTokens(third.getColor(), 14);
        second.addDebtTokens(fourth.getColor(), 2, "harness pool");
        game.setDebtPoolIcon("harness pool", "🏦");

        third.setSearchWarrant(true);
        third.setPathTokenCounter(4);
        third.setHarvestCounter(3);
        game.setTyrant(third);
        for (Leader leader : third.getLeaders()) {
            leader.setTgCount(2);
            break;
        }

        fourth.addAbility("incursion");
        fourth.addAbility("imperia");
        fourth.addTech("tf-singularityx");
    }
}
