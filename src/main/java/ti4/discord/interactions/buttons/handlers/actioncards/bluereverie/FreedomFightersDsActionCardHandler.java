package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class FreedomFightersDsActionCardHandler {

    @ButtonHandler("resolveDsFreedomFighters_")
    public static void resolveFreedomFighters(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] context = buttonID.replace("resolveDsFreedomFighters_", "").split("\\|", 2);
        if (context.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        CombatContext combat = getGroundCombat(game, player);
        if (combat == null
                || !combat.tilePosition().equals(context[0])
                || !combat.unitHolderName().equals(context[1])) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", _Freedom Fighters_ can only be resolved during that ground combat.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(combat.tilePosition());
        Planet planet =
                tile != null && tile.getUnitHolders().get(combat.unitHolderName()) instanceof Planet planetHolder
                        ? planetHolder
                        : null;
        if (planet == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", _Freedom Fighters_ could not find the combat planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int infantry = planet.getInfluence();
        if (infantry > 0) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), infantry + " infantry " + planet.getName());
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed " + infantry + " infantry on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " via _Freedom Fighters_.");
    }

    public static Button getResolveButton(Game game, Player player) {
        CombatContext combat = getGroundCombat(game, player);
        if (combat == null) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + "resolveDsFreedomFighters_" + combat.tilePosition() + "|"
                        + combat.unitHolderName(),
                "Resolve Freedom Fighters");
    }

    private static CombatContext getGroundCombat(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null || !combat.factions().contains(player.getFaction())) {
            return null;
        }
        Tile tile = game.getTileByPosition(combat.tilePosition());
        if (tile == null || !(tile.getUnitHolders().get(combat.unitHolderName()) instanceof Planet)) {
            return null;
        }
        return new CombatContext(combat.tilePosition(), combat.unitHolderName());
    }

    private record CombatContext(String tilePosition, String unitHolderName) {}
}
