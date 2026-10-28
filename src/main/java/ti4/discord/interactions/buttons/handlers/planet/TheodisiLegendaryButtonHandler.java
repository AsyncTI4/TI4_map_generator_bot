package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.combat.CancelGroundHitsButtonId;
import ti4.discord.interactions.buttons.handlers.combat.CombatButtonHandler;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.discord.interactions.commands.planet.PlanetExhaustAbility;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TechnologyModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class TheodisiLegendaryButtonHandler {
    private static final String AVARIN = "avarin";
    private static final String CELYON = "celyon";
    private static final String ILLYRA = "illyra";
    private static final String PELOR = "pelor";
    private static final String THREN = "thren";
    private static final String VESPIX = "vespix";
    private static final String VOSKA = "voska";
    private static final String REMOTE_FACTORY = "threnRemoteFactory";

    public static Button getAvarinPassButton(Player player) {
        return canUse(player, AVARIN)
                ? Buttons.green(player.factionButtonChecker() + "useAvarinLegendary", "Use Forbidden Research Facility")
                : null;
    }

    public static Button getCelyonEndTurnButton(Player player) {
        return canUse(player, CELYON)
                ? Buttons.green(player.factionButtonChecker() + "useCelyonLegendary", "Use Ghastly Projections")
                : null;
    }

    public static void offerEndOfTacticalActionAbilities(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return;
        }
        if (canUse(player, ILLYRA)
                && hasReinforcement(game, player, UnitType.Pds)
                && hasControlledPlanet(activeSystem, player)) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", you may exhaust _Ancient Infrastructure_ to place 1 PDS on a planet you control in the active system.",
                    Buttons.green(player.factionButtonChecker() + "useIllyraLegendary", "Use Ancient Infrastructure"));
        }
        if (canUse(player, VESPIX) && hasControlledPlanet(activeSystem, player)) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", you may exhaust _Paradise Lost_ to explore a planet you control in the active system.",
                    Buttons.green(player.factionButtonChecker() + "useVespixLegendary", "Use Paradise Lost"));
        }
    }

    public static void offerActivationAbility(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || activeSystem.isHomeSystem() || !canUse(player, THREN)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Remote Factory_ to give the active system PRODUCTION 3 for this tactical action.",
                Buttons.green(player.factionButtonChecker() + "useThrenLegendary", "Use Remote Factory"));
    }

    @ButtonHandler("useAvarinLegendary")
    public static void useAvarinLegendary(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUse(player, AVARIN)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        exhaustAndResolvePlanetAbility(event, game, player, AVARIN);
    }

    @ButtonHandler("useCelyonLegendary")
    public static void useCelyonLegendary(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUse(player, CELYON)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        exhaustAndResolvePlanetAbility(event, game, player, CELYON);
    }

    @ButtonHandler("useIllyraLegendary")
    public static void useIllyraLegendary(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!canUse(player, ILLYRA) || !hasReinforcement(game, player, UnitType.Pds) || activeSystem == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        exhaustAndResolvePlanetAbility(event, game, player, ILLYRA);
    }

    @ButtonHandler("useVespixLegendary")
    public static void useVespixLegendary(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!canUse(player, VESPIX) || activeSystem == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        exhaustAndResolvePlanetAbility(event, game, player, VESPIX);
    }

    @ButtonHandler("useThrenLegendary")
    public static void useThrenLegendary(ButtonInteractionEvent event, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!canUse(player, THREN)
                || activeSystem == null
                || activeSystem.isHomeSystem()
                || activeSystem.getPlanetUnitHolders().stream()
                        .noneMatch(TheodisiLegendaryButtonHandler::isRealPlanet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        exhaustAndResolvePlanetAbility(event, game, player, THREN);
    }

    public static void offerAvarinUnitUpgradeResearch(Game game, Player player) {
        List<Button> buttons = ListTechService.getAllTechOfAType(
                        game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, true)
                .stream()
                .map(tech -> Buttons.gray(
                        player.factionButtonChecker() + "avarinResearchUnitUpgrade_" + tech.getAlias(), tech.getName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " exhausted _Forbidden Research Facility_, but has no researchable unit upgrade technology.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Forbidden Research Facility_. Choose a unit upgrade technology to research:",
                buttons);
    }

    @ButtonHandler("avarinResearchUnitUpgrade_")
    public static void researchAvarinUnitUpgrade(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = buttonID.substring("avarinResearchUnitUpgrade_".length());
        boolean researchable = ListTechService.getAllTechOfAType(
                        game, TechnologyModel.TechnologyType.UNITUPGRADE.toString(), player, false, true)
                .stream()
                .anyMatch(tech -> tech.getAlias().equals(techId));
        if (!researchable) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        PlayerTechService.getTech(game, player, event, "getTech_" + techId + "__noPay__comp");
    }

    public static void offerCelyonPlanetSelection(Game game, Player player) {
        List<Button> buttons = getCelyonTargets(game, player).stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "celyonCommitInfantry_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " exhausted _Ghastly Projections_, but has no eligible planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _Ghastly Projections_:",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + "celyonCommitInfantry_", 0));
    }

    @ButtonHandler("celyonCommitInfantry_")
    public static void commitCelyonInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getCelyonTargets(game, player).stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "celyonCommitInfantry_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        String message = player.getRepresentationNoPing() + ", choose a planet for _Ghastly Projections_:";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + "celyonCommitInfantry_",
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring("celyonCommitInfantry_".length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null || !getCelyonTargets(game, player).contains(planet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " committed 1 infantry to "
                        + Helper.getPlanetRepresentation(planetName, game) + " using _Ghastly Projections_.");
        StartCombatService.groundCombatCheck(game, planet, tile, event);
    }

    public static void offerIllyraPlanetSelection(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return;
        }
        List<Button> buttons = controlledPlanets(activeSystem, player).stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "illyraPlacePds_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _Ancient Infrastructure_:",
                buttons);
    }

    @ButtonHandler("illyraPlacePds_")
    public static void placeIllyraPds(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring("illyraPlacePds_".length());
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        Planet planet = activeSystem == null
                ? null
                : activeSystem.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(planetName)
                                && player.getPlanets().contains(planetName))
                        .findFirst()
                        .orElse(null);
        if (planet == null || !hasReinforcement(game, player, UnitType.Pds)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(event, activeSystem, game, player.getColor(), "1 pds " + planetName);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 PDS on "
                        + Helper.getPlanetRepresentation(planetName, game) + " using _Ancient Infrastructure_.");
    }

    public static void offerVespixPlanetSelection(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : controlledPlanets(activeSystem, player)) {
            buttons.addAll(ButtonHelper.getPlanetExplorationButtons(game, planet, player));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " exhausted _Paradise Lost_, but has no explorable planet in the active system.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose how to explore a planet with _Paradise Lost_:",
                buttons);
    }

    public static void resolveThrenRemoteFactory(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null || activeSystem.isHomeSystem()) {
            return;
        }
        game.setStoredValue(REMOTE_FACTORY + player.getFaction(), activeSystem.getPosition());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " used _Remote Factory_. "
                        + activeSystem.getRepresentationForButtons(game, player)
                        + " has PRODUCTION 3 for this tactical action.");
    }

    public static void offerPelorHit(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType,
            int hits,
            GenericInteractionCreateEvent event) {
        if (hits < 1 || !canUse(player, PELOR) || opponent == player) {
            return;
        }
        String buttonId = player.factionButtonChecker() + "usePelorLegendary_" + tile.getPosition() + "_"
                + holder.getName() + "_" + rollType.name() + "_" + opponent.getFaction();
        Button button = Buttons.green(buttonId, "Use Sword of the Saint");
        MessageHelper.sendMessageToChannelWithButton(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _Sword of the Saint_ to produce 1 additional hit.",
                button);
    }

    @ButtonHandler("usePelorLegendary_")
    public static void usePelorLegendary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring("usePelorLegendary_".length()).split("_", 4);
        if (parts.length != 4 || !canUse(player, PELOR)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        Player opponent = game.getPlayerFromColorOrFaction(parts[3]);
        CombatRollType rollType;
        try {
            rollType = CombatRollType.valueOf(parts[2]);
        } catch (IllegalArgumentException exception) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (tile == null || holder == null || opponent == null || combat == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        PlanetExhaustAbility.doAction(event, player, PELOR, game, true);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Sword of the Saint_ to produce 1 additional hit.");
        if (holder instanceof Planet planet) {
            sendPelorGroundCombatHitAssignment(event, game, opponent, tile, planet, 1);
        } else {
            CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, 1);
        }
    }

    private static void sendPelorGroundCombatHitAssignment(
            ButtonInteractionEvent event, Game game, Player opponent, Tile tile, Planet planet, int hits) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                opponent.factionButtonChecker() + AutoAssignGroundHitsButtonIds.format(planet.getName(), hits),
                "Auto-Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                "Manually Assign Hit" + (hits == 1 ? "" : "s")));
        buttons.add(
                Buttons.gray(CancelGroundHitsButtonId.of(tile.getPosition(), hits, planet.getName()), "Cancel a Hit"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel(),
                opponent.getRepresentationNoPing() + ", assign the hits from _Sword of the Saint_:",
                buttons);
    }

    public static void addVoskaCancelButton(List<Button> buttons, Game game, Player player, String cancelId) {
        if (!canUse(player, VOSKA) || cancelId == null || cancelId.isBlank() || getHitCount(cancelId) < 1) {
            return;
        }
        buttons.add(
                Buttons.green(player.factionButtonChecker() + "useVoskaLegendary_" + cancelId, "Use Emperor's Aegis"));
    }

    @ButtonHandler("useVoskaLegendary_")
    public static void useVoskaLegendary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cancelId = buttonID.substring("useVoskaLegendary_".length());
        if (!canUse(player, VOSKA) || !cancelId.startsWith("cancel")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        PlanetExhaustAbility.doAction(event, player, VOSKA, game, true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " exhausted _Emperor's Aegis_ to cancel 1 additional hit.");
        if (cancelId.startsWith("cancelAFBHits_")) {
            CombatButtonHandler.cancelAFBHits(event, player, cancelId, game);
        } else if (cancelId.startsWith("cancelPdsOffenseHits_")) {
            CombatButtonHandler.cancelPDSOffenseHits(event, player, cancelId, game);
        } else if (cancelId.startsWith("cancelGroundHits_")) {
            CombatButtonHandler.cancelGroundHits(event, player, cancelId, game);
        } else if (cancelId.startsWith("cancelSpaceHits_")) {
            CombatButtonHandler.cancelSpaceHits(event, player, cancelId, game);
        } else {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    public static boolean ignoresAvarinAnomaly(Game game, Player player, Tile tile) {
        Tile avarinTile = game.getTileFromPlanet(AVARIN);
        if (player == null
                || tile == null
                || avarinTile == null
                || !tile.getPosition().equals(avarinTile.getPosition())) {
            return false;
        }
        Player avarinOwner = game.getRealPlayers().stream()
                .filter(candidate -> candidate.getPlanets().contains(AVARIN))
                .findFirst()
                .orElse(null);
        return avarinOwner != null && avarinOwner != player;
    }

    public static void addAvarinCombatModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, Tile tile, CombatRollType rollType) {
        if (rollType != CombatRollType.combatround || !ignoresAvarinAnomaly(game, player, tile)) {
            return;
        }
        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("avarin_forbidden_research_facility");
        modifier.setType(Constants.COMBAT_MODIFIERS);
        modifier.setValue(1);
        modifier.setScope("all");
        modifier.setForCombatAbility(CombatRollType.combatround);
        modifier.setPersistenceType("ALWAYS");
        modifier.setRelated(List.of());
        modifiers.add(new NamedCombatModifierModel(modifier, "+1 from Forbidden Research Facility"));
    }

    public static boolean hasRemoteFactoryProduction(Game game, Player player, Tile tile) {
        return tile != null && tile.getPosition().equals(game.getStoredValue(REMOTE_FACTORY + player.getFaction()));
    }

    public static void clearRemoteFactory(Game game, Player player) {
        game.removeStoredValue(REMOTE_FACTORY + player.getFaction());
    }

    private static boolean canUse(Player player, String planet) {
        return player.getPlanets().contains(planet)
                && !player.getExhaustedPlanetsAbilities().contains(planet);
    }

    private static void exhaustAndResolvePlanetAbility(
            ButtonInteractionEvent event, Game game, Player player, String planet) {
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteTheOneButton(event);
    }

    private static int getHitCount(String cancelId) {
        String[] parts = cancelId.split("_");
        if (parts.length < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean hasReinforcement(Game game, Player player, UnitType unitType) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unitType.value, true)
                < player.getUnitCap(unitType.value);
    }

    private static boolean hasControlledPlanet(Tile tile, Player player) {
        return !controlledPlanets(tile, player).isEmpty();
    }

    private static List<Planet> controlledPlanets(Tile tile, Player player) {
        return tile.getPlanetUnitHolders().stream()
                .filter(TheodisiLegendaryButtonHandler::isRealPlanet)
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .toList();
    }

    private static List<Planet> getCelyonTargets(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(TheodisiLegendaryButtonHandler::isRealPlanet)
                .filter(planet -> !game.isFowMode()
                        || FoWHelper.fowFilter(game, player)
                                .contains(
                                        game.getTileFromPlanet(planet.getName()).getPosition()))
                .filter(planet -> isAdjacentToControlledPlanet(game, player, planet))
                .sorted(Comparator.comparing(Planet::getName))
                .toList();
    }

    private static boolean isRealPlanet(Planet planet) {
        var model = Mapper.getPlanet(planet.getName());
        return model != null && !model.isSpaceStation() && !model.isFake();
    }

    private static boolean isAdjacentToControlledPlanet(Game game, Player player, Planet target) {
        Tile targetTile = game.getTileFromPlanet(target.getName());
        if (targetTile == null) {
            return false;
        }
        return player.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(tile -> tile != null)
                .anyMatch(tile -> FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)
                        .contains(targetTile.getPosition()));
    }
}
