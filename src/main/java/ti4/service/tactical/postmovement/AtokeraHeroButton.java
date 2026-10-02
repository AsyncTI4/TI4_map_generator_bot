package ti4.service.tactical.postmovement;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.PurgeHeroService;
import ti4.service.tactical.PostMovementAbilityButton;
import ti4.service.tactical.PostMovementButtonContext;
import ti4.service.tactical.TacticalActionService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

public final class AtokeraHeroButton implements PostMovementAbilityButton {
    private static final String ACTIVE_HERO_KEY = "atokeraHeroActive";

    public boolean enabled(PostMovementButtonContext ctx) {
        return ctx.player().hasLeaderUnlocked("atokerahero")
                && !ctx.tile().getPlanetUnitHolders().isEmpty();
    }

    public List<Button> build(PostMovementButtonContext ctx) {
        return List.of(Buttons.blue(
                ctx.player().factionButtonChecker() + "purgeAtokeraHero", "Use Atokera Hero", FactionEmojis.atokera));
    }

    public static void useHero(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find the active system.");
            return;
        }
        PurgeHeroService.purgeHeroPreamble(event, player, game, "atokerahero", "Kapoko Vui, the Atokera hero");
        game.setStoredValue(ACTIVE_HERO_KEY + player.getFaction(), tile.getPosition());
        MessageHelper.editMessageButtons(event, TacticalActionService.getLandingTroopsButtons(game, player, tile));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " may now commit non-fighter ships from the active system.");
    }

    public static List<UnitType> getCommittableShipTypes(Game game, Player player, Tile tile, UnitHolder space) {
        if (!tile.getPosition().equals(game.getStoredValue(ACTIVE_HERO_KEY + player.getFaction()))) {
            return List.of();
        }
        return space.getUnitKeysForPlayer(player).stream()
                .filter(unitKey -> isNonFighterShip(player, unitKey))
                .map(UnitKey::unitType)
                .distinct()
                .toList();
    }

    public static void returnCommittedShips(GenericInteractionCreateEvent event, Game game, Player player) {
        String tilePosition = game.getStoredValue(ACTIVE_HERO_KEY + player.getFaction());
        if (tilePosition.isEmpty()) {
            return;
        }
        game.removeStoredValue(ACTIVE_HERO_KEY + player.getFaction());
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            return;
        }

        List<RemovedUnit> removedShips = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            for (UnitKey unitKey : planet.getUnitKeysForPlayer(player)) {
                if (!isNonFighterShip(player, unitKey)) {
                    continue;
                }
                removedShips.addAll(RemoveUnitService.removeUnit(
                        event, tile, game, player, planet, unitKey.unitType(), planet.getUnitCount(unitKey)));
            }
        }
        if (removedShips.isEmpty()) {
            return;
        }

        UnitHolder space = tile.getSpaceUnitHolder();
        AddUnitService.addUnits(
                event,
                game,
                removedShips.stream().map(unit -> unit.onUnitHolder(space)).toList());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " returned their Atokera hero ships to the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }

    private static boolean isNonFighterShip(Player player, UnitKey unitKey) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        return unit != null && unit.isNonFighterShip();
    }
}
