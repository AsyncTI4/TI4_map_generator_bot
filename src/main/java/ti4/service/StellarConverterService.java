package ti4.service;

import java.util.concurrent.ThreadLocalRandom;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

@UtilityClass
public class StellarConverterService {

    public static void resolveStellar(Game game, ButtonInteractionEvent event, String buttonID) {
        secondHalfOfStellar(game, buttonID.split("_")[1], event);
        ButtonHelper.deleteMessage(event);
    }

    public static void secondHalfOfStellar(Game game, String planetName, GenericInteractionCreateEvent event) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }
        UnitHolder unitHolder = tile.getUnitHolderFromPlanet(planetName);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }

        String message1 = (ThreadLocalRandom.current().nextInt(20) == 0 ? "# _Hey, Stellar!_" : "There is a great disturbance in the Force, as if millions of voices suddenly cried out in terror and were suddenly silenced.");
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, "Moments before disaster in game " + game.getName());
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message1);

        for (Player p2 : game.getRealPlayers()) {
            if (p2.getPlanets().contains(planetName)) {
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + " we regret to inform you but " + Mapper.getPlanet(planetName).getName() + " has been Stellar Converted.");
                int amountToKill;
                amountToKill = unitHolder.getUnitCount(Units.UnitType.Infantry, p2.getColor());
                if (p2.hasInf2Tech()) {
                    ButtonHelper.resolveInfantryDeath(game, p2, amountToKill);
                    boolean cabalMech = unitHolder.getUnitCount(Units.UnitType.Mech,
                        p2.getColor()) > 0
                        && p2.hasUnit("cabal_mech")
                        && !ButtonHelper.isLawInPlay(game, "articles_war");
                    if (p2.hasAbility("amalgamation")
                        && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, amountToKill, "infantry", event);
                    }
                }
            }
        }
        game.removePlanet(unitHolder);
        unitHolder.removeAllTokens();
        unitHolder.addToken(Constants.WORLD_DESTROYED_PNG);

        StringBuilder message2 = new StringBuilder();
        message2.append(Mapper.getPlanet(planetName).getName());
        message2.append(" has been stellar converted");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2.toString());

        message2.append(" by ");
        message2.append(game.getPlayer(event.getUser().getId()).getRepresentation());
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 0, message2.toString());
    }
}
