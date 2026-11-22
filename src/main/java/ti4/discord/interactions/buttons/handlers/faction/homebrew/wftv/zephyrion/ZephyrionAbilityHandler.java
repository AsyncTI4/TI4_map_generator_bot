package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.zephyrion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class ZephyrionAbilityHandler {

    private static final int MERCENARY_MAX_SHIPS = 3;
    private static final int MERCENARY_HITS_ON = 5;
    private static final String MERCENARY_SHIPS = "zephyrionMercenaryShips_";
    private static final String MERCENARY_KEY = "mercenaryDefenseContract_";
    private static final String MERCENARY_UNDECLARED = "0";

    public static void activateMercenaryDefenseContract(Game game, Player player) {
        game.setStoredValue(MERCENARY_KEY + player.getFaction(), MERCENARY_UNDECLARED);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", up to 3 of your non-fighter ships gain SPACE CANNON 5 during"
                        + " this action via _Mercenary Defense Contract_.");
    }

    public static void offerMercenaryShipCount(Game game, List<Player> shooters) {
        for (Player shooter : shooters) {
            if (!MERCENARY_UNDECLARED.equals(game.getStoredValue(MERCENARY_KEY + shooter.getFaction()))) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            for (int ships = 1; ships <= MERCENARY_MAX_SHIPS; ships++) {
                buttons.add(Buttons.gray(
                        shooter.factionButtonChecker() + MERCENARY_SHIPS + ships,
                        ships + " Ship" + (ships == 1 ? "" : "s")));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    shooter.getCorrectChannel(),
                    shooter.getRepresentationUnfogged() + ", tell the bot how many non-fighter ships in the active"
                            + " system gained SPACE CANNON via _Mercenary Defense Contract_.",
                    buttons);
        }
    }

    @ButtonHandler(MERCENARY_SHIPS)
    public static void declareMercenaryShips(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String ships = buttonID.replace(MERCENARY_SHIPS, "");
        game.setStoredValue(MERCENARY_KEY + player.getFaction(), ships);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " has " + ships + " ship" + ("1".equals(ships) ? "" : "s")
                        + " with SPACE CANNON 5 in the active system via _Mercenary Defense Contract_. They are"
                        + " included when SPACE CANNON is rolled.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasMercenaryDefenseContractCoverage(Game game, Player player, Tile tile) {
        return isMercenaryDefenseContractActive(game, player)
                && tile != null
                && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                && ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile) > 0;
    }

    public static void addMercenaryDefenseContractCannons(
            Game game, Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        int ships = declaredMercenaryShips(game, player, tile);
        if (ships == 0) {
            return;
        }
        UnitModel cannon = new UnitModel();
        cannon.setSpaceCannonHitsOn(MERCENARY_HITS_ON);
        cannon.setSpaceCannonDieCount(1);
        cannon.setName("Mercenary Defense Contract");
        cannon.setAsyncId("mercenarypds");
        cannon.setId("mercenarypds");
        cannon.setBaseType("pds");
        cannon.setFaction(player.getFaction());
        units.put(new ImmutablePair<>(cannon, tile.getSpaceUnitHolder()), ships);
    }

    public static void clearMercenaryDefenseContract(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(MERCENARY_KEY + player.getFaction());
        }
    }

    private static boolean isMercenaryDefenseContractActive(Game game, Player player) {
        return !game.getStoredValue(MERCENARY_KEY + player.getFaction()).isEmpty();
    }

    private static int declaredMercenaryShips(Game game, Player player, Tile tile) {
        String declared = game.getStoredValue(MERCENARY_KEY + player.getFaction());
        if (!StringUtils.isNumeric(declared)) {
            return 0;
        }
        return Math.min(
                Integer.parseInt(declared), ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
    }
}
