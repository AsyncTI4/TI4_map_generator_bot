package ti4.discord.interactions.buttons.handlers.edict.resolver;

import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.edict.EdictResolveButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.regex.RegexService;
import ti4.service.unit.RemoveUnitService;

public class TkSpoilResolver implements EdictResolver {

    @Getter
    public String edict = "tk-spoil";

    private List<Button> buttons() {
        return List.of(Buttons.red("beginSpoil", "Remove 1 Ship"));
    }

    public void handle(ButtonInteractionEvent event, Game game, Player tyrant) {
        tyrant.setStoredValue(edict, "0");
        String msg = gamePing(game, "-# The tyrant will gain their trade goods automatically.");
        MessageHelper.sendMessageToChannelWithButtons(game.getActionsChannel(), msg, buttons());
    }

    private static void clearResolved(Game game) {
        for (Player p2 : game.getRealPlayers()) {
            p2.removeStoredValue("tk-spoil");
        }
    }

    private static boolean alreadyResolved(Player player) {
        boolean resolved = player.hasStoredValue("tk-spoil");
        return resolved;
    }

    private static void afterResolve(ButtonInteractionEvent event, Game game, Player player) {
        if (game.getRealPlayers().stream().allMatch(TkSpoilResolver::alreadyResolved)) {
            Player tyrant = EdictResolveButtonHandler.getEdictResolver(game);
            int total = 0;
            for (Player p2 : game.getRealPlayersExcludingThis(tyrant)) {
                try {
                    total += Integer.parseInt(p2.getStoredValue("tk-spoil"));
                } catch (Exception e) {
                }
            }
            String gain = tyrant.gainTG(total);
            String msg = tyrant.getRepresentationUnfogged() + " has gained " + total;
            msg += " trade goods " + gain + " from _Spoil_.";
            MessageHelper.sendMessageToChannel(tyrant.getCorrectChannel(), msg);

            ButtonHelperAbilities.pillageCheck(tyrant, game);
            ButtonHelperAgents.resolveArtunoCheck(tyrant, total);

            clearResolved(game);
            if (game.isFowMode()) {
                ButtonHelper.deleteMessage(event);
            } else {
                ButtonHelper.deleteAllButtons(event);
            }
        }
    }

    @ButtonHandler("beginSpoil")
    private static void beginSpoil(ButtonInteractionEvent event, Game game, Player player) {
        Predicate<Tile> hasNonFF = t -> t.hasPlayerNonFighterShips(player);
        List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(player, game, "spoilTile", hasNonFF, false);
        String msg = "Choose a tile that has a ship you want to _spoil_:";
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, msg, buttons);
    }

    @ButtonHandler("spoilTile_")
    private static void chooseTileToSpoil(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "spoilTile_" + RegexHelper.posRegex();
        RegexService.runMatcher(rx, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            Space space = tile.getSpaceUnitHolder();

            String pos = tile.getPosition();
            List<Button> buttons =
                    ButtonHelper.getUnitsOnHolderForAction(player, space, pos, "Remove ", "spoilShip", false);

            String msg = "Choose a ship in " + tile.getRepresentationForButtons(game, player) + " to spoil:";
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, msg, buttons);
        });
    }

    @ButtonHandler("spoilShip_")
    private static void spoilShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "spoilShip_" + RegexHelper.posRegex() + "_" + RegexHelper.unitTypeRegex() + "_"
                + RegexHelper.unitStateRegex();
        RegexService.runMatcher(rx, buttonID, matcher -> {
            Player tyrant = EdictResolveButtonHandler.getEdictResolver(game);

            Tile tile = game.getTileByPosition(matcher.group("pos"));
            Space space = tile.getSpaceUnitHolder();
            UnitType unitType = Units.findUnitType(matcher.group("unittype"));
            UnitState state = Units.findUnitState(matcher.group("state"));
            RemoveUnitService.removeUnit(event, tile, game, player, space, unitType, 1, state);

            UnitModel model = player.getUnitByType(unitType);
            String unitName = model.getNameRepresentation(state);

            int amt = Math.round(model.getCost());
            String msg = player.getRepresentationUnfogged() + " has removed 1 " + unitName;
            msg += " from tile " + tile.getRepresentationForButtons(game, player) + ",";
            msg += " giving " + (game.isFowMode() ? "the tyrant" : tyrant.getRepresentation());
            msg += " " + amt + " trade goods " + MiscEmojis.tg(amt);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

            player.setStoredValue("tk-spoil", Integer.toString(amt));
            afterResolve(event, game, player);
        });
    }
}
