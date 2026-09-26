package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class FoWVisionTokenTest extends BaseTi4Test {

    private static final String POS = "101";

    private Game game;
    private Player red;
    private Player blue;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("vision-test-game");
        red = addPlayer("red-user", "winnu", "red");
        blue = addPlayer("blue-user", "jolnar", "blue");
        tile = new Tile("19", POS);
        game.setTile(tile);
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player p = game.addPlayer(userId, faction);
        p.setFaction(faction);
        p.setColor(color);
        return p;
    }

    private boolean sees(Player p, String position) {
        return FoWHelper.getTilePositionsToShow(game, p).contains(position);
    }

    private void placeToken(String... colors) {
        tile.addToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
        tile.setFowVisionGrant(List.of(colors));
    }

    @Test
    void mapperRecognisesVisionTokenByIdAndImagePath() {
        assertTrue(Mapper.isFowVisionToken("fowvision"));
        assertTrue(Mapper.isFowVisionToken(Constants.TOKEN_FOWVISION_PNG));
        assertFalse(Mapper.isFowVisionToken("token_gravityrift.png"));
        assertFalse(Mapper.isFowVisionToken(null));
    }

    @Test
    void noTokenMeansNoVision() {
        assertFalse(sees(red, POS));
        assertFalse(sees(blue, POS));
    }

    @Test
    void tokenWithoutGrantRevealsToEveryone() {
        placeToken();
        assertTrue(sees(red, POS));
        assertTrue(sees(blue, POS));
    }

    @Test
    void grantRestrictsToListedColors() {
        placeToken("red");
        assertTrue(sees(red, POS));
        assertFalse(sees(blue, POS));
    }

    @Test
    void grantWithoutTokenRevealsNothing() {
        tile.setFowVisionGrant(List.of("red"));
        assertFalse(sees(red, POS));
    }

    @Test
    void removingTokenClearsGrantSoNextTokenIsForEveryone() {
        placeToken("red");
        tile.removeToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
        assertTrue(tile.getFowVisionGrant().isEmpty());

        tile.addToken(Constants.TOKEN_FOWVISION_PNG, Constants.SPACE);
        assertTrue(sees(blue, POS));
    }

    @Test
    void grantFollowsTileWhenMoved() {
        placeToken("red");
        String newPos = "205";
        game.removeTile(POS);
        tile.setPosition(newPos);
        game.setTile(tile);

        assertTrue(sees(red, newPos));
        assertFalse(sees(blue, newPos));
    }
}
