package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.map.FractureService;

@UtilityClass
public class NatauAbilityHandler {
    private static final String ADD_CATACLYSM_INGRESS = "natauAddCataclysmIngress_";

    public static Button getCataclysmFrankenButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + "beginCataclysmFranken", "Begin Cataclysm", FactionEmojis.natau);
    }

    @ButtonHandler("beginCataclysmFranken")
    public static void beginCataclysmFranken(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null || game == null || !player.hasAbility("cataclysm")) {
            return;
        }

        beginCataclysm(event, player, game);
    }

    public static void beginCataclysm(GenericInteractionCreateEvent event, Player player, Game game) {
        FractureService.enterPlayOrExplain(event, game, player, null);

        List<Button> anomalies = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.isAnomaly() || tile.isHomeSystem(game) || tile.isSupernova()) {
                continue;
            }

            anomalies.add(Buttons.green(
                    player.factionButtonChecker() + ADD_CATACLYSM_INGRESS + tile.getPosition(),
                    "Place Ingress in " + tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", after placing the normal ingress tokens, please choose a non-home, non-supernova anomaly in which to place an ingress token.",
                anomalies);
    }

    @ButtonHandler(ADD_CATACLYSM_INGRESS)
    public static void addCataclysmIngress(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(ADD_CATACLYSM_INGRESS.length()));
        if (tile == null || !tile.isAnomaly() || tile.isHomeSystem(game) || tile.isSupernova()) {
            return;
        }
        tile.addToken(Constants.TOKEN_INGRESS, Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed an ingress token in "
                        + tile.getRepresentationForButtons(game, player) + " with **Cataclysm**.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean chainedIgnoresAnomalies(Player player, UnitKey unitKey) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        return unit != null && "natau_flagship".equals(unit.getId());
    }

    public static boolean hasNatauMech(Game game, Tile tile, Player player) {
        if (player != null) {
            return player.hasUnit("natau_mech") && hasNatauMech(tile, player);
        }
        return game != null
                && game.getRealPlayers().stream().anyMatch(p -> p.hasUnit("natau_mech") && hasNatauMech(tile, p));
    }

    private static boolean hasNatauMech(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> holder.getUnitCount(UnitType.Mech, player) > 0);
    }

    public static void addChaosTurbineAdjacencies(
            Game game, Player player, String position, Set<String> adjacentPositions) {
        Tile origin = game.getTileByPosition(position);
        if (player == null || !player.hasTech("benatauct") || !isNonHomePrintedAnomaly(game, origin)) {
            return;
        }

        game.getTileMap().values().stream()
                .filter(tile -> !tile.getPosition().equals(position))
                .filter(tile -> isNonHomePrintedAnomaly(game, tile))
                .map(Tile::getPosition)
                .forEach(adjacentPositions::add);
    }

    private static boolean isNonHomePrintedAnomaly(Game game, Tile tile) {
        return tile != null
                && !tile.isHomeSystem(game)
                && tile.getTileModel() != null
                && tile.getTileModel().isAnomaly();
    }
}
