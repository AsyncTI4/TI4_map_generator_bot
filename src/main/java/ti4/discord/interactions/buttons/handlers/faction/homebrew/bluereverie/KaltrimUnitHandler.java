package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

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
import ti4.game.UnitHolder;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class KaltrimUnitHandler {
    private static final String JEWEL_OF_THE_ROSE = "useJewelOfTheRose_";
    private static final String OFFER_CONSULATE_DEPLOY = "offerConsulateDeploy_";
    private static final String PLACE_CONSULATE_DEPLOY = "placeConsulateDeploy_";
    private static final String CAPTURE_CONSULATE_INFANTRY = "captureConsulateInfantry_";
    private static final String CAPTURE_CONSULATE_INFANTRY_PAGE = "captureConsulateInfantryPage_";

    public static void offerJewelOfTheRose(Game game, String agendaId) {
        for (Player player : game.getRealPlayers()) {
            if (!player.ownsUnit("kaltrim_flagship") || getUndamagedJewelOfTheRose(game, player) == null) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.blue(
                            player.factionButtonChecker() + JEWEL_OF_THE_ROSE + agendaId,
                            "Use Jewel of the Rose",
                            FactionEmojis.kaltrim),
                    Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentation()
                            + ", you may damage **Jewel of the Rose** to swap the revealed agenda with the top agenda card.",
                    buttons);
        }
    }

    @ButtonHandler(JEWEL_OF_THE_ROSE)
    public static void useJewelOfTheRose(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String agendaId = buttonID.substring(JEWEL_OF_THE_ROSE.length());
        UnitHolder jewel = getUndamagedJewelOfTheRose(game, player);
        if (!player.ownsUnit("kaltrim_flagship") || !isCurrentRevealedAgenda(game, agendaId) || jewel == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Jewel of the Rose is no longer available for this agenda.");
            return;
        }

        jewel.addDamagedUnit(Units.getUnitKey(UnitType.Flagship, player.getColorID()), 1);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                player.getRepresentationNoPing()
                        + " is damaging **Jewel of the Rose** to swap the revealed agenda with the top card of the agenda deck.");
        decrementAgendaCount(game);
        bypassTightScheduling(game);
        game.removeStoredValue("lastAgendaReactTime");
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
        game.putAgendaBackIntoDeckOnTop(agendaId);
    }

    private static UnitHolder getUndamagedJewelOfTheRose(Game game, Player player) {
        UnitKey flagship = Units.getUnitKey(UnitType.Flagship, player.getColorID());
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space.getUnitCount(flagship) > space.getDamagedUnitCount(flagship)) {
                return space;
            }
        }
        return null;
    }

    private static boolean isCurrentRevealedAgenda(Game game, String agendaId) {
        if (agendaId.equals(AgendaHelper.getCurrentAgendaId(game))) {
            return true;
        }
        String currentAgendaInfo = game.getCurrentAgendaInfo();
        return currentAgendaInfo != null
                && currentAgendaInfo.endsWith("_covert")
                && game.getDiscardAgendas().containsKey(agendaId)
                && Mapper.getAgenda(agendaId) != null
                && "Covert Legislation"
                        .equalsIgnoreCase(Mapper.getAgenda(agendaId).getName());
    }

    private static void decrementAgendaCount(Game game) {
        String agendaCount = game.getStoredValue("agendaCount");
        if (agendaCount.isBlank()) {
            return;
        }
        try {
            game.setStoredValue("agendaCount", Integer.toString(Integer.parseInt(agendaCount) - 1));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void bypassTightScheduling(Game game) {
        boolean hasStoredAgendas = game.getRealPlayers().stream()
                .anyMatch(player -> player.hasAbility("tight_scheduling")
                        && !game.getStoredValue("tightSchedulingAgendas_" + player.getFaction())
                                .isEmpty());
        if (hasStoredAgendas) {
            game.setStoredValue("tightSchedulingBypass", "yes");
        }
    }

    public static boolean canDeployConsulate(Player player, Game game) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pd", true) < player.getUnitCap("pd");
    }

    public static void offerConsulateDeploy(Player player, Player activator, Game game) {
        if (getConsulatePlanets(activator, game).isEmpty()) {
            return;
        }
        List<Button> buttons = List.of(Buttons.green(
                player.factionButtonChecker() + OFFER_CONSULATE_DEPLOY + activator.getFaction(),
                "Offer Consulate to " + activator.getFactionNameOrColor(),
                FactionEmojis.kaltrim));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may offer for " + activator.getRepresentationNoPing()
                        + " to place 1 of your _Consulates_ on a planet they control in the active system.",
                buttons);
    }

    @ButtonHandler(OFFER_CONSULATE_DEPLOY)
    public static void offerConsulateToPlayer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(OFFER_CONSULATE_DEPLOY.length()));
        if (target == null
                || target == player
                || !player.ownsUnit("kaltrim_pds")
                || !player.isNeighboursWith(target)
                || !canDeployConsulate(player, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : getConsulatePlanets(target, game)) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + PLACE_CONSULATE_DEPLOY + player.getFaction() + "|"
                            + planet.getName(),
                    "Place Consulate on " + planet.getRepresentation(game),
                    FactionEmojis.kaltrim));
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", " + player.getRepresentationNoPing()
                        + " offered you a _Consulate_. You may place it into coexistence on a planet you control in the active system.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_CONSULATE_DEPLOY)
    public static void placeConsulate(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] values = buttonID.substring(PLACE_CONSULATE_DEPLOY.length()).split("\\|", 2);
        if (values.length != 2) {
            return;
        }
        Player owner = game.getPlayerFromColorOrFaction(values[0]);
        Planet planet = game.getUnitHolderFromPlanet(values[1]);
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (owner == null
                || planet == null
                || activeSystem == null
                || game.getTileFromPlanet(values[1]) != activeSystem
                || !target.getPlanets().contains(values[1])
                || !owner.ownsUnit("kaltrim_pds")
                || !canDeployConsulate(owner, game)) {
            return;
        }

        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, activeSystem, game, owner.getColor(), "pds " + planet.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " placed " + owner.getRepresentationNoPing()
                        + "'s _Consulate_ into coexistence on " + planet.getRepresentation(game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Planet> getConsulatePlanets(Player player, Game game) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return List.of();
        }
        return activeSystem.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .toList();
    }

    public static void offerButtonsToCaptureConsulateInf(Game game, Player owner, Player target, Tile activatedSystem) {
        if (!hasEligibleConsulateII(owner, activatedSystem)) {
            return;
        }
        List<Button> buttons = getConsulateInfantryCaptureButtons(game, owner, target, activatedSystem);
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                consulateCaptureMessage(owner, target),
                getConsulateInfantryCapturePage(owner, target, activatedSystem, buttons, 0));
    }

    @ButtonHandler(CAPTURE_CONSULATE_INFANTRY_PAGE)
    public static void changeConsulateInfantryCapturePage(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] values =
                buttonID.substring(CAPTURE_CONSULATE_INFANTRY_PAGE.length()).split("\\|", 3);
        if (values.length != 3) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(values[0]);
        Tile activatedSystem = game.getTileByPosition(values[1]);
        int page;
        try {
            page = Integer.parseInt(values[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (target == null || activatedSystem == null || !hasEligibleConsulateII(owner, activatedSystem)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getConsulateInfantryCaptureButtons(game, owner, target, activatedSystem);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageButtons(
                event, getConsulateInfantryCapturePage(owner, target, activatedSystem, buttons, page));
    }

    @ButtonHandler(CAPTURE_CONSULATE_INFANTRY)
    public static void captureConsulateInfantry(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] values =
                buttonID.substring(CAPTURE_CONSULATE_INFANTRY.length()).split("\\|", 4);
        if (values.length != 4) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(values[0]);
        Tile activatedSystem = game.getTileByPosition(values[1]);
        Tile targetTile = game.getTileByPosition(values[2]);
        UnitHolder targetHolder =
                targetTile == null ? null : targetTile.getUnitHolders().get(values[3]);
        if (target == null
                || activatedSystem == null
                || targetHolder == null
                || !hasEligibleConsulateII(owner, activatedSystem)
                || game.isFowMode()
                        && !FoWHelper.getTilePositionsToShow(game, owner).contains(targetTile.getPosition())
                || targetHolder.getUnitCount(UnitType.Infantry, target.getColor()) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnit(event, targetTile, game, target, targetHolder, UnitType.Infantry, 1);
        AddUnitService.addUnits(event, owner.getNomboxTile(), game, owner.getColor(), "1 infantry");
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + " captured 1 infantry belonging to "
                        + target.getRepresentationNoPing() + " with **Consulate II**.");
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasEligibleConsulateII(Player owner, Tile activatedSystem) {
        if (!owner.hasTech("dskaltpds") || activatedSystem == null) {
            return false;
        }
        for (Planet planet : activatedSystem.getPlanetUnitHolders()) {
            if (!owner.getPlanets().contains(planet.getName())
                    && planet.getUnitCount(UnitType.Pds, owner.getColor()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<Button> getConsulateInfantryCaptureButtons(
            Game game, Player owner, Player target, Tile activatedSystem) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (game.isFowMode()
                    && !FoWHelper.getTilePositionsToShow(game, owner).contains(tile.getPosition())) {
                continue;
            }
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (holder.getUnitCount(UnitType.Infantry, target.getColor()) < 1) {
                    continue;
                }
                String location = holder instanceof Planet planet
                        ? planet.getRepresentation(game)
                        : tile.getRepresentationForButtons(game, owner) + " space area";
                buttons.add(Buttons.green(
                        owner.factionButtonChecker() + CAPTURE_CONSULATE_INFANTRY + target.getFaction() + "|"
                                + activatedSystem.getPosition() + "|" + tile.getPosition() + "|" + holder.getName(),
                        "Capture 1 Infantry on " + location,
                        FactionEmojis.kaltrim));
            }
        }
        return buttons;
    }

    private static List<Button> getConsulateInfantryCapturePage(
            Player owner, Player target, Tile activatedSystem, List<Button> buttons, int page) {
        int pageSize = 22;
        int pageCount = Math.ceilDiv(buttons.size(), pageSize);
        int currentPage = Math.clamp(page, 0, pageCount - 1);
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, buttons.size());
        List<Button> pageButtons = new ArrayList<>(buttons.subList(start, end));
        if (currentPage > 0) {
            pageButtons.add(Buttons.blue(
                    owner.factionButtonChecker() + CAPTURE_CONSULATE_INFANTRY_PAGE + target.getFaction() + "|"
                            + activatedSystem.getPosition() + "|" + (currentPage - 1),
                    "Previous Page"));
        }
        if (currentPage + 1 < pageCount) {
            pageButtons.add(Buttons.blue(
                    owner.factionButtonChecker() + CAPTURE_CONSULATE_INFANTRY_PAGE + target.getFaction() + "|"
                            + activatedSystem.getPosition() + "|" + (currentPage + 1),
                    "Next Page"));
        }
        pageButtons.add(Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline"));
        return pageButtons;
    }

    private static String consulateCaptureMessage(Player owner, Player target) {
        return owner.getRepresentation() + ", " + target.getRepresentationNoPing()
                + " activated a system containing your uncontrolled **Consulate II**. You may capture 1 of their infantry on the game board.";
    }
}
