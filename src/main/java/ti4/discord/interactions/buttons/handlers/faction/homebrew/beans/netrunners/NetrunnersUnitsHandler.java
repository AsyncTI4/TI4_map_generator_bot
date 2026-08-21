package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

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
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersUnitsHandler {
    public static final String MECH_ID = "netrunners_mech";
    private static final String TROJAN_ABILITY = "netrunnersTrojanAbility";

    public static void offerTrojan(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !player.hasUnit("netrunners_flagship")
                || !ButtonHelper.doesPlayerHaveFSHere("netrunners_flagship", player, tile)) {
            return;
        }
        List<String> abilities = tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeys().stream())
                .map(key -> {
                    Player owner = game.getPlayerFromColorOrFaction(key.getColor());
                    return owner == null ? null : owner.getUnitFromUnitKey(key);
                })
                .filter(java.util.Objects::nonNull)
                .map(UnitModel::getAbility)
                .flatMap(java.util.Optional::stream)
                .filter(ability -> !ability.toLowerCase().contains("sustain damage"))
                .distinct()
                .limit(24)
                .toList();
        List<Button> buttons = abilities.stream()
                .map(ability -> Buttons.gray(
                        player.factionButtonChecker() + "trojanAbility_" + tile.getPosition() + "_"
                                + Integer.toHexString(ability.hashCode()),
                        ability,
                        FactionEmojis.netrunners))
                .toList();
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", please choose a unit ability for the Trojan (the Netrunners flagship) to suppress until the end of your turn.",
                    buttons);
        }
    }

    @ButtonHandler("trojanAbility_")
    public static void resolveTrojan(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("trojanAbility_", "").split("_", 2);
        if (parts.length != 2) {
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        if (tile == null
                || !player.hasUnit("netrunners_flagship")
                || !ButtonHelper.doesPlayerHaveFSHere("netrunners_flagship", player, tile)) {
            return;
        }
        String ability = tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeys().stream())
                .map(key -> {
                    Player owner = game.getPlayerFromColorOrFaction(key.getColor());
                    return owner == null ? null : owner.getUnitFromUnitKey(key);
                })
                .filter(java.util.Objects::nonNull)
                .map(UnitModel::getAbility)
                .flatMap(java.util.Optional::stream)
                .filter(candidate -> !candidate.toLowerCase().contains("sustain damage"))
                .distinct()
                .filter(candidate -> Integer.toHexString(candidate.hashCode()).equals(parts[1]))
                .findFirst()
                .orElse(null);
        if (ability == null) {
            return;
        }
        game.setStoredValue(TROJAN_ABILITY + player.getFaction(), tile.getPosition() + "|" + ability);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used the Trojan (the Netrunners flagship) to suppress "
                        + ability.toUpperCase() + " in " + tile.getRepresentationForButtons(game, player)
                        + " until the end of their turn.");
    }

    public static boolean isTrojanAbilitySuppressed(Game game, Tile tile, UnitModel unit) {
        if (game == null || tile == null || unit == null || unit.getAbility().isEmpty()) return false;
        return game.getRealPlayers().stream()
                .filter(player -> ButtonHelper.doesPlayerHaveFSHere("netrunners_flagship", player, tile))
                .anyMatch(
                        player -> (tile.getPosition() + "|" + unit.getAbility().get())
                                .equals(game.getStoredValue(TROJAN_ABILITY + player.getFaction())));
    }

    public static boolean isTrojanAbilitySuppressed(Game game, TileModel tile, UnitModel unit) {
        if (game == null || tile == null || unit == null || unit.getAbility().isEmpty()) return false;
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        return activeSystem != null
                && activeSystem.getTileModel().getAlias().equals(tile.getAlias())
                && isTrojanAbilitySuppressed(game, activeSystem, unit);
    }

    public static void clearTrojan(Game game, Player player) {
        if (game != null && player != null) game.removeStoredValue(TROJAN_ABILITY + player.getFaction());
    }

    public static void offerLegionDeploy(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        List<Button> buttons = player.getPlanets().stream()
                .filter(planet -> game.getTileFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "netrunnersLegion_" + planet,
                        "Deploy Mech to " + Helper.getPlanetRepresentation(planet, game),
                        FactionEmojis.netrunners))
                .toList();
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you gained a technology. You may place 1 mech from your reinforcements on a planet you control via a Legion (Netrunners mech).",
                buttons);
    }

    @ButtonHandler("netrunnersLegion_")
    public static void resolveLegionDeploy(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("netrunnersLegion_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !player.getPlanets().contains(planet)
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " deployed 1 mech to " + Helper.getPlanetRepresentation(planet, game)
                        + " via a Legion (Netrunners mech).");
    }
}
