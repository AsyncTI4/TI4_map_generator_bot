package ti4.helpers.twilight_kart;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class TkHelperStarflare {
    public static final String AC_ID = "tk-nova-starflare";

    public static void onMove(Game game, Tile fromTile, Tile toTile, List<Map.Entry<Units.UnitKey, Integer>> movingUnits) {
        onMove(game, List.of(fromTile), toTile, movingUnits);
    }

    public static void onMove(Game game, List<Tile> fromTiles, Tile toTile, List<Map.Entry<Units.UnitKey, Integer>> movingUnits) {
        Player acHolder = game.getRealPlayers().stream()
                .filter(p -> p.getPlayableActionCards().contains(AC_ID))
                .findAny()
                .orElse(null);
        if (acHolder == null) {
            return;
        }
        if (Stream.concat(fromTiles.stream(), Stream.of(toTile))
                .noneMatch(tile -> tile.isAnomaly(game, acHolder))) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                acHolder.getCardsInfoThread(), "", List.of(Buttons.green("", "", CardEmojis.ActionCard)));
    }
}
