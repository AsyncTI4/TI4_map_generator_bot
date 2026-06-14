package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AshenAbilityHandler {

    private static final String PHOENIX_RISING = "phoenix_rising";
    private static final String PHOENIX_USE_PREFIX = "ashenPhoenixUse_";
    private static final String PHOENIX_DECLINE_PREFIX = "ashenPhoenixDecline_";
    private static final String CINDERBORN_HIT_PREFIX = "ashenCinderbornHit_";
    private static final String CINDERBORN_NO_HIT_PREFIX = "ashenCinderbornNoHit_";
    private static final String BEAUTY_IN_DESTRUCTION = "beauty_in_destruction";
    private static final String BEAUTY_IN_DESTRUCTION_PREFIX = "ashenBeautyInDestruction_";

    public static boolean offerPhoenixRising(
            Player player, Game game, Tile tile, String planet, Die die, MessageChannel channel) {
        if (player == null
                || game == null
                || tile == null
                || planet == null
                || die == null
                || !player.hasAbility(PHOENIX_RISING)
                || die.getResult() < 9
                || channel == null) {
            return false;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + PHOENIX_USE_PREFIX + tile.getPosition() + "_" + planet,
                        "Use Phoenix Rising",
                        UnitEmojis.infantry),
                Buttons.red(
                        player.factionButtonChecker() + PHOENIX_DECLINE_PREFIX + tile.getPosition() + "_" + planet,
                        "Decline Phoenix Rising"));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentation() + ", you rolled a " + die.getResult()
                        + " for _Cinderborn_, so you may use _Phoenix Rising_ to place that infantry on "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ". If you decline, resolve _Cinderborn_ normally.",
                buttons);
        return true;
    }

    public static void resolveCinderbornRevive(
            Player player, Game game, Tile tile, String planet, MessageChannel channel) {
        if (player == null || game == null || channel == null) {
            return;
        }

        player.setStasisInfantry(player.getStasisInfantry() + 1);

        Player opponent = getGroundCombatOpponent(player, game, planet);
        if (tile == null || planet == null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentation()
                            + " placed 1 infantry in stasis from _Cinderborn_, but there was no valid planet to assign its produced hit.");
            return;
        }

        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentation()
                            + " placed 1 infantry in stasis from _Cinderborn_, but there was no opposing ground unit to assign its produced hit to.");
            return;
        }

        List<Button> hitButtons = new ArrayList<>();
        hitButtons.add(Buttons.green(
                opponent.factionButtonChecker() + "autoAssignGroundHits_" + planet + "_1", "Auto-Assign Hit"));
        hitButtons.addAll(getManualGroundHitButtons(opponent, game, tile, planet));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentation() + ", _Cinderborn_ produced 1 hit on "
                        + Helper.getPlanetRepresentation(planet, game) + ". "
                        + opponent.getRepresentationUnfogged() + ", please assign it.",
                hitButtons);
    }

    public static void offerCinderbornReviveChoice(
            Player player, Game game, Tile tile, String planet, MessageChannel channel) {
        if (player == null || game == null || tile == null || planet == null || channel == null) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + CINDERBORN_HIT_PREFIX + tile.getPosition() + "_" + planet,
                        "Revive and Produce 1 Hit",
                        UnitEmojis.infantry),
                Buttons.blue(
                        player.factionButtonChecker() + CINDERBORN_NO_HIT_PREFIX + tile.getPosition() + "_" + planet,
                        "Revive With No Hit",
                        UnitEmojis.infantry));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentation() + ", _Cinderborn_ succeeded on "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ". Choose whether to revive that infantry and produce 1 hit, or revive it with no hit.",
                buttons);
    }

    public static void resolveCinderbornReviveNoHit(Player player, Game game, String planet, MessageChannel channel) {
        if (player == null || game == null || channel == null) {
            return;
        }

        player.setStasisInfantry(player.getStasisInfantry() + 1);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentation() + " revived 1 infantry from _Cinderborn_ with no produced hit.");
            return;
        }

        MessageHelper.sendMessageToChannel(
                channel,
                player.getRepresentation() + " revived 1 infantry from _Cinderborn_ with no produced hit on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
    }

    public static void offerBeautyInDestruction(
            Game game, Player player, RemovedUnit unit, GenericInteractionCreateEvent event) {
        if (game == null
                || player == null
                || unit == null
                || event == null
                || !player.hasAbility(BEAUTY_IN_DESTRUCTION)
                || player.getStrategicCC() < 1) {
            return;
        }

        String techId = getCorrespondingUpgradeTechId(player, unit);
        if (techId == null || player.hasTech(techId) || Mapper.getTech(techId) == null) {
            return;
        }

        UnitModel unitModel = player.getUnitFromUnitKey(unit.unitKey());
        String techName = Mapper.getTech(techId).getName();
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + BEAUTY_IN_DESTRUCTION_PREFIX + techId,
                        "Spend 1 Strategy Token For " + techName),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", one of your "
                        + (unitModel == null ? unit.unitKey().unitType().humanReadableName() : unitModel.getName())
                        + " units was destroyed during combat. You may spend 1 token from your strategy pool to gain _"
                        + techName + "_. REMINDER: You may only use this ability once per action.",
                buttons);
    }

    @ButtonHandler(PHOENIX_USE_PREFIX)
    public static void resolvePhoenixRisingUse(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_", 3);
        String tilePos = parts[1];
        String planet = parts[2];

        Tile tile = game.getTileByPosition(tilePos);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "inf " + planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used _Phoenix Rising_ to place 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PHOENIX_DECLINE_PREFIX)
    public static void resolvePhoenixRisingDecline(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_", 3);
        String tilePos = parts[1];
        String planet = parts[2];

        offerCinderbornReviveChoice(player, game, game.getTileByPosition(tilePos), planet, event.getMessageChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CINDERBORN_HIT_PREFIX)
    public static void resolveCinderbornReviveWithHit(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_", 3);
        String tilePos = parts[1];
        String planet = parts[2];

        resolveCinderbornRevive(player, game, game.getTileByPosition(tilePos), planet, event.getMessageChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CINDERBORN_NO_HIT_PREFIX)
    public static void resolveCinderbornReviveWithoutHit(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_", 3);
        String planet = parts[2];

        resolveCinderbornReviveNoHit(player, game, planet, event.getMessageChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(BEAUTY_IN_DESTRUCTION_PREFIX)
    public static void resolveBeautyInDestruction(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String techId = buttonID.substring(BEAUTY_IN_DESTRUCTION_PREFIX.length());
        if (game == null
                || player == null
                || !player.hasAbility(BEAUTY_IN_DESTRUCTION)
                || player.getStrategicCC() < 1
                || player.hasTech(techId)
                || Mapper.getTech(techId) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + " spent 1 command token from their strategy pool to resolve _Beauty in Destruction_.");
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "used _Beauty in Destruction_");
        if (player.hasLeader("ashencommander") && !player.hasLeaderUnlocked("ashencommander")) {
            UnlockLeaderService.unlockLeader("ashencommander", game, player);
        }
        PlayerTechService.addTech(event, game, player, techId);
        ButtonHelper.deleteMessage(event);
    }

    private static String getCorrespondingUpgradeTechId(Player player, RemovedUnit unit) {
        UnitModel currentModel = player.getUnitByType(unit.unitKey().unitType());
        if (currentModel == null) {
            return null;
        }
        if (currentModel.getRequiredTechId().isPresent()) {
            return currentModel.getRequiredTechId().get();
        }
        if (currentModel.getUpgradesToUnitId().isEmpty()) {
            return null;
        }

        UnitModel upgradeModel =
                Mapper.getUnit(currentModel.getUpgradesToUnitId().get());
        return upgradeModel == null ? null : upgradeModel.getRequiredTechId().orElse(null);
    }

    private static Player getGroundCombatOpponent(Player player, Game game, String planet) {
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
        if (unitHolder == null) {
            return null;
        }

        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (player.unitBelongsToPlayer(unitKey)) {
                continue;
            }
            Player opponent = game.getPlayerFromColorOrFaction(unitKey.getColor());
            if (opponent != null && (unitKey.unitType() == UnitType.Infantry || unitKey.unitType() == UnitType.Mech)) {
                return opponent;
            }
        }
        return null;
    }

    private static List<Button> getManualGroundHitButtons(Player player, Game game, Tile tile, String planet) {
        List<Button> buttons = new ArrayList<>();
        UnitHolder unitHolder = game.getUnitHolderFromPlanet(planet);
        if (unitHolder == null) {
            return buttons;
        }

        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            if (!player.unitBelongsToPlayer(unitKey)) {
                continue;
            }

            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            boolean canSustain = ButtonHelper.unitCanSustainDamage(game, player, tile, unitModel);
            for (UnitState state : UnitState.defaultRemoveOrder()) {
                int amount = unitHolder.getUnitCountForState(unitKey, state);
                if (amount == 0) {
                    continue;
                }

                boolean sustain = canSustain && !state.isDamaged();
                String unitName = unitKey.unitName();
                if (state.isDamaged()) {
                    unitName += "damaged";
                }
                if (state.isGalvanized()) {
                    unitName += "galvanized";
                }

                String buttonID = player.factionButtonChecker() + "hitOpponentGround_" + planet + "_" + unitName + "_"
                        + unitKey.getColor() + "_ashen";
                String label =
                        (sustain ? "Damage 1 " : "Destroy 1 ") + state.humanDescr() + " " + unitKey.humanReadableName();
                buttons.add(Buttons.red(buttonID, label, unitKey.unitEmoji()));
            }
        }
        return buttons;
    }
}
