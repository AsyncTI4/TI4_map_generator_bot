package ti4.helpers.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.TechEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class BlueReverieTechHelper {
    private static final String GRAVITY_DRIVE_STATE = "gdBrGravityDrive_";
    private static final String PRODUCTION_BIOMES_PENDING = "pmBrPending_";

    @ButtonHandler("repairUnitsL4")
    public static void repairAllUnitsWithL4(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTechReady("l4_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustTech("l4_br");
        game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .forEach(unitHolder -> unitHolder.removeAllUnitDamage(player.getColorID()));

        TechnologyModel l4Model = Mapper.getTech("l4_br");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + l4Model.getRepresentation(false)
                        + " to repair all of their damaged units.");

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void offerInheritanceSystemsBr(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "ignoreIsBrPrereqs", "Ignore All Prerequisites"));
        buttons.add(Buttons.green(player.factionButtonChecker() + "gainIsBrCc", "Gain 1 Command Token"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please decide how you want to use _Inheritance Systems_.",
                buttons);
    }

    @ButtonHandler("ignoreIsBrPrereqs")
    public static void ignoreIsBrPrereqs(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is ignoring all prerequisites for their researched technology with _Inheritance Systems_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gainIsBrCc")
    public static void gainIsBrCc(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " is gaining 1 command token with _Inheritance Systems_.",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteMessage(event);
    }

    public void offerProductionBiomes(Game game, Player player) {
        if (!player.hasTech("pm_br")
                || player.getStrategicCC() < 1
                || player.getCurrentProducedUnits().isEmpty()
                || !game.getStoredValue(PRODUCTION_BIOMES_PENDING + player.getFaction())
                        .isEmpty()
                || game.getRealPlayers().stream()
                        .noneMatch(otherPlayer -> otherPlayer != player && !otherPlayer.isEliminated())) {
            return;
        }
        String resolutionId = UUID.randomUUID().toString();
        game.setStoredValue(PRODUCTION_BIOMES_PENDING + player.getFaction(), resolutionId);
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "pmBrUse_" + resolutionId,
                        "Spend 1 Strategy Token: Gain 4 Trade Goods",
                        TechEmojis.BioticTech),
                Buttons.gray(player.factionButtonChecker() + "pmBrDecline_" + resolutionId, "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", 1 or more of your units used PRODUCTION. You may spend 1 command token from your strategy pool to gain 4 trade goods and choose another player to gain 2 trade goods.",
                buttons);
    }

    public void clearProductionBiomes(Game game, Player player) {
        game.removeStoredValue(PRODUCTION_BIOMES_PENDING + player.getFaction());
    }

    @ButtonHandler("pmBrUse_")
    public void useProductionBiomes(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String resolutionId = StringUtils.substringAfter(buttonID, "pmBrUse_");
        if (!canResolveProductionBiomes(game, player, resolutionId) || player.getStrategicCC() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        String tradeGoodChange = player.gainTG(4, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 4);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " spent 1 command token from their strategy pool to gain 4 trade goods "
                        + tradeGoodChange
                        + " due to _Production Biomes_.");

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player && !target.isEliminated()) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + "pmBrTarget_" + resolutionId + "~" + target.getFaction(),
                        "gray",
                        target));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose another player to gain 2 trade goods.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pmBrTarget_")
    public void chooseProductionBiomesRecipient(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "pmBrTarget_").split("~", 2);
        Player target = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[1]) : null;
        if (target == null
                || target == player
                || target.isEliminated()
                || !game.getRealPlayers().contains(target)
                || !canResolveProductionBiomes(game, player, parts[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String tradeGoodChange = target.gainTG(2, true);
        ButtonHelperAgents.resolveArtunoCheck(target, 2);
        String message = target.getRepresentation() + " gained 2 trade goods " + tradeGoodChange
                + " due to _Production Biomes_.";
        FoWHelper.notifyPlayerAndAffectedInFog(game, target, player, message);
        clearProductionBiomes(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pmBrDecline_")
    public void declineProductionBiomes(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String resolutionId = StringUtils.substringAfter(buttonID, "pmBrDecline_");
        if (canResolveProductionBiomes(game, player, resolutionId)) {
            clearProductionBiomes(game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    private boolean canResolveProductionBiomes(Game game, Player player, String resolutionId) {
        return player.hasTech("pm_br")
                && resolutionId.equals(game.getStoredValue(PRODUCTION_BIOMES_PENDING + player.getFaction()));
    }

    public Button getBioplasmosisEndTurnButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + "bioBrStart", "Use Bioplasmosis", TechEmojis.BioticTech);
    }

    @ButtonHandler("bioBrStart")
    public void beginBioplasmosis(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTechReady("bio_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustTech("bio_br");
        sendBioplasmosisSourceButtons(event, game, player);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("bioBrSource_")
    public void chooseBioplasmosisSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String source = StringUtils.substringAfter(buttonID, "bioBrSource_");
        if (!player.hasTech("bio_br")
                || !player.getPlanetsAllianceMode().contains(source)
                || !isBioplasmosisPlanet(game, source)
                || getInfantryOnPlanet(game, player, source) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        int maximum = Math.min(2, getInfantryOnPlanet(game, player, source));
        for (int amount = 1; amount <= maximum; amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "bioBrAmount_" + source + "~" + amount,
                    "Move " + amount + " Infantry"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose how many infantry to move from "
                        + Helper.getPlanetRepresentation(source, game)
                        + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bioBrAmount_")
    public void chooseBioplasmosisDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "bioBrAmount_").split("~", 2);
        int amount;
        try {
            amount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (parts.length != 2
                || !player.hasTech("bio_br")
                || !player.getPlanetsAllianceMode().contains(parts[0])
                || !isBioplasmosisPlanet(game, parts[0])
                || amount < 1
                || amount > 2
                || amount > getInfantryOnPlanet(game, player, parts[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String destination : player.getPlanetsAllianceMode()) {
            if (!isEligibleBioplasmosisDestination(game, player, parts[0], destination)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "bioBrMove_" + parts[0] + "~" + destination + "~" + amount,
                    Helper.getPlanetRepresentation(destination, game)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No eligible destination is available for _Bioplasmosis_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a destination for "
                        + amount
                        + " infantry from "
                        + Helper.getPlanetRepresentation(parts[0], game)
                        + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bioBrMove_")
    public void moveBioplasmosisInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "bioBrMove_").split("~");
        if (parts.length != 3 || !player.hasTech("bio_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int count;
        try {
            count = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (count < 1
                || !player.getPlanetsAllianceMode().contains(parts[0])
                || !isBioplasmosisPlanet(game, parts[0])
                || count > getInfantryOnPlanet(game, player, parts[0])
                || !isEligibleBioplasmosisDestination(game, player, parts[0], parts[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile sourceTile = game.getTileFromPlanet(parts[0]);
        Tile destinationTile = game.getTileFromPlanet(parts[1]);
        List<RemoveUnitService.RemovedUnit> removedUnits = RemoveUnitService.removeUnits(
                event, sourceTile, game, player.getColor(), count + " infantry " + parts[0]);
        AddUnitService.addUnits(
                event, destinationTile, game, player.getColor(), count + " infantry " + parts[1], removedUnits);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation(false, false)
                        + " moved "
                        + count
                        + " infantry from "
                        + Helper.getPlanetRepresentation(parts[0], game)
                        + " to "
                        + Helper.getPlanetRepresentation(parts[1], game)
                        + " with _Bioplasmosis_.");
        ButtonHelper.deleteMessage(event);
    }

    private void sendBioplasmosisSourceButtons(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String source : player.getPlanetsAllianceMode()) {
            if (isBioplasmosisPlanet(game, source) && getInfantryOnPlanet(game, player, source) > 0) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "bioBrSource_" + source,
                        Helper.getPlanetRepresentation(source, game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No eligible infantry can be moved with _Bioplasmosis_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose an infantry source for _Bioplasmosis_. A reminder that you cannot move your ground forces out of systems that contain your command tokens.",
                buttons);
    }

    private int getInfantryOnPlanet(Game game, Player player, String planet) {
        Planet unitHolder = game.getUnitHolderFromPlanet(planet);
        return unitHolder == null ? 0 : unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
    }

    private boolean isEligibleBioplasmosisDestination(Game game, Player player, String source, String destination) {
        if (source.equals(destination)
                || !player.getPlanetsAllianceMode().contains(destination)
                || !isBioplasmosisPlanet(game, destination)) {
            return false;
        }
        Tile sourceTile = game.getTileFromPlanet(source);
        Tile destinationTile = game.getTileFromPlanet(destination);
        return sourceTile != null
                && destinationTile != null
                && (sourceTile == destinationTile
                        || FoWHelper.getAdjacentTiles(game, sourceTile.getPosition(), player, false, true)
                                .contains(destinationTile.getPosition()));
    }

    private boolean isBioplasmosisPlanet(Game game, String planet) {
        Planet unitHolder = game.getUnitHolderFromPlanet(planet);
        return unitHolder != null && !unitHolder.isSpaceStation(game) && !unitHolder.isFake();
    }

    public void offerSelfAssemblyRoutinesGroundCombatChoice(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder unitHolder,
            int destroyedMechs) {
        if (!player.hasTech("sar_br") || !(unitHolder instanceof Planet)) {
            return;
        }
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !tile.getPosition().equals(combat.tilePosition())
                || !unitHolder.getName().equals(combat.unitHolderName())
                || !combat.factions().contains(player.getFaction())) {
            return;
        }
        String context = tile.getPosition() + "~" + unitHolder.getName();
        for (int index = 0; index < destroyedMechs; index++) {
            List<Button> buttons = new java.util.ArrayList<>();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "sarBrGroundCombat_" + context + "~tg",
                    "Gain 1 Trade Good",
                    ti4.service.emoji.MiscEmojis.tg));
            if (player.hasTechReady("sar_br")) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "sarBrGroundCombat_" + context + "~hit",
                        "Exhaust Self-Assembly Routines to Produce 1 Hit",
                        TechEmojis.WarfareTech));
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "sarBrGroundCombat_" + context + "~decline", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", 1 of your mechs was destroyed during ground combat. You may gain 1 trade good"
                            + (player.hasTechReady("sar_br")
                                    ? " or exhaust _Self-Assembly Routines_ to produce 1 hit against your opponent."
                                    : "."),
                    buttons);
        }
    }

    @ButtonHandler("sarBrGroundCombat_")
    public void resolveSelfAssemblyRoutinesGroundCombat(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts =
                StringUtils.substringAfter(buttonID, "sarBrGroundCombat_").split("~");
        if (parts.length != 3 || !player.hasTech("sar_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (!(unitHolder instanceof Planet)
                || combat == null
                || !parts[0].equals(combat.tilePosition())
                || !parts[1].equals(combat.unitHolderName())
                || !combat.factions().contains(player.getFaction())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Self-Assembly Routines cannot be resolved here.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player opponent = combat.factions().stream()
                .filter(faction -> !faction.equals(player.getFaction()))
                .map(game::getPlayerFromColorOrFaction)
                .findFirst()
                .orElse(null);
        if (opponent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if ("tg".equals(parts[2])) {
            player.setTg(player.getTg() + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation(false, false)
                            + " gained 1 trade good from _Self-Assembly Routines_ ("
                            + (player.getTg() - 1)
                            + "->"
                            + player.getTg()
                            + ").");
        } else if ("hit".equals(parts[2]) && player.hasTechReady("sar_br")) {
            player.exhaustTech("sar_br");
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation(false, false) + " exhausted _Self-Assembly Routines_ to produce 1 hit.");
            CombatRollService.sendGroundCombatHitAssignment(
                    game, player, opponent, tile, (Planet) unitHolder, 1, event);
        } else if (!"decline".equals(parts[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sarBR_")
    public static void resolveBRSelfAssemblyRoutines(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.split("_", 3);
        if (parts.length != 3 || !player.hasTech("sar_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That system cannot be used for Self-Assembly Routines.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new java.util.ArrayList<>();
        for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(unitHolder.getName())) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "sarBRPlaceMech_" + unitHolder.getName() + "~" + parts[2],
                        "Place Mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No eligible planet is available for Self-Assembly Routines.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose a planet in that system on which to place 1 mech.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("sarBRPlaceMech_")
    public static void placeSelfAssemblyRoutinesMech(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "sarBRPlaceMech_").split("~", 2);
        if (parts.length != 2 || !player.hasTech("sar_br")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planet = parts[0];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !(tile.getUnitHolders().get(planet) instanceof Planet)
                || !player.getPlanetsAllianceMode().contains(planet)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That planet cannot be used for Self-Assembly Routines.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String msg1 = player.getFactionEmoji() + " used _Self-Assembly Routines_ to place 1 mech on "
                + Helper.getPlanetRepresentation(planet, game) + ".";
        AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + planet);
        ButtonHelper.deleteMessage(event);
        ButtonHelper.sendMessageToRightStratThread(player, game, msg1, parts[1]);
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
    }

    public void exhaustGravityDrive(Game game, Player player) {
        game.setStoredValue(GRAVITY_DRIVE_STATE + player.getFaction(), "pending");
    }

    public boolean consumeGravityDriveBonus(Game game, Player player) {
        String key = GRAVITY_DRIVE_STATE + player.getFaction();
        if (!player.hasTech("gd_br")
                || !player.getExhaustedTechs().contains("gd_br")
                || !"pending".equals(game.getStoredValue(key))) {
            return false;
        }
        game.setStoredValue(key, "used");
        return true;
    }

    public boolean offerGravitonLaserSystemAfterSpaceCannon(
            Game game, Player player, Player opponent, Tile tile, int hits, GenericInteractionCreateEvent event) {
        if (!player.hasTechReady("gls_br")) {
            return false;
        }
        String context = tile.getPosition() + "~" + opponent.getColor() + "~" + hits;
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "glsBrAfterSpaceCannon_" + context + "~exhaust",
                        "Exhaust Graviton Laser System",
                        TechEmojis.CyberneticTech),
                Buttons.gray(
                        player.factionButtonChecker() + "glsBrAfterSpaceCannon_" + context + "~decline", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentation()
                        + ", you produced "
                        + hits
                        + " SPACE CANNON "
                        + (hits == 1 ? "hit" : "hits")
                        + ". You may exhaust _Graviton Laser System_ before those hits are assigned:",
                buttons);
        return true;
    }

    @ButtonHandler("glsBrAfterSpaceCannon_")
    public void resolveGravitonLaserSystemAfterSpaceCannon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts =
                StringUtils.substringAfter(buttonID, "glsBrAfterSpaceCannon_").split("~");
        if (parts.length != 4) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!"exhaust".equals(parts[3]) && !"decline".equals(parts[3])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        Player opponent = game.getPlayerFromColorOrFaction(parts[1]);
        int hits;
        try {
            hits = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null || opponent == null || hits < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The SPACE CANNON assignment could not be found.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if ("exhaust".equals(parts[3])) {
            if (!player.hasTechReady("gls_br")) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Graviton Laser System is not ready.");
                ButtonHelper.deleteMessage(event);
                return;
            }
            player.exhaustTech("gls_br");
            game.setStoredValue(player.getFaction() + "graviton", "true");
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation(false, false) + " exhausted _Graviton Laser System_.");
        }
        CombatRollService.sendSpaceCannonOffenceHitAssignment(
                game,
                player,
                opponent,
                tile,
                hits,
                game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel(),
                event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dxaBrStartGroundCombat_")
    public void resolveDaxciveStartGroundCombat(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("dxaBrStartGroundCombat_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.exhaustTech("dxa_br");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "gf " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation(false, false) + " exhausted _Daxcive Animators_ and placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }
}
