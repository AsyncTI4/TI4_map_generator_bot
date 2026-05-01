package ti4.service.draft;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeAll;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

public class FrankenDraftApplyTest extends BaseTi4Test {

    private static Game game;

    @BeforeAll
    public static void setupGame() {
        if (game != null) return;
        game = new Game();
        game.newGameSetup();
        game.setName("Test Franken Draft Application");
        game.setCcNPlasticLimit(false);

        Player p1 = setupFrankenPlayer("franken12");
        Player p2 = setupFrankenPlayer("franken13");
        Player p3 = setupFrankenPlayer("franken14");
    }

    private static Player setupFrankenPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        var player = game.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(game, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(PlayerColorService.getPreferredColor(player));
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.addBreakthrough(model.getBreakthrough());
        player.setBreakthroughUnlocked(model.getBreakthrough(), true);
        player.setCommoditiesBase(model.getCommodities());
        player.setPlanets(model.getHomePlanets());
        player.setFactionTechs(model.getFactionTech());

        // unlock leaders
        for (Leader ll : player.getLeaders()) {
            ll.setLocked(false);
        }

        // techs: add all faction techs
        if (model.getStartingTech() != null) {
            player.setTechs(model.getStartingTech());
        }
        for (String tech : player.getFactionTechs()) {
            player.addTech(tech);
        }
        return player;
    }
}
