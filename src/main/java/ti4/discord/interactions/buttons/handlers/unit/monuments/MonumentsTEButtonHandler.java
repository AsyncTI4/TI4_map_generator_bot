package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.game.MonumentsService;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MonumentsTEButtonHandler {
    private static final String PLACE_SDC = "placeSeraphDataCenter_";
    private static final String SERAPH_DATA_CENTER = "seraphdatacenter";
    private static final String SELECT_CRIMSON_MONUMENT_TARGET = "selectCrimsonMonumentTarget_";
    private static final String START_PELAGION_THUNDERDOME = "startPelagionThunderdome_";
    private static final String SELECT_PELAGION_THUNDERDOME_COMBATANT = "selectPelagionThunderdomeCombatant_";
    private static final String SELECT_PELAGION_THUNDERDOME_OPPONENT = "selectPelagionThunderdomeOpponent_";
    private static final String START_EPIPHANYHOLLOW = "startEpiphanyHollow";
    private static final String SELECT_HOLLOW_SYSTEM = "selectEpiphanyHollowSystem_";
    private static final String REPLACE_HOLLOW_INFANTRY = "replaceEpiphanyHollowInfantry_";
    private static final String KELERES_MONUMENT_COMMAND_TOKENS = "keleresMonumentCommandTokens_";
    private static final String RESOLVE_KELERES_MONUMENT_TOKEN = "resolveKeleresMonumentToken_";
    private static final String USE_RALNEL_MONUMENT = "useRalnelMonument";
    private static final String SELECT_RALNEL_MONUMENT_STRUCTURE = "selectRalnelMonumentStructure_";
    private static final String MOVE_RALNEL_MONUMENT_STRUCTURE = "moveRalnelMonumentStructure_";

    // Pharus Iustitiae
    public static int getKeleresMonumentCommandTokenCount(Game game, Player player) {
        if (game == null || player == null || !game.isMonumentsMode() || !hasKeleresMonument(game, player)) {
            return 0;
        }
        String storedCount = game.getStoredValue(KELERES_MONUMENT_COMMAND_TOKENS + player.getFaction());
        return storedCount.isBlank() ? 0 : Integer.parseInt(storedCount);
    }

    public static Button getKeleresMonumentStatusButton(Game game, Player player) {
        int tokens = getKeleresMonumentCommandTokenCount(game, player);
        return Buttons.gray(
                player.factionButtonChecker() + "checkKeleresMonumentTokens",
                "Pharus Iustitiae: " + tokens + " Command Token" + (tokens == 1 ? "" : "s"),
                FactionEmojis.Keleres);
    }

    public static void offerKeleresMonumentCommandToken(Game game) {
        if (game == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            if (!canPlaceKeleresMonumentCommandToken(game, player) || player.getCorrectChannel() == null) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", a law entered play. You may place 1 command token from your reinforcements on _Pharus Iustitiae_.",
                    List.of(
                            Buttons.gray(
                                    player.factionButtonChecker() + "placeKeleresMonumentCommandToken",
                                    "Place a Command Token on Pharus Iustitiae",
                                    FactionEmojis.Keleres),
                            Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
        }
    }

    @ButtonHandler("placeKeleresMonumentCommandToken")
    public static void placeKeleresMonumentCommandToken(ButtonInteractionEvent event, Game game, Player player) {
        if (!canPlaceKeleresMonumentCommandToken(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Pharus Iustitiae cannot take a command token right now.");
            return;
        }
        int tokens = getKeleresMonumentCommandTokenCount(game, player) + 1;
        game.setStoredValue(KELERES_MONUMENT_COMMAND_TOKENS + player.getFaction(), Integer.toString(tokens));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a command token on _Pharus Iustitiae_ (" + tokens + ").");
    }

    @ButtonHandler("checkKeleresMonumentTokens")
    public static void checkKeleresMonumentTokens(ButtonInteractionEvent event, Game game, Player player) {
        int tokens = getKeleresMonumentCommandTokenCount(game, player);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "_Pharus Iustitiae_ has " + tokens + " command token" + (tokens == 1 ? "" : "s") + " on it.");
    }

    public static boolean offerKeleresMonumentTokenReplacement(
            GenericInteractionCreateEvent event, Player player, Tile tile, boolean ping, boolean useTactic) {
        Game game = player == null ? null : player.getGame();
        if (game == null
                || tile == null
                || getKeleresMonumentCommandTokenCount(game, player) < 1
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "keleres_monument", tile)) {
            return false;
        }
        String payload = tile.getPosition() + "|" + (ping ? "1" : "0") + "|" + (useTactic ? "1" : "0");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you are about to place a command token in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". Use a command token from _Pharus Iustitiae_ instead?",
                List.of(
                        Buttons.gray(
                                player.factionButtonChecker() + RESOLVE_KELERES_MONUMENT_TOKEN + payload + "|use",
                                "Use Pharus Iustitiae Token",
                                FactionEmojis.Keleres),
                        Buttons.red(
                                player.factionButtonChecker() + RESOLVE_KELERES_MONUMENT_TOKEN + payload + "|decline",
                                "Decline")));
        return true;
    }

    @ButtonHandler(RESOLVE_KELERES_MONUMENT_TOKEN)
    public static void resolveKeleresMonumentToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_KELERES_MONUMENT_TOKEN.length()).split("\\|", 4);
        Tile tile = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        boolean ping = payload.length == 4 && "1".equals(payload[1]);
        boolean useTactic = payload.length == 4 && "1".equals(payload[2]);
        boolean useMonumentToken = payload.length == 4 && "use".equals(payload[3]);
        if (tile == null || tile.hasCC(ti4.image.Mapper.getCCID(player.getColor()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (useMonumentToken) {
            if (getKeleresMonumentCommandTokenCount(game, player) < 1
                    || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "keleres_monument", tile)) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "Pharus Iustitiae cannot provide a command token for that system.");
                return;
            }
            int remainingTokens = getKeleresMonumentCommandTokenCount(game, player) - 1;
            if (remainingTokens == 0) {
                game.removeStoredValue(KELERES_MONUMENT_COMMAND_TOKENS + player.getFaction());
            } else {
                game.setStoredValue(
                        KELERES_MONUMENT_COMMAND_TOKENS + player.getFaction(), Integer.toString(remainingTokens));
            }
        }
        CommandCounterHelper.addCC(event, player, tile, ping, useMonumentToken ? false : useTactic, true);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasKeleresMonument(Game game, Player player) {
        return player.hasUnit("keleres_monument")
                || MonumentsService.isMonumentOnBoard(game, player, "keleres_monument");
    }

    private static boolean canPlaceKeleresMonumentCommandToken(Game game, Player player) {
        if (!game.isMonumentsMode() || !hasKeleresMonument(game, player)) {
            return false;
        }
        int commandTokenLimit = 16;
        if (!game.getStoredValue("ccLimit").isBlank()) {
            commandTokenLimit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        if (!game.getStoredValue("ccLimit" + player.getColor()).isBlank()) {
            commandTokenLimit = Integer.parseInt(game.getStoredValue("ccLimit" + player.getColor()));
        }
        if (player.hasRelic("endurance_steroids")) {
            commandTokenLimit += 2;
        }
        return Helper.getCCCount(game, player.getColor()) < commandTokenLimit;
    }

    // Epiphany Hollow
    public static Button sendEpiphanyHollowButton(Player owner, Player active) {
        return Buttons.green(
                owner.factionButtonChecker() + START_EPIPHANYHOLLOW + active.getFaction(),
                "Use Epiphany Hollow",
                FactionEmojis.Obsidian);
    }

    @ButtonHandler(START_EPIPHANYHOLLOW)
    public static void startEpiphanyHollow(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !MonumentsService.isMonumentOnBoard(game, player, "obsidian_monument")) {
            return;
        }

        String target = buttonID.substring(START_EPIPHANYHOLLOW.length());
        Player puppetedPlayer = game.getPlayerFromColorOrFaction(target);
        if (puppetedPlayer == null || !player.isOtherPlayerPuppeted(puppetedPlayer)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + SELECT_HOLLOW_SYSTEM + puppetedPlayer.getFaction();
        PlanetTargetSpec targetSpec = PlanetTargetSpec.of(buttonPrefix).requiringController();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (game.getPlayerThatControlsPlanet(planet.getName()) != puppetedPlayer
                        || planet.getUnitCount(UnitType.Infantry, puppetedPlayer) < 1) {
                    continue;
                }
                buttons.add(Buttons.green(
                        buttonPrefix + "_" + planet.getName(), Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        buttons = PlanetTargetService.targetButtons(game, player, targetSpec, buttons);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", the puppeted player has no eligible infantry to replace.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " please select the system to resolve _Epiphany Hollow_ in.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_HOLLOW_SYSTEM)
    public static void selectEpiphanyHollowSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player puppetedPlayer = game.getRealPlayers().stream()
                .filter(candidate -> buttonID.startsWith(SELECT_HOLLOW_SYSTEM + candidate.getFaction() + "_"))
                .findFirst()
                .orElse(null);
        if (puppetedPlayer == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String buttonPrefix = player.factionButtonChecker() + SELECT_HOLLOW_SYSTEM + puppetedPlayer.getFaction();
        PlanetTargetSpec targetSpec = PlanetTargetSpec.of(buttonPrefix).requiringController();
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, targetSpec)) {
            return;
        }
        var target = PlanetTargetService.resolve(
                game,
                player,
                buttonID,
                targetSpec,
                resolved -> resolved.owner() == puppetedPlayer
                        && resolved.unitHolder().getUnitCount(UnitType.Infantry, puppetedPlayer) > 0);
        Planet planet = target == null ? null : target.unitHolder();
        if (planet == null
                || !player.isOtherPlayerPuppeted(puppetedPlayer)
                || game.getPlayerThatControlsPlanet(planet.getName()) != puppetedPlayer
                || planet.getUnitCount(UnitType.Infantry, puppetedPlayer) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + REPLACE_HOLLOW_INFANTRY + planet.getName() + "|"
                        + puppetedPlayer.getFaction() + "|1",
                "Replace 1 Infantry"));
        if (planet.getUnitCount(UnitType.Infantry, puppetedPlayer) > 2) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REPLACE_HOLLOW_INFANTRY + planet.getName() + "|"
                            + puppetedPlayer.getFaction() + "|2",
                    "Replace 2 Infantry"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose how many infantry to replace on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REPLACE_HOLLOW_INFANTRY)
    public static void replaceEpiphanyHollowInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(REPLACE_HOLLOW_INFANTRY.length()).split("\\|", 3);
        Planet planet = payload.length == 3 ? game.getUnitHolderFromPlanet(payload[0]) : null;
        Player puppetedPlayer = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        int amount = payload.length == 3 && "2".equals(payload[2]) ? 2 : 1;
        if (planet == null
                || puppetedPlayer == null
                || !player.isOtherPlayerPuppeted(puppetedPlayer)
                || game.getPlayerThatControlsPlanet(planet.getName()) != puppetedPlayer
                || planet.getUnitCount(UnitType.Infantry, puppetedPlayer) < amount
                || (amount == 2 && planet.getUnitCount(UnitType.Infantry, puppetedPlayer) <= 2)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileFromPlanet(planet.getName());
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnit(event, tile, game, puppetedPlayer, planet, UnitType.Infantry, amount);
        String coexistFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " infantry " + planet.getName());
        if (coexistFlag.isEmpty()) {
            game.removeStoredValue("coexistFlag");
        } else {
            game.setStoredValue("coexistFlag", coexistFlag);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " replaced " + amount + " "
                        + puppetedPlayer.getRepresentationNoPing()
                        + " infantry with their own infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " using _Epiphany Hollow_.");
        ButtonHelper.deleteMessage(event);
    }

    // Epiphany
    public static void sendEpiphanyMessage(Player owner) {
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentation()
                        + ", someone has activated the system containing _Epiphany_. You may replace 1 control token on 1 of your plots with 1 of this player's control tokens."
                        + "\n-# You may use the normal plot buttons in your `#cards-info` thread to resolve this.");
    }

    // The Pelagion (Thunderdome)
    public static boolean allowsPelagionSpaceCoexistence(Game game, Tile tile) {
        if (game == null || tile == null || !game.isMonumentsMode()) {
            return false;
        }
        return game.getRealPlayers().stream()
                .anyMatch(owner -> tile.equals(MonumentsService.getMonumentTile(game, owner, "deepwrought_monument")));
    }

    public static boolean isPelagionMonument(Game game, Player owner, Tile tile) {
        return game != null
                && owner != null
                && tile != null
                && game.isMonumentsMode()
                && tile.equals(MonumentsService.getMonumentTile(game, owner, "deepwrought_monument"));
    }

    public static void offerPelagionThunderdome(Game game, Player owner, Tile tile, String planetName) {
        if (game == null || owner == null || tile == null || planetName == null || !game.isMonumentsMode()) {
            return;
        }
        if (ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).size() < 2) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + ", _The Pelagion_ was removed from "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + ". Start the Thunderdome when you are ready to resolve the resulting space combats.",
                Buttons.red(
                        owner.factionButtonChecker() + START_PELAGION_THUNDERDOME + tile.getPosition(),
                        "Start Thunderdome"));
    }

    @ButtonHandler(START_PELAGION_THUNDERDOME)
    public static void startPelagionThunderdome(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(START_PELAGION_THUNDERDOME.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).size() < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "# THE THUNDERDOME IS OPEN!\n"
                        + "### WARNING: Combat will be incredibly janky! It will ask people not participating to assign hits just because they have ships in the system.\n"
                        + "-# To be clear, the DWS player selects someone, then that person chooses who they fight. Repeat until only 1 player is left in the space area.\n"
                        + tile.getRepresentationForButtons(game, player)
                        + " has become a no-holds-barred battle for survival. Choose a combatant; only one fleet may remain.");
        sendPelagionThunderdomeCombatantButtons(event.getMessageChannel(), game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_PELAGION_THUNDERDOME_COMBATANT)
    public static void selectPelagionThunderdomeCombatant(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PELAGION_THUNDERDOME_COMBATANT.length())
                .split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player combatant = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        List<Player> playersWithShips =
                tile == null ? List.of() : ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        if (tile == null || playersWithShips.size() < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (combatant == null || !playersWithShips.contains(combatant)) {
            MessageHelper.replyToMessage(event, "That fleet is no longer eligible for the Thunderdome.");
            return;
        }
        List<Button> buttons = playersWithShips.stream()
                .filter(opponent -> opponent != combatant)
                .map(opponent -> Buttons.red(
                        combatant.factionButtonChecker() + SELECT_PELAGION_THUNDERDOME_OPPONENT + tile.getPosition()
                                + "|" + combatant.getFaction() + "|" + opponent.getFaction(),
                        "Fight " + StringUtils.capitalize(opponent.getColor()) + " Ships"))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                combatant.getCorrectChannel(),
                combatant.getRepresentation() + ", choose which fleet you will fight in the Thunderdome.",
                buttons);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_PELAGION_THUNDERDOME_OPPONENT)
    public static void selectPelagionThunderdomeOpponent(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_PELAGION_THUNDERDOME_OPPONENT.length())
                .split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        Player combatant = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        List<Player> playersWithShips =
                tile == null ? List.of() : ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        if (tile == null || playersWithShips.size() < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (combatant == null
                || opponent == null
                || player != combatant
                || combatant == opponent
                || !playersWithShips.contains(combatant)
                || !playersWithShips.contains(opponent)) {
            MessageHelper.replyToMessage(event, "That Thunderdome combat is no longer eligible.");
            return;
        }
        StartCombatService.startSpaceCombat(game, combatant, opponent, tile, event, "-thunderdome");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendPelagionThunderdomeCombatantButtons(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, Game game, Player owner, Tile tile) {
        List<Button> buttons = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .map(combatant -> Buttons.red(
                        owner.factionButtonChecker() + SELECT_PELAGION_THUNDERDOME_COMBATANT + tile.getPosition() + "|"
                                + combatant.getFaction(),
                        "Choose " + StringUtils.capitalize(combatant.getColor()) + " Ships"))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                owner.getRepresentation()
                        + ", choose a fleet to enter the Thunderdome. This menu remains until only one player's ships remain in the system.",
                buttons);
    }

    // Veils of Lost Creuss
    public static void offerCrimsonMonumentHit(Game game, Tile breachTile) {
        if (game == null || breachTile == null || !game.isMonumentsMode()) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            Tile monumentTile = MonumentsService.getMonumentTile(game, owner, "crimson_monument");
            if (monumentTile == null
                    || monumentTile.getPosition().equals(breachTile.getPosition())
                    || !MonumentsService.isInOrAdjacentToMonumentSystem(game, owner, "crimson_monument", breachTile)) {
                continue;
            }
            List<Button> buttons = game.getRealPlayers().stream()
                    .filter(target -> target != owner)
                    .filter(target -> FoWHelper.playerHasShipsInSystem(target, breachTile))
                    .map(target -> Buttons.red(
                            owner.factionButtonChecker() + SELECT_CRIMSON_MONUMENT_TARGET + breachTile.getPosition()
                                    + "|" + target.getFaction(),
                            "Hit " + StringUtils.capitalize(target.getColor()) + " Ships"))
                    .toList();
            if (buttons.isEmpty()) {
                continue;
            }
            List<Button> promptButtons = new ArrayList<>(buttons);
            promptButtons.add(Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing() + ", a Breach became active in "
                            + breachTile.getRepresentationForButtons(game, owner)
                            + ". Please choose another player's ships to suffer 1 hit from _Veils Of Lost Creuss_.",
                    promptButtons);
        }
    }

    @ButtonHandler(SELECT_CRIMSON_MONUMENT_TARGET)
    public static void selectCrimsonMonumentTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_CRIMSON_MONUMENT_TARGET.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[1]) : null;
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "crimson_monument");
        if (tile == null
                || target == null
                || target == player
                || monumentTile == null
                || monumentTile.getPosition().equals(tile.getPosition())
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "crimson_monument", tile)
                || !FoWHelper.playerHasShipsInSystem(target, tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        CombatRollService.sendSpaceAssignHitsButtons(event, game, target, tile, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                target.getRepresentationNoPing() + ", your ships in " + tile.getRepresentationForButtons(game, target)
                        + " suffer 1 hit from _Veils Of Lost Creuss_.");
        ButtonHelper.deleteMessage(event);
    }

    // Sor Tek Linkhub
    public static void offerRalNelMonumentStructureMove(Game game, Player player) {
        if (game == null
                || player == null
                || !MonumentsService.isMonumentOnBoard(game, player, "ralnel_monument")
                || getRalNelMonumentStructureButtons(game, player).isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may move 1 structure in the space area of _Sor Tek Linkhub_'s system or an adjacent system.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_RALNEL_MONUMENT,
                                "Use Sor Tek Linkhub",
                                FactionEmojis.Ralnel),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_RALNEL_MONUMENT)
    public static void useRalNelMonument(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getRalNelMonumentStructureButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a structure to move with _Sor Tek Linkhub_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_RALNEL_MONUMENT_STRUCTURE)
    public static void selectRalNelMonumentStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_RALNEL_MONUMENT_STRUCTURE.length()).split("\\|", 3);
        Tile source = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        UnitState state = payload.length == 3 ? getUnitState(payload[2]) : null;
        UnitHolder sourceSpace = source == null ? null : source.getSpaceUnitHolder();
        UnitKey unitKey = sourceSpace == null
                ? null
                : sourceSpace.getUnitsByStateForPlayer(player).keySet().stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        if (source == null
                || sourceSpace == null
                || unitKey == null
                || state == null
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "ralnel_monument", source)
                || sourceSpace.getUnitCountForState(unitKey, state) < 1
                || !isRalNelMovableStructure(player, sourceSpace, unitKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons =
                FoWHelper.getAdjacentTilesAndNotThisTile(game, source.getPosition(), player, false).stream()
                        .map(game::getTileByPosition)
                        .filter(destination -> destination != null
                                && (destination.getTileModel() == null
                                        || !destination.getTileModel().isHyperlane()))
                        .sorted(Comparator.comparing(Tile::getPosition))
                        .map(destination -> Buttons.green(
                                player.factionButtonChecker() + MOVE_RALNEL_MONUMENT_STRUCTURE + source.getPosition()
                                        + "|" + unitKey.asyncID() + "|" + state.name() + "|"
                                        + destination.getPosition(),
                                destination.getRepresentationForButtons(game, player)))
                        .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose where to move that structure.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_RALNEL_MONUMENT_STRUCTURE)
    public static void moveRalNelMonumentStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(MOVE_RALNEL_MONUMENT_STRUCTURE.length()).split("\\|", 4);
        Tile source = payload.length == 4 ? game.getTileByPosition(payload[0]) : null;
        Tile destination = payload.length == 4 ? game.getTileByPosition(payload[3]) : null;
        UnitState state = payload.length == 4 ? getUnitState(payload[2]) : null;
        UnitHolder sourceSpace = source == null ? null : source.getSpaceUnitHolder();
        UnitKey unitKey = sourceSpace == null
                ? null
                : sourceSpace.getUnitsByStateForPlayer(player).keySet().stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        if (source == null
                || destination == null
                || sourceSpace == null
                || unitKey == null
                || state == null
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "ralnel_monument", source)
                || !FoWHelper.getAdjacentTilesAndNotThisTile(game, source.getPosition(), player, false)
                        .contains(destination.getPosition())
                || (destination.getTileModel() != null
                        && destination.getTileModel().isHyperlane())
                || sourceSpace.getUnitCountForState(unitKey, state) < 1
                || !isRalNelMovableStructure(player, sourceSpace, unitKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        destination.getSpaceUnitHolder().addUnitsWithStates(unitKey, sourceSpace.removeUnit(unitKey, 1, state));
        UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), destination.getSpaceUnitHolder());
        String unitName = unit == null ? unitKey.unitType().humanReadableName() : unit.getName();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved 1 " + unitName + " to "
                        + destination.getRepresentationForButtons(game, player) + " using _Sor Tek Linkhub_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRalNelMonumentStructureButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "ralnel_monument", tile)) {
                continue;
            }
            UnitHolder space = tile.getSpaceUnitHolder();
            for (UnitKey unitKey : space.getUnitsByStateForPlayer(player).keySet()) {
                if (!isRalNelMovableStructure(player, space, unitKey)) {
                    continue;
                }
                for (UnitState state : space.getNonZeroUnitStates(unitKey)) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + SELECT_RALNEL_MONUMENT_STRUCTURE + tile.getPosition() + "|"
                                    + unitKey.asyncID() + "|" + state.name(),
                            tile.getRepresentationForButtons(game, player),
                            unitKey.unitEmoji()));
                }
            }
        }
        return buttons;
    }

    private static boolean isRalNelMovableStructure(Player player, UnitHolder space, UnitKey unitKey) {
        UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
        return unit != null && unit.getIsStructure();
    }

    private static UnitState getUnitState(String state) {
        try {
            return UnitState.valueOf(state);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Seraph Data Center
    public static List<Button> getSDCPlacementButtons(Game game, Player player) {
        if (!game.isMonumentsMode()
                || (!player.hasUnit("bastion_monument")
                        && !MonumentsService.isMonumentOnBoard(game, player, "bastion_monument"))) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile ->
                        tile.getTileModel() != null && !tile.getTileModel().isHyperlane())
                .filter(tile -> tile.containsPlayersUnits(player))
                .filter(tile -> tile.getPlanetUnitHolders().size() == 1)
                .filter(tile -> !tile.hasLegendary())
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + PLACE_SDC + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler(PLACE_SDC)
    public static void placeSDC(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSDCPlacementButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the system in which to place _Seraph Data Center_ in space.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_SDC;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }
        String position = buttonID.replace(PLACE_SDC, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || buttons.stream().noneMatch(button -> button.getCustomId().endsWith(PLACE_SDC + position))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument");
        tile.getUnitHolders().put(SERAPH_DATA_CENTER, new Planet(SERAPH_DATA_CENTER, Constants.SPACE_CENTER_POSITION));
        game.clearPlanetsCache();
        AddPlanetService.addPlanet(player, SERAPH_DATA_CENTER, game, event, false);
        player.refreshPlanet(SERAPH_DATA_CENTER);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed _Seraph Data Center_ in the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void checkSeraphDataCenterBlockade(GenericInteractionCreateEvent event, Game game) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            Tile tile = MonumentsService.getMonumentTile(game, owner, "bastion_monument");
            if (tile == null
                    || FoWHelper.playerHasActualShipsInSystem(owner, tile)
                    || game.getRealPlayers().stream()
                            .noneMatch(
                                    other -> other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile))) {
                continue;
            }
            DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), "1 monument", false);
            tile.getUnitHolders().remove(SERAPH_DATA_CENTER);
            game.clearPlanetsCache();
            owner.removePlanet(SERAPH_DATA_CENTER);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentationNoPing() + " lost control of _Seraph Data Center_; it was destroyed.");
            for (Player other : game.getRealPlayers()) {
                if (other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile)) {
                    CombatRollService.sendSpaceAssignHitsButtons(event, game, other, tile, 2);
                }
            }
        }
    }
}
