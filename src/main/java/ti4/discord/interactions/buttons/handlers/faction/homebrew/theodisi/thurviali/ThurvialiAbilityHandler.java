package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali;

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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ThurvialiAbilityHandler {
    private static final List<String> RADIANT_GRAFTS = List.of(
            "radiant_grafting_flight",
            "radiant_grafting_claws",
            "radiant_grafting_parturition",
            "radiant_grafting_scales",
            "radiant_grafting_phalanges");
    private static final String RADIANT_GRAFTING_PENDING = "radiantGraftingPending_";
    private static final String GAIN_RADIANT_GRAFT = "gainRadiantGraft_";
    private static final String REMOVE_RADIANT_GRAFT = "removeRadiantGraft_";
    private static final String CELESTIAL_ENVOYS_PLACE = "celestialEnvoysPlace_";
    private static final String CELESTIAL_ENVOYS_USED = "celestialEnvoysUsed_";

    public static void clearCelestialEnvoysUsed(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(CELESTIAL_ENVOYS_USED + player.getFaction());
        }
    }

    public static void offerCelestialEnvoys(Game game, Player secondaryPlayer, int strategyCard) {
        Player owner = game.getPlayerFromSC(strategyCard);
        if (owner == null || owner == secondaryPlayer || !owner.hasAbility("celestial_envoys")) {
            return;
        }
        String useKey = CELESTIAL_ENVOYS_USED + owner.getFaction();
        if (!game.getStoredValue(useKey).isEmpty()) {
            return;
        }
        List<Button> buttons = getCelestialEnvoyPlanetButtons(game, owner, secondaryPlayer, strategyCard);
        if (buttons.isEmpty()) {
            return;
        }
        String message = owner.getRepresentationNoPing() + ", " + secondaryPlayer.getRepresentationNoPing()
                + " is performing the secondary ability of your strategy card. You may use _Celestial Envoys_ to place 1 infantry into coexistence on one of their non-home planets containing ground forces.";
        String buttonPrefix = owner.factionButtonChecker() + CELESTIAL_ENVOYS_PLACE + strategyCard + "|"
                + secondaryPlayer.getFaction() + "|";
        List<Button> displayedButtons;
        if (buttons.size() <= 24) {
            displayedButtons = new ArrayList<>(buttons);
            displayedButtons.add(Buttons.red("deleteButtons", "Decline"));
        } else {
            displayedButtons = NewStuffHelper.buttonPagination(
                    buttons, List.of(Buttons.red("deleteButtons", "Decline")), buttonPrefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(owner.getCorrectChannel(), message, displayedButtons);
    }

    @ButtonHandler(CELESTIAL_ENVOYS_PLACE)
    public static void resolveCelestialEnvoys(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CELESTIAL_ENVOYS_PLACE.length()).split("\\|", 3);
        if (payload.length != 3) {
            return;
        }
        int strategyCard;
        try {
            strategyCard = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(payload[1]);
        if (target == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getCelestialEnvoyPlanetButtons(game, player, target, strategyCard);
        String message = player.getRepresentationNoPing() + ", " + target.getRepresentationNoPing()
                + " is performing the secondary ability of your strategy card. You may use _Celestial Envoys_ to place 1 infantry into coexistence on one of their non-home planets containing ground forces.";
        String buttonPrefix =
                player.factionButtonChecker() + CELESTIAL_ENVOYS_PLACE + strategyCard + "|" + target.getFaction() + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                message,
                buttonPrefix,
                buttonID)) {
            return;
        }
        Tile tile = game.getTileFromPlanet(payload[2]);
        var planet = game.getUnitHolderFromPlanet(payload[2]);
        String useKey = CELESTIAL_ENVOYS_USED + player.getFaction();
        if (!player.hasAbility("celestial_envoys")
                || game.getPlayerFromSC(strategyCard) != player
                || target == null
                || tile == null
                || tile.equals(target.getHomeSystemTile())
                || planet == null
                || !target.getPlanets().contains(planet.getName())
                || !planet.hasGroundForces(target)
                || !game.getStoredValue(useKey).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "gf " + planet.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        game.setStoredValue(useKey, "used");
        checkRadiantGrafting(game);
        ThurvialiBreakthroughHandler.offerNeurografting(event, game, player, planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " using _Celestial Envoys_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getCelestialEnvoyPlanetButtons(
            Game game, Player owner, Player target, int strategyCard) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.equals(target.getHomeSystemTile())) {
                continue;
            }
            for (var planet : tile.getPlanetUnitHolders()) {
                if (!target.getPlanets().contains(planet.getName()) || !planet.hasGroundForces(target)) {
                    continue;
                }
                buttons.add(Buttons.green(
                        owner.factionButtonChecker() + CELESTIAL_ENVOYS_PLACE + strategyCard + "|" + target.getFaction()
                                + "|" + planet.getName(),
                        "Place Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        return buttons;
    }

    public static void checkRadiantGrafting(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility("radiant_grafting")
                    || !game.getStoredValue(RADIANT_GRAFTING_PENDING + player.getFaction())
                            .isEmpty()) {
                continue;
            }
            int coexistingPlanets = game.getPlanetsPlayerIsCoexistingOn(player).size();
            List<String> ownedGrafts =
                    RADIANT_GRAFTS.stream().filter(player::hasAbility).toList();
            if (ownedGrafts.size() == coexistingPlanets) {
                continue;
            }
            List<String> choices = ownedGrafts.size() < coexistingPlanets
                    ? RADIANT_GRAFTS.stream()
                            .filter(graft -> !player.hasAbility(graft))
                            .toList()
                    : ownedGrafts;
            if (choices.isEmpty()) {
                continue;
            }
            boolean gaining = ownedGrafts.size() < coexistingPlanets;
            List<Button> buttons = new ArrayList<>();
            for (String graft : choices) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + (gaining ? GAIN_RADIANT_GRAFT : REMOVE_RADIANT_GRAFT) + graft,
                        (gaining ? "Gain " : "Remove ")
                                + Mapper.getAbility(graft).getName()));
            }
            game.setStoredValue(RADIANT_GRAFTING_PENDING + player.getFaction(), gaining ? "gain" : "remove");
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", choose a _Radiant Grafting_ ability card to "
                            + (gaining ? "gain." : "remove."),
                    buttons);
        }
    }

    @ButtonHandler(GAIN_RADIANT_GRAFT)
    public static void gainRadiantGraft(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String graft = buttonID.substring(GAIN_RADIANT_GRAFT.length());
        game.removeStoredValue(RADIANT_GRAFTING_PENDING + player.getFaction());
        if (!player.hasAbility("radiant_grafting")
                || !RADIANT_GRAFTS.contains(graft)
                || player.hasAbility(graft)
                || RADIANT_GRAFTS.stream().filter(player::hasAbility).count()
                        >= game.getPlanetsPlayerIsCoexistingOn(player).size()) {
            ButtonHelper.deleteMessage(event);
            checkRadiantGrafting(game);
            return;
        }
        player.addAbility(graft);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained "
                        + Mapper.getAbility(graft).getRepresentation() + ".");
        checkRadiantGrafting(game);
    }

    @ButtonHandler(REMOVE_RADIANT_GRAFT)
    public static void removeRadiantGraft(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String graft = buttonID.substring(REMOVE_RADIANT_GRAFT.length());
        game.removeStoredValue(RADIANT_GRAFTING_PENDING + player.getFaction());
        if (!player.hasAbility("radiant_grafting")
                || !RADIANT_GRAFTS.contains(graft)
                || !player.hasAbility(graft)
                || RADIANT_GRAFTS.stream().filter(player::hasAbility).count()
                        <= game.getPlanetsPlayerIsCoexistingOn(player).size()) {
            ButtonHelper.deleteMessage(event);
            checkRadiantGrafting(game);
            return;
        }
        player.removeAbility(graft);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " removed "
                        + Mapper.getAbility(graft).getRepresentation() + ".");
        checkRadiantGrafting(game);
    }
}
