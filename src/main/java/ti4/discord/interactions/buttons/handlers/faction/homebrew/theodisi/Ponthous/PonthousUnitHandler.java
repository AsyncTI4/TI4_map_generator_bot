package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;
import ti4.service.unit.UnitModelValueInjectionService;
import ti4.service.unit.UnitModelValueInjectionService.BooleanValueInjection;
import ti4.service.unit.UnitModelValueInjectionService.UnitValueInjection;

@UtilityClass
public class PonthousUnitHandler {

    private static final String PONTHOUS_FLAGSHIP = "ponthous_flagship";
    private static final String OLD_GLORY_SUSTAIN = "ponthousOldGlorySustain_";
    private static final String USE_OLD_GLORY_SUSTAIN = "useOldGlorySustain_";
    private static final String DRAGOONS = "ponthous_mech";
    private static final String USE_DRAGOONS = "useDragoonsPlaceInf_";

    // Old Glory
    public static Button getOldGlorySustainButton(Player player, Tile tile) {
        if (!canOfferOldGlorySustain(player, tile)) return null;
        return Buttons.gray(
                player.factionButtonChecker() + USE_OLD_GLORY_SUSTAIN + tile.getPosition(),
                "Use Old Glory Ability",
                FactionEmojis.ponthous);
    }

    @ButtonHandler(USE_OLD_GLORY_SUSTAIN)
    public static void useOldGlorySustain(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_OLD_GLORY_SUSTAIN.length()));
        if (tile == null || !canResolveOldGlorySustain(player, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "The Old Glory (the Ponthous flagship) cannot use this ability right now.");
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        UnitKey fighter = Units.getUnitKey(UnitType.Fighter, player.getColorID());
        int fightersToGrantSustain = Math.min(2, space.getUnitCount(fighter) - space.getDamagedUnitCount(fighter));
        if (fightersToGrantSustain < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no undamaged fighters to gain sustain damage.");
            return;
        }

        space.addDamagedUnit(flagship, 1);
        game.setStoredValue(getOldGlorySustainKey(player, tile), Integer.toString(fightersToGrantSustain));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " damaged the Old Glory (the Ponthous flagship). "
                        + fightersToGrantSustain
                        + " fighter"
                        + (fightersToGrantSustain == 1 ? " gains" : "s gain")
                        + " SUSTAIN DAMAGE until this combat ends.");
    }

    public static UnitModel injectTemporaryFighterSustain(Game game, Player player, Tile tile, UnitModel unit) {
        if (unit.getUnitType() != UnitType.Fighter || getRemainingFighterSustains(game, player, tile) < 1) {
            return unit;
        }
        return UnitModelValueInjectionService.injectTemporaryValues(
                unit, UnitValueInjection.of(BooleanValueInjection.create().sustainDamage(true)));
    }

    public static int getTemporaryFighterSustainRemaining(Game game, Player player, Tile tile) {
        return getRemainingFighterSustains(game, player, tile);
    }

    public static void consumeTemporaryFighterSustain(Game game, Player player, Tile tile, int amount) {
        int remaining = getRemainingFighterSustains(game, player, tile) - amount;
        String key = getOldGlorySustainKey(player, tile);
        if (remaining > 0) {
            game.setStoredValue(key, Integer.toString(remaining));
        } else {
            game.removeStoredValue(key);
        }
    }

    public static void clearOldGlorySustain(Game game) {
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(OLD_GLORY_SUSTAIN))
                .toList()
                .forEach(game::removeStoredValue);
    }

    private static boolean canOfferOldGlorySustain(Player player, Tile tile) {
        if (!player.ownsUnit(PONTHOUS_FLAGSHIP)) return false;
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        UnitKey fighter = Units.getUnitKey(UnitType.Fighter, player.getColorID());
        return space.getUnitCount(flagship) > 0 && space.getUnitCount(fighter) > 0;
    }

    private static boolean canResolveOldGlorySustain(Player player, Tile tile) {
        if (!canOfferOldGlorySustain(player, tile)) return false;
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        UnitKey fighter = Units.getUnitKey(UnitType.Fighter, player.getColorID());
        return space.getUnitCount(flagship) > space.getDamagedUnitCount(flagship)
                && space.getUnitCount(fighter) > space.getDamagedUnitCount(fighter);
    }

    private static int getRemainingFighterSustains(Game game, Player player, Tile tile) {
        return NumberUtils.toInt(game.getStoredValue(getOldGlorySustainKey(player, tile)));
    }

    private static String getOldGlorySustainKey(Player player, Tile tile) {
        return OLD_GLORY_SUSTAIN + player.getFaction() + "_" + tile.getPosition();
    }

    // Dragoons
    public static void offerDragoonsButton(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        if (player == null
                || !player.ownsUnit(DRAGOONS)
                || !(unit.uh() instanceof Planet planet)
                || !"groundcombat".equalsIgnoreCase(game.getStoredValue(player.getFaction() + "latestAssignHits"))) {
            return;
        }

        String planetName = planet.getName();

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + USE_DRAGOONS + planetName,
                        "Pay 1R to Place 2 Inf",
                        FactionEmojis.ponthous),
                Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may pay 1 resource to place 2 infantry on the planet that contained the destroyed mech.",
                buttons);
    }

    @ButtonHandler(USE_DRAGOONS)
    public static void resolveDragoonsInfPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.ownsUnit(DRAGOONS)) {
            return;
        }

        if (player.getReadiedPlanets().isEmpty() && player.getTg() == 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You do not have enough resources to pay for _Dragoons_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = buttonID.replace(USE_DRAGOONS, "");
        Tile tile = game.getTileFromPlanet(planetName);
        if (planetName == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not locate the mech's planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planetName);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose how to pay 1 resource to place 2 infantry on this planet.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }
}
