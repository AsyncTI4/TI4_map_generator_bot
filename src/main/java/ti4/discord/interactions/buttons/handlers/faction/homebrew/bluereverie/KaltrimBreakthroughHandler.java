package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class KaltrimBreakthroughHandler {
    private static final String USE_ECHO_OPERATIVES = "useEchoOperatives_";
    private static final String DECLINE_ECHO_OPERATIVES = "declineEchoOperatives_";
    private static final String KEPT_RETURN = "echoOperativesKeptReturn_";
    private static final String DECLINED_RETURN = "echoOperativesDeclinedReturn_";
    private static final String REPAIR_ECHO_OPERATIVES = "repairEchoOperatives_";
    private static final String SELECT_ECHO_OPERATIVES_PROMISSORY = "selectEchoOperativesPromissory_";
    private static final String SELECT_ECHO_OPERATIVES_REPAIR = "selectEchoOperativesRepair_";
    private static final String ECHO_OPERATIVES_REPAIR_OWNER = "echoOperativesRepairOwner_";
    private static final String ECHO_OPERATIVES_PENDING_REPAIR_RETURN = "echoOperativesPendingRepairReturn_";

    public static List<Button> getEchoOperativesRepairButtons(Game game, Player kaltrim) {
        if (!kaltrim.hasUnlockedBreakthrough("kaltrimbt")
                || getReturnablePromissoryNotes(game, kaltrim).isEmpty()) {
            return List.of();
        }
        return List.of(Buttons.gray(
                kaltrim.factionButtonChecker() + REPAIR_ECHO_OPERATIVES,
                "Return Promissory Note to Repair Ship",
                FactionEmojis.kaltrim));
    }

    @ButtonHandler(REPAIR_ECHO_OPERATIVES)
    public static void offerEchoOperativesRepairTargets(ButtonInteractionEvent event, Game game, Player kaltrim) {
        if (!kaltrim.hasUnlockedBreakthrough("kaltrimbt")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> promissoryNoteButtons = getReturnablePromissoryNoteButtons(game, kaltrim);
        if (promissoryNoteButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have no promissory note in hand that can repair one of your damaged ships.");
            return;
        }
        String message = kaltrim.getRepresentationNoPing()
                + ", choose a promissory note from your hand to return for _Echo Operatives_.";
        String prefix = kaltrim.factionButtonChecker() + SELECT_ECHO_OPERATIVES_PROMISSORY;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(promissoryNoteButtons, prefix, 0));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    @ButtonHandler(SELECT_ECHO_OPERATIVES_PROMISSORY)
    public static void selectEchoOperativesPromissory(
            ButtonInteractionEvent event, Game game, Player kaltrim, String buttonID) {
        List<Button> promissoryNoteButtons = getReturnablePromissoryNoteButtons(game, kaltrim);
        String message = kaltrim.getRepresentationNoPing()
                + ", choose a promissory note from your hand to return for _Echo Operatives_.";
        String prefix = kaltrim.factionButtonChecker() + SELECT_ECHO_OPERATIVES_PROMISSORY;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), promissoryNoteButtons, message, prefix, buttonID)) {
            return;
        }

        String promissoryNote = buttonID.substring(SELECT_ECHO_OPERATIVES_PROMISSORY.length());
        Player owner = game.getPNOwner(promissoryNote);
        if (!kaltrim.hasUnlockedBreakthrough("kaltrimbt")
                || owner == null
                || owner == kaltrim
                || !kaltrim.hasPlayablePromissoryInHand(promissoryNote)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> repairButtons = getEchoOperativesRepairButtons(kaltrim, owner, game);
        if (repairButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        beginEchoOperativesRepairReturn(event, game, kaltrim, owner, promissoryNote);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ECHO_OPERATIVES_REPAIR)
    public static void repairEchoOperativesUnit(
            ButtonInteractionEvent event, Game game, Player kaltrim, String buttonID) {
        String[] values =
                buttonID.substring(SELECT_ECHO_OPERATIVES_REPAIR.length()).split("\\|", 4);
        Player owner = values.length > 0 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (owner == null
                || !kaltrim.hasUnlockedBreakthrough("kaltrimbt")
                || !owner.getFaction().equals(game.getStoredValue(echoOperativesRepairOwnerKey(kaltrim)))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> repairButtons = getEchoOperativesRepairButtons(kaltrim, owner, game);
        String message =
                kaltrim.getRepresentationNoPing() + ", choose 1 damaged ship to repair with _Echo Operatives_.";
        String prefix = kaltrim.factionButtonChecker() + SELECT_ECHO_OPERATIVES_REPAIR + owner.getFaction() + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), repairButtons, message, prefix, buttonID)) {
            return;
        }

        if (values.length != 4) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(values[1]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(values[2]);
        UnitType unitType = Units.findUnitType(values[3]);
        if (tile == null || unitHolder == null || unitType == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey unitKey = Units.getUnitKey(unitType, kaltrim.getColorID());
        UnitModel unitModel = kaltrim.getUnitFromUnitKey(unitKey);
        if (!kaltrim.unitBelongsToPlayer(unitKey)
                || unitModel == null
                || !unitModel.getIsShip()
                || unitHolder.getDamagedUnitCount(unitKey) < 1
                || !isAdjacentToOwnersUnits(game, kaltrim, tile, owner)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That unit is no longer damaged or available to repair.");
            return;
        }

        unitHolder.removeDamagedUnit(unitKey, 1);
        game.removeStoredValue(echoOperativesRepairOwnerKey(kaltrim));

        MessageHelper.sendMessageToChannel(
                kaltrim.getCorrectChannel(),
                kaltrim.getRepresentationNoPing()
                        + " repaired 1 "
                        + unitKey.humanReadableName()
                        + " with _Echo Operatives_.");

        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getEchoOperativesRepairButtons(Player kaltrim, Player owner, Game game) {
        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            if (!isAdjacentToOwnersUnits(game, kaltrim, tile, owner)) {
                continue;
            }
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : unitHolder.getUnitKeysForPlayer(kaltrim)) {
                    UnitModel unitModel = kaltrim.getUnitFromUnitKey(unitKey);
                    if (unitModel == null || !unitModel.getIsShip() || unitHolder.getDamagedUnitCount(unitKey) < 1) {
                        continue;
                    }

                    buttons.add(Buttons.green(
                            kaltrim.factionButtonChecker()
                                    + SELECT_ECHO_OPERATIVES_REPAIR
                                    + owner.getFaction()
                                    + "|"
                                    + tile.getPosition()
                                    + "|"
                                    + unitHolder.getName()
                                    + "|"
                                    + unitKey.unitType().getValue(),
                            "Repair "
                                    + unitKey.humanReadableName()
                                    + " in "
                                    + tile.getRepresentationForButtons(game, kaltrim)));
                }
            }
        }

        return buttons;
    }

    private static List<String> getReturnablePromissoryNotes(Game game, Player kaltrim) {
        return kaltrim.getPromissoryNotes().keySet().stream()
                .filter(kaltrim::hasPlayablePromissoryInHand)
                .filter(promissoryNote -> {
                    Player owner = game.getPNOwner(promissoryNote);
                    return owner != null
                            && owner != kaltrim
                            && !getEchoOperativesRepairButtons(kaltrim, owner, game)
                                    .isEmpty();
                })
                .toList();
    }

    private static List<Button> getReturnablePromissoryNoteButtons(Game game, Player kaltrim) {
        List<Button> buttons = new ArrayList<>();
        for (String promissoryNote : getReturnablePromissoryNotes(game, kaltrim)) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(promissoryNote);
            String name = model == null ? promissoryNote : model.getName();
            buttons.add(Buttons.green(
                    kaltrim.factionButtonChecker() + SELECT_ECHO_OPERATIVES_PROMISSORY + promissoryNote,
                    "Return " + name));
        }
        return buttons;
    }

    private static boolean isAdjacentToOwnersUnits(Game game, Player kaltrim, Tile tile, Player owner) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), kaltrim, false, true).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacentTile -> adjacentTile != null
                        && (!game.isFowMode()
                                || FoWHelper.getTilePositionsToShow(game, kaltrim)
                                        .contains(adjacentTile.getPosition()))
                        && FoWHelper.playerHasUnitsInSystem(owner, adjacentTile));
    }

    private static String echoOperativesRepairOwnerKey(Player kaltrim) {
        return ECHO_OPERATIVES_REPAIR_OWNER + kaltrim.getFaction();
    }

    public static boolean isEchoOperativesRepairReturn(Game game, Player kaltrim, String promissoryNote) {
        return promissoryNote.equals(game.getStoredValue(echoOperativesPendingRepairReturnKey(kaltrim)));
    }

    public static void cancelEchoOperativesRepairReturn(Game game, Player kaltrim) {
        game.removeStoredValue(echoOperativesPendingRepairReturnKey(kaltrim));
    }

    public static void completeEchoOperativesRepairReturn(
            ButtonInteractionEvent event, Game game, Player kaltrim, String promissoryNote) {
        if (!isEchoOperativesRepairReturn(game, kaltrim, promissoryNote)
                || !kaltrim.hasPlayablePromissoryInHand(promissoryNote)) {
            cancelEchoOperativesRepairReturn(game, kaltrim);
            return;
        }
        Player owner = game.getPNOwner(promissoryNote);
        if (owner == null || owner == kaltrim) {
            cancelEchoOperativesRepairReturn(game, kaltrim);
            return;
        }

        kaltrim.removePromissoryNote(promissoryNote);
        owner.setPromissoryNote(promissoryNote);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, kaltrim, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        cancelEchoOperativesRepairReturn(game, kaltrim);

        offerEchoOperativesRepair(game, kaltrim, owner);
    }

    private static void beginEchoOperativesRepairReturn(
            ButtonInteractionEvent event, Game game, Player kaltrim, Player owner, String promissoryNote) {
        game.setStoredValue(echoOperativesPendingRepairReturnKey(kaltrim), promissoryNote);
        if (XinPnHandler.offerStatecraftMentor(game, kaltrim, owner, promissoryNote)) {
            return;
        }
        continueEchoOperativesRepairReturn(event, game, kaltrim, owner, promissoryNote);
    }

    public static void continueEchoOperativesRepairReturn(
            ButtonInteractionEvent event, Game game, Player kaltrim, Player owner, String promissoryNote) {
        if (offerEchoOperatives(game, kaltrim, owner, promissoryNote)) {
            return;
        }
        completeEchoOperativesRepairReturn(event, game, kaltrim, promissoryNote);
    }

    public static void offerEchoOperativesRepair(Game game, Player kaltrim, Player owner) {
        List<Button> repairButtons = getEchoOperativesRepairButtons(kaltrim, owner, game);
        if (repairButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    kaltrim.getCorrectChannel(),
                    kaltrim.getRepresentationNoPing()
                            + " has no eligible damaged ship to repair with _Echo Operatives_.");
            return;
        }
        game.setStoredValue(echoOperativesRepairOwnerKey(kaltrim), owner.getFaction());
        String message =
                kaltrim.getRepresentationNoPing() + ", choose 1 damaged ship to repair with _Echo Operatives_.";
        String prefix = kaltrim.factionButtonChecker() + SELECT_ECHO_OPERATIVES_REPAIR + owner.getFaction() + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                kaltrim.getCorrectChannel(), message, NewStuffHelper.buttonPagination(repairButtons, prefix, 0));
    }

    private static String echoOperativesPendingRepairReturnKey(Player kaltrim) {
        return ECHO_OPERATIVES_PENDING_REPAIR_RETURN + kaltrim.getFaction();
    }

    public static boolean offerEchoOperatives(Game game, Player holder, Player owner, String promissoryNote) {
        if (owner == null
                || owner == holder
                || !holder.hasReadyBreakthrough("kaltrimbt")
                || holder.getStrategicCC() < 1
                || consumeDeclinedEchoOperativesReturn(game, holder, promissoryNote)) {
            return false;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        holder.factionButtonChecker() + USE_ECHO_OPERATIVES + promissoryNote,
                        "Use Echo Operatives",
                        FactionEmojis.kaltrim),
                Buttons.red(
                        holder.factionButtonChecker() + DECLINE_ECHO_OPERATIVES + promissoryNote,
                        "Return Promissory Note"));

        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationNoPing()
                        + ", you may exhaust _Echo Operatives_ and spend 1 strategy token instead of returning this promissory note.",
                buttons);
        return true;
    }

    @ButtonHandler(USE_ECHO_OPERATIVES)
    public static void useEchoOperatives(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        String promissoryNote = buttonID.substring(USE_ECHO_OPERATIVES.length());

        if (!holder.hasReadyBreakthrough("kaltrimbt")
                || holder.getStrategicCC() < 1
                || !holder.getPromissoryNotes().containsKey(promissoryNote)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        holder.setBreakthroughExhausted("kaltrimbt", true);
        holder.setStrategicCC(holder.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(holder, game, event, "used _Echo Operatives_");
        if (isEchoOperativesRepairReturn(game, holder, promissoryNote)) {
            Player owner = game.getPNOwner(promissoryNote);
            cancelEchoOperativesRepairReturn(game, holder);
            ButtonHelper.deleteMessage(event);
            if (owner != null) {
                offerEchoOperativesRepair(game, holder, owner);
            }
            return;
        }
        game.setStoredValue(keptReturnKey(holder), promissoryNote);

        ButtonHelper.deleteMessage(event);
        PromissoryNoteHelper.resolvePNPlay(promissoryNote, holder, game, event);

        MessageHelper.sendMessageToChannel(
                holder.getCorrectChannel(),
                holder.getRepresentationNoPing()
                        + " exhausted _Echo Operatives_ and spent 1 strategy token to keep _"
                        + promissoryNote + "_.");
    }

    @ButtonHandler(DECLINE_ECHO_OPERATIVES)
    public static void declineEchoOperatives(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        String promissoryNote = buttonID.substring(DECLINE_ECHO_OPERATIVES.length());

        if (!holder.getPromissoryNotes().containsKey(promissoryNote)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (isEchoOperativesRepairReturn(game, holder, promissoryNote)) {
            ButtonHelper.deleteMessage(event);
            completeEchoOperativesRepairReturn(event, game, holder, promissoryNote);
            return;
        }

        game.setStoredValue(declinedReturnKey(holder), promissoryNote);
        ButtonHelper.deleteMessage(event);
        PromissoryNoteHelper.resolvePNPlay(promissoryNote, holder, game, event);
    }

    public static boolean consumeEchoOperativesReturn(Game game, Player holder, String promissoryNote) {
        String key = keptReturnKey(holder);
        if (!promissoryNote.equals(game.getStoredValue(key))) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static boolean consumeDeclinedEchoOperativesReturn(Game game, Player holder, String promissoryNote) {
        String key = declinedReturnKey(holder);
        if (!promissoryNote.equals(game.getStoredValue(key))) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static String keptReturnKey(Player player) {
        return KEPT_RETURN + player.getFaction();
    }

    private static String declinedReturnKey(Player player) {
        return DECLINED_RETURN + player.getFaction();
    }
}
