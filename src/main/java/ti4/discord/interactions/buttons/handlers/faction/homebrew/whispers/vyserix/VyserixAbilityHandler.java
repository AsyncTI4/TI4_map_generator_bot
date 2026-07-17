package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.vyserix;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VyserixAbilityHandler {

    public static void onGainPlanetWithTechSpec(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, UnitHolder unitHolder) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed 1 PDS on "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game)
                        + " due to the Veiled Ember Forge ability. This is optional but was done automatically.");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "pds " + unitHolder.getName());
    }
}
