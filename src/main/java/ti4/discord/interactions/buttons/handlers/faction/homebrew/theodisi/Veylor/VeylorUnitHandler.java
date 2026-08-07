package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class VeylorUnitHandler {
    private static final String SELECT_AC = "selectEdictAcToDiscard_";

    public static void sendEdictDiscardButtons(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> discardButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> actionCard : player.getActionCards().entrySet()) {
            discardButtons.add(Buttons.blue(
                    player.factionButtonChecker() + SELECT_AC + actionCard.getValue(),
                    "(" + actionCard.getValue() + ") "
                            + Mapper.getActionCard(actionCard.getKey()).getName(),
                    CardEmojis.getACEmoji(game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please choose an action card to discard.",
                discardButtons);
    }

    @ButtonHandler(SELECT_AC)
    public static void resolveEdictAcDiscard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null) {
            return;
        }

        int handIndex;
        try {
            handIndex = Integer.parseInt(buttonID.substring(SELECT_AC.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.getActionCards().containsValue(handIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.discardAC(event, game, player, handIndex);

        ButtonHelper.deleteMessage(event);
    }

    // Vox Sentinels
    public static void checkVeylorMech(Game activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            String tokenToAddOrRemove = Constants.VOX_SENTINELS_PNG;
            if (player.ownsUnit("veylor_mech")) {
                for (Tile tile : activeMap.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder instanceof Planet planet) {
                            if (player.getPlanets().contains(planet.getName())) {
                                if (!oneMechCheck(planet.getName(), activeMap, player)
                                        && ((planet.getTokenList().contains(tokenToAddOrRemove)))) {
                                    planet.removeToken(tokenToAddOrRemove);
                                } else if (oneMechCheck(planet.getName(), activeMap, player)) {
                                    planet.addToken(tokenToAddOrRemove);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean oneMechCheck(String planetName, Game activeMap, Player player) {
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) return false;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;

        String colorID = Mapper.getColorID(player.getColor());
        if (unitHolder.getUnits() != null) {
            numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
        }
        return numMechs >= 1;
    }
}
