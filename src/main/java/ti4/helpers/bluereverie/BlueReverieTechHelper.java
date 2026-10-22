package ti4.helpers.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.TechEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class BlueReverieTechHelper {
    private static final String GRAVITY_DRIVE_STATE = "gdBrGravityDrive_";
    private static final String PRODUCTION_BIOMES_PENDING = "pmBrPending_";
    private static final String NETWORKED_CONSTRUCTORS_PENDING = "ncBrPending_";
    private static final String VIRAL_ENTANGLEMENT_INFANTRY_REMAINING = "veBrInfantryRemaining_";
    private static final String STASIS_FIELD_GENERATORS_HITS = "sfgBrHits_";
    private static final String STASIS_FIELD_GENERATORS_RELEASE = "sfgBrRelease_";
    private static final String EE_RES_TOKEN = "attachment_positiveres.png";
    private static final String EE_INF_TOKEN = "attachment_positiveinf.png";

    public static boolean hasEcologicalEngineeringShield(Game game, Planet planet) {
        return game.getRealPlayers().stream()
                .anyMatch(player -> player.hasTech("ee")
                        && player.getPlanets().contains(planet.getName())
                        && !planet.getTechSpecialities().isEmpty());
    }

    public static void syncEcologicalEngineeringAttachments(Game game) {
        for (Planet planet : game.getPlanetsInfo().values()) {
            planet.removeToken(EE_RES_TOKEN);
            planet.removeToken(EE_INF_TOKEN);
        }

        for (Player player : game.getRealPlayers()) {
            if (!player.hasTech("ee")) {
                continue;
            }

            for (String planetId : player.getPlanets()) {
                Planet planet = game.getPlanetsInfo().get(planetId);
                if (planet == null) {
                    continue;
                }

                if (planet.getResources() == 0) {
                    planet.addToken(EE_RES_TOKEN);
                }
                if (planet.getInfluence() == 0) {
                    planet.addToken(EE_INF_TOKEN);
                }
            }
        }
    }

    public static void sendNearFieldCloakingCombatMessages(
            ThreadChannel threadChannel, Game game, Player player, Player opponent) {
        if (!player.hasTech("nfc")) {
            return;
        }

        List<Button> buttons = List.of(Buttons.green(
                player.factionButtonChecker() + "nfcResolve_" + opponent.getFaction(),
                "Resolve Near-Field Cloaking (Upon Combat Win)",
                TechEmojis.WarfareTech));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", if you win this combat, you may exhaust _Near-Field Cloaking_ to take 1 of your opponent's action cards at random, or ready _Near-Field Cloaking_. Press this button after the combat to resolve it.",
                buttons);
        MessageHelper.sendMessageToChannel(
                threadChannel,
                opponent.getRepresentation()
                        + ", your opponent has _Near-Field Cloaking_. Their units cannot be destroyed by your action cards or hits produced by them.");
    }

    public static Button getViralEntanglementButton(Player player) {
        return player.hasTechReady("ve")
                ? Buttons.green(
                        player.factionButtonChecker() + "veBrStart", "Use Viral Entanglement", TechEmojis.BioticTech)
                : null;
    }

    @ButtonHandler("veBrStart")
    public static void startViralEntanglement(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTechReady("ve")
                || player.getStrategicCC() < 1
                || getViralEntanglementInfantryCount(game, player) < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int strategicCC = player.getStrategicCC();
        player.exhaustTech("ve");
        player.setStrategicCC(strategicCC - 1);
        game.setStoredValue(VIRAL_ENTANGLEMENT_INFANTRY_REMAINING + player.getFaction(), "2");
        sendViralEntanglementInfantryButtons(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("veBrDestroyInfantry_")
    public static void destroyViralEntanglementInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String remainingValue = game.getStoredValue(VIRAL_ENTANGLEMENT_INFANTRY_REMAINING + player.getFaction());
        String message = player.getRepresentationNoPing() + ", destroy " + remainingValue
                + " infantry for _Viral Entanglement_.";
        String prefix = player.factionButtonChecker() + "veBrDestroyInfantry_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                getViralEntanglementInfantryButtons(game, player),
                message,
                prefix,
                buttonID)) {
            return;
        }
        String[] parts =
                StringUtils.substringAfter(buttonID, "veBrDestroyInfantry_").split("~", 2);
        Tile tile = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        int remaining = StringUtils.isNumeric(remainingValue) ? Integer.parseInt(remainingValue) : 0;
        if (remaining < 1 || unitHolder == null || unitHolder.getUnitCount(UnitType.Infantry, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnits(
                event, tile, game, player.getColor(), "1 infantry " + unitHolder.getName(), false);
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            game.setStoredValue(
                    VIRAL_ENTANGLEMENT_INFANTRY_REMAINING + player.getFaction(), Integer.toString(remaining - 1));
            sendViralEntanglementInfantryButtons(game, player);
            return;
        }

        game.removeStoredValue(VIRAL_ENTANGLEMENT_INFANTRY_REMAINING + player.getFaction());
        List<Button> buttons = TeHelperActionCards.getReadiedStrategyCardSecondaryButtons(game, player);
        buttons.add(Buttons.red("deleteButtons", "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose a strategy-card secondary ability to resolve with _Viral Entanglement_. A strategy token has already been spent. You cannot perform that strategy card's primary ability during this effect.",
                buttons);
    }

    private static void sendViralEntanglementInfantryButtons(Game game, Player player) {
        String remaining = game.getStoredValue(VIRAL_ENTANGLEMENT_INFANTRY_REMAINING + player.getFaction());
        List<Button> buttons = getViralEntanglementInfantryButtons(game, player);
        String prefix = player.factionButtonChecker() + "veBrDestroyInfantry_";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", destroy " + remaining + " infantry for _Viral Entanglement_.",
                NewStuffHelper.buttonPagination(buttons, prefix, 0));
    }

    private static List<Button> getViralEntanglementInfantryButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player) < 1) {
                    continue;
                }
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "veBrDestroyInfantry_" + tile.getPosition() + "~"
                                + unitHolder.getName(),
                        "Destroy 1 Infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
            }
        }
        return buttons;
    }

    private static int getViralEntanglementInfantryCount(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .mapToInt(unitHolder -> unitHolder.getUnitCount(UnitType.Infantry, player))
                .sum();
    }

    @ButtonHandler("nfcResolve_")
    public static void resolveNearFieldCloaking(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player opponent = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(buttonID, "nfcResolve_"));
        if (!player.hasTech("nfc") || opponent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.hasTechReady("nfc")) {
            player.refreshTech("nfc");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationNoPing() + " readied _Near-Field Cloaking_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(Buttons.green(
                player.factionButtonChecker() + "nfcTakeActionCard_" + opponent.getFaction(),
                "Take 1 Random Action Card from " + opponent.getFactionNameOrColor()));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose a target for _Near-Field Cloaking_:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nfcTakeActionCard_")
    public static void takeNearFieldCloakingActionCard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player opponent = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(buttonID, "nfcTakeActionCard_"));
        if (!player.hasTechReady("nfc") || opponent == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustTech("nfc");
        ActionCardHelper.sendRandomACPart2(event, game, opponent, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerNetworkedConstructors(Game game, Tile tile) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTechReady("nc")
                    || getControlledPlanets(tile, player).isEmpty()
                    || !hasStructureReinforcement(game, player)) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.green(
                            player.factionButtonChecker() + "ncBrUse_" + tile.getPosition(),
                            "Exhaust Networked Constructors",
                            TechEmojis.CyberneticTech),
                    Buttons.gray(player.factionButtonChecker() + "ncBrDecline", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", a system was activated containing a planet you control. You may exhaust _Networked Constructors_ to place 1 structure from your reinforcements on one of those planets.",
                    buttons);
        }
    }

    @ButtonHandler("ncBrUse_")
    public static void useNetworkedConstructors(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePosition = StringUtils.substringAfter(buttonID, "ncBrUse_");
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null
                || !tilePosition.equals(game.getActiveSystem())
                || !player.hasTechReady("nc")
                || getControlledPlanets(tile, player).isEmpty()
                || !hasStructureReinforcement(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.exhaustTech("nc");
        game.setStoredValue(NETWORKED_CONSTRUCTORS_PENDING + player.getFaction(), tilePosition);
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : getControlledPlanets(tile, player)) {
            if (hasReinforcement(game, player, UnitType.Pds)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "ncBrPlace_" + tilePosition + "~" + planet.getName() + "~pds",
                        "Place 1 PDS on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
            if (hasReinforcement(game, player, UnitType.Spacedock)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "ncBrPlace_" + tilePosition + "~" + planet.getName() + "~sd",
                        "Place 1 Space Dock on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a structure to place with _Networked Constructors_:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ncBrPlace_")
    public static void placeNetworkedConstructorsStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "ncBrPlace_").split("~", 3);
        if (parts.length != 3
                || !parts[0].equals(game.getActiveSystem())
                || !parts[0].equals(game.getStoredValue(NETWORKED_CONSTRUCTORS_PENDING + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        Planet planet = tile == null
                ? null
                : tile.getUnitHolders().get(parts[1]) instanceof Planet candidate ? candidate : null;
        UnitType unitType = "pds".equals(parts[2]) ? UnitType.Pds : "sd".equals(parts[2]) ? UnitType.Spacedock : null;
        if (planet == null
                || unitType == null
                || !player.getPlanets().contains(planet.getName())
                || !hasReinforcement(game, player, unitType)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + parts[2] + " " + planet.getName());
        game.removeStoredValue(NETWORKED_CONSTRUCTORS_PENDING + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 " + (unitType == UnitType.Pds ? "PDS" : "space dock")
                        + " on " + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " with _Networked Constructors_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ncBrDecline")
    public static void declineNetworkedConstructors(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    private static List<Planet> getControlledPlanets(Tile tile, Player player) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .toList();
    }

    private static boolean hasStructureReinforcement(Game game, Player player) {
        return hasReinforcement(game, player, UnitType.Pds) || hasReinforcement(game, player, UnitType.Spacedock);
    }

    private static boolean hasReinforcement(Game game, Player player, UnitType unitType) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unitType.value, true)
                < player.getUnitCap(unitType.value);
    }

    public static void addAtmanWeaveSuppressorsButton(List<Button> buttons, Player player, Tile tile) {
        if (!player.hasTechReady("aws")) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "exhaustTech_aws_" + tile.getPosition(),
                "Use Atman-Weave Suppressors",
                TechEmojis.BioticTech));
    }

    public static void addStasisFieldGeneratorsButton(List<Button> buttons, Player player) {
        if (player.hasTech("sfg")) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "getReleaseButtons",
                    "Release Captured Units",
                    TechEmojis.WarfareTech));
        }
    }

    public static void registerStasisFieldGeneratorsUnitAbilityHits(
            Game game, Player player, Player target, Tile tile, CombatRollType rollType, int hits) {
        if (rollType == CombatRollType.combatround
                || !player.hasTech("sfg")
                || target == null
                || target == player
                || hits < 1) {
            return;
        }

        game.setStoredValue(getStasisFieldGeneratorsHitsKey(tile, target), player.getFaction() + "|" + hits);
    }

    public static Player consumeStasisFieldGeneratorsCapture(Game game, RemoveUnitService.RemovedUnit unit) {
        Player destroyedUnitOwner = unit.getPlayer(game);
        if (destroyedUnitOwner == null) {
            return null;
        }

        String key = getStasisFieldGeneratorsHitsKey(unit.tile(), destroyedUnitOwner);
        String[] state = game.getStoredValue(key).split("\\|", 2);
        if (state.length != 2) {
            return null;
        }

        Player capturer = game.getPlayerFromColorOrFaction(state[0]);
        int remainingHits;
        try {
            remainingHits = Integer.parseInt(state[1]);
        } catch (NumberFormatException e) {
            game.removeStoredValue(key);
            return null;
        }
        if (capturer == null || !capturer.hasTech("sfg") || remainingHits < 1) {
            game.removeStoredValue(key);
            return null;
        }

        int remainingAfterCapture = remainingHits - unit.getTotalRemoved();
        if (remainingAfterCapture > 0) {
            game.setStoredValue(key, capturer.getFaction() + "|" + remainingAfterCapture);
        } else {
            game.removeStoredValue(key);
        }
        return capturer;
    }

    public static void captureWithStasisFieldGenerators(
            GenericInteractionCreateEvent event, Game game, Player capturer, RemoveUnitService.RemovedUnit unit) {
        Player destroyedUnitOwner = unit.getPlayer(game);
        if (destroyedUnitOwner == null || capturer == destroyedUnitOwner) {
            return;
        }

        int amount = unit.getTotalRemoved();
        AddUnitService.addUnits(
                event,
                capturer.getNomboxTile(),
                game,
                destroyedUnitOwner.getColor(),
                amount + " " + unit.unitKey().unitName());
        MessageHelper.sendMessageToChannel(
                capturer.getCorrectChannel(),
                capturer.getRepresentationNoPing() + " captured " + amount + " "
                        + unit.unitKey().unitName() + (amount == 1 ? "" : "s") + " with _Stasis Field Generators_.");
    }

    public static String getStasisFieldGeneratorsHitAssignmentReminder(
            Game game, Player player, CombatRollType rollType) {
        if (rollType == CombatRollType.combatround || !player.hasTech("sfg")) {
            return "";
        }
        String owner = game.isFowMode() ? "The player who rolled these hits" : player.getRepresentationNoPing();
        return "\n-# " + owner + " has _Stasis Field Generators_; these hits cannot be canceled.";
    }

    private static String getStasisFieldGeneratorsHitsKey(Tile tile, Player target) {
        return STASIS_FIELD_GENERATORS_HITS + tile.getPosition() + "_" + target.getFaction();
    }

    public static void offerStasisFieldGeneratorsReleasePrompt(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder combatOnHolder,
            CombatRollType rollType,
            String bombardPlanet,
            CombatRollPayload payload) {
        int nearMisses = getStasisFieldGeneratorsNearMissCount(payload);
        int potentialHits = Math.min(nearMisses, getStasisFieldGeneratorsCapturedUnitCount(player));
        if (!player.hasTech("sfg") || opponent == null || tile == null || combatOnHolder == null || potentialHits < 1) {
            return;
        }
        String resolutionId = UUID.randomUUID().toString().substring(0, 12);
        game.setStoredValue(
                STASIS_FIELD_GENERATORS_RELEASE + resolutionId,
                String.join(
                        "~",
                        tile.getPosition(),
                        combatOnHolder.getName(),
                        opponent.getFaction(),
                        rollType.name(),
                        bombardPlanet,
                        Integer.toString(potentialHits)));

        MessageHelper.sendMessageToChannelWithButton(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", up to " + potentialHits + " die" + (potentialHits == 1 ? " could" : "s could")
                        + " hit with +1. You may return 1 captured unit for each die you choose to increase with _Stasis Field Generators_.",
                Buttons.gray(
                        player.factionButtonChecker() + "sfgReturnCaptured_" + resolutionId,
                        "Return Captured Units (+1 to up to " + potentialHits + " Dice)",
                        TechEmojis.WarfareTech));
    }

    private static int getStasisFieldGeneratorsNearMissCount(CombatRollPayload payload) {
        return payload.unitRolls().stream()
                .flatMap(unitRoll -> unitRoll.dice().stream())
                .mapToInt(die -> !die.success() && die.result() + 1 >= die.threshold() ? 1 : 0)
                .sum();
    }

    @ButtonHandler("sfgReturnCaptured_")
    public static void chooseStasisFieldGeneratorsReturnCount(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String resolutionId = StringUtils.substringAfter(buttonID, "sfgReturnCaptured_");
        int maxReturns = getStasisFieldGeneratorsPotentialHits(game, player, resolutionId);
        if (maxReturns < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (int count = 1; count <= maxReturns; count++) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "sfgChooseReturnCount_" + resolutionId + "~" + count,
                    "Return " + count + " Captured Unit"
                            + (count == 1 ? " (+1 to 1 Die)" : "s (+1 to " + count + " Dice)"),
                    TechEmojis.WarfareTech));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose how many captured units to return for _Stasis Field Generators_:",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sfgChooseReturnCount_")
    public static void chooseStasisFieldGeneratorsCapturedUnits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts =
                StringUtils.substringAfter(buttonID, "sfgChooseReturnCount_").split("~", 2);
        String resolutionId = parts.length == 2 ? parts[0] : "";
        int count = parts.length == 2 ? parseStasisFieldGeneratorsCount(parts[1]) : 0;
        if (count < 1 || count > getStasisFieldGeneratorsPotentialHits(game, player, resolutionId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendStasisFieldGeneratorsCapturedUnitButtons(event, game, player, resolutionId, count, count);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sfgReturnUnit_")
    public static void returnStasisFieldGeneratorsCapturedUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = StringUtils.substringAfter(buttonID, "sfgReturnUnit_").split("~", 5);
        if (parts.length != 5) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String resolutionId = parts[0];
        int remaining = parseStasisFieldGeneratorsCount(parts[1]);
        int total = parseStasisFieldGeneratorsCount(parts[2]);
        Player capturedUnitOwner = game.getPlayerFromColorOrFaction(parts[3]);
        String unitName = parts[4];
        UnitKey unitKey = capturedUnitOwner == null
                ? null
                : Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), capturedUnitOwner.getColor());
        if (remaining < 1
                || total < remaining
                || !player.hasTech("sfg")
                || capturedUnitOwner == null
                || unitKey == null
                || total > getStasisFieldGeneratorsMaximumHits(game, resolutionId)
                || player.getNomboxTile().getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnits(
                event, player.getNomboxTile(), game, capturedUnitOwner.getColor(), "1 " + unitName);
        int remainingAfterReturn = remaining - 1;
        if (remainingAfterReturn > 0) {
            sendStasisFieldGeneratorsCapturedUnitButtons(
                    event, game, player, resolutionId, remainingAfterReturn, total);
        } else {
            resolveStasisFieldGeneratorsReturnedHits(event, game, player, resolutionId, total);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void sendStasisFieldGeneratorsCapturedUnitButtons(
            ButtonInteractionEvent event, Game game, Player player, String resolutionId, int remaining, int total) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey :
                player.getNomboxTile().getUnitHolders().get("space").getUnits().keySet()) {
            for (Player capturedUnitOwner : game.getRealPlayersNNeutral()) {
                if (capturedUnitOwner.unitBelongsToPlayer(unitKey)) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + "sfgReturnUnit_" + resolutionId + "~" + remaining + "~"
                                    + total + "~" + capturedUnitOwner.getFaction() + "~" + unitKey.unitName(),
                            "Return 1 " + unitKey.unitName() + " (" + remaining + " remaining)"));
                }
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", select a captured unit to return for _Stasis Field Generators_:",
                buttons);
    }

    private static int getStasisFieldGeneratorsCapturedUnitCount(Player player) {
        return player.getNomboxTile().getUnitHolders().get("space").getUnits().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static int getStasisFieldGeneratorsPotentialHits(Game game, Player player, String resolutionId) {
        if (!player.hasTech("sfg")) {
            return 0;
        }
        return Math.min(
                getStasisFieldGeneratorsMaximumHits(game, resolutionId),
                getStasisFieldGeneratorsCapturedUnitCount(player));
    }

    private static int getStasisFieldGeneratorsMaximumHits(Game game, String resolutionId) {
        String[] context = game.getStoredValue(STASIS_FIELD_GENERATORS_RELEASE + resolutionId)
                .split("~", 6);
        return context.length == 6 ? parseStasisFieldGeneratorsCount(context[5]) : 0;
    }

    private static void resolveStasisFieldGeneratorsReturnedHits(
            ButtonInteractionEvent event, Game game, Player player, String resolutionId, int hits) {
        String[] context = game.getStoredValue(STASIS_FIELD_GENERATORS_RELEASE + resolutionId)
                .split("~", 6);
        game.removeStoredValue(STASIS_FIELD_GENERATORS_RELEASE + resolutionId);
        if (context.length != 6) {
            return;
        }
        Tile tile = game.getTileByPosition(context[0]);
        UnitHolder combatOnHolder = tile == null ? null : tile.getUnitHolders().get(context[1]);
        Player opponent = game.getPlayerFromColorOrFaction(context[2]);
        CombatRollType rollType;
        try {
            rollType = CombatRollType.valueOf(context[3]);
        } catch (IllegalArgumentException e) {
            return;
        }
        Planet groundCombatPlanet = rollType == CombatRollType.bombardment
                ? game.getUnitHolderFromPlanet(context[4])
                : rollType == CombatRollType.combatround && combatOnHolder instanceof Planet planet ? planet : null;
        if (tile == null || opponent == null) {
            return;
        }
        if (groundCombatPlanet != null) {
            CombatRollService.sendGroundCombatHitAssignment(
                    game, player, opponent, tile, groundCombatPlanet, hits, event, "_Stasis Field Generators_");
        } else {
            CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, hits);
        }
    }

    private static int parseStasisFieldGeneratorsCount(String buttonID, String prefix) {
        return parseStasisFieldGeneratorsCount(StringUtils.substringAfter(buttonID, prefix));
    }

    private static int parseStasisFieldGeneratorsCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void offerAtmanWeaveSuppressorsPlanets(
            GenericInteractionCreateEvent event, Game game, Player player, String tilePosition) {
        if (!player.hasTech("aws")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null && combat != null) {
            tile = game.getTileByPosition(combat.tilePosition());
        }
        if (tile == null) {
            tile = game.getTileByPosition(game.getActiveSystem());
        }
        if (tile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Planet> planets = tile.getPlanetUnitHolders();
        if (planets.size() == 1) {
            resolveAtmanWeaveSuppressorsOnPlanet(
                    event, game, player, planets.getFirst().getName(), false);
            return;
        }
        List<Button> buttons = planets.stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "awsBrResolvePlanet_" + planet.getName(),
                        "Use Atman-Weave Suppressors on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        TechEmojis.BioticTech))
                .toList();
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", choose a planet for _Atman-Weave Suppressors_.",
                    buttons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("awsBrResolvePlanet_")
    public static void resolveAtmanWeaveSuppressorsPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveAtmanWeaveSuppressorsOnPlanet(
                event, game, player, StringUtils.substringAfter(buttonID, "awsBrResolvePlanet_"), true);
    }

    private static void resolveAtmanWeaveSuppressorsOnPlanet(
            GenericInteractionCreateEvent event, Game game, Player player, String planetName, boolean deleteMessage) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.hasTech("aws") || planet == null) {
            deleteAtmanWeaveSuppressorsButton(event, deleteMessage);
            return;
        }
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                    .filter(otherPlayer -> otherPlayer != player)
                    .findFirst()
                    .ifPresent(otherPlayer -> {
                        if (player.getPlanets().contains(planetName)) {
                            AddPlanetService.addPlanet(otherPlayer, planetName, game);
                        }
                    });
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Atman-Weave Suppressors_ on "
                        + Helper.getPlanetRepresentation(planetName, game) + "; units are now coexisting.");
        deleteAtmanWeaveSuppressorsButton(event, deleteMessage);
    }

    private static void deleteAtmanWeaveSuppressorsButton(GenericInteractionCreateEvent event, boolean deleteMessage) {
        if (deleteMessage) {
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

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
