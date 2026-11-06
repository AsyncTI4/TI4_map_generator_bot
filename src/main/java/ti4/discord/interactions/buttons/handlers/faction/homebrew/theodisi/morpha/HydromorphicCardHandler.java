package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.HydromorphicCardModel;
import ti4.model.NamedCombatModifierModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class HydromorphicCardHandler {
    private static final String RIPTIDE_RUSH = "hydromorphicRiptideRush_";
    private static final String RISING_TIDE = "hydromorphicRisingTide_";
    private static final String TIDAL_BOUNTY = "hydromorphicTidalBounty_";
    private static final String TIDAL_BOUNTY_SYSTEM = "hydromorphicTidalBountySystem_";
    private static final String TIDAL_BOUNTY_UNIT = "hydromorphicTidalBountyUnit_";

    private static final List<NeutralUnit> TIDAL_BOUNTY_UNITS = List.of(
            new NeutralUnit(UnitType.Fighter, 1),
            new NeutralUnit(UnitType.Destroyer, 1),
            new NeutralUnit(UnitType.Cruiser, 2),
            new NeutralUnit(UnitType.Carrier, 3),
            new NeutralUnit(UnitType.Dreadnought, 4),
            new NeutralUnit(UnitType.Flagship, 8),
            new NeutralUnit(UnitType.Warsun, 12));

    public static void resolve(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            HydromorphicCardModel card,
            int discardedActionCards) {
        switch (baseAlias(card.getAlias())) {
            case "hydraulic_torrent" -> resolveHydraulicTorrent(event, game, player, discardedActionCards);
            case "riptide_rush" -> resolveRiptideRush(game, player);
            case "rising_tide" -> resolveRisingTide(game, player);
            case "ebbing_flow" -> resolveEbbingFlow(game, player);
            case "tides_of_creation" -> resolveTidesOfCreation(game, player, discardedActionCards);
            case "tidal_influence" -> resolveTidalInfluence(game, player);
            case "tidal_bounty" -> resolveTidalBounty(game, player, discardedActionCards);
            case "charted_currents" -> resolveChartedCurrents(game, player);
            default -> {}
        }
    }

    public static boolean hasRiptideRush(Game game, Player player) {
        return game.getActiveSystem().equals(game.getStoredValue(RIPTIDE_RUSH + player.getFaction()));
    }

    public static int getTidesOfCreationProductionBonus(Player player) {
        return player.getSpentThingsThisWindow().stream()
                        .anyMatch(thing -> thing.startsWith("hydromorphicTidesOfCreation_"))
                ? 2
                : 0;
    }

    public static int getTidesOfCreationCostDiscount(Player player) {
        return player.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith("hydromorphicTidesOfCreation_"))
                .mapToInt(thing -> Integer.parseInt(thing.substring("hydromorphicTidesOfCreation_".length())))
                .findFirst()
                .orElse(0);
    }

    public static void addRisingTideCombatModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, Tile tile, CombatRollType rollType) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (rollType != CombatRollType.combatround
                || combat == null
                || !risingTideState(combat).equals(game.getStoredValue(RISING_TIDE + player.getFaction()))) {
            return;
        }
        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("hydromorphic_rising_tide");
        modifier.setType("combat_modifiers");
        modifier.setValue(1);
        modifier.setScope("all");
        modifier.setForCombatAbility(CombatRollType.combatround);
        modifier.setPersistenceType("ALWAYS");
        modifier.setRelated(List.of());
        modifiers.add(new NamedCombatModifierModel(modifier, "+1 from Rising Tide"));
    }

    public static void clearTacticalActionEffects(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(RIPTIDE_RUSH + player.getFaction());
            game.removeStoredValue(TIDAL_BOUNTY + player.getFaction());
        }
    }

    public static void clearCombatEffects(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(RISING_TIDE + player.getFaction());
        }
    }

    private static void resolveHydraulicTorrent(
            ButtonInteractionEvent event, Game game, Player player, int discardedActionCards) {
        if (discardedActionCards < 1) {
            return;
        }
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " must assign "
                            + discardedActionCards
                            + " additional hit"
                            + (discardedActionCards == 1 ? "" : "s")
                            + " for _Hydraulic Torrent_.");
            return;
        }
        Player opponent = combat.factions().stream()
                .map(game::getPlayerFromColorOrFaction)
                .filter(candidate -> candidate != null && candidate != player)
                .findFirst()
                .orElse(null);
        Tile tile = game.getTileByPosition(combat.tilePosition());
        if (opponent == null || tile == null) {
            return;
        }
        if ("space".equals(combat.unitHolderName())) {
            CombatRollService.sendSpaceAssignHitsButtons(
                    event, game, opponent, tile, discardedActionCards, opponent.getCorrectChannel());
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(combat.unitHolderName());
        if (planet != null) {
            CombatRollService.sendGroundCombatHitAssignment(
                    game,
                    player,
                    opponent,
                    tile,
                    planet,
                    discardedActionCards,
                    event,
                    "_Hydraulic Torrent_",
                    opponent.getCorrectChannel());
        }
    }

    private static void resolveRiptideRush(Game game, Player player) {
        if (game.getActiveSystem().isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " must apply _Riptide Rush_'s +1 move manually because there is no active system.");
            return;
        }
        game.setStoredValue(RIPTIDE_RUSH + player.getFaction(), game.getActiveSystem());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " gains +1 move for ships during this tactical action from _Riptide Rush_.");
    }

    private static void resolveRisingTide(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " must apply _Rising Tide_'s +1 combat-roll modifier manually because there is no current combat.");
            return;
        }
        game.setStoredValue(RISING_TIDE + player.getFaction(), risingTideState(combat));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gains +1 to combat rolls this round from _Rising Tide_.");
    }

    private static void resolveEbbingFlow(Game game, Player player) {
        if (game.getActiveSystem().isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " cannot automate _Ebbing Flow_ without an active system.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to resolve _Ebbing Flow_.",
                Buttons.green(
                        player.factionButtonChecker() + "retreat_" + game.getActiveSystem() + "_hydromorphicEbbing",
                        "Resolve Ebbing Flow"));
    }

    private static void resolveTidesOfCreation(Game game, Player player, int discardedActionCards) {
        player.addSpentThing("hydromorphicTidesOfCreation_" + discardedActionCards);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gains +2 PRODUCTION and a " + discardedActionCards
                        + " resource production discount from _Tides of Creation_.");
    }

    private static void resolveTidalInfluence(Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a player to give you 1 promissory note for _Tidal Influence_.",
                Buttons.green(player.factionButtonChecker() + "resolveDiplomaticPressureStep1", "Choose a Player"));
    }

    private static void resolveTidalBounty(Game game, Player player, int discardedActionCards) {
        if (discardedActionCards < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " discarded no Action Cards, so no neutral unit can be placed with _Tidal Bounty_.");
            return;
        }
        List<Button> buttons = getTidalBountySystemButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible system for _Tidal Bounty_.");
            return;
        }
        game.setStoredValue(TIDAL_BOUNTY + player.getFaction(), Integer.toString(discardedActionCards));
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a non-home system without non-neutral ships for _Tidal Bounty_.",
                buttons,
                List.of(),
                player.factionButtonChecker() + TIDAL_BOUNTY_SYSTEM);
    }

    @ButtonHandler(TIDAL_BOUNTY_SYSTEM)
    public static void chooseTidalBountySystem(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String message = player.getRepresentationUnfogged()
                + ", choose a non-home system without non-neutral ships for _Tidal Bounty_.";
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                message,
                () -> getTidalBountySystemButtons(game, player),
                List.of(),
                player.factionButtonChecker() + TIDAL_BOUNTY_SYSTEM)) {
            return;
        }
        int costLimit = getTidalBountyCostLimit(game, player);
        Tile tile = game.getTileByPosition(buttonID.substring(TIDAL_BOUNTY_SYSTEM.length()));
        if (costLimit < 1 || tile == null || tile.isHomeSystem(game)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = TIDAL_BOUNTY_UNITS.stream()
                .filter(unit -> unit.cost() <= costLimit)
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + TIDAL_BOUNTY_UNIT + tile.getPosition() + "|"
                                + unit.type().name(),
                        "Place Neutral " + unit.type().humanReadableName() + " (" + unit.cost() + ")"))
                .toList();
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationUnfogged()
                        + ", choose a neutral unit costing up to "
                        + costLimit
                        + " for _Tidal Bounty_.",
                buttons);
    }

    @ButtonHandler(TIDAL_BOUNTY_UNIT)
    public static void placeTidalBountyUnit(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String[] values = buttonID.substring(TIDAL_BOUNTY_UNIT.length()).split("\\|", 2);
        int costLimit = getTidalBountyCostLimit(game, player);
        Tile tile = values.length == 2 ? game.getTileByPosition(values[0]) : null;
        NeutralUnit unit = values.length == 2 ? findTidalBountyUnit(values[1]) : null;
        if (costLimit < 1
                || tile == null
                || unit == null
                || unit.cost() > costLimit
                || tile.isHomeSystem(game)
                || tile.getTileModel().isHyperlane()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(
                event, tile, game, game.getNeutralColor(), "1 " + unit.type().getValue());
        game.removeStoredValue(TIDAL_BOUNTY + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 neutral "
                        + unit.type().humanReadableName() + " in " + tile.getRepresentationForButtons(game, player)
                        + " with _Tidal Bounty_.");
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveChartedCurrents(Game game, Player player) {
        List<Button> buttons =
                new ArrayList<>(TeHelperActionCards.getReadiedStrategyCardSecondaryButtons(game, player));
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a strategy-card secondary ability to resolve with _Charted Currents_. Do not spend a command token.",
                buttons);
    }

    private static int getTidalBountyCostLimit(Game game, Player player) {
        try {
            return Integer.parseInt(game.getStoredValue(TIDAL_BOUNTY + player.getFaction()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Button> getTidalBountySystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> !tile.getTileModel().isHyperlane())
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + TIDAL_BOUNTY_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static boolean isShip(UnitKey unitKey) {
        return switch (unitKey.unitType()) {
            case Fighter,
                    Destroyer,
                    Cruiser,
                    Carrier,
                    Dreadnought,
                    Flagship,
                    Warsun,
                    PlenaryOrbital,
                    TyrantsLament,
                    Lady,
                    Celagrom,
                    Aurelion,
                    Cavalry -> true;
            default -> false;
        };
    }

    private static NeutralUnit findTidalBountyUnit(String name) {
        return TIDAL_BOUNTY_UNITS.stream()
                .filter(unit -> unit.type().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String risingTideState(StartCombatService.CurrentCombat combat) {
        return combat.tilePosition() + "|" + combat.unitHolderName() + "|" + combat.round();
    }

    private static String baseAlias(String alias) {
        return alias.endsWith("2") ? alias.substring(0, alias.length() - 1) : alias;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record NeutralUnit(UnitType type, int cost) {}
}
