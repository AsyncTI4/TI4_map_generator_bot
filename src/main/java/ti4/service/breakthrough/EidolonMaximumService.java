package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.regex.RegexService;

@UtilityClass
public class EidolonMaximumService {

    public static final Button refreshButton = Buttons.blue("checkEidolonMaximum", "Fuse Eidolon Maximum");

    private String eidolonRep(boolean includeCardText) {
        return Mapper.getBreakthrough("naazbt").getRepresentation(includeCardText);
    }

    public boolean playerHasIdleMax(Player player) {
        return player.hasUnlockedBreakthrough("naazbt") && !player.hasActiveBreakthrough("naazbt");
    }

    public boolean playerHasActiveMax(Player player) {
        return player.hasActiveBreakthrough("naazbt");
    }

    public void sendEidolonMaximumFlipButtons(Game game, Player player) {
        checkIfAbleToFlip(game, player);
    }

    @ButtonHandler("checkEidolonMaximum")
    private void checkIfAbleToFlip(Game game, Player player) {
        if (!playerHasIdleMax(player)) return;

        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            int count = 0;
            List<UnitHolder> availableSpots = new ArrayList<>();
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                int x = uh.getUnitCount(UnitType.Mech, player);
                if (x > 0) availableSpots.add(uh);
                count += x;
            }
            if (count == 4) {
                sendFlipButtonsToCardsInfo(player, tile, availableSpots);
                return;
            }
        }
        String msg = player.getRepresentation(true, false)
                + " You do not have 4 mechs in the same system, so you cannot use " + eidolonRep(false) + " right now.";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
    }

    public void sendFlipButtonsToCardsInfo(Player player, Tile tile, List<UnitHolder> unitHolders) {
        String tileRep = tile.getRepresentationForButtons(player.getGame(), player);
        String msg = player.getRepresentation(true, false) + " you have 4 mechs in " + tileRep
                + ". You can remove 3 of them to activate " + eidolonRep(true);
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uh : unitHolders) {
            String id = "activateEidolonMaximum_" + tile.getPosition() + "_" + uh.getName();
            String label = "Create in space";
            if (!uh.getName().equals("space")) label = "Create on " + Helper.getPlanetName(uh.getName());
            buttons.add(Buttons.green(id, label));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("activateEidolonMaximum_")
    private void flipEidolonMaximum(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "activateEidolonMaximum_" + RegexHelper.posRegex(game) + "_"
                + RegexHelper.unitHolderRegex(game, "unitholder");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            // fetch data
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            String keepUnitHolder = matcher.group("unitholder");

            // validate that there are still 4 mechs here
            int mechs = tile.getUnitHolders().values().stream()
                    .collect(Collectors.summingInt(h -> h.getUnitCount(UnitType.Mech, player)));
            if (mechs < 4) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "There are no longer 4 mechs at this location.");
                ButtonHelper.deleteMessage(event);
            }

            // Remove all other mechs
            UnitKey mech = Units.getUnitKey(UnitType.Mech, player.getColorID());
            for (UnitHolder uh : tile.getUnitHolders().values()) {
                if (uh.getName().equals(keepUnitHolder)) {
                    uh.removeUnit(mech, uh.getUnitCount(mech) - 1);
                } else {
                    uh.removeUnit(mech, uh.getUnitCount(mech));
                }
            }
            BreakthroughCommandHelper.activateBreakthrough(event, player);
        });
        ButtonHelper.deleteMessage(event);
    }

    public void unflipEidolonMaximum(GenericInteractionCreateEvent event, Game game, Player player) {
        if (playerHasActiveMax(player)) BreakthroughCommandHelper.deactivateBreakthrough(player);
    }
}
