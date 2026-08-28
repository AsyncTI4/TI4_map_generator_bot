package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.ExploreModel;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TaAbilityHandler {

    private static final String PLANETARY_RECONFIGURATION = "planetary_reconfiguration";
    private static final String PERFECT_ARCHITECTURE = "perfect_architecture";
    private static final String PERFECT_ARCHITECTURE_RESOLVE = "taPerfectArchitecture_";
    private static final String ATTACH_DESIGN_PREFIX = "taAttachDesign_";
    private static final List<String> ATTACHABLE_DESIGNS =
            List.of("designtranspose", "designprestige", "designabundance");

    public static void sendPlanetaryReconfigurationStatus(Player player, Game game) {
        if (player == null
                || game == null
                || player.getCardsInfoThread() == null
                || !player.hasAbility(PLANETARY_RECONFIGURATION)) {
            return;
        }

        Map<String, String> attachedDesigns = getAttachedDesigns(game);
        StringBuilder message =
                new StringBuilder().append("__**Planetary Reconfiguration**__").append("\n__Attached to planets:__");

        boolean hasAttachedDesigns = false;
        for (String design : ATTACHABLE_DESIGNS) {
            String planet = attachedDesigns.get(design);
            if (planet == null) {
                continue;
            }
            hasAttachedDesigns = true;
            message.append("\n- ")
                    .append(getDesignName(design))
                    .append(": ")
                    .append(Helper.getPlanetRepresentation(planet, game));
        }
        if (!hasAttachedDesigns) {
            message.append("\n- None");
        }

        message.append("\n__In reinforcements:__");
        boolean hasUnattachedDesigns = false;
        for (String design : ATTACHABLE_DESIGNS) {
            if (attachedDesigns.containsKey(design)) {
                continue;
            }
            hasUnattachedDesigns = true;
            message.append("\n- ").append(getDesignName(design));
        }
        if (!hasUnattachedDesigns) {
            message.append("\n- None");
        }

        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message.toString());
    }

    public static int getControlledPlanetCountWithAnyDesign(Player player, Game game) {
        if (player == null || game == null) {
            return 0;
        }

        int count = 0;
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile != null && planetHasAnyDesignAttached(tile, planetName)) {
                count++;
            }
        }
        return count;
    }

    public static void offerPlanetaryReconfigurationButtons(Player player, Game game, Tile tile, String planetName) {
        if (player == null
                || game == null
                || tile == null
                || planetName == null
                || planetName.isBlank()
                || !player.hasAbility(PLANETARY_RECONFIGURATION)
                || !player.getPlanetsAllianceMode().contains(planetName)
                || planetHasAnyDesignAttached(tile, planetName)) {
            return;
        }

        List<String> availableDesigns = getAvailableDesigns(game);
        if (availableDesigns.isEmpty()) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String design : availableDesigns) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + ATTACH_DESIGN_PREFIX + tile.getPosition() + "|" + planetName + "|"
                            + design,
                    "Attach " + getDesignName(design)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        String message = player.getRepresentationUnfogged()
                + ", you may attach 1 design from your reinforcements to "
                + Helper.getPlanetRepresentation(planetName, game) + ".";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static boolean offerPerfectArchitecture(
            GenericInteractionCreateEvent event,
            Player player,
            Game game,
            Tile tile,
            String planetName,
            String firstCard,
            String exploreType) {
        if (event == null
                || player == null
                || game == null
                || tile == null
                || !player.hasAbility(PERFECT_ARCHITECTURE)) {
            return false;
        }
        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        boolean hasStructure = planet != null
                && planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && unit.getIsStructure());
        if (planet == null || (!planetHasAnyAttachment(tile, planetName) && !hasStructure)) {
            return false;
        }
        String secondCard = game.drawExplore(exploreType);
        if (secondCard == null) {
            return false;
        }
        ExploreModel firstExplore = Mapper.getExplore(firstCard);
        ExploreModel secondExplore = Mapper.getExplore(secondCard);
        if (firstExplore == null || secondExplore == null) {
            game.discardExplore(firstCard);
            if (secondExplore != null) game.discardExplore(secondCard);
            return true;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the exploration card to resolve with **Perfect Architecture**.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + PERFECT_ARCHITECTURE_RESOLVE + firstCard + "|"
                                        + secondCard + "|" + planetName,
                                "Resolve " + firstExplore.getName()),
                        Buttons.green(
                                player.factionButtonChecker() + PERFECT_ARCHITECTURE_RESOLVE + secondCard + "|"
                                        + firstCard + "|" + planetName,
                                "Resolve " + secondExplore.getName())));
        return true;
    }

    @ButtonHandler(PERFECT_ARCHITECTURE_RESOLVE)
    public static void resolvePerfectArchitectureExplore(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String[] parts =
                buttonID.substring(PERFECT_ARCHITECTURE_RESOLVE.length()).split("\\|", 3);
        if (event == null
                || player == null
                || game == null
                || parts.length != 3
                || !player.hasAbility(PERFECT_ARCHITECTURE)) {
            return;
        }
        Tile tile = game.getTileFromPlanet(parts[2]);
        ExploreModel explore = Mapper.getExplore(parts[0]);
        if (tile == null || explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.discardExplore(parts[1]);
        ExploreService.resolveExplore(
                event,
                parts[0],
                tile,
                parts[2],
                player.getRepresentationNoPing() + " resolved _" + explore.getName()
                        + " with **Perfect Architecture**.",
                player,
                game);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "tacommander")) {
            TaLeadersHandler.resolveTaCommander(player, tile, game, parts[2]);
        }
        if (player.getPlanets().contains(parts[2])) {
            offerPlanetaryReconfigurationButtons(player, game, tile, parts[2]);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void returnPlanetaryReconfigurationDesigns(Player player, Game game, Planet planet) {
        if (player == null || game == null || planet == null || !player.hasAbility(PLANETARY_RECONFIGURATION)) {
            return;
        }

        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) {
            return;
        }

        for (String token : new ArrayList<>(planet.getTokenList())) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment == null) {
                continue;
            }
            if (ATTACHABLE_DESIGNS.contains(attachment.getAlias())) {
                tile.removeToken(token, planet.getName());
            }
        }
    }

    @ButtonHandler(ATTACH_DESIGN_PREFIX)
    public static void attachPlanetaryReconfigurationDesign(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null || !player.hasAbility(PLANETARY_RECONFIGURATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(ATTACH_DESIGN_PREFIX.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planetName = parts[1];
        String design = parts[2];
        String tokenPath = Mapper.getAttachmentImagePath(design);
        if (tile == null
                || tokenPath == null
                || !ATTACHABLE_DESIGNS.contains(design)
                || !player.getPlanetsAllianceMode().contains(planetName)
                || planetHasAnyDesignAttached(tile, planetName)
                || !getAvailableDesigns(game).contains(design)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        tile.addToken(tokenPath, planetName);
        TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetName);
        CommanderUnlockCheckService.checkPlayer(player, "ta");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " attached _" + getDesignName(design) + "_ to "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static Map<String, String> getAttachedDesigns(Game game) {
        Map<String, String> attachedDesigns = new LinkedHashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                for (String token : planet.getTokenList()) {
                    AttachmentModel attachment = Mapper.getAttachmentInfo(token);
                    if (attachment == null) {
                        continue;
                    }
                    String alias = attachment.getAlias();
                    if (ATTACHABLE_DESIGNS.contains(alias)) {
                        attachedDesigns.putIfAbsent(alias, planet.getName());
                    }
                }
            }
        }
        return attachedDesigns;
    }

    private static List<String> getAvailableDesigns(Game game) {
        Map<String, String> attachedDesigns = getAttachedDesigns(game);
        return ATTACHABLE_DESIGNS.stream()
                .filter(design -> !attachedDesigns.containsKey(design))
                .toList();
    }

    public static boolean planetHasAnyDesignAttached(Tile tile, String planetName) {
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return false;
        }

        for (String token : planet.getTokenList()) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment != null && attachment.getAlias().startsWith("design")) {
                return true;
            }
        }
        return false;
    }

    public static boolean planetHasAnyAttachment(Tile tile, String planetName) {
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        return planet != null
                && planet.getTokenList().stream().anyMatch(token -> Mapper.getAttachmentInfo(token) != null);
    }

    public static boolean planetHasGrandDesignAttached(Tile tile, String planetName) {
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return false;
        }

        for (String token : planet.getTokenList()) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment != null && "designgrand".equals(attachment.getAlias())) {
                return true;
            }
        }
        return false;
    }

    private static String getDesignName(String design) {
        AttachmentModel attachment = Mapper.getAttachmentInfo(design);
        if (attachment == null) {
            return design;
        }
        return attachment.getName().replace(" (Design)", "");
    }

    public static void resolveGrandDesign(Player player, Game game, String planetName) {
        if (game == null || player == null) {
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || tile == null || !player.hasAbility(PLANETARY_RECONFIGURATION)) {
            return;
        }

        if (planetHasGrandDesignAttached(tile, planetName)) {
            List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may explore " + Helper.getPlanetRepresentation(planetName, game)
                            + ".",
                    buttons);
        }
    }
}
