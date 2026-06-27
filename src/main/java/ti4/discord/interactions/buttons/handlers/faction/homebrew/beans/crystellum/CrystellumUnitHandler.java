package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class CrystellumUnitHandler {

    public static void resolveCrystFlagDestroy(
            GenericInteractionCreateEvent event, Player player, Game game, RemovedUnit unit) {
        if (player == null || unit == null) {
            return;
        }
        if (!player.hasUnit("crystellum_flagship")) {
            return;
        }
        UnitModel model = player.getUnitFromUnitKey(unit.unitKey());
        if (model != null) {
            int fightersToAdd = model.getCapacityValue();
            AddUnitService.addUnits(event, unit.tile(), game, player.getColor(), fightersToAdd + " fighter");

            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " placed "
                            + fightersToAdd
                            + " fighters in "
                            + unit.tile().getRepresentation()
                            + " using _The Fractal_ ability.");

            if (player.hasLeaderUnlocked("crystellumcommander")) {
                String tilePos = unit.tile().getPosition();
                List<Button> buttons = List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "fractalCommanderFighter_" + tilePos,
                                "Place Additional Fighter"),
                        Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        event.getMessageChannel(),
                        player.getRepresentation()
                                + ", the bot sees that you have _Highbearer Lumina_, the Crystellum commander unlocked. If the flagship was given +1 capacity due to the commander, you may press this button to add an additional fighter.",
                        buttons);
            }
        }
    }

    @ButtonHandler("fractalCommanderFighter_")
    public static void resolveFractalCommanderFighter(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnit("crystellum_flagship")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasLeaderUnlocked("crystellumcommander")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String pos = buttonID.replace("fractalCommanderFighter_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find the tile to place the fighter.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " added 1 fighter to " + tile.getRepresentation()
                        + " from the _Highbearer Lumina_ commander capacity boost on _The Fractal_.");

        ButtonHelper.deleteMessage(event);
    }

    public static void offerRefractumButtonIfRelevant(
            List<Button> buttons, Player player, Game game, Tile tile, UnitHolder combatOnHolder, int hits) {
        if (buttons == null || player == null || game == null || tile == null || combatOnHolder == null) {
            return;
        }
        if (hits < 1) {
            return;
        }
        if (!player.hasUnit("crystellum_mech")) {
            return;
        }
        if (!(combatOnHolder instanceof Planet)) {
            return;
        }
        if (combatOnHolder.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
            return;
        }
        if (tile.getSpaceUnitHolder() == null) {
            return;
        }
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Fighter, player.getColor()) < 1) {
            return;
        }

        String tilePos = tile.getPosition();
        String planetName = combatOnHolder.getName();
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "crystellumUseRefractum_" + tilePos + "|" + planetName + "|" + hits,
                "Use Refractum"));
    }

    @ButtonHandler("crystellumUseRefractum_")
    public static void resolveUseRefractum(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring("crystellumUseRefractum_".length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = parts[0];
        String planetName = parts[1];
        int realHits;
        try {
            realHits = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitHolder planet = tile.getUnitHolders().get(planetName);
        UnitHolder space = tile.getSpaceUnitHolder();
        if (planet == null || space == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasUnit("crystellum_mech")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (realHits < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (planet.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (space.getUnitCount(UnitType.Fighter, player.getColor()) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnits(event, tile, game, player.getColor(), "1 fighter", true);
        int remainingHits = realHits - 1;

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " used _Refractum_ to redirect 1 produced hit against their ground force to a fighter in the active system.");

        List<Button> updatedButtons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();

        if (remainingHits > 0) {
            updatedButtons.add(Buttons.green(
                    factionChecker + "autoAssignGroundHits_" + planetName + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            updatedButtons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            updatedButtons.add(Buttons.gray(
                    factionChecker + "cancelGroundHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));

            String msg2 = player.getRepresentation() + " you may autoassign "
                    + StringHelper.pluralize(remainingHits, "hit") + ".";

            event.getMessage()
                    .editMessage(msg2)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(updatedButtons))
                    .queue();
        } else {
            event.getMessage()
                    .editMessage(player.getRepresentationNoPing() + " has no remaining ground combat hits to assign.")
                    .setComponents()
                    .queue();
        }
    }
}
