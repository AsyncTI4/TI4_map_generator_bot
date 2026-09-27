package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class MonumentsDSButtonHandler {
    private static final String USE_CONCLAVE = "useAiConclave";
    private static final String DEPLOY_TUCC = "deployTuccAcademy_";
    private static final String EXPLORE_TUCC = "exploreTuccAcademy_";
    private static final String CELDAURI_MONUMENT_COMMIT = "celdauriMonumentCommit_";
    private static final String USE_CYCLOTRON = "useCyclotron";
    private static final String USE_TWILIGHT_THRONE = "useTwilightThrone";
    private static final String REVEAL_FREE_SYSTEMS_PROMISSORY = "revealFreeSystemsPromissory_";
    private static final String PRODUCE_FLORZEN_STASIS_FIGHTER = "produceFlorzenStasisFighter_";
    private static final String RELEASE_FLORZEN_STASIS_FIGHTER = "releaseFlorzenStasisFighter_";
    private static final String USE_FLORZEN_STASIS_PRODUCTION = "useFlorzenStasisProduction";
    private static final String FLORZEN_STASIS_PRODUCTION = "florzenMonument_";
    private static final String FLORZEN_STASIS_HELD = "florzenStasisHeld_";
    private static final String USE_HALO = "useVerdantHalo";
    private static final String EXHAUST_CORE_PLANET = "exhaustCorePlanet_";
    private static final String SELECT_VERDANT_HALO_PLAYER = "selectVerdantHaloPlayer_";
    private static final String VERDANT_HALO_EXHAUSTED_PLANETS = "verdantHaloExhaustedPlanets_";
    private static final String KJALENGARD_MONUMENT_USED = "kjalengardMonumentUsed_";
    private static final String USE_KJALENGARD_MONUMENT = "useKjalengardMonument_";
    private static final String SELECT_KJALENGARD_MONUMENT_WINNER = "selectKjalengardMonumentWinner_";
    private static final String USE_KOLLECC_MONUMENT = "useKolleccMonument_";
    private static final String PLACE_KOLLECC_MONUMENT_UNIT = "placeKolleccMonumentUnit_";
    private static final String USE_KOLUME_MONUMENT = "useKolumeMonument";
    private static final String SELECT_KOLUME_MONUMENT_SYSTEM = "kolumeSystem_";
    private static final String SHOW_KOLUME_MONUMENT_UNITS = "kolumeCannon_";
    private static final String SELECT_KOLUME_MONUMENT_TARGET = "kolumeTarget_";
    private static final String FIRE_KOLUME_MONUMENT_SPACE_CANNON = "kolumeFire_";
    private static final String PLACE_KYRO_RELIQUARY = "placeKyroReliquary_";
    private static final String USE_FORBIDDEN_LIBRARY = "useForbiddenLibrary";
    private static final String SELECT_FORBIDDEN_LIBRARY_PLANET = "selectForbiddenLibraryPlanet_";
    private static final String FORBIDDEN_LIBRARY_USED = "lanefirMonumentUsed_";
    private static final String REMOVE_NIGHTFALL_CC = "removeNightfallCC_";
    private static final String GAIN_NIGHTFALL_CC = "gainNightfallCC";
    private static final String USE_MIRRORFORGE = "useMirrorforge";
    private static final String SELECT_MIRRORFORGE_SOURCE = "selectMirrorforgeSource_";
    private static final String MOVE_MIRRORFORGE_SHIP = "moveMirrorforgeShip_";
    private static final String MIRRORFORGE_MOVEMENT = "mortheusMonumentMovement_";
    private static final String FLIP_VAULT = "flipVaultToReliquat";
    private static final String FLIP_RELIQUAT = "flipReliquatToVault";
    private static final String USE_DAWNSTAR_HQ = "useDawnstarHq";
    private static final String DESTROY_DAWNSTAR_UNIT = "destroyDawnstarUnit_";
    private static final String RESOLVE_BB = "resolveBountyBrokerage_";
    private static final String DRAW_AYLOR_AC = "drawAylorAc";

    // Aylor Raider Hoard
    public static Button getAylorButton(Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + DRAW_AYLOR_AC + tile.getPosition(),
                "Draw 1 AC (On TG Spend)",
                FactionEmojis.vaylerian);
    }

    @ButtonHandler(DRAW_AYLOR_AC)
    public static void drawAylorAc(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(DRAW_AYLOR_AC.length()));
        if (tile == null
                || !game.isMonumentsMode()
                || !MonumentsService.isMonumentReady(game, player, "vaylerian_monument")
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "vaylerian_monument", tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        MonumentsService.exhaustMonument(game, player, "vaylerian_monument");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " exhausted _Aylor Raider Hoard_ to draw an action card.");
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Perdition Array
    public static boolean hasVaylerianMonumentCommodityBonus(Game game, Player player) {
        return game.isMonumentsMode()
                && MonumentsService.isMonumentOnBoard(game, player, "vaylerian_monument")
                && MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player).stream()
                        .anyMatch(tile -> game.getRealPlayers().stream()
                                .anyMatch(otherPlayer -> otherPlayer != player
                                        && FoWHelper.playerHasActualShipsInSystem(otherPlayer, tile)));
    }

    public static void sendPerditionArrayReminder(MessageChannel channel, Game game, Tile tile, Player opponent) {
        List<Player> monumentOwners = game.getRealPlayers().stream()
                .filter(player -> player != opponent)
                .filter(player -> MonumentsService.isMonumentOnBoard(game, player, "veldyr_monument"))
                .filter(player -> tile == MonumentsService.getMonumentTile(game, player, "veldyr_monument"))
                .toList();
        if (monumentOwners.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannel(
                channel,
                opponent.getRepresentationNoPing() + ", _Perdition Array_ means hits produced by "
                        + monumentOwners.stream()
                                .map(Player::getRepresentationNoPing)
                                .collect(java.util.stream.Collectors.joining(", "))
                        + "'s SPACE CANNON abilities in this system cannot be canceled.");
    }

    // Bounty Brokerage
    public static void offerBountyBrokerage(Game game, Player player, Player target) {
        offerBountyBrokerage(game, player, target, false);
    }

    public static void offerBountyBrokerage(Game game, Player player, Player target, boolean wasWashed) {
        if (!game.isMonumentsMode()
                || player == target
                || (target.getCommodities() < 1 && (!wasWashed || target.getTg() < 1))
                || !MonumentsService.isMonumentOnBoard(game, player, "vaden_monument")
                || player.getDebtTokenCount(target.getColor(), Constants.VADEN_DEBT_POOL) < 1
                || MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player).stream()
                        .noneMatch(tile -> FoWHelper.playerHasActualShipsInSystem(target, tile))) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you may take 1 commodity from "
                        + target.getRepresentationNoPing() + " with _Bounty Brokerage_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker()
                                        + RESOLVE_BB
                                        + target.getFaction()
                                        + (wasWashed ? "|washed" : ""),
                                "Take 1 Commodity from " + target.getColor(),
                                FactionEmojis.vaden),
                        Buttons.red("deleteButtons", "Decline")));
    }

    @ButtonHandler(RESOLVE_BB)
    public static void resolveBountyBrokerage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(RESOLVE_BB.length()).split("\\|", 2);
        String targetFaction = payload[0];
        boolean wasWashed = payload.length > 1 && "washed".equals(payload[1]);
        Player target = targetFaction.isBlank() ? null : game.getPlayerFromColorOrFaction(targetFaction);
        if (!game.isMonumentsMode()
                || target == null
                || target == player
                || (target.getCommodities() < 1 && (!wasWashed || target.getTg() < 1))
                || !MonumentsService.isMonumentOnBoard(game, player, "vaden_monument")
                || player.getDebtTokenCount(target.getColor(), Constants.VADEN_DEBT_POOL) < 1
                || MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player).stream()
                        .noneMatch(tile -> FoWHelper.playerHasActualShipsInSystem(target, tile))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (target.getCommodities() > 0) {
            target.setCommodities(target.getCommodities() - 1);
        } else {
            target.setTg(target.getTg() - 1);
        }
        player.gainTG(1);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);

        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentation()
                        + " the Vaden have come to collect on their bounty, and 1 commodity has been taken from you.");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " took 1 commodity from " + target.getRepresentationNoPing()
                        + " with _Bounty Brokerage_.");

        ButtonHelper.deleteMessage(event);
    }

    // Vault of New Phrad
    public static List<Button> getVaultFlipButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + FLIP_VAULT, "Flip Vault", FactionEmojis.zealots));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    @ButtonHandler(FLIP_VAULT)
    public static void flipVaultToReliquat(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "rhodun_monument")) {
            return;
        }

        player.removeOwnedUnitByID("rhodun_monument");
        player.addOwnedUnitByID("rhodun_monumentback");

        UnitModel monument = Mapper.getUnit("rhodun_monumentback");

        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " flipped _Vault of New Phrad_.",
                monument.getRepresentationEmbed());

        ButtonHelper.deleteMessage(event);
    }

    // Reliquat Unleashed
    public static Button getReliquatFlipButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + FLIP_RELIQUAT, "Flip Reliquat", FactionEmojis.zealots);
    }

    @ButtonHandler(FLIP_RELIQUAT)
    public static void flipReliquatToVault(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "rhodun_monumentback")) {
            return;
        }

        player.removeOwnedUnitByID("rhodun_monumentback");
        player.addOwnedUnitByID("rhodun_monument");

        UnitModel monument = Mapper.getUnit("rhodun_monument");

        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " flipped _Reliquat Unleashed_.",
                monument.getRepresentationEmbed());

        ButtonHelper.deleteMessage(event);
    }

    // Freehold Starport
    public static boolean canMoveOutOfFreeholdSystem(Game game, Player player, Tile tile) {
        return game.isMonumentsMode() && tile == MonumentsService.getMonumentTile(game, player, "nokar_monument");
    }

    // The Maw
    public static boolean blocksNivynMonumentMovement(Game game, Player movingPlayer, Tile tile) {
        if (!game.isMonumentsMode() || movingPlayer == null || tile == null) {
            return false;
        }

        return game.getRealPlayers().stream()
                .anyMatch(monumentOwner -> monumentOwner != movingPlayer
                        && MonumentsService.isMonumentOnBoard(game, monumentOwner, "nivyn_monument")
                        && tile == MonumentsService.getMonumentTile(game, monumentOwner, "nivyn_monument"));
    }

    // Gravelord's Keep
    public static List<Button> getGravelordProduceFighterButton(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "mykomentori_monument");
        if (monumentTile == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "placeOneNDone_skipbuild_1ff_" + monumentTile.getPosition(),
                "Produce 1 Fighter",
                UnitEmojis.fighter));
        buttons.add(Buttons.red("deleteButtons", "Done"));

        return buttons;
    }

    // Mirrorforge
    public static Button getMirrorforgeButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_MIRRORFORGE, "Use Mirrorforge", FactionEmojis.mortheus);
    }

    public static void clearMirrorforgeActionState(Game game, Player player) {
        if (game.isMonumentsMode()) {
            game.removeStoredValue(MIRRORFORGE_MOVEMENT + player.getFaction());
        }
    }

    @ButtonHandler(USE_MIRRORFORGE)
    public static void useMirrorforge(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "mortheus_monument")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "mortheus_monument");
        if (monumentTile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getMirrorforgeSourceButtons(game, player, monumentTile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " has no adjacent non-fighter ships to move with _Mirrorforge_.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the ships to move with _Mirrorforge_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT_MIRRORFORGE_SOURCE, 0));
        ButtonHelper.deleteTheOneButton(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    @ButtonHandler(SELECT_MIRRORFORGE_SOURCE)
    public static void selectMirrorforgeSource(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_MIRRORFORGE_SOURCE.length()).split("\\|", 2);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "mortheus_monument");
        if (payload.length == 1 && payload[0].startsWith("page") && monumentTile != null) {
            try {
                int page = Integer.parseInt(payload[0].substring(4));
                String message = player.getRepresentationUnfogged() + ", choose the ships to move with _Mirrorforge_.";
                NewStuffHelper.sendOrEditButtons(
                        event,
                        player.getCorrectChannel(),
                        message,
                        NewStuffHelper.buttonPagination(
                                getMirrorforgeSourceButtons(game, player, monumentTile),
                                player.factionButtonChecker() + SELECT_MIRRORFORGE_SOURCE,
                                page));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteTheOneButton(event);
            }
            return;
        }
        Player opponent = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile sourceTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (opponent == null
                || sourceTile == null
                || monumentTile == null
                || opponent == player
                || !MonumentsService.exhaustMonument(game, player, "mortheus_monument")
                || !FoWHelper.getAdjacentTilesAndNotThisTile(game, monumentTile.getPosition(), player, false)
                        .contains(sourceTile.getPosition())) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(
                MIRRORFORGE_MOVEMENT + player.getFaction(),
                opponent.getFaction() + "|" + sourceTile.getPosition() + "|0");
        sendMirrorforgeShipButtons(event, game, player, opponent, sourceTile, monumentTile, 0F);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getMirrorforgeSourceButtons(Game game, Player player, Tile monumentTile) {
        List<Button> buttons = new ArrayList<>();
        for (String position :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, monumentTile.getPosition(), player, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null
                    || (tile.getTileModel() != null && tile.getTileModel().isHyperlane())) {
                continue;
            }
            for (Player opponent : game.getRealPlayersNNeutral()) {
                if (opponent == player
                        || tile.getSpaceUnitHolder().getUnitKeysForPlayer(opponent).stream()
                                .map(opponent::getUnitFromUnitKey)
                                .noneMatch(unit ->
                                        unit != null && unit.getIsShip() && unit.getUnitType() != UnitType.Fighter)) {
                    continue;
                }
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_MIRRORFORGE_SOURCE + opponent.getFaction() + "|"
                                + position,
                        "Move " + opponent.getColor() + " ships from " + tile.getRepresentationForButtons()));
            }
        }
        return buttons;
    }

    @ButtonHandler(MOVE_MIRRORFORGE_SHIP)
    public static void moveMirrorforgeShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String movement = game.getStoredValue(MIRRORFORGE_MOVEMENT + player.getFaction());
        String[] movementParts = movement.split("\\|", 3);
        Player opponent = movementParts.length == 3 ? game.getPlayerFromColorOrFaction(movementParts[0]) : null;
        Tile sourceTile = movementParts.length == 3 ? game.getTileByPosition(movementParts[1]) : null;
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "mortheus_monument");
        float movedCost;
        try {
            movedCost = movementParts.length == 3 ? Float.parseFloat(movementParts[2]) : -1F;
        } catch (NumberFormatException e) {
            movedCost = -1F;
        }
        String asyncId = buttonID.substring(MOVE_MIRRORFORGE_SHIP.length());
        UnitKey unitKey = opponent == null || sourceTile == null
                ? null
                : sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(opponent).stream()
                        .filter(key -> asyncId.equals(key.asyncID()))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null || opponent == null ? null : opponent.getUnitFromUnitKey(unitKey);
        if (opponent == null
                || sourceTile == null
                || monumentTile == null
                || opponent == player
                || movedCost < 0
                || unit == null
                || !unit.getIsShip()
                || unit.getUnitType() == UnitType.Fighter
                || sourceTile.getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MoveUnitService.moveUnits(
                event, sourceTile, game, opponent.getColor(), "1 " + asyncId, monumentTile, Constants.SPACE);
        movedCost += unit.getCost();
        if (movedCost >= 4F
                || sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(opponent).stream()
                        .map(opponent::getUnitFromUnitKey)
                        .noneMatch(model ->
                                model != null && model.getIsShip() && model.getUnitType() != UnitType.Fighter)) {
            game.removeStoredValue(MIRRORFORGE_MOVEMENT + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " moved " + movedCost + " resources worth of "
                            + opponent.getRepresentationNoPing() + " ships into "
                            + monumentTile.getRepresentationForButtons()
                            + " with _Mirrorforge_.");
            StartCombatService.startSpaceCombat(game, player, opponent, monumentTile, event, "-mirrorforge");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(
                MIRRORFORGE_MOVEMENT + player.getFaction(),
                opponent.getFaction() + "|" + sourceTile.getPosition() + "|" + movedCost);
        sendMirrorforgeShipButtons(event, game, player, opponent, sourceTile, monumentTile, movedCost);
        ButtonHelper.deleteMessage(event);
    }

    private static void sendMirrorforgeShipButtons(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            Player opponent,
            Tile sourceTile,
            Tile monumentTile,
            float movedCost) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(opponent)) {
            UnitModel unit = opponent.getUnitFromUnitKey(unitKey);
            if (unit == null || !unit.getIsShip() || unit.getUnitType() == UnitType.Fighter) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + MOVE_MIRRORFORGE_SHIP + unitKey.asyncID(),
                    "Move 1 " + unit.getName() + " (" + unit.getCost() + ")",
                    unit.getUnitEmoji()));
        }
        if (buttons.isEmpty()) {
            game.removeStoredValue(MIRRORFORGE_MOVEMENT + player.getFaction());
            StartCombatService.startSpaceCombat(game, player, opponent, monumentTile, event, "-mirrorforge");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", move non-fighter ships from "
                        + sourceTile.getRepresentationForButtons()
                        + " until at least 4 resources worth have moved. Moved: " + movedCost + "/4.",
                buttons);
    }

    // Nightfall Fortress
    public static List<Button> getNightfallButtons(Player activator, Player owner, Game game, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (player == owner && activator == owner) {
                continue;
            }
            if (!tile.hasPlayerCC(player)) {
                continue;
            }

            buttons.add(Buttons.green(
                    owner.factionButtonChecker() + REMOVE_NIGHTFALL_CC + player.getFaction() + "|" + tile.getPosition(),
                    "Remove " + player.getFactionNameOrColor() + "'s Command Token",
                    player.getFactionEmojiOrColor()));
        }
        buttons.add(Buttons.green(owner.factionButtonChecker() + GAIN_NIGHTFALL_CC, "Gain 1 Command Token"));

        return buttons;
    }

    @ButtonHandler(REMOVE_NIGHTFALL_CC)
    public static void resolveNightfallCCRemoval(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "lizho_monument")) {
            return;
        }

        String payload = buttonID.substring(REMOVE_NIGHTFALL_CC.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String target = parts[0];
        String tilePos = parts[1];

        Player targetPlayer = game.getPlayerFromColorOrFaction(target);
        Tile tile = game.getTileByPosition(tilePos);
        if (targetPlayer == null || tile == null) {
            return;
        }

        String ccID = Mapper.getCCID(targetPlayer.getColor());
        tile.removeCC(ccID);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " removed " + targetPlayer.getRepresentation()
                        + "'s command token from " + tile.getRepresentation()
                        + " using _Nightfall Fortress_.");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_NIGHTFALL_CC)
    public static void resolveNightfallCCGain(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "lizho_monument")) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please use the buttons to gain 1 command token due to _Nightfall Fortress_.",
                ButtonHelper.getGainCCButtons(player));

        ButtonHelper.deleteMessage(event);
    }

    // Forbidden Library
    public static Button getForbiddenLibraryButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_FORBIDDEN_LIBRARY, "Use Forbidden Library", FactionEmojis.lanefir);
    }

    public static void clearForbiddenLibraryActionState(Game game, Player player) {
        if (game.isMonumentsMode()) {
            game.removeStoredValue(FORBIDDEN_LIBRARY_USED + player.getFaction());
        }
    }

    @ButtonHandler(USE_FORBIDDEN_LIBRARY)
    public static void useForbiddenLibrary(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentOnBoard(game, player, "lanefir_monument")
                || !game.getStoredValue(FORBIDDEN_LIBRARY_USED + player.getFaction())
                        .isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getTechSpecialities().stream()
                        .anyMatch(type -> List.of("biotic", "cybernetic", "propulsion", "warfare")
                                .contains(type.toLowerCase()))) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + SELECT_FORBIDDEN_LIBRARY_PLANET + planet.getName(),
                            "Use " + Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " has no technology-specialty planets in or adjacent to _Forbidden Library_.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a technology-specialty planet to use with _Forbidden Library_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_FORBIDDEN_LIBRARY_PLANET)
    public static void selectForbiddenLibraryPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(SELECT_FORBIDDEN_LIBRARY_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "lanefir_monument", tile)
                || !game.getStoredValue(FORBIDDEN_LIBRARY_USED + player.getFaction())
                        .isEmpty()
                || planet.getTechSpecialities().stream()
                        .noneMatch(type -> List.of("biotic", "cybernetic", "propulsion", "warfare")
                                .contains(type.toLowerCase()))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(FORBIDDEN_LIBRARY_USED + player.getFaction(), "used");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " is using the technology specialty of "
                        + planet.getRepresentation(game) + " with _Forbidden Library_."
                        + "\n-# This is not automated, and is just alerting the table of your intent.");
        ButtonHelper.deleteMessage(event);
    }

    // Kyro Reliquary
    public static void offerKyroReliquaryRelocation(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (RemovedUnit destroyedUnit : destroyedUnits) {
            Player player = destroyedUnit.getPlayer(game);
            if (destroyedUnit.unitKey().unitType() != UnitType.Monument
                    || !(destroyedUnit.uh() instanceof Planet)
                    || !MonumentsService.hasKyroReliquary(game, player)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            List<Tile> tiles = new ArrayList<>();
            tiles.add(destroyedUnit.tile());
            FoWHelper.getAdjacentTilesAndNotThisTile(game, destroyedUnit.tile().getPosition(), player, false).stream()
                    .map(game::getTileByPosition)
                    .filter(tile -> tile != null
                            && (tile.getTileModel() == null
                                    || !tile.getTileModel().isHyperlane()))
                    .forEach(tiles::add);
            UnitModel monument = Mapper.getUnit("kyro_monument");
            for (Tile tile : tiles) {
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    if (player.getPlanets().contains(planet.getName())
                            && monument != null
                            && monument.canBePlacedOnPlanetTypes(planet.getPlanetTypes())) {
                        buttons.add(Buttons.green(
                                player.factionButtonChecker() + PLACE_KYRO_RELIQUARY
                                        + destroyedUnit.tile().getPosition() + "|" + planet.getName(),
                                "Place Kyro Reliquary on " + Helper.getPlanetRepresentation(planet.getName(), game)));
                    }
                }
            }
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", place _Kyro Reliquary_ on a planet you control in this or an adjacent system to draw a relic.",
                        buttons);
            }
        }
    }

    @ButtonHandler(PLACE_KYRO_RELIQUARY)
    public static void placeKyroReliquary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_KYRO_RELIQUARY.length()).split("\\|", 2);
        Tile destroyedTile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Tile destinationTile = payload.length == 2 ? game.getTileFromPlanet(payload[1]) : null;
        Planet destination = destinationTile == null
                ? null
                : destinationTile.getPlanetUnitHolders().stream()
                        .filter(planet -> planet.getName().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        UnitModel monument = Mapper.getUnit("kyro_monument");
        if (destroyedTile == null
                || destination == null
                || monument == null
                || !MonumentsService.hasKyroReliquary(game, player)
                || !player.getPlanets().contains(destination.getName())
                || !monument.canBePlacedOnPlanetTypes(destination.getPlanetTypes())
                || (destroyedTile != destinationTile
                        && !FoWHelper.getAdjacentTilesAndNotThisTile(game, destroyedTile.getPosition(), player, false)
                                .contains(destinationTile.getPosition()))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(event, destinationTile, game, player.getColor(), "1 monument " + destination.getName());
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    // Queen's Rest
    public static void resolveKortaliMonument(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            Tile monumentTile = MonumentsService.getMonumentTile(game, player, "kortali_monument");
            if (monumentTile == null || !MonumentsService.isMonumentOnBoard(game, player, "kortali_monument")) {
                continue;
            }
            int hits = 0;
            boolean rolled = false;
            StringBuilder rollMessage = new StringBuilder(player.getRepresentation() + " rolled for _Queen's Rest_:\n");
            for (RemovedUnit unit : destroyedUnits) {
                if (game.getPlayerFromColorOrFaction(unit.unitKey().colorID()) != player
                        || unit.tile() != monumentTile
                        || unit.unitKey().unitType() == UnitType.Fighter
                        || unit.unitKey().unitType() == UnitType.Infantry) {
                    continue;
                }
                UnitModel destroyedModel = player.getUnitFromUnitKey(unit.unitKey());
                int hitsOn = destroyedModel != null && destroyedModel.getCombatDieCount() > 0
                        ? destroyedModel.getCombatHitsOn()
                        : 8;
                List<DiceHelper.Die> dice = DiceHelper.rollDice(hitsOn, unit.getTotalRemoved());
                int unitHits = DiceHelper.countSuccesses(dice);
                hits += unitHits;
                rolled = true;
                if (destroyedModel != null) {
                    rollMessage.append(CombatMessageHelper.displayUnitRoll(
                            destroyedModel, hitsOn, 0, unit.getTotalRemoved(), 1, 0, dice, unitHits));
                } else {
                    rollMessage.append(DiceHelper.formatDiceOutput(dice));
                }
            }
            if (!rolled) {
                continue;
            }
            rollMessage.append(CombatMessageHelper.displayHitResults(hits));
            if (hits == 0) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), rollMessage.toString());
                continue;
            }
            Player target = game.getRealPlayersNNeutral().stream()
                    .filter(otherPlayer -> otherPlayer != player)
                    .filter(otherPlayer -> FoWHelper.playerHasActualShipsInSystem(otherPlayer, monumentTile))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        rollMessage + "\nNo other player's ships are in this system to assign the hits to.");
                continue;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    rollMessage + "\n" + target.getRepresentationNoPing() + " suffers " + hits + " hit"
                            + (hits == 1 ? "." : "s."));
            CombatRollService.sendSpaceAssignHitsButtons(event, game, target, monumentTile, hits);
        }
    }

    // Dawnstar HQ
    public static Button getDawnstarHqButton(Game game, Player player) {
        return getDawnstarHqTargetButtons(game, player).isEmpty()
                ? null
                : Buttons.green(
                        player.factionButtonChecker() + USE_DAWNSTAR_HQ, "Use Dawnstar HQ", FactionEmojis.tnelis);
    }

    @ButtonHandler(USE_DAWNSTAR_HQ)
    public static void useDawnstarHq(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getDawnstarHqTargetButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 other player's coexisting unit to destroy with _Dawnstar HQ_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DESTROY_DAWNSTAR_UNIT)
    public static void destroyDawnstarUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DESTROY_DAWNSTAR_UNIT.length()).split("\\|", 5);
        Tile tile = payload.length == 5 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        Player target = payload.length == 5 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        UnitState state;
        try {
            state = payload.length == 5 ? UnitState.valueOf(payload[4]) : null;
        } catch (IllegalArgumentException e) {
            state = null;
        }
        UnitKey unitKey = planet == null || target == null
                ? null
                : planet.getUnitsByStateForPlayer(target).keySet().stream()
                        .filter(key -> key.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "tnelis_monument");
        if (tile == null
                || planet == null
                || target == null
                || unitKey == null
                || state == null
                || tile != monumentTile
                || !MonumentsService.isMonumentOnBoard(game, player, "tnelis_monument")
                || !FoWHelper.playerHasUnitsOnPlanet(player, planet)
                || planet.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyUnit(
                event, tile, game, new ti4.service.unit.ParsedUnit(unitKey, 1, planet.getName()), false, state);
        UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed 1 of "
                        + target.getColorDisplayName() + "'s "
                        + (unit == null ? unitKey.unitType().humanReadableName() : unit.getName()) + " on "
                        + planet.getRepresentation(game) + " with _Dawnstar HQ_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void addDawnstarHqGroundCombatButton(
            List<Button> buttons, Game game, Tile tile, String unitHolderName) {
        Planet planet = tile.getPlanetUnitHolders().stream()
                .filter(candidate -> candidate.getName().equals(unitHolderName))
                .findFirst()
                .orElse(null);
        if (planet == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (MonumentsService.isMonumentOnBoard(game, player, "tnelis_monument")
                    && planet == MonumentsService.getPlayerMonumentPlanet(game, player)
                    && FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "destroyDawnstarAll_" + tile.getPosition() + "|"
                                + planet.getName(),
                        "Use Dawnstar HQ (On Loss)",
                        FactionEmojis.tnelis));
            }
        }
    }

    @ButtonHandler("destroyDawnstarAll_")
    public static void destroyDawnstarAll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("destroyDawnstarAll_".length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        if (tile == null
                || planet == null
                || tile != MonumentsService.getMonumentTile(game, player, "tnelis_monument")
                || !MonumentsService.isMonumentOnBoard(game, player, "tnelis_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyAllUnits(event, tile, game, planet, false);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed every unit on " + planet.getRepresentation(game)
                        + " with _Dawnstar HQ_.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    private static List<Button> getDawnstarHqTargetButtons(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "tnelis_monument");
        if (!MonumentsService.isMonumentOnBoard(game, player, "tnelis_monument") || monumentTile == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : monumentTile.getPlanetUnitHolders()) {
            if (!FoWHelper.playerHasUnitsOnPlanet(player, planet)) {
                continue;
            }
            for (Player target : game.getRealPlayersNNeutral()) {
                if (target == player || !FoWHelper.playerHasUnitsOnPlanet(target, planet)) {
                    continue;
                }
                for (UnitKey unitKey : planet.getUnitsByStateForPlayer(target).keySet()) {
                    UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), planet);
                    for (UnitState state : planet.getNonZeroUnitStates(unitKey)) {
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + DESTROY_DAWNSTAR_UNIT + monumentTile.getPosition() + "|"
                                        + planet.getName() + "|" + target.getFaction() + "|" + unitKey.asyncID()
                                        + "|" + state.name(),
                                "Destroy 1 of "
                                        + target.getColorDisplayName() + "'s "
                                        + (unit == null ? unitKey.unitType().humanReadableName() : unit.getName())
                                        + " on " + Helper.getPlanetRepresentation(planet.getName(), game),
                                unitKey.unitEmoji()));
                    }
                }
            }
        }
        return buttons;
    }

    // Krotas Bannerhall
    public static void addKjalengardMonumentButton(
            List<Button> buttons, Game game, Tile tile, Player player1, Player player2) {
        for (Player monumentOwner : game.getRealPlayers()) {
            if (!MonumentsService.isMonumentOnBoard(game, monumentOwner, "kjalengard_monument")
                    || !MonumentsService.isInOrAdjacentToMonumentSystem(
                            game, monumentOwner, "kjalengard_monument", tile)
                    || !game.getStoredValue(KJALENGARD_MONUMENT_USED + monumentOwner.getFaction())
                            .isBlank()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    monumentOwner.factionButtonChecker() + USE_KJALENGARD_MONUMENT + tile.getPosition() + "|"
                            + player1.getFaction() + "|" + player2.getFaction(),
                    "Resolve Krotas Bannerhall",
                    FactionEmojis.kjalengard));
        }
    }

    @ButtonHandler(USE_KJALENGARD_MONUMENT)
    public static void useKjalengardMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_KJALENGARD_MONUMENT.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Player player1 = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        Player player2 = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        if (tile == null
                || player1 == null
                || player2 == null
                || !MonumentsService.isMonumentOnBoard(game, player, "kjalengard_monument")
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "kjalengard_monument", tile)
                || !game.getStoredValue(KJALENGARD_MONUMENT_USED + player.getFaction())
                        .isBlank()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player combatant : List.of(player1, player2)) {
            if (combatant.isDummy() || combatant.isNpc()) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_KJALENGARD_MONUMENT_WINNER + tile.getPosition() + "|"
                            + combatant.getFaction(),
                    "Select " + combatant.getColor() + " as Winner"));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player who won combat in "
                        + tile.getRepresentationForButtons(game, player)
                        + " to resolve _Krotas Bannerhall_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_KJALENGARD_MONUMENT_WINNER)
    public static void selectKjalengardMonumentWinner(
            ButtonInteractionEvent event, Game game, Player monumentOwner, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_KJALENGARD_MONUMENT_WINNER.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player combatWinner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        if (tile == null
                || combatWinner == null
                || combatWinner.isDummy()
                || combatWinner.isNpc()
                || !MonumentsService.isMonumentOnBoard(game, monumentOwner, "kjalengard_monument")
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, monumentOwner, "kjalengard_monument", tile)
                || !game.getStoredValue(KJALENGARD_MONUMENT_USED + monumentOwner.getFaction())
                        .isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(KJALENGARD_MONUMENT_USED + monumentOwner.getFaction(), "used");
        MessageHelper.sendMessageToChannelWithButtons(
                monumentOwner.getCorrectChannel(),
                monumentOwner.getRepresentationUnfogged()
                        + ", you may gain 1 command token due to _Krotas Bannerhall_. Use the buttons to gain it, or press Done to decline.",
                ButtonHelper.getGainCCButtons(monumentOwner));
        if (combatWinner != monumentOwner) {
            MessageHelper.sendMessageToChannelWithButtons(
                    combatWinner.getCorrectChannel(),
                    combatWinner.getRepresentationUnfogged()
                            + ", you may gain 1 command token due to _Krotas Bannerhall_. Use the buttons to gain it, or press Done to decline.",
                    ButtonHelper.getGainCCButtons(combatWinner));
        }
        ButtonHelper.deleteMessage(event);
    }

    // Verdant Halo
    public static Button getVerdantHaloButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_HALO, "Use Verdant Halo", FactionEmojis.gledge);
    }

    public static boolean hasTwoReadiedCorePlanets(Player player, Game game) {
        return player.getPlanets().stream()
                        .filter(planetName -> !player.getExhaustedPlanets().contains(planetName))
                        .map(game::getUnitHolderFromPlanet)
                        .filter(planet -> planet != null)
                        .filter(planet -> planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG))
                        .count()
                >= 2;
    }

    public static List<Button> getReadiedCorePlanetButtons(Game game, Player player) {
        return player.getReadiedPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(planet -> planet != null)
                .filter(planet -> planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG))
                .map(planet -> Buttons.red(
                        player.factionButtonChecker() + EXHAUST_CORE_PLANET + planet.getName(),
                        "Exhaust " + Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    @ButtonHandler(EXHAUST_CORE_PLANET)
    public static void exhaustCorePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(EXHAUST_CORE_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        List<String> exhaustedPlanets =
                new ArrayList<>(List.of(game.getStoredValue(VERDANT_HALO_EXHAUSTED_PLANETS + player.getFaction())
                        .split("\\|")));
        exhaustedPlanets.removeIf(String::isBlank);

        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "gledge_monument")
                || exhaustedPlanets.size() >= 2
                || planet == null
                || !player.getReadiedPlanets().contains(planetName)
                || !planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        player.exhaustPlanet(planetName);
        exhaustedPlanets.add(planetName);
        game.setStoredValue(VERDANT_HALO_EXHAUSTED_PLANETS + player.getFaction(), String.join("|", exhaustedPlanets));
        if (exhaustedPlanets.size() < 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " exhausted " + planet.getRepresentation(game)
                            + " for _Verdant Halo_. Choose one more eligible planet.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_VERDANT_HALO_PLAYER + target.getFaction(),
                        "Choose " + target.getFactionModel().getShortName(),
                        target.getFactionEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player who treats all laws as blank until the end of this turn.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_HALO)
    public static void useVerdantHalo(ButtonInteractionEvent event, Player player, Game game) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "gledge_monument")
                || !hasTwoReadiedCorePlanets(player, game)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.removeStoredValue(VERDANT_HALO_EXHAUSTED_PLANETS + player.getFaction());
        List<Button> buttons = new ArrayList<>(getReadiedCorePlanetButtons(game, player));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose two planets to exhaust for _Verdant Halo_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_VERDANT_HALO_PLAYER)
    public static void selectVerdantHaloPlayer(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_VERDANT_HALO_PLAYER.length()));
        String exhaustedPlanets = game.getStoredValue(VERDANT_HALO_EXHAUSTED_PLANETS + player.getFaction());
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "gledge_monument")
                || target == null
                || exhaustedPlanets.split("\\|").length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(VERDANT_HALO_EXHAUSTED_PLANETS + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " exhausted two planets with Core tokens. "
                        + target.getRepresentationNoPing() + " treats all laws as blank until the end of this turn.");
        ButtonHelper.deleteMessage(event);
    }

    // Twilight Throne
    public static Button getTwilightThroneButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_TWILIGHT_THRONE, "Use Twilight Throne", FactionEmojis.edyn);
    }

    @ButtonHandler(USE_TWILIGHT_THRONE)
    public static void resolveTwilightThrone(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "edyn_monument")
                || !MonumentsService.isMonumentReady(game, player, "edyn_monument")) {
            return;
        }

        MonumentsService.exhaustMonument(game, player, "edyn_monument");
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " exhausted _Twilight Throne_ to produce a mech on "
                        + monumentPlanet.getRepresentation(game)
                        + " without spending resources.");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation() + ", use the production button to place the mech.",
                Buttons.green(
                        player.factionButtonChecker() + "placeOneNDone_skipbuild_mech_" + monumentPlanet.getName(),
                        "Produce Mech on " + Helper.getPlanetRepresentation(monumentPlanet.getName(), game),
                        UnitEmojis.mech));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Flotilla Cyclotron
    public static Button offerCyclotronButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_CYCLOTRON,
                "Use Flotilla Cyclotron (+1 Move)",
                FactionEmojis.dihmohn);
    }

    @ButtonHandler(USE_CYCLOTRON)
    public static void resolveCyclotron(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "dihmohn_monument")
                || !MonumentsService.isMonumentReady(game, player, "dihmohn_monument")) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "dihmohn_monument");
        if (monumentTile == null) {
            return;
        }
        MonumentsService.exhaustMonument(game, player, "dihmohn_monument");

        game.setStoredValue("dihmohnCyclotron_" + player.getFaction(), monumentTile.getPosition());

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted _Flotilla Cyclotron_ to add +1 movement to all their ships in "
                        + monumentTile.getRepresentation()
                        + " until the end of this tactical action.");

        ButtonHelper.deleteMessage(event);
    }

    // Tucc Academy
    public static boolean isPlanetNotAdjacentToHomeSystem(Game game, Planet planet, Player player) {
        Tile homeSystem = player.getHomeSystemTile();
        Tile planetTile = game.getTileFromPlanet(planet.getName());
        if (homeSystem == null || planetTile == null) {
            return false;
        }
        return !homeSystem.getPosition().equals(planetTile.getPosition())
                && !FoWHelper.getAdjacentTilesAndNotThisTile(game, homeSystem.getPosition(), player, false)
                        .contains(planetTile.getPosition());
    }

    public static Button getTuccAcademyButton(Player monumentPlayer, Player exploringPlayer, Planet exploredPlanet) {
        return Buttons.green(
                monumentPlayer.factionButtonChecker() + DEPLOY_TUCC + exploredPlanet.getName() + "|"
                        + exploringPlayer.getFaction(),
                "Deploy Tucc Academy",
                FactionEmojis.bentor);
    }

    public static List<Button> getTuccAcademyExploreButtons(Game game, Player player, Planet planet) {
        return planet.getPlanetTypes().stream()
                .filter(trait -> List.of("cultural", "industrial", "hazardous").contains(trait))
                .map(trait -> Buttons.gray(
                        player.factionButtonChecker() + EXPLORE_TUCC + planet.getName() + "|" + trait,
                        "Explore " + planet.getRepresentation(game) + " As " + StringUtils.capitalize(trait),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
    }

    @ButtonHandler(DEPLOY_TUCC)
    public static void deployTuccAcademy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || MonumentsService.isMonumentOnBoard(game, player, "bentor_monument")) {
            return;
        }

        String[] payload = buttonID.substring(DEPLOY_TUCC.length()).split("\\|", 2);
        String planetName = payload.length == 2 ? payload[0] : null;
        Player exploringPlayer = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        Tile planetTile = planetName == null ? null : game.getTileFromPlanet(planetName);
        Planet planet = planetTile == null
                ? null
                : planetTile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(planetName))
                        .findFirst()
                        .orElse(null);
        UnitModel monument = Mapper.getUnit("bentor_monument");
        if (planet == null
                || exploringPlayer == null
                || exploringPlayer == player
                || !isPlanetNotAdjacentToHomeSystem(game, planet, exploringPlayer)
                || monument == null
                || !monument.canBePlacedOnPlanetTypes(planet.getPlanetTypes())
                || planet.getUnitKeys().isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        String coexistFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 monument " + planetName);

        if (coexistFlag.isEmpty()) {
            game.removeStoredValue("coexistFlag");
        } else {
            game.setStoredValue("coexistFlag", coexistFlag);
        }

        planet = MonumentsService.getPlayerMonumentPlanet(game, player);

        List<Button> buttons = getTuccAcademyExploreButtons(game, player, planet);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed their monument into coexistence on " + planet.getRepresentation(game)
                        + ", and may now explore it.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXPLORE_TUCC)
    public static void exploreTuccAcademyPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(EXPLORE_TUCC.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileFromPlanet(payload[0]) : null;
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(candidate -> candidate.getName().equals(payload[0]))
                        .findFirst()
                        .orElse(null);
        if (planet == null
                || !MonumentsService.isMonumentOnBoard(game, player, "bentor_monument")
                || MonumentsService.getPlayerMonumentPlanet(game, player) != planet
                || !planet.getPlanetTypes().contains(payload.length == 2 ? payload[1] : "")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        ExploreService.explorePlanet(event, tile, planet.getName(), payload[1], player, false, game, 1, true);
        ButtonHelper.deleteMessage(event);
    }

    // Anvil of Atlas
    public static boolean producedNonFighterShipInMonumentSystem(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "axis_monument");
        if (monumentTile == null) {
            return false;
        }

        for (String producedUnitKey : player.getCurrentProducedUnits().keySet()) {
            int locationSeparator = producedUnitKey.lastIndexOf('_');
            int tileSeparator = producedUnitKey.lastIndexOf('_', locationSeparator - 1);
            if (tileSeparator < 0 || locationSeparator < 0) {
                continue;
            }

            String unitAlias = producedUnitKey.substring(0, tileSeparator);
            String tilePosition = producedUnitKey.substring(tileSeparator + 1, locationSeparator);
            if (!monumentTile.getPosition().equals(tilePosition)) {
                continue;
            }

            UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitAlias), player.getColor());
            UnitModel producedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).stream()
                    .findFirst()
                    .orElse(null);

            if (producedUnit != null && producedUnit.getIsShip() && producedUnit.getUnitType() != UnitType.Fighter) {
                return true;
            }
        }

        return false;
    }

    // Independence Tower
    public static void offerFreeSystemsMonumentPromissoryReveal(Game game, Player leadershipPlayer) {
        if (game == null || leadershipPlayer == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayers()) {
            Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "free_systems_monument");
            if (monumentTile == null || monumentOwner != leadershipPlayer) {
                continue;
            }
            for (Player recipient : game.getRealPlayers()) {
                if (recipient == monumentOwner
                        || !hasUnitsInOrAdjacentToFreeSystemsMonument(game, recipient, monumentTile)) {
                    continue;
                }
                List<Button> buttons = recipient.getPromissoryNotes().keySet().stream()
                        .filter(pn -> !recipient.getPromissoryNotesInPlayArea().contains(pn))
                        .filter(pn -> game.getPNOwner(pn) == monumentOwner)
                        .map(Mapper::getPromissoryNote)
                        .filter(java.util.Objects::nonNull)
                        .map(pn -> Buttons.gray(
                                recipient.factionButtonChecker() + REVEAL_FREE_SYSTEMS_PROMISSORY
                                        + monumentOwner.getFaction() + "|" + pn.getAlias(),
                                "Reveal " + pn.getName()))
                        .toList();
                if (!buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(
                            recipient.getCorrectChannel(),
                            recipient.getRepresentationNoPing()
                                    + ", reveal one of "
                                    + monumentOwner.getRepresentationNoPing()
                                    + "'s promissory notes from your hand to gain 1 command token with _Independence Tower_.",
                            buttons);
                }
            }
        }
    }

    @ButtonHandler(REVEAL_FREE_SYSTEMS_PROMISSORY)
    public static void revealFreeSystemsMonumentPromissory(
            ButtonInteractionEvent event, Game game, Player recipient, String buttonID) {
        String[] payload =
                buttonID.substring(REVEAL_FREE_SYSTEMS_PROMISSORY.length()).split("\\|", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile monumentTile = monumentOwner == null
                ? null
                : MonumentsService.getMonumentTile(game, monumentOwner, "free_systems_monument");
        PromissoryNoteModel promissoryNote = payload.length == 2 ? Mapper.getPromissoryNote(payload[1]) : null;
        if (monumentOwner == null
                || monumentTile == null
                || promissoryNote == null
                || recipient == monumentOwner
                || !recipient.getPromissoryNotes().containsKey(promissoryNote.getAlias())
                || recipient.getPromissoryNotesInPlayArea().contains(promissoryNote.getAlias())
                || game.getPNOwner(promissoryNote.getAlias()) != monumentOwner
                || !hasUnitsInOrAdjacentToFreeSystemsMonument(game, recipient, monumentTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageEmbed embed = promissoryNote.getRepresentationEmbed(false, false, false);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                monumentOwner,
                recipient.getRepresentationNoPing() + " revealed a promissory note with _Independence Tower_.",
                List.of(embed));
        MessageHelper.sendMessageToChannelWithButtons(
                recipient.getCorrectChannel(),
                recipient.getRepresentationNoPing() + " revealed a promissory note to "
                        + monumentOwner.getRepresentationNoPing()
                        + " and as such, gains 1 command token with _Independence Tower_.",
                ButtonHelper.getGainCCButtons(recipient));
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasUnitsInOrAdjacentToFreeSystemsMonument(Game game, Player player, Tile monumentTile) {
        if (FoWHelper.playerHasUnitsInSystem(player, monumentTile)) {
            return true;
        }
        return FoWHelper.getAdjacentTiles(game, monumentTile.getPosition(), player, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && FoWHelper.playerHasUnitsInSystem(player, tile));
    }

    // Corsairs' Cove
    public static Button getFlorzenStasisFighterButton(Game game, Player player, Tile tile) {
        if (!MonumentsService.isMonumentOnBoard(game, player, "florzen_monument")) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + PRODUCE_FLORZEN_STASIS_FIGHTER + tile.getPosition(),
                "Produce 1 Fighter to Stasis",
                UnitEmojis.fighter);
    }

    @ButtonHandler(PRODUCE_FLORZEN_STASIS_FIGHTER)
    public static void produceFlorzenStasisFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(PRODUCE_FLORZEN_STASIS_FIGHTER.length()));
        if (tile == null || !MonumentsService.isMonumentOnBoard(game, player, "florzen_monument")) {
            return;
        }
        player.setStasisFighters(player.getStasisFighters() + 1);
        player.produceUnit("ff_" + tile.getPosition() + "_" + Constants.SPACE);
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        int heldCount = held.length == 2 && tile.getPosition().equals(held[0]) ? Integer.parseInt(held[1]) : 0;
        game.setStoredValue(FLORZEN_STASIS_HELD + player.getFaction(), tile.getPosition() + "|" + (heldCount + 1));
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game)
                        + "\n-# "
                        + (heldCount + 1)
                        + " fighter"
                        + (heldCount == 0 ? " is" : "s are")
                        + " held in stasis on _Corsairs' Cove_.")
                .queue();
    }

    public static boolean canUseFlorzenStasisProduction(Game game, Player player) {
        return game.isMonumentsMode()
                && player.getStasisFighters() > 0
                && MonumentsService.isMonumentOnBoard(game, player, "florzen_monument");
    }

    public static Button getFlorzenStasisProductionButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_FLORZEN_STASIS_PRODUCTION,
                "Use Corsairs' Cove",
                UnitEmojis.fighter);
    }

    @ButtonHandler(USE_FLORZEN_STASIS_PRODUCTION)
    public static void useFlorzenStasisProduction(ButtonInteractionEvent event, Game game, Player player) {
        if (!canUseFlorzenStasisProduction(game, player)) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "florzen_monument");
        if (monumentTile == null) {
            return;
        }
        player.resetProducedUnits();
        game.setStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction(), monumentTile.getPosition() + "|0");
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + RELEASE_FLORZEN_STASIS_FIGHTER + monumentTile.getPosition(),
                        "Produce 1 Fighter",
                        UnitEmojis.fighter),
                Buttons.red(
                        player.factionButtonChecker() + "deleteButtons_florzenMonument_" + monumentTile.getPosition(),
                        "Done Producing Units"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", produce up to "
                        + player.getStasisFighters()
                        + " fighters in "
                        + monumentTile.getRepresentationForButtons(game, player)
                        + " without spending resources with _Corsairs' Cove_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(RELEASE_FLORZEN_STASIS_FIGHTER)
    public static void releaseFlorzenStasisFighter(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePosition = buttonID.substring(RELEASE_FLORZEN_STASIS_FIGHTER.length());
        String[] production = game.getStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction())
                .split("\\|", 2);
        Tile tile = game.getTileByPosition(tilePosition);
        int produced = production.length == 2 ? Integer.parseInt(production[1]) : 0;
        if (!canUseFlorzenStasisProduction(game, player)
                || tile == null
                || production.length != 2
                || !tilePosition.equals(production[0])
                || produced >= player.getStasisFighters()) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fighter");
        player.produceUnit("ff_" + tilePosition + "_" + Constants.SPACE);
        int totalProduced = produced + 1;
        game.setStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction(), tilePosition + "|" + totalProduced);
        var message = event.getMessage()
                .editMessage(player.getRepresentationNoPing()
                        + " has produced "
                        + totalProduced
                        + " of "
                        + player.getStasisFighters()
                        + " fighters from _Corsairs' Cove_.");
        if (totalProduced == player.getStasisFighters()) {
            message.setComponents(ButtonHelper.turnButtonListIntoActionRowList(List.of(Buttons.red(
                    player.factionButtonChecker() + "deleteButtons_florzenMonument_" + tilePosition,
                    "Done Producing Units"))));
        }
        message.queue();
    }

    public static void resolveFlorzenStasisProduction(Game game, Player player, String buttonID) {
        String[] production = game.getStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction())
                .split("\\|", 2);
        String tilePosition = buttonID.substring(FLORZEN_STASIS_PRODUCTION.length());
        if (production.length != 2 || !tilePosition.equals(production[0])) {
            return;
        }
        int produced = Integer.parseInt(production[1]);
        player.setStasisFighters(Math.max(0, player.getStasisFighters() - produced));
        game.removeStoredValue(FLORZEN_STASIS_PRODUCTION + player.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "-# "
                        + produced
                        + " fighter"
                        + (produced == 1 ? " was" : "s were")
                        + " produced for free with _Corsairs' Cove_; "
                        + player.getStasisFighters()
                        + " remain"
                        + (player.getStasisFighters() == 1 ? "s" : "")
                        + " in stasis.");
    }

    public static int getFlorzenStasisFightersInProduction(Game game, Player player, String producedUnit) {
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        if (held.length != 2 || !producedUnit.equals("ff_" + held[0] + "_" + Constants.SPACE)) {
            return 0;
        }
        return Integer.parseInt(held[1]);
    }

    public static void resetFlorzenStasisFighters(Game game, Player player) {
        String[] held =
                game.getStoredValue(FLORZEN_STASIS_HELD + player.getFaction()).split("\\|", 2);
        if (held.length == 2) {
            player.setStasisFighters(Math.max(0, player.getStasisFighters() - Integer.parseInt(held[1])));
        }
        game.removeStoredValue(FLORZEN_STASIS_HELD + player.getFaction());
    }

    // Hyperlane Relay
    public static List<Button> getCeldauriMonumentCommitButtons(Game game, Player player, Tile activeTile) {
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        Tile monumentTile = MonumentsService.getPlayerMonumentTile(game, player);
        if (activeTile == null
                || monumentPlanet == null
                || monumentTile == null
                || !MonumentsService.isMonumentOnBoard(game, player, "celdauri_monument")
                || CommandCounterHelper.hasCC(player, monumentTile)) {
            return List.of();
        }

        List<UnitType> groundForces = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));
        if (player.hasUnlockedBreakthrough("xytherisbt") && player.hasUpgradedUnit("pds2")) {
            groundForces.add(UnitType.Pds);
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet destination : activeTile.getPlanetUnitHolders()) {
            if (destination.getName().equals(monumentPlanet.getName())
                    || destination.getTokenList().stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE))
                    || destination.getUnitCount(UnitType.Spacedock, player) < 1) {
                continue;
            }
            for (UnitType unitType : groundForces) {
                if (monumentPlanet.getUnitCount(unitType, player) < 1) {
                    continue;
                }
                String unitName = unitType.humanReadableName();
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CELDAURI_MONUMENT_COMMIT + destination.getName() + "|"
                                + unitType.name(),
                        "Commit 1 " + unitName + " from "
                                + Helper.getPlanetRepresentation(monumentPlanet.getName(), game) + " to "
                                + Helper.getPlanetRepresentation(destination.getName(), game),
                        switch (unitType) {
                            case Infantry -> UnitEmojis.infantry;
                            case Mech -> UnitEmojis.mech;
                            case Pds -> UnitEmojis.pds;
                            default -> null;
                        }));
            }
        }
        return buttons;
    }

    @ButtonHandler(CELDAURI_MONUMENT_COMMIT)
    public static void resolveCeldauriMonumentCommit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CELDAURI_MONUMENT_COMMIT.length()).split("\\|", 2);
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        Tile monumentTile = MonumentsService.getPlayerMonumentTile(game, player);
        Tile destinationTile = payload.length == 2 ? game.getTileFromPlanet(payload[0]) : null;
        Planet destination = destinationTile == null
                ? null
                : destinationTile.getPlanetUnitHolders().stream()
                        .filter(planet -> planet.getName().equals(payload[0]))
                        .findFirst()
                        .orElse(null);
        UnitType unitType;
        try {
            unitType = payload.length == 2 ? UnitType.valueOf(payload[1]) : null;
        } catch (IllegalArgumentException e) {
            unitType = null;
        }
        if (monumentPlanet == null
                || monumentTile == null
                || destination == null
                || unitType == null
                || !MonumentsService.isMonumentOnBoard(game, player, "celdauri_monument")
                || CommandCounterHelper.hasCC(player, monumentTile)
                || destination.getUnitCount(UnitType.Spacedock, player) < 1
                || (unitType != UnitType.Infantry
                        && unitType != UnitType.Mech
                        && (unitType != UnitType.Pds
                                || !player.hasUnlockedBreakthrough("xytherisbt")
                                || !player.hasUpgradedUnit("pds2")))
                || monumentPlanet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<RemoveUnitService.RemovedUnit> removedUnits = RemoveUnitService.removeUnits(
                event,
                monumentTile,
                game,
                player.getColor(),
                "1 " + unitType.name().toLowerCase() + " " + monumentPlanet.getName());
        AddUnitService.addUnits(
                event,
                destinationTile,
                game,
                player.getColor(),
                "1 " + unitType.name().toLowerCase() + " " + destination.getName(),
                removedUnits);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentationNoPing() + " committed 1 " + unitType.humanReadableName() + " from "
                        + Helper.getPlanetRepresentation(monumentPlanet.getName(), game) + " to "
                        + Helper.getPlanetRepresentation(destination.getName(), game) + " with _Hyperlane Relay_.");
        if (monumentPlanet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    // Shades' Den
    public static boolean canUseKolleccMonument(Game game, Player player, Tile tile) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "kollecc_monument")
                || MonumentsService.getMonumentTile(game, player, "kollecc_monument") != tile
                || player.getNomboxTile().getUnitHolders().values().stream()
                        .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                        .findAny()
                        .isEmpty()) {
            return false;
        }
        return game.getRealPlayers().stream()
                        .filter(otherPlayer -> otherPlayer != player)
                        .filter(otherPlayer -> tile.getSpaceUnitHolder().getUnitKeysForPlayer(otherPlayer).stream()
                                .map(otherPlayer::getUnitFromUnitKey)
                                .anyMatch(unit -> unit != null && unit.getIsShip()))
                        .count()
                <= 1;
    }

    @ButtonHandler(USE_KOLLECC_MONUMENT)
    public static void useKolleccMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_KOLLECC_MONUMENT.length()));
        if (tile == null || !canUseKolleccMonument(game, player, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        player.getNomboxTile().getUnitHolders().values().stream()
                .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                .distinct()
                .forEach(unitKey -> {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit == null) {
                        return;
                    }
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + PLACE_KOLLECC_MONUMENT_UNIT + tile.getPosition() + "|"
                                    + unitKey.asyncID(),
                            "Place 1 " + unit.getName(),
                            unit.getUnitEmoji()));
                });
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you may place any of your captured units in the space area of "
                        + tile.getRepresentationForButtons(game, player) + " with _Shades' Den_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(PLACE_KOLLECC_MONUMENT_UNIT)
    public static void placeKolleccMonumentUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_KOLLECC_MONUMENT_UNIT.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        String asyncId = payload.length == 2 ? payload[1] : null;
        UnitKey unitKey = asyncId == null
                ? null
                : player.getNomboxTile().getUnitHolders().values().stream()
                        .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                        .filter(key -> key.asyncID().equals(asyncId))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (tile == null || unitKey == null || unit == null || !canUseKolleccMonument(game, player, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        List<RemoveUnitService.RemovedUnit> removedUnits =
                RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 " + asyncId);
        if (removedUnits.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + asyncId + " space", removedUnits);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " placed 1 captured " + unit.getName() + " in the space area of "
                        + tile.getRepresentationForButtons(game, player) + " with _Shades' Den_.");

        boolean hasCapturedUnits = player.getNomboxTile().getUnitHolders().values().stream()
                .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                .findAny()
                .isPresent();
        if (!hasCapturedUnits) {
            ButtonHelper.deleteMessage(event);
        } else if (player.getNomboxTile().getUnitHolders().values().stream()
                .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream())
                .noneMatch(key -> key.asyncID().equals(asyncId))) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    // Wonell's Bastion
    public static void offerKolumeMonumentButton(Game game, Player passedPlayer) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayers()) {
            Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "kolume_monument");
            if (monumentOwner == passedPlayer
                    || monumentTile == null
                    || !MonumentsService.isMonumentOnBoard(game, monumentOwner, "kolume_monument")
                    || getKolumeMonumentSystemButtons(game, monumentOwner, monumentTile)
                            .isEmpty()) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    monumentOwner.getCardsInfoThread(),
                    monumentOwner.getRepresentationUnfogged() + ", " + passedPlayer.getRepresentationNoPing()
                            + " passed. You may use _Wonell's Bastion_.",
                    List.of(Buttons.gray(
                            monumentOwner.factionButtonChecker() + USE_KOLUME_MONUMENT,
                            "Use Wonell's Bastion",
                            FactionEmojis.kolume)));
        }
    }

    @ButtonHandler(USE_KOLUME_MONUMENT)
    public static void useKolumeMonument(ButtonInteractionEvent event, Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "kolume_monument");
        if (!MonumentsService.isMonumentOnBoard(game, player, "kolume_monument") || monumentTile == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = getKolumeMonumentSystemButtons(game, player, monumentTile);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentationUnfogged() + ", choose a system containing a SPACE CANNON unit.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_KOLUME_MONUMENT_SYSTEM)
    public static void selectKolumeMonumentSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_KOLUME_MONUMENT_SYSTEM.length()).split("\\|", 2);
        Tile originTile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Tile selectedTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (originTile == null
                || selectedTile == null
                || !MonumentsService.isMonumentOnBoard(game, player, "kolume_monument")
                || getKolumeMonumentSystemButtons(game, player, originTile).stream()
                        .noneMatch(button -> button.getCustomId().endsWith("|" + selectedTile.getPosition()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + SHOW_KOLUME_MONUMENT_UNITS + selectedTile.getPosition(),
                        "Use SPACE CANNON",
                        UnitEmojis.pds),
                Buttons.gray(
                        player.factionButtonChecker() + SELECT_KOLUME_MONUMENT_TARGET + selectedTile.getPosition(),
                        "Select System"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Wonell's Bastion_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_KOLUME_MONUMENT_TARGET)
    public static void selectKolumeMonumentTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile sourceTile = game.getTileByPosition(buttonID.substring(SELECT_KOLUME_MONUMENT_TARGET.length()));
        if (sourceTile == null || !MonumentsService.isMonumentOnBoard(game, player, "kolume_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getKolumeMonumentSystemButtons(game, player, sourceTile);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentationUnfogged() + ", choose a system for _Wonell's Bastion_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SHOW_KOLUME_MONUMENT_UNITS)
    public static void showKolumeMonumentUnits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SHOW_KOLUME_MONUMENT_UNITS.length()));
        if (tile == null || !MonumentsService.isMonumentOnBoard(game, player, "kolume_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (var unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                Player unitOwner = game.getPlayerFromColorOrFaction(unitKey.getColor());
                UnitModel unit = unitOwner == null ? null : unitOwner.getUnitFromUnitKey(unitKey);
                if (unit == null || unit.getSpaceCannonDieCount(unitOwner) < 1) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + FIRE_KOLUME_MONUMENT_SPACE_CANNON + tile.getPosition() + "|"
                                + unitHolder.getName() + "|" + unitOwner.getColor() + "|" + unitKey.asyncID(),
                        "Use " + unitOwner.getColor() + " " + unit.getName() + " SPACE CANNON "
                                + unit.getSpaceCannonHitsOn(unitOwner) + "x" + unit.getSpaceCannonDieCount(unitOwner),
                        unit.getUnitEmoji()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentationUnfogged() + ", choose a SPACE CANNON unit to roll.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FIRE_KOLUME_MONUMENT_SPACE_CANNON)
    public static void fireKolumeMonumentSpaceCannon(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(FIRE_KOLUME_MONUMENT_SPACE_CANNON.length()).split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        Player unitOwner = payload.length == 4 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        var unitHolder = tile == null || payload.length != 4
                ? null
                : tile.getUnitHolders().get(payload[1]);
        UnitKey unitKey = unitHolder == null || unitOwner == null
                ? null
                : unitHolder.getUnitKeysForPlayer(unitOwner).stream()
                        .filter(key -> key.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : unitOwner.getUnitFromUnitKey(unitKey);
        Player targetPlayer = tile == null
                ? null
                : game.getRealPlayers().stream()
                        .filter(candidate -> tile.getSpaceUnitHolder().getUnitKeysForPlayer(candidate).stream()
                                .map(candidate::getUnitFromUnitKey)
                                .anyMatch(candidateUnit -> candidateUnit != null && candidateUnit.getIsShip()))
                        .findFirst()
                        .orElse(null);
        if (tile == null
                || unitHolder == null
                || unit == null
                || targetPlayer == null
                || unit.getSpaceCannonDieCount(unitOwner) < 1
                || !MonumentsService.isMonumentOnBoard(game, player, "kolume_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        CombatRollService.secondHalfOfSelectedSpaceCannonRoll(
                unitOwner, game, event, tile, unit, unitHolder, targetPlayer);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getKolumeMonumentSystemButtons(Game game, Player player, Tile originTile) {
        if (originTile == null) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        positions.add(originTile.getPosition());
        positions.addAll(FoWHelper.getAdjacentTiles(game, originTile.getPosition(), player, false, true));
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null
                    || (tile.getTileModel() != null && tile.getTileModel().isHyperlane())) {
                continue;
            }
            boolean hasSpaceCannon = tile.getUnitHolders().values().stream()
                    .flatMap(unitHolder -> unitHolder.getUnitKeys().stream())
                    .anyMatch(unitKey -> {
                        Player unitOwner = game.getPlayerFromColorOrFaction(unitKey.getColor());
                        UnitModel unit = unitOwner == null ? null : unitOwner.getUnitFromUnitKey(unitKey);
                        return unit != null && unit.getSpaceCannonDieCount(unitOwner) > 0;
                    });
            if (hasSpaceCannon) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_KOLUME_MONUMENT_SYSTEM + originTile.getPosition() + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    // AI Conclave
    public static Button getAiConclaveButton(Game game, Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_CONCLAVE,
                "Explore "
                        + MonumentsService.getMonumentTile(game, player, "augurs_monument")
                                .getRepresentationForButtons(game, player),
                FactionEmojis.augers);
    }

    @ButtonHandler(USE_CONCLAVE)
    public static void resolveAiConclave(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "augurs_monument")) {
            return;
        }

        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        if (monumentPlanet == null) {
            return;
        }

        int startingTg = player.getTg();
        player.gainTG(player.getSoScored(), true);
        int gainedTg = player.getTg() - startingTg;

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, monumentPlanet, player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you gained " + gainedTg + " because you have " + player.getSoScored() + " scored secrets."
                        + "\n-# This occurs after exploring the planet, so these trade goods cannot be used during this exploration.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }
}
