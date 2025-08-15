package ti4.service.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.tactical.movement.MoveAbilities;
import ti4.service.tactical.movement.MoveAbility;
import ti4.service.tactical.movement.MoveContext;
import ti4.service.tactical.planet.LandingContext;
import ti4.service.tactical.planet.PlanetAbilities;
import ti4.service.tactical.postmovement.PostMovementAbilities;
import ti4.service.tactical.postmovement.PostMovementButtonAbility;
import ti4.service.tactical.postmovement.PostMovementButtonContext;

@UtilityClass
public class TacticalActionService {

    public void reverseAllUnitMovement(ButtonInteractionEvent event, Game game, Player player) {
        boolean unkEncountered = TacticalActionDisplacementService.hasUnknownDisplacement(game);
        TacticalActionDisplacementService.reverseAllUnitMovement(game, player);

        // Handle output
        MessageHelper.sendMessageToEventChannel(
                event, player.fogSafeEmoji() + " put all units back where they started.");
        if (unkEncountered)
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.fogSafeEmoji()
                            + " some units could not be put back... You can put them back manually after movement.");
        TacticalActionOutputService.refreshButtonsAndMessageForChoosingTile(event, game, player);
    }

    public void reverseTileUnitMovement(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        reverseTileUnitMovement(event, game, player, tile, moveOrRemove, false);
    }

    public void reverseTileUnitMovement(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            String moveOrRemove,
            boolean skipOutput) {
        TacticalActionDisplacementService.reverseTileUnitMovement(game, player, tile);
        if (skipOutput) return;
        refreshTileUI(event, game, player, tile, moveOrRemove);
    }

    public void moveAllFromTile(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        TacticalActionDisplacementService.moveAllFromTile(game, player, tile);
        refreshTileUI(event, game, player, tile, moveOrRemove);
    }

    public void moveAllShipsFromTile(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        TacticalActionDisplacementService.moveAllShipsFromTile(game, player, tile);
        refreshTileUI(event, game, player, tile, moveOrRemove);
    }

    public void moveSingleUnit(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            String planetName,
            UnitType type,
            int amt,
            UnitState state,
            String moveOrRemove,
            String color) {
        TacticalActionDisplacementService.moveSingleUnit(game, player, tile, planetName, type, amt, state, color);

        // refresh buttons
        refreshTileUI(event, game, player, tile, moveOrRemove);
    }

    public void reverseSingleUnit(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Tile tile,
            String planetName,
            UnitType type,
            int amt,
            UnitState state,
            String moveOrRemove,
            String color) {
        TacticalActionDisplacementService.reverseSingleUnit(game, player, tile, planetName, type, amt, state, color);

        // refresh buttons
        refreshTileUI(event, game, player, tile, moveOrRemove);
    }

    public boolean spendAndPlaceTokenIfNecessary(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        boolean skipPlacingAbilities = shouldSkipPlacingAbilities(game, player);
        if (!skipPlacingAbilities
                && !CommandCounterHelper.hasCC(event, player.getColor(), tile)
                && game.getStoredValue("vaylerianHeroActive").isEmpty()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                player.setTacticalCC(player.getTacticalCC() + 1);
            }
            player.setTacticalCC(player.getTacticalCC() - 1);
            CommandCounterHelper.addCC(event, player, tile);
            return true;
        }
        return false;
    }

    public boolean moveUnitsIntoActiveSystem(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        // Flip mallice
        if (TacticalActionDisplacementService.hasPendingDisplacement(game)) {
            tile = FlipTileService.flipTileIfNeeded(event, tile, game);
            if (tile == null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Failed to flip the Wormhole Nexus. Please yell at Jazzxhands");
                return false;
            }
        }

        return TacticalActionDisplacementService.applyDisplacementToActiveSystem(game, player, tile);
    }

    public void finishMovement(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        // Pre-check: The Void
        if (FOWPlusService.isVoid(game, tile.getPosition())) {
            FOWPlusService.resolveVoidActivation(player, game);
            Button conclude = Buttons.red(player.finChecker() + "doneWithTacticalAction", "Conclude Tactical Action");
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(), "All units were lost to The Void.", conclude);
            ButtonHelper.deleteAllButtons(event);
            return;
        }

        // Core logic block: movement, token placement, after-move effects, and state flags
        FinishMovementContext ctx = executeCoreFinishMovement(event, game, player, tile);

        // UI/message block: build message and buttons
        String message = buildFinishMovementMessage(game, player, ctx);
        List<Button> systemButtons = buildFinishMovementButtons(event, game, player, ctx);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);

        // Post-core triggers
        CommanderUnlockCheckService.checkPlayer(player, "naaz", "empyrean", "ghost");
        CommanderUnlockCheckService.checkPlayer(player, "nivyn", "ghoti", "zelian", "gledge", "mortheus");
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "empyrean");

        if (!game.isL1Hero() && !ctx.playersWithPds2.isEmpty()) {
            ButtonHelperTacticalAction.tacticalActionSpaceCannonOffenceStep(
                    game, player, ctx.playersWithPds2, ctx.tile);
        }
        StartCombatService.combatCheck(game, event, ctx.tile);
        ButtonHelper.deleteAllButtons(event);
    }

    private static final class FinishMovementContext {
        final Tile tile;
        final boolean unitsWereMoved;
        final boolean hasGfsInRange;
        final List<Player> playersWithPds2;

        FinishMovementContext(Tile tile, boolean unitsWereMoved, boolean hasGfsInRange, List<Player> playersWithPds2) {
            this.tile = tile;
            this.unitsWereMoved = unitsWereMoved;
            this.hasGfsInRange = hasGfsInRange;
            this.playersWithPds2 = playersWithPds2;
        }
    }

    private FinishMovementContext executeCoreFinishMovement(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, tile.getPosition());

        boolean unitsWereMoved = moveUnitsIntoActiveSystem(event, game, player, tile);
        Tile updatedTile = game.getTileByPosition(tile.getPosition());
        spendAndPlaceTokenIfNecessary(event, game, player, updatedTile);

        boolean hasGfsInRange = game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
                || updatedTile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) > 0
                || updatedTile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player) > 0;

        if (unitsWereMoved) {
            ButtonHelperTacticalAction.resolveAfterMovementEffects(event, game, player, updatedTile, unitsWereMoved);
            game.setStoredValue(
                    "currentActionSummary" + player.getFaction(),
                    game.getStoredValue("currentActionSummary" + player.getFaction()) + " Moved ships there.");
        } else if (!hasGfsInRange) {
            game.setStoredValue(
                    "currentActionSummary" + player.getFaction(),
                    game.getStoredValue("currentActionSummary" + player.getFaction()) + " Did not move units.");
        }

        return new FinishMovementContext(updatedTile, unitsWereMoved, hasGfsInRange, playersWithPds2);
    }

    private String buildFinishMovementMessage(Game game, Player player, FinishMovementContext ctx) {
        if (!ctx.unitsWereMoved && !ctx.hasGfsInRange) {
            return "Nothing moved. Use buttons to decide if you wish to build (if you can), or finish the activation.";
        }
        return "Moved all units to the space area.";
    }

    private List<Button> buildFinishMovementButtons(
            ButtonInteractionEvent event, Game game, Player player, FinishMovementContext ctx) {
        List<Button> systemButtons;
        if (!ctx.unitsWereMoved && !ctx.hasGfsInRange) {
            systemButtons = getBuildButtons(event, game, player, ctx.tile);
        } else {
            systemButtons = getLandingTroopsButtons(game, player, ctx.tile);
        }

        int landingButtons = 1;
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) landingButtons = 2;
        if (systemButtons.size() == landingButtons || game.isL1Hero()) {
            systemButtons = getBuildButtons(event, game, player, ctx.tile);
        }
        return systemButtons;
    }

    public List<Button> getLandingTroopsButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = getLandingUnitsButtons(game, player, tile);
        if (game.isNaaluAgent() || player == game.getActivePlayer()) {
            buttons.add(Buttons.red(player.finChecker() + "doneLanding_" + tile.getPosition(), "Done Landing Troops"));
        } else {
            buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Done Resolving"));
        }
        return buttons;
    }

    public List<Button> getBuildButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();

        int productionVal = Helper.getProductionValue(player, game, tile, false);
        if (productionVal > 0) {
            buttons.add(createBuildButton(player, tile, productionVal));
        }
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            buttons.add(Buttons.green(
                    player.finChecker() + "getRiftButtons_" + tile.getPosition(),
                    "Units Travelled Through Gravity Rift",
                    MiscEmojis.GravityRift));
        }
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperAgents.getSardakkAgentButtons(game));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
        }
        if (player.hasAbility("shroud_of_lith")
                && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game)
                                .size()
                        > 1) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc));
        }
        buttons.add(Buttons.red(player.finChecker() + "doneWithTacticalAction", "Conclude Tactical Action"));
        return buttons;
    }

    public static List<Button> getTilesToMoveFrom(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();

        // Tile selection buttons
        buttons.addAll(buildMoveFromTileSelectionButtons(player, game, event));

        // Ability contributions
        MoveContext pubCtx = new MoveContext(player, game, event);
        for (MoveAbility ability : MoveAbilities.ABILITIES) {
            if (ability.enabled(pubCtx)) ability.contribute(pubCtx, buttons);
        }

        // Finish movement controls
        buttons.add(Buttons.red(player.finChecker() + "concludeMove_" + game.getActiveSystem(), "Done moving"));
        buttons.add(Buttons.blue(player.finChecker() + "ChooseDifferentDestination", "Activate a different system"));
        buttons.add(Buttons.red(player.finChecker() + "resetTacticalMovement", "Reset all unit movement"));

        return buttons;
    }

    private static List<Button> buildMoveFromTileSelectionButtons(
            Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> out = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            Tile tile = tileEntry.getValue();
            boolean movedFrom = TacticalActionDisplacementService.hasDisplacementFromPosition(game, tile.getPosition());
            boolean hasUnits = hasUnitsOrAlliedUnitsWithoutCC(player, game, event, tile);
            boolean canSelect = (movedFrom || hasUnits)
                    && (!CommandCounterHelper.hasCC(event, player.getColor(), tile)
                            || ButtonHelper.nomadHeroAndDomOrbCheck(player, game));
            if (canSelect) {
                out.add(Buttons.green(
                        player.finChecker() + "tacticalMoveFrom_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player),
                        tile.getTileEmoji(player)));
            }
        }
        return out;
    }

    public List<Button> getLandingUnitsButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        List<Button> unlandUnitButtons = new ArrayList<>();

        UnitHolder space = tile.getSpaceUnitHolder();
        List<UnitType> committable = getCommittableGroundUnitTypes(player, space);

        String landPrefix = player.finChecker() + "landUnits_" + tile.getPosition() + "_";
        String unlandPrefix = player.finChecker() + "spaceUnits_" + tile.getPosition() + "_";
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (shouldSkipLandingOnPlanet(planet)) continue;

            LandingContext ctx =
                    LandingContext.of(game, player, tile, space, planet, landPrefix, unlandPrefix, committable);

            addLandingAndUnlandingButtonsForPlanet(ctx, buttons, unlandUnitButtons);
            for (PlanetAbilities.PlanetButtonAbility ability : PlanetAbilities.ABILITIES) {
                if (ability.enabled(ctx)) ability.contribute(ctx, buttons);
            }

            buttons.addAll(unlandUnitButtons);
            unlandUnitButtons.clear();
        }

        PostMovementButtonContext ctx = new PostMovementButtonContext(game, player, tile);
        for (PostMovementButtonAbility ability : PostMovementAbilities.ABILITIES) {
            if (ability.enabled(ctx)) ability.contribute(ctx, buttons);
        }

        return buttons;
    }

    private void refreshTileUI(ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    private boolean shouldSkipPlacingAbilities(Game game, Player player) {
        return game.isNaaluAgent()
                || game.isL1Hero()
                || (!game.getStoredValue("hiredGunsInPlay").isEmpty() && player != game.getActivePlayer());
    }

    private Button createBuildButton(Player player, Tile tile, int productionVal) {
        String id = player.finChecker() + "tacticalActionBuild_" + tile.getPosition();
        String label = "Build in This System (" + productionVal + " PRODUCTION value)";
        return Buttons.green(id, label);
    }

    private boolean hasUnitsOrAlliedUnitsWithoutCC(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile) {
        boolean hasUnits = FoWHelper.playerHasUnitsInSystem(player, tile);
        for (Player p2 : game.getRealPlayers()) {
            if (player.getAllianceMembers().contains(p2.getFaction())) {
                if (FoWHelper.playerHasUnitsInSystem(p2, tile)
                        && !CommandCounterHelper.hasCC(event, p2.getColor(), tile)) {
                    hasUnits = true;
                }
            }
        }
        return hasUnits;
    }

    private List<UnitType> getCommittableGroundUnitTypes(Player player, UnitHolder space) {
        List<UnitType> committable = new ArrayList<>(List.of(UnitType.Mech, UnitType.Infantry));
        boolean naaluFS = (player.hasUnit("naalu_flagship") || player.hasUnit("sigma_naalu_flagship_2"))
                && space.getUnitCount(UnitType.Flagship, player) > 0;
        boolean belkoFF = player.hasUnit("belkosea_fighter") || player.hasUnit("belkosea_fighter2");
        if (naaluFS || belkoFF) committable.add(UnitType.Fighter);
        return committable;
    }

    private boolean shouldSkipLandingOnPlanet(Planet planet) {
        return planet.getTokenList().stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
    }

    private void addLandingAndUnlandingButtonsForPlanet(
            LandingContext ctx, List<Button> buttons, List<Button> unlandUnitButtons) {
        for (UnitType unitType : ctx.committable) {
            for (UnitState state : UnitState.values()) {
                for (int count = 1; count <= 2; count++) {
                    // Owner (main player)
                    maybeAddLandButton(ctx, ctx.mainPlayer, unitType, state, count, buttons);
                    maybeAddUnlandButton(ctx, ctx.mainPlayer, unitType, state, count, unlandUnitButtons);

                    // Allies
                    for (Player ally : ctx.alliedPlayers) {
                        maybeAddLandButton(ctx, ally, unitType, state, count, buttons);
                        maybeAddUnlandButton(ctx, ally, unitType, state, count, unlandUnitButtons);
                    }
                }
            }
        }
    }

    private void maybeAddLandButton(
            LandingContext ctx, Player unitOwner, UnitType unitType, UnitState state, int count, List<Button> buttons) {
        if (!ctx.hasSpaceUnits(unitOwner, unitType, state, count)) return;
        String id = ctx.buildLandButtonId(count, unitType, unitOwner);
        String label = ctx.buildLandLabel(count, unitOwner, state, unitType);
        buttons.add(Buttons.red(id, label, unitType.getUnitTypeEmoji()));
    }

    private void maybeAddUnlandButton(
            LandingContext ctx, Player unitOwner, UnitType unitType, UnitState state, int count, List<Button> unland) {
        if (!ctx.hasPlanetUnits(unitOwner, unitType, state, count)) return;
        String id = ctx.buildUnlandButtonId(count, unitType, unitOwner);
        String label = ctx.buildUnlandLabel(count, unitOwner, state, unitType);
        unland.add(Buttons.gray(id, label, unitType.getUnitTypeEmoji()));
    }
}
