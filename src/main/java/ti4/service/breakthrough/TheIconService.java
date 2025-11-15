package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class TheIconService {

    public String theIcon() {
        return Mapper.getBreakthrough("bastionbt").getNameRepresentation();
    }

    private static boolean playerProducedShips(Game game, Player player) {
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        for (String unit : producedUnits.keySet()) {
            String unit2 = unit.split("_")[0];
            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
            UnitModel producedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
            if (producedUnit != null && producedUnit.getIsShip()) return true;
        }
        return false;
    }

    public List<String> getEligibleIconDestinations(Game game, Player player) {
        List<String> destinations = new ArrayList<>();
        for (Tile t : game.getTileMap().values()) {
            if (!CommandCounterHelper.hasCC(player, t)) continue;
            if (!t.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsGroundForce)) continue;
            // don't bother including a button for the active system production
            if (player.isActivePlayer() && game.getActiveSystem().equals(t.getPosition())) continue;

            boolean eligible = true;
            for (String color : t.getSpaceUnitHolder().getUnitColorsOnHolder()) {
                Player p2 = game.getPlayerFromColorOrFaction(color);
                if (player.is(p2) || player.isPlayerMemberOfAlliance(p2)) continue;
                eligible = false;
            }
            if (eligible) destinations.add(t.getPosition());
        }
        return destinations;
    }

    public void checkAndSendIconButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasReadyBreakthrough("bastionbt")) return;
        if (!playerProducedShips(game, player)) return;
        if (getEligibleIconDestinations(game, player).isEmpty()) return;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("useTheIcon", "Use The Icon", FactionEmojis.Bastion));
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Decline"));

        String msg = player.getRepresentationUnfogged() + " you can use your breakthrough, " + theIcon()
                + ", to place your produced ships in a different eligible system.";
        MessageHelper.sendMessageToChannel(event.getChannel(), msg, buttons);
    }

    @ButtonHandler("useTheIcon")
    private void doExhaustIcon(ButtonInteractionEvent event, Player player) {
        BreakthroughCommandHelper.exhaustBreakthrough(event, player);
        iconStepOne(event, player.getGame(), player);
    }

    public void iconStepOne(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!playerProducedShips(game, player)
                || getEligibleIconDestinations(game, player).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Something went wrong, I don't know how to resolve The Icon right now. Please resolve it manually.");
            return;
        }

        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        StringBuilder producedSummary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : producedUnits.entrySet()) {
            String[] data = entry.getKey().split("_");
            UnitType type = Units.findUnitType(data[0]);
            UnitModel model = player.getUnitByType(type);
            if (model.getIsShip()) {
                producedSummary
                        .append("\n> ")
                        .append(model.getUnitEmoji().toString().repeat(entry.getValue()));
            }
        }

        String msg = "You produced the following ships:" + producedSummary;
        msg += "\n\nChoose a system that contains your command token, your ground force, and no other player's ships.";
        msg += "\n> Warning: ALL of the listed ships will be moved to the destination system.";
        List<Button> destButtons = new ArrayList<>();
        for (String pos : getEligibleIconDestinations(game, player)) {
            Tile tile = game.getTileByPosition(pos);
            String id = "exhaustTheIcon_" + pos;
            String label = tile.getRepresentationForButtons(game, player);
            destButtons.add(Buttons.gray(id, label, tile.getTileEmoji(player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, destButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("exhaustTheIcon_")
    private void iconStepTwo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "exhaustTheIcon_" + RegexHelper.posRegex(game, "pos");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile dest = game.getTileByPosition(matcher.group("pos"));
            Map<String, Integer> producedUnits = player.getCurrentProducedUnits();

            StringBuilder movedSummary = new StringBuilder();
            for (Map.Entry<String, Integer> entry : producedUnits.entrySet()) {
                String[] data = entry.getKey().split("_");
                UnitType type = Units.findUnitType(data[0]);
                String pos = data[1];
                String uhName = data[2];

                UnitModel model = player.getUnitByType(type);
                if (model.getIsShip()) {
                    Tile src = game.getTileByPosition(pos);
                    int amt = entry.getValue();
                    UnitKey key = Units.getUnitKey(type, player.getColor());
                    var states = src.getUnitHolders().get(uhName).removeUnit(key, amt, UnitState.none);
                    dest.getSpaceUnitHolder().addUnitsWithStates(key, states);
                    movedSummary
                            .append("\n> ")
                            .append(model.getUnitEmoji().toString().repeat(entry.getValue()));
                }
            }

            String msg = player.getRepresentation() + " produced the following units in "
                    + dest.getRepresentationForButtons(game, player) + " using " + theIcon() + ":";
            msg += movedSummary;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
        });
    }
}
