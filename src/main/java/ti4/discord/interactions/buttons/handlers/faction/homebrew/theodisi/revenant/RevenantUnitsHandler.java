package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.revenant;

import java.util.ArrayList;
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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class RevenantUnitsHandler {
    private static final String GRAVE_ROBBERS = "revenant_mech";
    private static final String DAMAGE_GRAVE_ROBBERS = "damageRevenantMech_";
    private static final String PAGE_GRAVE_ROBBERS = "pageRevenantMech_";

    public static void doRevenantMechCheck(Game game, Player player) {
        if (!player.hasUnit(GRAVE_ROBBERS)) {
            return;
        }

        List<Button> buttons = getRevenantMechDamageButtons(game, player);
        if (buttons.isEmpty()) {
            return;
        }
        String message = player.getRepresentation()
                + ", you may damage 1 Grave Robber to place 1 infantry on its planet after exhausting an agent.";
        List<Button> extraButtons = List.of(Buttons.DECLINE_DELETE_BUTTONS);
        List<Button> displayedButtons = buttons.size() + extraButtons.size() <= 25
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + PAGE_GRAVE_ROBBERS, 25, 0, false);
        if (displayedButtons.size() == buttons.size()) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayedButtons);
    }

    @ButtonHandler(PAGE_GRAVE_ROBBERS)
    public static void pageRevenantMechDamage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                getRevenantMechDamageButtons(game, player),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                player.getRepresentation()
                        + ", you may damage 1 Grave Robber to place 1 infantry on its planet after exhausting an agent.",
                player.factionButtonChecker() + PAGE_GRAVE_ROBBERS,
                buttonID);
    }

    @ButtonHandler(DAMAGE_GRAVE_ROBBERS)
    public static void damageRevenantMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(DAMAGE_GRAVE_ROBBERS.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        if (!player.hasUnit(GRAVE_ROBBERS)
                || planet == null
                || planet.getUnitCount(mech) <= planet.getDamagedUnitCount(mech)) {
            return;
        }
        planet.addDamagedUnit(mech, 1);
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 inf " + planetName);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " damaged 1 Grave Robber and placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRevenantMechDamageButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(mech) <= planet.getDamagedUnitCount(mech)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + DAMAGE_GRAVE_ROBBERS + planet.getName(),
                        "Damage Grave Robber on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }
}
