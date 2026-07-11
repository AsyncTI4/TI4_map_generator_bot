package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class IronUnitsHandler {

    private static final String EJECTION_PN_ID = "bepniron";
    private static final String EJECTION_USE_BUTTON_ID = "ironEjectionUse_";
    private static final String EJECTION_DECLINE_BUTTON_ID = "ironEjectionDecline";
    private static final int EJECTION_INFANTRY_COUNT = 1;
    private static final String IRON_FLAGSHIP_ID = "iron_flagship";
    private static final String IRON_FLAGSHIP_AFB_MECH_ID_SUFFIX = "_flagship_afb";
    private static final int IRON_FLAGSHIP_MECH_AFB_HITS_ON = 7;
    private static final int IRON_FLAGSHIP_MECH_AFB_DIE_COUNT = 2;

    public static void resolveRiptideDestroy(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        String replacementUnitList = getRiptideReplacementUnitList(unit);
        if (replacementUnitList.isEmpty()) {
            return;
        }

        AddUnitService.addUnits(event, unit.tile(), game, player.getColor(), replacementUnitList);
        sendRiptideMessage(event, player, unit);
    }

    static String getRiptideReplacementUnitList(RemovedUnit unit) {
        if (unit == null || unit.uh() == null || unit.getTotalRemoved() <= 0) {
            return "";
        }

        int totalRemoved = unit.getTotalRemoved();
        if (Constants.SPACE.equals(unit.uh().getName())) {
            return totalRemoved + " fighter";
        }
        return totalRemoved + " infantry " + unit.uh().getName();
    }

    private static void sendRiptideMessage(GenericInteractionCreateEvent event, Player player, RemovedUnit unit) {
        int totalRemoved = unit.getTotalRemoved();
        boolean inSpace = Constants.SPACE.equals(unit.uh().getName());
        String replacementName = inSpace ? "fighter" + (totalRemoved == 1 ? "" : "s") : "infantry";
        String placement = inSpace ? "the space area" : unit.uh().getName();
        String message = "> Added " + totalRemoved + " " + replacementName + " from reinforcements to " + placement
                + " due to _Riptide_.\n";

        if (event != null) {
            MessageHelper.sendMessageToEventChannel(event, message);
        } else if (player.getCorrectChannel() != null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }

    public static void resolveEjectionDestroy(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit, List<Player> killers) {
        if (!canUseEjection(game, player, unit, killers)) {
            return;
        }

        sendEjectionButtons(event, game, player, unit);
    }

    public static Map<UnitModel, Integer> getIronFlagshipAfbUnits(Player player, Tile tile) {
        Map<UnitModel, Integer> afbUnits = new HashMap<>();
        if (player == null || !ButtonHelper.doesPlayerHaveFSHere(IRON_FLAGSHIP_ID, player, tile)) {
            return afbUnits;
        }

        int mechCount = tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player);
        if (mechCount < 1) {
            return afbUnits;
        }

        UnitModel mechModel = player.getUnitByType(UnitType.Mech);
        if (mechModel == null) {
            return afbUnits;
        }

        afbUnits.put(getIronFlagshipAfbMechUnit(player, mechModel), mechCount);
        return afbUnits;
    }

    private static UnitModel getIronFlagshipAfbMechUnit(Player player, UnitModel mechModel) {
        UnitModel afbMechUnit = new UnitModel();
        afbMechUnit.setId(mechModel.getId() + IRON_FLAGSHIP_AFB_MECH_ID_SUFFIX);
        afbMechUnit.setBaseType(mechModel.getBaseType());
        afbMechUnit.setAsyncId(mechModel.getAsyncId());
        afbMechUnit.setName(mechModel.getName());
        afbMechUnit.setFaction(player.getFaction());
        afbMechUnit.setIsGroundForce(mechModel.getIsGroundForce());
        afbMechUnit.setAfbHitsOn(IRON_FLAGSHIP_MECH_AFB_HITS_ON);
        afbMechUnit.setAfbDieCount(IRON_FLAGSHIP_MECH_AFB_DIE_COUNT);
        return afbMechUnit;
    }

    @ButtonHandler(EJECTION_USE_BUTTON_ID)
    public static void resolveEjectionButton(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String data = buttonID.replace(EJECTION_USE_BUTTON_ID, "");
        String[] parts = data.split(";", 3);
        if (parts.length < 3) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve _Ejection_.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        int infantryToPlace = Integer.parseInt(parts[1]);
        String planetName = parts[2];
        if (tile == null
                || game.getPlanet(planetName) == null
                || !player.getPromissoryNotes().containsKey(EJECTION_PN_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Ejection_ is no longer available.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), infantryToPlace + " infantry " + planetName);
        returnEjectionPromissoryNote(game, player);
        sendEjectionMessage(event, game, player, infantryToPlace, planetName);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EJECTION_DECLINE_BUTTON_ID)
    public static void declineEjectionButton(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToEventChannel(
                event, player.getRepresentationNoPing() + " declined to use _Ejection_.");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean canUseEjection(Game game, Player player, RemovedUnit unit, List<Player> killers) {
        if (game == null
                || player == null
                || unit == null
                || unit.uh() == null
                || unit.getTotalRemoved() <= 0
                || Constants.SPACE.equals(unit.uh().getName())
                || !player.getPromissoryNotes().containsKey(EJECTION_PN_ID)
                || player.getPromissoryNotesOwned().contains(EJECTION_PN_ID)) {
            return false;
        }
        Player owner = game.getPNOwner(EJECTION_PN_ID);
        return owner != null && owner != player;
    }

    private static void sendEjectionButtons(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        String planetName = unit.uh().getName();
        String useId = player.factionButtonChecker() + EJECTION_USE_BUTTON_ID
                + unit.tile().getPosition() + ";" + EJECTION_INFANTRY_COUNT + ";" + planetName;
        List<Button> buttons = List.of(
                Buttons.green(useId, "Use Ejection"),
                Buttons.red(player.factionButtonChecker() + EJECTION_DECLINE_BUTTON_ID, "Decline"));
        String message = player.getRepresentationUnfogged() + ", you may use _Ejection_ to place "
                + EJECTION_INFANTRY_COUNT + " infantry on " + planetName;
        if (event != null && !game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
    }

    private static void returnEjectionPromissoryNote(Game game, Player player) {
        Player owner = game.getPNOwner(EJECTION_PN_ID);
        if (owner == null) {
            return;
        }
        player.removePromissoryNote(EJECTION_PN_ID);
        owner.setPromissoryNote(EJECTION_PN_ID);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
    }

    private static void sendEjectionMessage(
            GenericInteractionCreateEvent event, Game game, Player player, int totalRemoved, String planetName) {
        Player owner = game.getPNOwner(EJECTION_PN_ID);
        String ownerText = owner == null ? "the Iron Tide player" : owner.getRepresentationNoPing();
        String message = "> Added " + totalRemoved + " infantry to " + planetName + " due to _Ejection_.\n"
                + "> Returned _Ejection_ to " + ownerText + ".";

        if (event != null) {
            MessageHelper.sendMessageToEventChannel(event, message);
        } else if (player.getCorrectChannel() != null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }
}
