package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class VeylorLeadersHandler {
    private static final String UNLOCK = "unlockVeylorCommander";
    private static final String EXHAUST_PLANET = "exhaustVeylorPlanet_";
    private static final String DONE = "doneVeylorCommander";
    private static final String GAIN_HERO_CC = "gainVeylorHeroCC_";
    private static final String CHOOSE_COMMANDER_SYSTEM = "veylorCommanderSelectSystem_";
    private static final String CHOOSE_COMMANDER_SHIP = "veylorCommanderSelectShip_";

    // Commander
    public static Button offerVeylorCommanderUnlock(Player player) {
        if (player == null || !player.hasLeader("veylorcommander") || player.hasLeaderUnlocked("veylorcommander")) {
            return null;
        }

        return Buttons.green(player.factionButtonChecker() + UNLOCK, "Unlock Commander", FactionEmojis.veylor);
    }

    @ButtonHandler(UNLOCK)
    public static void startVeylorCommanderUnlock(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null
                || player == null
                || game == null
                || !player.hasLeader("veylorcommander")
                || player.hasLeaderUnlocked("veylorcommander")) {
            return;
        }

        List<Button> buttons = getExhaustPlanetButtons(player, game);
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE, "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", exhaust any planets you choose, then select **Done Exhausting Planets**. Reminder that you must exhaust all but 3 to unlock it.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXHAUST_PLANET)
    public static void exhaustPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasLeader("veylorcommander")) {
            return;
        }

        String planet = buttonID.substring(EXHAUST_PLANET.length());
        if (!player.getReadiedPlanets().contains(planet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        player.exhaustPlanet(planet);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DONE)
    public static void finishVeylorCommanderUnlock(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null
                || game == null
                || player == null
                || !player.hasLeader("veylorcommander")
                || player.hasLeaderUnlocked("veylorcommander")) {
            return;
        }

        UnlockLeaderService.unlockLeader("veylorcommander", game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getExhaustPlanetButtons(Player player, Game game) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        planets.sort(String::compareTo);

        List<Button> buttons = new ArrayList<>();
        for (String planet : planets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXHAUST_PLANET + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        return buttons;
    }

    // Hero
    public static boolean isVeylorAgendaPhase(Game game) {
        if (game == null
                || !game.getStoredValue("executiveOrder").isEmpty()
                || "action".equals(game.getPhaseOfGame())) {
            return false;
        }
        return true;
    }

    public static boolean hasHeroAdditionalAgenda(Game game, int aCount) {
        return isVeylorAgendaPhase(game)
                && aCount == 3
                && game.getRealPlayers().stream().anyMatch(player -> player.hasLeaderUnlocked("veylorhero"));
    }

    public static void resolveVeylorHeroLosingVote(Game game, String winner) {
        for (Player player : AgendaHelper.getLosingVoters(winner, game)) {
            if (!player.hasLeaderUnlocked("veylorhero")) {
                continue;
            }

            List<Button> buttons = List.of(
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "tactic", "Gain 1 Tactic Token"),
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "fleet", "Gain 1 Fleet Token"),
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "strategy", "Gain 1 Strategy Token"));

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", an agenda outcome you voted for was not resolved. Gain 1 command token from _Adoration of the Masses_.",
                    buttons);
        }
    }

    @ButtonHandler(GAIN_HERO_CC)
    public static void gainVeylorHeroCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasLeaderUnlocked("veylorhero")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pool =
                switch (buttonID.substring(GAIN_HERO_CC.length())) {
                    case "tactic" -> {
                        player.setTacticalCC(player.getTacticalCC() + 1);
                        yield "tactic";
                    }
                    case "fleet" -> {
                        player.setFleetCC(player.getFleetCC() + 1);
                        yield "fleet";
                    }
                    case "strategy" -> {
                        player.setStrategicCC(player.getStrategicCC() + 1);
                        yield "strategy";
                    }
                    default -> {
                        ButtonHelper.deleteMessage(event);
                        yield null;
                    }
                };

        if (pool == null) {
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " gained 1 " + pool + " token from _Adoration of the Masses_.");

        ButtonHelper.deleteMessage(event);
    }

    // Commander
    public static void resolveVeylorCommanderLosingVote(GenericInteractionCreateEvent event, Game game, String winner) {
        if (event == null || game == null) {
            return;
        }

        for (Player player : AgendaHelper.getLosingVoters(winner, game)) {
            if (!game.playerHasLeaderUnlockedOrAlliance(player, "veylorcommander")) {
                continue;
            }

            int votesCast = getVotesCastForLosingOutcome(player, winner, game);
            if (votesCast < 1) {
                continue;
            }
            if (player.getUnitModels().stream().noneMatch(unit -> unit.getIsShip() && unit.getCost() < votesCast)) {
                continue;
            }

            List<Button> systemsWithShips = getVeylorCommanderSystemButtons(player, game, votesCast);

            if (systemsWithShips.isEmpty()) {
                continue;
            }

            List<Button> displayedButtons = systemsWithShips.size() <= 25
                    ? systemsWithShips
                    : NewStuffHelper.buttonPagination(
                            systemsWithShips,
                            player.factionButtonChecker() + CHOOSE_COMMANDER_SYSTEM + "page_" + votesCast + "_",
                            0);

            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", since the outcome you voted for was not resolved, you may produce 1 ship in a system that contains your ships with cost less than your cast votes due to _Cyrala Vey_.",
                    displayedButtons);
        }
    }

    @ButtonHandler(CHOOSE_COMMANDER_SYSTEM)
    public static void produceVeylorCommanderShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !game.playerHasLeaderUnlockedOrAlliance(player, "veylorcommander")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_COMMANDER_SYSTEM.length());
        if (payload.startsWith("page_")) {
            String[] pageParts = payload.split("_", 4);
            if (pageParts.length < 3) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            try {
                int votesCast = Integer.parseInt(pageParts[1]);
                List<Button> systemsWithShips = getVeylorCommanderSystemButtons(player, game, votesCast);
                String message = player.getRepresentation()
                        + ", since the outcome you voted for was not resolved, you may produce 1 ship in a system that contains your ships with cost less than your cast votes due to _Cyrala Vey_.";
                if (NewStuffHelper.checkAndHandlePaginationChange(
                        event,
                        event.getMessageChannel(),
                        systemsWithShips,
                        message,
                        player.factionButtonChecker() + CHOOSE_COMMANDER_SYSTEM + "page_" + votesCast + "_",
                        buttonID)) {
                    return;
                }
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePos = parts[0];
        int votesCast;
        try {
            votesCast = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getVeylorCommanderShipButtons(player, tile, votesCast);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No eligible ship can be produced.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = "Please choose the ship to produce in " + tile.getRepresentation()
                + " with cost less than your " + votesCast + " votes cast:";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(
                        buttons,
                        player.factionButtonChecker() + CHOOSE_COMMANDER_SHIP + "page_" + tile.getPosition() + "|"
                                + votesCast + "_",
                        0));

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_COMMANDER_SHIP)
    public static void produceVeylorCommanderSelectedShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !game.playerHasLeaderUnlockedOrAlliance(player, "veylorcommander")) {
            return;
        }

        String payload = buttonID.substring(CHOOSE_COMMANDER_SHIP.length());
        if (payload.startsWith("page_")) {
            String[] pageParts = payload.substring("page_".length()).split("_", 2);
            String[] pageData = pageParts[0].split("\\|", 2);
            if (pageParts.length != 2 || pageData.length != 2) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            Tile pageTile = game.getTileByPosition(pageData[0]);
            try {
                int votesCast = Integer.parseInt(pageData[1]);
                if (pageTile == null || !FoWHelper.playerHasShipsInSystem(player, pageTile)) {
                    ButtonHelper.deleteMessage(event);
                    return;
                }
                List<Button> buttons = getVeylorCommanderShipButtons(player, pageTile, votesCast);
                String message = "Please choose the ship to produce in " + pageTile.getRepresentation()
                        + " with cost less than your " + votesCast + " votes cast:";
                NewStuffHelper.checkAndHandlePaginationChange(
                        event,
                        event.getMessageChannel(),
                        buttons,
                        message,
                        player.factionButtonChecker() + CHOOSE_COMMANDER_SHIP + "page_" + pageTile.getPosition() + "|"
                                + votesCast + "_",
                        buttonID);
                return;
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
                return;
            }
        }

        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        int votesCast;
        try {
            votesCast = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getVeylorCommanderShipButtons(player, tile, votesCast);
        String message = "Please choose the ship to produce in " + tile.getRepresentation()
                + " with cost less than your " + votesCast + " votes cast:";
        if (buttons.stream().noneMatch(button -> buttonID.equals(button.getCustomId()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperModifyUnits.placeUnitAndDeleteButton(
                "placeOneNDone_dontskip_" + parts[0] + "_" + tile.getPosition(), event, game, player);
    }

    private static int getVotesCastForLosingOutcome(Player player, String winner, Game game) {
        int votesCast = 0;
        for (var outcome : game.getCurrentAgendaVotes().entrySet()) {
            if (outcome.getKey().equalsIgnoreCase(winner)) {
                continue;
            }
            for (String vote : outcome.getValue().split(";")) {
                String[] parts = vote.split("_", 2);
                if (parts.length == 2 && parts[0].equalsIgnoreCase(player.getFaction())) {
                    try {
                        votesCast += Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                        // Riders and other non-vote entries do not count as cast votes.
                    }
                }
            }
        }
        return votesCast;
    }

    private static List<Button> getVeylorCommanderSystemButtons(Player player, Game game, int votesCast) {
        List<Button> systemsWithShips = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                systemsWithShips.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_SYSTEM + tile.getPosition() + "|" + votesCast,
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return systemsWithShips;
    }

    private static List<Button> getVeylorCommanderShipButtons(Player player, Tile tile, int votesCast) {
        return player.getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .filter(unit -> unit.getCost() < votesCast)
                .sorted(Comparator.comparing(UnitModel::getName))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_SHIP + unit.getAsyncId() + "|"
                                + tile.getPosition() + "|" + votesCast,
                        "Produce 1 " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();
    }
}
