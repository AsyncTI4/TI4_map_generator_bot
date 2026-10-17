package ti4.ai.seat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class AiSetupRestoreTest extends BaseTi4Test {

    // /ai remove must undo what /ai add changed: the replaced map tile, the speaker and the game flags it set.
    @Test
    void restoresTheTileSpeakerAndFlagsThatAddingTheSeatChanged() {
        Game game = new Game();
        game.setName("ai-setup-restore-test");
        game.setTile(new Tile("19", "201"));
        Player human = game.addPlayer("human-speaker", "Human");
        human.setFaction("sol");
        human.setColor("blue");
        game.setSpeakerUserID("human-speaker");
        game.setHomebrew(false);
        game.setNewTransactionMethod(false);
        Player seat = game.addPlayer("7100000123456789", "Nekro AI");
        seat.setColor("black");

        AiSetupRestore.remember(game, seat, "201");
        game.setTile(new Tile("18", "201"));
        game.setSpeakerUserID(seat.getUserID());
        game.setHomebrew(true);
        game.setNewTransactionMethod(true);

        AiSetupRestore.restore(game, seat);

        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("19");
        assertThat(game.getSpeakerUserID()).isEqualTo("human-speaker");
        assertThat(game.isHomebrew()).isFalse();
        assertThat(game.isNewTransactionMethod()).isFalse();
        assertThat(game.getStoredValue(AiSetupRestore.GAME_KEY)).isEmpty();
    }

    // Player stored values are saved as "key,value;" pairs and the loader cannot read an empty value, so one blank
    // value makes the whole game file unloadable. The self-play harness caught this: adding the first AI seat to a
    // game without a speaker stored a blank speaker snapshot.
    @Test
    void neverStoresABlankOrUnsafeValueOnTheSeat() {
        Game game = new Game();
        game.setName("ai-setup-restore-blank-test");
        Player seat = game.addPlayer("7100000123456789", "Nekro AI");
        seat.setColor("black");

        AiSetupRestore.remember(game, seat, "301");

        assertThat(seat.getStoredValueMap()).allSatisfy((key, value) -> {
            assertThat(value).isNotBlank();
            assertThat(ti4.service.testbed.TestBedService.isSaveSafe(value)).isTrue();
        });
    }

    @Test
    void removesAHomeTilePlacedOnAnEmptyPosition() {
        Game game = new Game();
        game.setName("ai-setup-restore-empty-test");
        Player seat = game.addPlayer("7100000123456789", "Nekro AI");
        seat.setColor("black");

        AiSetupRestore.remember(game, seat, "202");
        game.setTile(new Tile("18", "202"));

        AiSetupRestore.restore(game, seat);

        assertThat(game.getTileByPosition("202")).isNull();
    }

    // With several AI seats, removing one restores only its own tile; the game flags the first seat changed stay
    // until the last AI seat leaves.
    @Test
    void keepsTheGameFlagsUntilTheLastAiSeatIsRemoved() {
        Game game = new Game();
        game.setName("ai-setup-restore-multi-test");
        game.setTile(new Tile("19", "201"));
        game.setTile(new Tile("20", "204"));
        game.setHomebrew(false);
        Player first = game.addPlayer("7100000123456789", "Nekro AI");
        first.setColor("black");
        AiSetupRestore.remember(game, first, "201");
        game.setTile(new Tile("8", "201"));
        game.setHomebrew(true);
        Player second = game.addPlayer("7100000987654321", "Sardakk AI");
        second.setColor("red");
        AiSetupRestore.remember(game, second, "204");
        game.setTile(new Tile("13", "204"));

        AiSetupRestore.restore(game, first);
        game.removePlayer(first.getUserID());

        assertThat(game.getTileByPosition("201").getTileID()).isEqualTo("19");
        assertThat(game.getTileByPosition("204").getTileID()).isEqualTo("13");
        assertThat(game.isHomebrew()).isTrue();

        AiSetupRestore.restore(game, second);

        assertThat(game.getTileByPosition("204").getTileID()).isEqualTo("20");
        assertThat(game.isHomebrew()).isFalse();
        assertThat(game.getStoredValue(AiSetupRestore.GAME_KEY)).isEmpty();
    }
}
