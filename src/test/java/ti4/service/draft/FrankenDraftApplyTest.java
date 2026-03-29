package ti4.service.draft;

import java.util.HashSet;
import org.junit.jupiter.api.BeforeAll;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.model.FactionModel;
import ti4.service.player.PlayerColorService;
import ti4.testUtils.BaseTi4Test;

public class FrankenDraftApplyTest extends BaseTi4Test {

    private static Game game;
    private static Player p1, p2, p3;

    @BeforeAll
    public static void setupGame() {
        if (game != null) return;
        game = new Game();
        game.newGameSetup();
        game.setName("Test Franken Draft Application");
        game.setCcNPlasticLimit(false);

        p1 = setupFrankenPlayer("franken12");
        p2 = setupFrankenPlayer("franken13");
        p3 = setupFrankenPlayer("franken14");
    }

    private static Player setupFrankenPlayer(String faction) {
        FactionModel model = Mapper.getFaction(faction);
        var player = game.addPlayer(model.getAlias(), model.getFactionName());
        player.setFaction(game, faction);
        player.setFactionEmoji("<" + faction + ">");
        player.setColor(PlayerColorService.getNewColor(player));
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
