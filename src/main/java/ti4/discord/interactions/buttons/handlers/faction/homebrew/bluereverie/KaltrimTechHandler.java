package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class KaltrimTechHandler {
    private static final String KALDUR_ARREST_FIELD = "dskaltr";
    private static final String PAY_ARREST_FIELD = "payKaldurArrestField_";
    private static final String DECLINE_ARREST_FIELD = "declineKaldurArrestField_";
    private static final String FINISH_ARREST_FIELD_PAYMENT = "finishKaldurArrestFieldPayment_";
    private static final String CAPTURE_ARREST_FIELD_INFANTRY = "captureKaldurArrestFieldInfantry_";
    private static final String CAPTURE_ARREST_FIELD_PAGE = "captureKaldurArrestFieldPage_";
    private static final String SPEND_CAPTURED_INFANTRY = "spendKaldurCapturedInfantry_";
    private static final int CAPTURE_PAGE_SIZE = 22;

    public static void offerKaldurArrestField(Player activator, Game game, Tile activatedSystem) {
        for (Player owner : game.getRealPlayers()) {
            if (owner == activator || !owner.hasTech(KALDUR_ARREST_FIELD) || !hasStructure(owner, activatedSystem)) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.red(
                            activator.factionButtonChecker() + PAY_ARREST_FIELD + owner.getFaction(),
                            "Pay 1 Influence",
                            FactionEmojis.kaltrim),
                    Buttons.gray(
                            activator.factionButtonChecker() + DECLINE_ARREST_FIELD + owner.getFaction(), "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    activator.getCorrectChannel(),
                    activator.getRepresentation() + ", you activated a system containing one of "
                            + owner.getRepresentationNoPing()
                            + "'s structures. Spend 1 influence or they may capture 2 of your infantry on the game board using _Kaldur Arrest Field_.",
                    buttons);
        }
    }

    @ButtonHandler(PAY_ARREST_FIELD)
    public static void payKaldurArrestField(
            ButtonInteractionEvent event, Game game, Player activator, String buttonID) {
        Player owner = game.getPlayerFromColorOrFaction(buttonID.substring(PAY_ARREST_FIELD.length()));
        if (owner == null || !owner.hasTech(KALDUR_ARREST_FIELD)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, activator, "inf"));
        buttons.add(Buttons.red(
                activator.factionButtonChecker() + FINISH_ARREST_FIELD_PAYMENT + owner.getFaction(),
                "Done Paying Influence"));
        MessageHelper.sendMessageToChannelWithButtons(
                activator.getCorrectChannel(),
                activator.getRepresentation() + ", spend 1 influence for _Kaldur Arrest Field_.\n"
                        + Helper.buildSpentThingsMessage(activator, game, "inf"),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_ARREST_FIELD_PAYMENT)
    public static void finishKaldurArrestFieldPayment(
            ButtonInteractionEvent event, Game game, Player activator, String buttonID) {
        Player owner = game.getPlayerFromColorOrFaction(buttonID.substring(FINISH_ARREST_FIELD_PAYMENT.length()));
        if (owner != null) {
            MessageHelper.sendMessageToChannel(
                    activator.getCorrectChannel(),
                    activator.getRepresentationNoPing() + " paid 1 influence for " + owner.getRepresentationNoPing()
                            + "'s _Kaldur Arrest Field_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_ARREST_FIELD)
    public static void declineKaldurArrestField(
            ButtonInteractionEvent event, Game game, Player activator, String buttonID) {
        Player owner = game.getPlayerFromColorOrFaction(buttonID.substring(DECLINE_ARREST_FIELD.length()));
        if (owner != null && owner.hasTech(KALDUR_ARREST_FIELD)) {
            offerKaldurInfantryCapture(game, owner, activator, 2);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CAPTURE_ARREST_FIELD_INFANTRY)
    public static void captureKaldurArrestFieldInfantry(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] values =
                buttonID.substring(CAPTURE_ARREST_FIELD_INFANTRY.length()).split("\\|", 4);
        if (values.length != 4) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(values[0]);
        int remaining;
        try {
            remaining = Integer.parseInt(values[1]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileByPosition(values[2]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[3]);
        if (target == null
                || !owner.hasTech(KALDUR_ARREST_FIELD)
                || holder == null
                || game.isFowMode()
                        && !FoWHelper.getTilePositionsToShow(game, owner).contains(tile.getPosition())
                || holder.getUnitCount(UnitType.Infantry, target) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnit(event, tile, game, target, holder, UnitType.Infantry, 1);
        AddUnitService.addUnits(event, owner.getNomboxTile(), game, owner.getColor(), "1 infantry");
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + " captured 1 infantry belonging to "
                        + target.getRepresentationNoPing() + " with _Kaldur Arrest Field_.");
        if (remaining > 1) {
            offerKaldurInfantryCapture(game, owner, target, remaining - 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CAPTURE_ARREST_FIELD_PAGE)
    public static void changeKaldurArrestFieldCapturePage(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] values = buttonID.substring(CAPTURE_ARREST_FIELD_PAGE.length()).split("\\|", 3);
        if (values.length != 3) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(values[0]);
        int remaining;
        int page;
        try {
            remaining = Integer.parseInt(values[1]);
            page = Integer.parseInt(values[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (target == null || !owner.hasTech(KALDUR_ARREST_FIELD)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<CaptureLocation> locations = getKaldurCaptureLocations(game, owner, target);
        MessageHelper.editMessageWithButtons(
                event,
                kaldurCaptureMessage(owner, target, remaining),
                getKaldurCapturePage(owner, target, locations, remaining, page));
    }

    public static boolean canSpendCapturedInfantry(Player player) {
        return player.hasTech(KALDUR_ARREST_FIELD) && player.getNombox().getUnitCount(UnitType.Infantry, player) > 0;
    }

    public static Button getSpendCapturedInfantryButton(Player player, String whatIsItFor) {
        return Buttons.red(
                player.factionButtonChecker() + SPEND_CAPTURED_INFANTRY + whatIsItFor,
                "Spend 1 Infantry as TG",
                UnitEmojis.infantry);
    }

    @ButtonHandler(SPEND_CAPTURED_INFANTRY)
    public static void spendCapturedInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String whatIsItFor = buttonID.substring(SPEND_CAPTURED_INFANTRY.length());
        if (!canSpendCapturedInfantry(player)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 infantry");
        int spentInfantry = getCapturedInfantrySpent(player);
        if (spentInfantry > 0) {
            player.removeSpentThing("kaldurCapturedInfantry_" + spentInfantry);
        }
        player.addSpentThing("kaldurCapturedInfantry_" + (spentInfantry + 1));
        game.setStoredValue("resetSpend", "yes");
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor));
        event.getMessage().getComponentTree().findAll(Button.class).stream()
                .filter(KaltrimTechHandler::isPaymentCompletionButton)
                .filter(button -> !Helper.doesListContainButtonID(buttons, button.getCustomId()))
                .forEach(buttons::add);
        MessageHelper.editMessageWithButtons(event, Helper.buildSpentThingsMessage(player, game, whatIsItFor), buttons);
    }

    private static boolean isPaymentCompletionButton(Button button) {
        String buttonId = button.getCustomId();
        return buttonId != null
                && (buttonId.contains(FINISH_ARREST_FIELD_PAYMENT) || buttonId.contains("deleteButtons"));
    }

    public static int getCapturedInfantrySpent(Player player) {
        return player.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith("kaldurCapturedInfantry_"))
                .mapToInt(thing -> parseCapturedInfantrySpent(thing.substring("kaldurCapturedInfantry_".length())))
                .sum();
    }

    private static int parseCapturedInfantrySpent(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void offerKaldurInfantryCapture(Game game, Player owner, Player target, int remaining) {
        List<CaptureLocation> locations = getKaldurCaptureLocations(game, owner, target);
        if (locations.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing()
                            + " has no eligible infantry to capture with _Kaldur Arrest Field_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                kaldurCaptureMessage(owner, target, remaining),
                getKaldurCapturePage(owner, target, locations, remaining, 0));
    }

    private static List<CaptureLocation> getKaldurCaptureLocations(Game game, Player owner, Player target) {
        List<CaptureLocation> locations = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (game.isFowMode()
                    && !FoWHelper.getTilePositionsToShow(game, owner).contains(tile.getPosition())) {
                continue;
            }
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (holder.getUnitCount(UnitType.Infantry, target) > 0) {
                    locations.add(new CaptureLocation(tile, holder));
                }
            }
        }
        locations.sort(Comparator.comparing(
                location -> location.tile().getPosition() + location.holder().getName()));
        return locations;
    }

    private static boolean hasStructure(Player player, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (holder.getUnitCount(UnitType.Pds, player) > 0
                    || holder.getUnitCount(UnitType.Spacedock, player) > 0
                    || holder.getUnitCount(UnitType.Monument, player) > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<Button> getKaldurCapturePage(
            Player owner, Player target, List<CaptureLocation> locations, int remaining, int page) {
        int pageCount = Math.max(1, (int) Math.ceil((double) locations.size() / CAPTURE_PAGE_SIZE));
        int safePage = Math.clamp(page, 0, pageCount - 1);
        int from = safePage * CAPTURE_PAGE_SIZE;
        int to = Math.min(from + CAPTURE_PAGE_SIZE, locations.size());
        List<Button> buttons = new ArrayList<>();
        for (CaptureLocation location : locations.subList(from, to)) {
            buttons.add(Buttons.green(
                    owner.factionButtonChecker() + CAPTURE_ARREST_FIELD_INFANTRY + target.getFaction() + "|" + remaining
                            + "|" + location.tile().getPosition() + "|"
                            + location.holder().getName(),
                    "Capture Infantry from " + location.tile().getPosition() + " "
                            + location.holder().getName(),
                    FactionEmojis.kaltrim));
        }
        if (safePage > 0) {
            buttons.add(Buttons.gray(
                    owner.factionButtonChecker() + CAPTURE_ARREST_FIELD_PAGE + target.getFaction() + "|" + remaining
                            + "|" + (safePage - 1),
                    "Previous Page"));
        }
        if (safePage + 1 < pageCount) {
            buttons.add(Buttons.gray(
                    owner.factionButtonChecker() + CAPTURE_ARREST_FIELD_PAGE + target.getFaction() + "|" + remaining
                            + "|" + (safePage + 1),
                    "Next Page"));
        }
        buttons.add(Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline"));
        return buttons;
    }

    private static String kaldurCaptureMessage(Player owner, Player target, int remaining) {
        return owner.getRepresentation() + ", choose an infantry belonging to "
                + target.getRepresentationNoPing() + " to capture with _Kaldur Arrest Field_"
                + (remaining > 1 ? ". You may capture " + remaining + " infantry." : ".");
    }

    private record CaptureLocation(Tile tile, UnitHolder holder) {}
}
