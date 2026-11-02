package ti4.image.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class TransactionGeneratorTest extends BaseTi4Test {

    private Game game;
    private Player sender;
    private Player receiver;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("transaction-generator-test");
        sender = seat("p1", "sol", "blue");
        receiver = seat("p2", "hacan", "yellow");
        sender.addTransactionItem("sendingsol_receivinghacan_TGs_3");
    }

    private Player seat(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        return player;
    }

    @Test
    void drawingAnEmptySideLeavesTheNothingMessageUnstored() {
        BufferedImage image = TransactionGenerator.drawTradeOfferMeme(game, sender, receiver);

        assertNotNull(image);
        assertEquals("", game.getStoredValue("hacanNothingMessage"));
    }

    @Test
    void drawingAnEmptySideUsesTheStoredNothingMessage() {
        BufferedImage fallback = TransactionGenerator.drawTradeOfferMeme(game, sender, receiver);
        game.setStoredValue("hacanNothingMessage", "Absolutely nothing, as agreed");

        BufferedImage stored = TransactionGenerator.drawTradeOfferMeme(game, sender, receiver);

        assertEquals("Absolutely nothing, as agreed", game.getStoredValue("hacanNothingMessage"));
        org.junit.jupiter.api.Assertions.assertFalse(sameRaster(fallback, stored));
    }

    private static boolean sameRaster(BufferedImage first, BufferedImage second) {
        return java.util.Arrays.equals(
                first.getRGB(0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth()),
                second.getRGB(0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth()));
    }
}
