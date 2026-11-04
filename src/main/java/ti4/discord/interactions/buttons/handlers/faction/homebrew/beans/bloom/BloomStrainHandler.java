package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
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
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BloomStrainHandler {
    private static final String CARNIVOROUS_PLACE = "carnivorousPlace_";

    public static void resolveRhizomaticActive(Player player, Game game) {
        for (Player bloomPlayer : game.getRealPlayers()) {
            boolean coexistingWithPlayer =
                    game.getPlanetsPlayerIsCoexistingOn(bloomPlayer).stream().anyMatch(player.getPlanets()::contains);
            if (player != bloomPlayer && bloomPlayer.hasAbility("rhizomatic_active") && coexistingWithPlayer) {
                String replenishingPlayer = game.isFowMode() ? "another player" : player.getRepresentationNoPing();
                MessageHelper.sendMessageToChannelWithButtons(
                        bloomPlayer.getCardsInfoThread(),
                        bloomPlayer.getRepresentationNoPing()
                                + ", you may gain 1 commodity or convert 1 commodity to a trade good using _Rhizomatic Active_ because "
                                + replenishingPlayer
                                + " replenished their commodities while you are coexisting on one of their planets.",
                        ButtonHelperFactionSpecific.gainOrConvertCommButtons(bloomPlayer, true));
            }
        }
    }

    public static void addCarnivorousActiveButton(
            List<Button> buttons, Game game, Player player, Planet planet, Tile tile) {
        if (player.hasAbility("carnivorous_active") && planet != null) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + CARNIVOROUS_PLACE + tile.getPosition() + "|" + planet.getName(),
                    "Use Carnivorous Active",
                    FactionEmojis.bloom));
        }
    }

    @ButtonHandler(CARNIVOROUS_PLACE)
    public static void placeCarnivorousActiveInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CARNIVOROUS_PLACE.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getUnitHolders().get(payload[1]) instanceof Planet candidate ? candidate : null;
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (!player.hasAbility("carnivorous_active")
                || planet == null
                || combat == null
                || !tile.getPosition().equals(combat.tilePosition())
                || !planet.getName().equals(combat.unitHolderName())) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with _Carnivorous Active_.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
