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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.transaction.SendPromissoryService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class XinPnHandler {
    private static final String STATECRAFT_MENTOR = "dspnxin";
    private static final String USE_MENTOR = "useStatecraftMentor_";
    private static final String DECLINE_MENTOR = "declineStatecraftMentor_";
    private static final String SELECT_GROUND_FORCE = "selectStatecraftGroundForce_";
    private static final String SELECT_PLANET = "selectStatecraftPlanet_";
    private static final String REPLACED_RETURN = "statecraftMentorReplacedReturn_";
    private static final String DECLINED_RETURN = "statecraftMentorDeclinedReturn_";
    private static final String PLAY_AREA_RETURN = "statecraftMentorPlayAreaReturn_";
    private static final String SELECTED_GROUND_FORCE = "statecraftMentorSelectedGroundForce_";

    public static boolean offerStatecraftMentor(
            Game game, Player holder, Player returningOwner, String returningPromissoryNote) {
        if (!canOfferStatecraftMentor(game, holder, returningOwner, returningPromissoryNote)) {
            return false;
        }
        sendStatecraftMentorButtons(holder, returningPromissoryNote);
        return true;
    }

    public static boolean offerStatecraftMentorForPlayAreaReturn(
            Game game, Player holder, Player returningOwner, String returningPromissoryNote) {
        if (!canOfferStatecraftMentor(game, holder, returningOwner, returningPromissoryNote)) {
            return false;
        }
        game.setStoredValue(playAreaReturnKey(holder), returningPromissoryNote);
        sendStatecraftMentorButtons(holder, returningPromissoryNote);
        return true;
    }

    private static boolean canOfferStatecraftMentor(
            Game game, Player holder, Player returningOwner, String returningPromissoryNote) {
        return returningOwner != null
                && holder != returningOwner
                && !STATECRAFT_MENTOR.equals(returningPromissoryNote)
                && holder.getPromissoryNotes().containsKey(STATECRAFT_MENTOR)
                && !consumeDeclinedReturn(game, holder, returningPromissoryNote);
    }

    private static void sendStatecraftMentorButtons(Player holder, String returningPromissoryNote) {
        PromissoryNoteModel returnedPN = Mapper.getPromissoryNote(returningPromissoryNote);
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentation() + ", you may return _Statecraft Mentor_ instead of returning _"
                        + returnedPN.getNameRepresentation() + "_.",
                List.of(
                        Buttons.green(
                                holder.factionButtonChecker() + USE_MENTOR + returningPromissoryNote,
                                "Use Statecraft Mentor",
                                FactionEmojis.xin),
                        Buttons.red(
                                holder.factionButtonChecker() + DECLINE_MENTOR + returningPromissoryNote,
                                "Return Original Promissory Note")));
    }

    @ButtonHandler(USE_MENTOR)
    public static void useStatecraftMentor(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        String returningPromissoryNote = buttonID.substring(USE_MENTOR.length());
        Player originalOwner = game.getPNOwner(returningPromissoryNote);
        Player xin = game.getPNOwner(STATECRAFT_MENTOR);
        boolean playAreaReturn = isPlayAreaReturn(game, holder, returningPromissoryNote);
        if (originalOwner == null
                || xin == null
                || originalOwner == holder
                || (!playAreaReturn && !holder.getPromissoryNotes().containsKey(returningPromissoryNote))
                || !holder.getPromissoryNotes().containsKey(STATECRAFT_MENTOR)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        holder.removePromissoryNote(STATECRAFT_MENTOR);
        originalOwner.setPromissoryNote(STATECRAFT_MENTOR);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, originalOwner, false);
        ButtonHelper.deleteMessage(event);
        PromissoryNoteModel returnedPN = Mapper.getPromissoryNote(returningPromissoryNote);
        if (KaltrimBreakthroughHandler.isEchoOperativesRepairReturn(game, holder, returningPromissoryNote)) {
            KaltrimBreakthroughHandler.cancelEchoOperativesRepairReturn(game, holder);
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentation() + " returned _Statecraft Mentor_ to "
                            + originalOwner.getRepresentationNoPing() + " instead of returning _"
                            + returnedPN.getNameRepresentation() + "_.");
            KaltrimBreakthroughHandler.offerEchoOperativesRepair(game, holder, originalOwner);
            sendGroundForceButtons(game, xin, holder);
            return;
        }
        if (consumePlayAreaReturn(game, holder, returningPromissoryNote)) {
            MessageHelper.sendMessageToChannel(
                    holder.getCorrectChannel(),
                    holder.getRepresentation() + " returned _Statecraft Mentor_ to "
                            + originalOwner.getRepresentationNoPing() + " instead of returning _"
                            + returnedPN.getNameRepresentation() + "_.");
            sendGroundForceButtons(game, xin, holder);
            return;
        }
        game.setStoredValue(replacedReturnKey(holder), returningPromissoryNote);
        PromissoryNoteHelper.resolvePNPlay(returningPromissoryNote, holder, game, event);
        sendGroundForceButtons(game, xin, holder);
    }

    @ButtonHandler(DECLINE_MENTOR)
    public static void declineStatecraftMentor(
            ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        String returningPromissoryNote = buttonID.substring(DECLINE_MENTOR.length());
        boolean playAreaReturn = isPlayAreaReturn(game, holder, returningPromissoryNote);
        if (!playAreaReturn && !holder.getPromissoryNotes().containsKey(returningPromissoryNote)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (consumePlayAreaReturn(game, holder, returningPromissoryNote)) {
            Player owner = game.getPNOwner(returningPromissoryNote);
            game.setStoredValue(declinedReturnKey(holder), returningPromissoryNote);
            ButtonHelper.deleteMessage(event);
            if (owner != null) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(
                        event, game, holder, owner, returningPromissoryNote);
            }
            return;
        }
        if (KaltrimBreakthroughHandler.isEchoOperativesRepairReturn(game, holder, returningPromissoryNote)) {
            ButtonHelper.deleteMessage(event);
            Player owner = game.getPNOwner(returningPromissoryNote);
            if (owner != null) {
                KaltrimBreakthroughHandler.continueEchoOperativesRepairReturn(
                        event, game, holder, owner, returningPromissoryNote);
            } else {
                KaltrimBreakthroughHandler.cancelEchoOperativesRepairReturn(game, holder);
            }
            return;
        }
        game.setStoredValue(declinedReturnKey(holder), returningPromissoryNote);
        ButtonHelper.deleteMessage(event);
        PromissoryNoteHelper.resolvePNPlay(returningPromissoryNote, holder, game, event);
    }

    @ButtonHandler(SELECT_GROUND_FORCE)
    public static void selectGroundForce(ButtonInteractionEvent event, Game game, Player xin, String buttonID) {
        String[] values = buttonID.substring(SELECT_GROUND_FORCE.length()).split("\\|", 2);
        Player holder = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        String unit = values.length == 2 ? values[1] : "";
        if (holder == null || !hasAvailableGroundForce(xin, game, unit)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        xin.setStoredValue(selectedGroundForceKey(holder), unit);
        PlanetTargetSpec planetSpec = statecraftPlanetSpec(xin, holder);
        List<Button> buttons = new ArrayList<>(holder.getPlanets().stream()
                .map(planet -> Buttons.green(
                        planetSpec.buttonPrefix() + "_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList());
        buttons = PlanetTargetService.targetButtons(game, xin, planetSpec, buttons);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                xin.getRepresentation() + ", choose one of " + holder.getRepresentationNoPing() + "'s planets for your "
                        + unit + " to enter coexistence.",
                buttons);
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player xin, String buttonID) {
        for (Player candidate : game.getRealPlayers()) {
            if (PlanetTargetService.handlePlanetPage(
                    event, game, xin, buttonID, statecraftPlanetSpec(xin, candidate))) {
                return;
            }
        }
        String[] values = buttonID.substring(SELECT_PLANET.length()).split("_", 2);
        Player holder = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (holder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String unit = xin.getStoredValue(selectedGroundForceKey(holder));
        String planet = values.length == 2 ? values[1] : "";
        Tile tile = game.getTileFromPlanet(planet);
        if (holder == null
                || tile == null
                || !holder.getPlanets().contains(planet)
                || !hasAvailableGroundForce(xin, game, unit)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, xin.getColor(), "1 " + unit + " " + planet);
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        xin.removeStoredValue(selectedGroundForceKey(holder));
        MessageHelper.sendMessageToChannel(
                xin.getCorrectChannel(),
                xin.getRepresentation() + " placed 1 " + unit + " into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + " using _Statecraft Mentor_.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean consumeReplacedReturn(Game game, Player holder, String promissoryNote) {
        String key = replacedReturnKey(holder);
        if (!promissoryNote.equals(game.getStoredValue(key))) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static boolean consumeDeclinedReturn(Game game, Player holder, String promissoryNote) {
        String key = declinedReturnKey(holder);
        if (!promissoryNote.equals(game.getStoredValue(key))) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static boolean consumePlayAreaReturn(Game game, Player holder, String promissoryNote) {
        String key = playAreaReturnKey(holder);
        if (!promissoryNote.equals(game.getStoredValue(key))) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static boolean isPlayAreaReturn(Game game, Player holder, String promissoryNote) {
        return promissoryNote.equals(game.getStoredValue(playAreaReturnKey(holder)));
    }

    private static void sendGroundForceButtons(Game game, Player xin, Player holder) {
        List<Button> buttons = new ArrayList<>();
        if (hasAvailableGroundForce(xin, game, "inf")) {
            buttons.add(Buttons.green(
                    xin.factionButtonChecker() + SELECT_GROUND_FORCE + holder.getFaction() + "|inf",
                    "Place 1 Infantry",
                    FactionEmojis.xin));
        }
        if (hasAvailableGroundForce(xin, game, "mech")) {
            buttons.add(Buttons.green(
                    xin.factionButtonChecker() + SELECT_GROUND_FORCE + holder.getFaction() + "|mech",
                    "Place 1 Mech",
                    FactionEmojis.xin));
        }
        if (buttons.isEmpty() || (!game.isFowMode() && holder.getPlanets().isEmpty())) {
            return;
        }
        if (holder != xin) {
            MessageHelper.sendMessageToChannelWithButtons(
                    xin.getCorrectChannel(),
                    xin.getRepresentation() + ", choose a ground force to place into coexistence on a planet "
                            + holder.getRepresentationNoPing() + " controls using _Statecraft Mentor_.",
                    buttons);
        }
    }

    private static boolean hasAvailableGroundForce(Player xin, Game game, String unit) {
        if ("inf".equals(unit)) {
            return xin.getUnitByBaseType("infantry") != null;
        }
        return "mech".equals(unit)
                && xin.getUnitByBaseType("mech") != null
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, xin, "mech", true) < 4;
    }

    private static String replacedReturnKey(Player player) {
        return REPLACED_RETURN + player.getFaction();
    }

    private static String declinedReturnKey(Player player) {
        return DECLINED_RETURN + player.getFaction();
    }

    private static String playAreaReturnKey(Player player) {
        return PLAY_AREA_RETURN + player.getFaction();
    }

    private static PlanetTargetSpec statecraftPlanetSpec(Player xin, Player holder) {
        return PlanetTargetSpec.of(xin.factionButtonChecker() + SELECT_PLANET + holder.getFaction())
                .requiringController();
    }

    private static String selectedGroundForceKey(Player holder) {
        return SELECTED_GROUND_FORCE + holder.getFaction();
    }
}
