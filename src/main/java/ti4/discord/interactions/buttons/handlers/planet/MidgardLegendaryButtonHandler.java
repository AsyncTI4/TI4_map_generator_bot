package ti4.discord.interactions.buttons.handlers.planet;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class MidgardLegendaryButtonHandler {

    @ButtonHandler("musterManheim_")
    public static void musterManheim(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring("musterManheim_".length());
        if (!player.hasPlanet("midgard")
                || player.getExhaustedPlanetsAbilities().contains("midgard")
                || game.getActiveSystem().isEmpty()
                || !(game.getTileFromPlanet(planet).getUnitHolderFromPlanet(planet) instanceof Planet)) {
            return;
        }
        player.exhaustPlanetAbility("midgard");
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "2 infantry " + planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Muster Manheim_ to place 2 infantry on "
                        + game.getUnitHolderFromPlanet(planet).getRepresentation(game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerMusterManheim(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (!combat) return;
        StartCombatService.CurrentCombat combatContext = StartCombatService.getCurrentCombat(game);
        if (combatContext == null
                || combatContext.tilePosition() == null
                || combatContext.unitHolderName() == null
                || "space".equals(combatContext.unitHolderName())) return;
        Set<Player> eligiblePlayers = destroyedUnits.stream()
                .filter(unit -> unit.uh() instanceof Planet)
                .filter(unit -> combatContext.tilePosition().equals(unit.tile().getPosition()))
                .filter(unit -> combatContext.unitHolderName().equals(unit.uh().getName()))
                .map(unit -> game.getPlayerFromColorOrFaction(unit.unitKey().colorID()))
                .filter(player -> player != null
                        && player.hasPlanet("midgard")
                        && !player.getExhaustedPlanetsAbilities().contains("midgard"))
                .collect(Collectors.toSet());
        for (Player player : eligiblePlayers) {
            Button button = Buttons.green(
                    player.factionButtonChecker() + "musterManheim_" + combatContext.unitHolderName(),
                    "Use Muster Manheim",
                    PlanetEmojis.getPlanetEmojiOrNull("midgard"));
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", one of your units was destroyed during ground combat. You may exhaust _Muster Manheim_ to place 2 infantry on "
                            + game.getUnitHolderFromPlanet(combatContext.unitHolderName())
                                    .getRepresentation(game)
                            + ".",
                    button);
        }
    }
}
