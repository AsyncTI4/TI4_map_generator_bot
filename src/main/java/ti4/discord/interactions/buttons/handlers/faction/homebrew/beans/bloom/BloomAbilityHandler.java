package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BloomAbilityHandler {
    private static final String NEUROTROPIC_DRAW = "neurotropicDraw_";
    private static final String SPOREFALL_PLACE = "sporefallPlace_";
    private static final Map<String, StrainCard> STRAIN_CARDS = Map.of(
            "cultural", new StrainCard("neurotropic_dormant", "neurotropic_active"),
            "industrial", new StrainCard("rhizomatic_dormant", "rhizomatic_active"),
            "hazardous", new StrainCard("carnivorous_dormant", "carnivorous_active"));

    private record StrainCard(String dormantAbility, String activeAbility) {}

    public static void syncStrainCards(Game game, Player player) {
        if (!player.hasAbility("strain_dispersal")) {
            STRAIN_CARDS.values().forEach(strain -> setStrainCardState(player, strain, 0));
            return;
        }

        Set<String> coexistingPlanets = Set.copyOf(game.getPlanetsPlayerIsCoexistingOn(player));
        for (Map.Entry<String, StrainCard> entry : STRAIN_CARDS.entrySet()) {
            long matchingPlanets = coexistingPlanets.stream()
                    .map(game::getUnitHolderFromPlanet)
                    .filter(planet -> hasTrait(planet, entry.getKey()))
                    .count();
            setStrainCardState(player, entry.getValue(), matchingPlanets);
        }
    }

    public static void syncCoexistingAbilities(Game game, Player player) {
        syncStrainCards(game, player);
        CommanderUnlockCheckService.checkPlayer(player, "bloom");
    }

    public static void snapshotCoexistingPlanetsAtAction(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue(
                    "bloomCoexistingAtAction_" + player.getFaction(),
                    String.join("|", game.getPlanetsPlayerIsCoexistingOn(player)));
        }
    }

    public static boolean wasCoexistingAtStartOfAction(Game game, Player player, String planetName) {
        return Set.of(game.getStoredValue("bloomCoexistingAtAction_" + player.getFaction())
                        .split("\\|"))
                .contains(planetName);
    }

    public static void offerNeurotropicActive(Game game, Player drawer) {
        for (Player owner : game.getRealPlayers()) {
            if (!canUseNeurotropicActive(game, owner, drawer)) {
                continue;
            }

            String drawerName = game.isFowMode() ? "another player" : drawer.getRepresentationNoPing();
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationNoPing() + ", " + drawerName
                            + " drew action cards while you are coexisting on one of their planets. You may use _Neurotropic Active_ to draw 1 action card.",
                    List.of(
                            Buttons.green(
                                    owner.factionButtonChecker() + NEUROTROPIC_DRAW + drawer.getFaction(),
                                    "Draw 1 Action Card"),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    @ButtonHandler(NEUROTROPIC_DRAW)
    public static void drawNeurotropicActionCard(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        Player drawer = game.getPlayerFromColorOrFaction(buttonID.substring(NEUROTROPIC_DRAW.length()));
        if (!canUseNeurotropicActive(game, owner, drawer)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.drawActionCards(owner, 1);
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing()
                        + " drew 1 action card with _Neurotropic Active_ because another player drew action cards while they were coexisting on that player's planet.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerSporefall(Game game, Player resolver, int strategyCard) {
        if (resolver.getSCs().contains(strategyCard)) {
            return;
        }

        for (Player owner : game.getRealPlayers()) {
            if (owner == resolver || !owner.hasAbility("sporefall")) {
                continue;
            }

            List<Button> buttons = getSporefallPlanetButtons(game, owner, resolver);
            if (buttons.isEmpty()) {
                continue;
            }

            NewStuffHelper.sendPaginated(
                    owner.getCardsInfoThread(),
                    getSporefallPrompt(game, owner, resolver),
                    buttons,
                    List.of(Buttons.DECLINE_DELETE_BUTTONS),
                    getSporefallPrefix(owner, resolver));
        }
    }

    @ButtonHandler(SPOREFALL_PLACE)
    public static void placeSporefallInfantry(ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload = buttonID.substring(SPOREFALL_PLACE.length()).split("\\|", 2);
        Player resolver = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;

        if (resolver == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                getSporefallPrompt(game, owner, resolver),
                () -> getSporefallPlanetButtons(game, owner, resolver),
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                getSporefallPrefix(owner, resolver))) {
            return;
        }

        String planetName = payload[1];
        Tile tile = game.getTileFromPlanet(planetName);
        if (!owner.hasAbility("sporefall")
                || tile == null
                || !isEligibleSporefallPlanet(game, owner, resolver, planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, owner.getColor(), "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }

        ButtonHelperAbilities.oceanBoundCheck(game);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        syncCoexistingAbilities(game, owner);
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Sporefall_ after another player resolved a strategy card secondary.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getSporefallPlanetButtons(Game game, Player owner, Player resolver) {
        return resolver.getPlanets().stream()
                .filter(planetName -> isEligibleSporefallPlanet(game, owner, resolver, planetName))
                .sorted()
                .map(planetName -> Buttons.green(
                        owner.factionButtonChecker() + SPOREFALL_PLACE + resolver.getFaction() + "|" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static boolean isEligibleSporefallPlanet(Game game, Player owner, Player resolver, String planetName) {
        Tile targetTile = game.getTileFromPlanet(planetName);
        if (targetTile == null
                || targetTile.isHomeSystem(game)
                || !resolver.getPlanets().contains(planetName)) {
            return false;
        }

        return game.getTileMap().values().stream()
                .filter(tile -> hasGroundForces(owner, tile))
                .map(Tile::getPosition)
                .anyMatch(position ->
                        Adjacency.of(game, owner).forEffects().from(position).contains(targetTile.getPosition()));
    }

    private static boolean hasGroundForces(Player player, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(holder -> holder.getUnitCount(UnitType.Infantry, player) > 0
                        || holder.getUnitCount(UnitType.Mech, player) > 0);
    }

    private static boolean hasTrait(Planet planet, String trait) {
        return planet != null && planet.getPlanetTypes().contains(trait);
    }

    private static boolean canUseNeurotropicActive(Game game, Player owner, Player drawer) {
        return drawer != null
                && owner != drawer
                && owner.hasAbility("neurotropic_active")
                && game.getPlanetsPlayerIsCoexistingOn(owner).stream().anyMatch(drawer.getPlanets()::contains);
    }

    private static void setStrainCardState(Player player, StrainCard strain, long matchingPlanets) {
        String current = player.hasAbility(strain.activeAbility())
                ? strain.activeAbility()
                : player.hasAbility(strain.dormantAbility()) ? strain.dormantAbility() : null;
        String updated =
                matchingPlanets == 1 ? strain.dormantAbility() : matchingPlanets >= 2 ? strain.activeAbility() : null;
        if (java.util.Objects.equals(current, updated)) {
            return;
        }
        removeStrainCard(player, strain);
        if (updated != null) {
            player.addAbility(updated);
        }
        sendStrainCardUpdate(player, current, updated);
    }

    private static void removeStrainCard(Player player, StrainCard strain) {
        player.removeAbility(strain.dormantAbility());
        player.removeAbility(strain.activeAbility());
    }

    private static void sendStrainCardUpdate(Player player, String current, String updated) {
        if (current == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained _" + getStrainName(updated) + "_.");
        } else if (updated == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " removed _" + getStrainName(current) + "_.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " flipped _" + getStrainName(current) + "_ to _"
                            + getStrainName(updated) + "_.");
        }
    }

    private static String getStrainName(String ability) {
        return Mapper.getAbility(ability) == null
                ? ability
                : Mapper.getAbility(ability).getName();
    }

    private static String getSporefallPrefix(Player owner, Player resolver) {
        return owner.factionButtonChecker() + SPOREFALL_PLACE + resolver.getFaction() + "|";
    }

    private static String getSporefallPrompt(Game game, Player owner, Player resolver) {
        String resolverName = game.isFowMode() ? "another player" : resolver.getRepresentationNoPing();
        return owner.getRepresentationNoPing()
                + ", after "
                + resolverName
                + " resolved a strategy card secondary, you may use _Sporefall_ to place 1 infantry into coexistence.";
    }
}
