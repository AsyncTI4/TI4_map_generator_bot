package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class IronFactionTechsHandler {

    public static final String ADVANCED_TARGETING_SYSTEMS_TECH = "beironats";
    private static final String ADVANCED_TARGETING_SYSTEMS_BUTTON_PREFIX = "ironATS_";
    private static final String ADVANCED_TARGETING_SYSTEMS_BUTTON_SEPARATOR = ";";
    private static final String ATS_TILE_KEY = "ironATSActiveTile_";
    private static final String ATS_OPPONENT_KEY = "ironATSActiveOpponent_";
    private static final String ATS_HOLDER_KEY = "ironATSBoundHolder_";

    public static void addAdvancedTargetingSystemsButton(
            List<Button> buttons, Game game, Player player, Player opponent, String pos, String groundOrSpace) {
        if (!"ground".equalsIgnoreCase(groundOrSpace) || game == null) {
            return;
        }
        Tile tile = game.getTileByPosition(pos);
        addButtonIfEligible(buttons, game, player, opponent, tile);
    }

    @ButtonHandler(ADVANCED_TARGETING_SYSTEMS_BUTTON_PREFIX)
    public static void useAdvancedTargetingSystems(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(ADVANCED_TARGETING_SYSTEMS_BUTTON_PREFIX, "")
                .split(ADVANCED_TARGETING_SYSTEMS_BUTTON_SEPARATOR, 2);
        if (parts.length < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Unable to resolve Advanced Targeting Systems here.");
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        Player opponent = game.getPlayerFromColorOrFaction(parts[1]);
        if (!canUseAdvancedTargetingSystems(game, player, opponent, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "Advanced Targeting Systems is no longer available here. It can only be used before the first ground combat roll in this system while you have a participating mech.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        player.exhaustTech(ADVANCED_TARGETING_SYSTEMS_TECH);
        armAdvancedTargetingSystems(game, player, opponent, tile);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.toString()
                        + " exhausted _Advanced Targeting Systems_. Their mechs will roll 1 additional combat die in whichever ground combat roll in this system happens first.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static List<NamedCombatModifierModel> getAdvancedTargetingSystemsExtraRollModifier(
            Game game,
            Player rollingPlayer,
            Player opposingPlayer,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType) {
        if (game == null
                || rollingPlayer == null
                || opposingPlayer == null
                || tile == null
                || combatOnHolder == null
                || rollType != CombatRollType.combatround
                || Constants.SPACE.equalsIgnoreCase(combatOnHolder.getName())) {
            return List.of();
        }

        for (Player atsOwner : List.of(rollingPlayer, opposingPlayer)) {
            if (!hasArmedAdvancedTargetingSystems(game, atsOwner)) {
                continue;
            }
            Player atsOpponent = atsOwner == rollingPlayer ? opposingPlayer : rollingPlayer;
            if (!tile.getPosition().equals(game.getStoredValue(ATS_TILE_KEY + atsOwner.getFaction()))
                    || !atsOpponent
                            .getFaction()
                            .equals(game.getStoredValue(ATS_OPPONENT_KEY + atsOwner.getFaction()))) {
                continue;
            }

            String boundHolder = game.getStoredValue(ATS_HOLDER_KEY + atsOwner.getFaction());
            if (boundHolder.isEmpty() && isContestedPlanet(game, tile, combatOnHolder, atsOwner, atsOpponent)) {
                game.setStoredValue(ATS_HOLDER_KEY + atsOwner.getFaction(), combatOnHolder.getName());
                boundHolder = combatOnHolder.getName();
            }
            if (!combatOnHolder.getName().equals(boundHolder) || rollingPlayer != atsOwner) {
                continue;
            }
            if (!getCombatRound(game, atsOwner, tile, combatOnHolder.getName()).isEmpty()) {
                continue;
            }

            clearAdvancedTargetingSystems(game, atsOwner);
            return List.of(new NamedCombatModifierModel(
                    getAdvancedTargetingSystemsCombatModifier(), "_Advanced Targeting Systems_"));
        }
        return List.of();
    }

    private static void addButtonIfEligible(
            List<Button> buttons, Game game, Player player, Player opponent, Tile tile) {
        if (!canUseAdvancedTargetingSystems(game, player, opponent, tile)) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker()
                        + ADVANCED_TARGETING_SYSTEMS_BUTTON_PREFIX
                        + String.join(
                                ADVANCED_TARGETING_SYSTEMS_BUTTON_SEPARATOR, tile.getPosition(), opponent.getFaction()),
                "Use ATS",
                FactionEmojis.iron));
    }

    private static boolean canUseAdvancedTargetingSystems(Game game, Player player, Player opponent, Tile tile) {
        if (game == null
                || player == null
                || opponent == null
                || tile == null
                || !player.hasTechReady(ADVANCED_TARGETING_SYSTEMS_TECH)) {
            return false;
        }
        if (!hasParticipatingMech(tile, game, player, opponent)) {
            return false;
        }
        return isBeforeFirstGroundCombatRoll(game, player, opponent, tile);
    }

    private static boolean hasParticipatingMech(Tile tile, Game game, Player player, Player opponent) {
        return getContestedPlanetUnitHolders(tile, game, player, opponent).stream()
                .anyMatch(unitHolder -> unitHolder.hasUnit(UnitType.Mech, player));
    }

    private static boolean isBeforeFirstGroundCombatRoll(Game game, Player player, Player opponent, Tile tile) {
        return getContestedPlanetUnitHolders(tile, game, player, opponent).stream()
                .allMatch(unitHolder ->
                        getCombatRound(game, player, tile, unitHolder.getName()).isEmpty()
                                && getCombatRound(game, opponent, tile, unitHolder.getName())
                                        .isEmpty());
    }

    private static List<UnitHolder> getContestedPlanetUnitHolders(
            Tile tile, Game game, Player player, Player opponent) {
        List<UnitHolder> contestedPlanets = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
            List<Player> playersWithUnits = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, unitHolder);
            if (playersWithUnits.contains(player) && playersWithUnits.contains(opponent)) {
                contestedPlanets.add(unitHolder);
            }
        }
        return contestedPlanets;
    }

    private static String getCombatRound(Game game, Player player, Tile tile, String unitHolderName) {
        return game.getStoredValue("combatRoundTracker" + player.getFaction() + tile.getPosition() + unitHolderName);
    }

    private static void armAdvancedTargetingSystems(Game game, Player player, Player opponent, Tile tile) {
        game.setStoredValue(ATS_TILE_KEY + player.getFaction(), tile.getPosition());
        game.setStoredValue(ATS_OPPONENT_KEY + player.getFaction(), opponent.getFaction());
        game.removeStoredValue(ATS_HOLDER_KEY + player.getFaction());
    }

    private static boolean hasArmedAdvancedTargetingSystems(Game game, Player player) {
        return game != null
                && player != null
                && !game.getStoredValue(ATS_TILE_KEY + player.getFaction()).isEmpty()
                && !game.getStoredValue(ATS_OPPONENT_KEY + player.getFaction()).isEmpty();
    }

    private static void clearAdvancedTargetingSystems(Game game, Player player) {
        game.removeStoredValue(ATS_TILE_KEY + player.getFaction());
        game.removeStoredValue(ATS_OPPONENT_KEY + player.getFaction());
        game.removeStoredValue(ATS_HOLDER_KEY + player.getFaction());
    }

    private static boolean isContestedPlanet(
            Game game, Tile tile, UnitHolder combatOnHolder, Player player, Player opponent) {
        if (combatOnHolder == null || Constants.SPACE.equalsIgnoreCase(combatOnHolder.getName())) {
            return false;
        }
        List<Player> playersWithUnits = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, combatOnHolder.getName());
        return playersWithUnits.contains(player) && playersWithUnits.contains(opponent);
    }

    private static CombatModifierModel getAdvancedTargetingSystemsCombatModifier() {
        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("iron_advanced_targeting_systems");
        modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
        modifier.setValue(1);
        modifier.setApplyEachForQuantity(true);
        modifier.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
        modifier.setScope("mf");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(CombatRollType.combatround);
        return modifier;
    }
}
