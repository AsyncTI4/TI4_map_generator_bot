package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class CrystellumPromissoryHandler {
    private static final String FRACTURE = "bepncryst";

    public static void resolveFracture(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit, boolean combat) {
        Player owner = game.getPNOwner(FRACTURE);
        if (!combat
                || player == null
                || owner == null
                || owner == player
                || unit.uh() == null
                || !player.hasPlayablePromissoryInHand(FRACTURE)
                || unit.unitKey().unitType() == UnitType.Fighter
                || !player.unitBelongsToPlayer(unit.unitKey())
                || !unit.tile().getPosition().equals(game.getActiveSystem())) return;
        var model = player.getUnitFromUnitKey(unit.unitKey());
        if (model == null || !model.getIsShip()) return;
        int fighters = (int) Math.ceil(model.getCost()) * unit.getTotalRemoved();
        AddUnitService.addUnits(event, unit.tile(), game, player.getColor(), fighters + " fighter " + Constants.SPACE);
        player.removePromissoryNote(FRACTURE);
        owner.setPromissoryNote(FRACTURE);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " resolved _Fracture_, placed " + fighters + " fighter"
                        + (fighters == 1 ? "" : "s") + " in " + unit.tile().getRepresentationForButtons(game, player)
                        + ", and returned the promissory note to " + owner.getRepresentationNoPing() + ".");
    }
}
