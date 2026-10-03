package ti4.service.planet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class AddPlanetServiceTest extends BaseTi4Test {

    @Test
    void ministerOfExplorationGrantsTradeGoodWhenGainingControlOfAPlanet() {
        Game game = electedMinisterGame();
        game.setTile(new Tile("18", "18"));

        AddPlanetService.addPlanet(game.getPlayer("user"), "mr", game);

        assertThat(game.getPlayer("user").getTg()).isEqualTo(1);
    }

    @Test
    void ministerOfExplorationDoesNotGrantTradeGoodWhenGainingControlOfASpaceStation() {
        Game game = electedMinisterGame();
        game.setTile(new Tile("111", "111"));

        AddPlanetService.addPlanet(game.getPlayer("user"), "oluzstation", game);

        assertThat(game.getPlayer("user").getTg()).isZero();
    }

    // The Psychoarchaeology holder is the one who gains the trade good, so whether they are worth
    // pillaging must be judged on their own trade goods, not on the new owner's.
    @Test
    void psychoarchaeologyAutoExhaustsWhenHolderIsTooPoorToPillageEvenIfNewOwnerIsRich() {
        Game game = new Game();
        Player holder = psychoarchaeologyGameWithNeighbouringPillager(game);
        Player newOwner = realPlayer(game, "newowner", "sol", "red");
        holder.setTg(0);
        newOwner.setTg(5);

        AddPlanetService.addPlanet(newOwner, "thibah", game);

        assertThat(holder.getTg()).isEqualTo(1);
    }

    @Test
    void psychoarchaeologyDoesNotAutoExhaustWhenHolderWouldBecomePillageableEvenIfNewOwnerIsPoor() {
        Game game = new Game();
        Player holder = psychoarchaeologyGameWithNeighbouringPillager(game);
        Player newOwner = realPlayer(game, "newowner", "sol", "red");
        holder.setTg(2);
        newOwner.setTg(0);

        AddPlanetService.addPlanet(newOwner, "thibah", game);

        assertThat(holder.getTg()).isEqualTo(2);
    }

    private static Player psychoarchaeologyGameWithNeighbouringPillager(Game game) {
        game.setPhaseOfGame("action");
        game.setTile(new Tile("21", "101"));
        Tile pillagerTile = new Tile("18", "000");
        game.setTile(pillagerTile);

        Player holder = realPlayer(game, "holder", "hacan", "yellow");
        holder.addTech("pa");
        holder.addPlanet("thibah");

        Player pillager = realPlayer(game, "pillager", "mentak", "black");
        pillager.addAbility("pillage");
        pillagerTile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, pillager.getColorID()), 1);
        return holder;
    }

    private static Player realPlayer(Game game, String userId, String faction, String color) {
        Player player = game.addPlayer(userId, userId);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    private static Game electedMinisterGame() {
        Game game = new Game();
        game.setLaws(Map.of("minister_exploration", 1));
        game.setLawsInfo(Map.of("minister_exploration", "arborec"));
        var player = game.addPlayer("user", "User");
        player.setFaction("arborec");
        return game;
    }
}
