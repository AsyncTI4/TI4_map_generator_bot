package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.xan;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class XanUnitHandler {

    public static int countSpaceDocksInTile(Tile tile, Game game) {
        int count = 0;
        for (var unitHolder : tile.getPlanetUnitHolders()) count += countSpaceDocksOnHolder(unitHolder, game);
        return count;
    }

    public static int countSpaceDocksOnHolder(UnitHolder unitHolder, Game game) {
        int count = 0;
        for (var player : game.getRealPlayers())
            count += unitHolder.getUnitCount(UnitType.Spacedock, player.getColor());
        return count;
    }

    public static void offerFlagshipReplace(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Tile> flagshipTiles =
                CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Flagship);
        if (flagshipTiles.isEmpty()) return;
        Tile flagshipTile = flagshipTiles.getFirst();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getFactionEmoji() + " **Aegis Ascendant**: you may replace the flagship with a war sun.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "xanFlagshipReplace_" + flagshipTile.getPosition(),
                                "Replace Aegis Ascendant with a War Sun"),
                        Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler("xanFlagshipReplace_")
    public static void xanFlagshipReplace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("xanFlagshipReplace_", "");
        Tile tile = game.getTileByPosition(tilePos);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 fs");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 ws");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " replaced **Aegis Ascendant** with a war sun in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
