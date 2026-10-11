package ti4.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.ResourceHelper;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.base.arborec.ArborecButtonHandlers;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie.SarcosaAgentHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.aeterna.AeternaLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.ardentia.ArdentiaLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kryxos.KryxosLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.vanguard.VanguardLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.veylor.VeylorLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.onyxxa.OnyxxaLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.zephyrion.ZephyrionLeaderHandler;
import ti4.discord.interactions.commands.planet.PlanetExhaustAbility;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperAgents;
import ti4.helpers.twilight_kart.TkHelperGenomes;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.game.MonumentsService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.planet.PlanetService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.GalvanizeService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

public final class ButtonHelperAgents {

    public static final Set<UnitType> SHIP_TYPES_NO_FIGHTERS = Set.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    private static List<Button> getTilesToArboAgent(Player player, Game game) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(
                        factionChecker + "arboAgentIn_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Buttons.red(factionChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static void cabalAgentInitiation(Game game, Player p2) {
        for (Player cabal : game.getRealPlayers()) {
            if (cabal == p2) {
                continue;
            }
            if (cabal.hasUnexhaustedLeader("cabalagent")) {
                List<Button> buttons = new ArrayList<>();
                String msg = (cabal.hasUnexhaustedLeader("yssarilagent")
                                ? cabal.getRepresentationNoPing()
                                : cabal.getRepresentationUnfogged())
                        + " you may use "
                        + agentRef(cabal, "The Stillness of Stars, the Vuil'raith") + ", on "
                        + p2.getFactionEmojiOrColor() + " who has "
                        + p2.getCommoditiesTotal() + " commodit" + (p2.getCommoditiesTotal() == 1 ? "y" : "ies") + ".";
                buttons.add(Buttons.green(
                        "exhaustAgent_cabalagent_startCabalAgent_" + p2.getFaction(), "Use Vuil'raith Agent"));
                buttons.add(Buttons.DECLINE);
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
            if (cabal.hasUnexhaustedLeader("toldaragent")) {
                List<Button> buttons = new ArrayList<>();
                String msg = cabal.getRepresentationUnfogged() + ", you may use "
                        + agentRef(cabal, "Maertin Donaais, the Toldar") + ", on "
                        + p2.getFactionEmojiOrColor() + ", who has "
                        + p2.getCommoditiesTotal() + " commodit" + (p2.getCommoditiesTotal() == 1 ? "y" : "ies") + ".";
                buttons.add(Buttons.green(
                        "toldarAgent_" + p2.getFaction() + "_" + p2.getCommoditiesTotal(), "Use Toldar Agent"));
                buttons.add(Buttons.DECLINE);
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void toldarAgentInitiation(Game game, Player p2, int amount) {
        for (Player toldar : game.getRealPlayers()) {
            if (toldar == p2) {
                continue;
            }
            if (toldar.hasUnexhaustedLeader("toldaragent")) {
                List<Button> buttons = new ArrayList<>();
                String msg = toldar.getRepresentationUnfogged() + ", you may use "
                        + agentRef(toldar, "Maertin Donaais, the Toldar") + ", on "
                        + p2.getFactionEmojiOrColor() + " for " + amount + " commodit" + (amount == 1 ? "y" : "ies")
                        + ".";
                buttons.add(Buttons.green("toldarAgent_" + p2.getFaction() + "_" + amount, "Use Toldar Agent"));
                buttons.add(Buttons.DECLINE);
                MessageHelper.sendMessageToChannelWithButtons(toldar.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    @ButtonHandler("toldarAgent_")
    public static void toldarAgent(Player toldar, Game game, String buttonID, GenericInteractionCreateEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        exhaustAgent("exhaustAgent_toldaragent_startToldarAgent_" + p2.getFaction(), event, game, toldar);
        int commodities = p2.getCommodities();
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + ", your " + commodities + " commodit"
                        + (commodities == 1 ? "y" : "ies") + " have been washed by "
                        + agentRef(toldar, "Maertin Donaais, the Toldar") + ".");
        p2.setTg(p2.getTg() + commodities);
        p2.setCommodities(0);

        List<Button> buttons = new ArrayList<>();
        if (p2 != toldar) {
            buttons.add(Buttons.green(
                    toldar.factionButtonChecker() + "draw_1_ACDelete", "Draw 1 Action Card", CardEmojis.ActionCard));
            buttons.add(Buttons.blue(
                    toldar.factionButtonChecker() + "gain_2_comms", "Gain 2 Commodities", MiscEmojis.comm));
            MessageHelper.sendMessageToChannel(
                    toldar.getCorrectChannel(),
                    toldar.getRepresentation() + " please choose to either gain 2 commodities or draw 1 AC",
                    buttons);
        }
    }

    @ButtonHandler("startCabalAgent_")
    private static void startCabalAgent(Player cabal, Game game, String buttonID, GenericInteractionCreateEvent event) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = getUnitsForCabalAgent(game, p2);
        String msg = cabal.getRepresentationUnfogged() + ", please choose which ship you wish to capture.";
        MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static List<Button> getUnitsForCabalAgent(Game game, Player p2) {
        List<Button> buttons = new ArrayList<>();
        int maxComms = p2.getCommoditiesTotal();
        addCabalAgentCaptureButton(buttons, game, p2, "destroyer", maxComms > 0, 8, UnitEmojis.destroyer);
        addCabalAgentCaptureButton(buttons, game, p2, "fighter", maxComms > 0, -1, UnitEmojis.destroyer);
        addCabalAgentCaptureButton(buttons, game, p2, "infantry", maxComms > 0, -1, UnitEmojis.destroyer);
        addCabalAgentCaptureButton(buttons, game, p2, "mech", maxComms > 1, 4, UnitEmojis.mech);
        addCabalAgentCaptureButton(buttons, game, p2, "cruiser", maxComms > 1, 8, UnitEmojis.cruiser);
        addCabalAgentCaptureButton(buttons, game, p2, "carrier", maxComms > 2, 4, UnitEmojis.carrier);
        addCabalAgentCaptureButton(buttons, game, p2, "dreadnought", maxComms > 3, 5, UnitEmojis.dreadnought);
        addCabalAgentCaptureButton(buttons, game, p2, "flagship", maxComms > 7, 1, UnitEmojis.flagship);
        return buttons;
    }

    private static void addCabalAgentCaptureButton(
            List<Button> buttons, Game game, Player p2, String unit, boolean affordable, int cap, UnitEmojis emoji) {
        if (affordable && (cap < 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit) < cap)) {
            buttons.add(Buttons.red("cabalAgentCapture_" + unit + "_" + p2.getFaction(), "Capture " + unit, emoji));
        }
    }

    public static List<Button> getUnitsToArboAgent(Player player, Tile tile) {
        List<Button> buttons = ButtonHelperModifyUnits.getSpaceUnitButtons(
                player, tile, SHIP_TYPES_NO_FIGHTERS, false, true, "Remove", "arboAgentOn_", unitKey -> "");
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unit) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
        String msg = (damaged ? "A damaged " : "") + unitKey.unitEmoji() + " was removed by "
                + player.getFactionEmoji()
                + ". A ship costing up to 2 more than it may now be placed.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        List<String> allowedUnits = Stream.of(
                        UnitType.Destroyer,
                        UnitType.Cruiser,
                        UnitType.Carrier,
                        UnitType.Dreadnought,
                        UnitType.Flagship,
                        UnitType.Warsun,
                        UnitType.Fighter)
                .map(UnitType::getValue)
                .toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID =
                        factionChecker + "arboAgentPutShip_" + ownedUnit.getBaseType() + "_" + tile.getPosition();
                String buttonText = "Place " + ownedUnit.getName();
                buttons.add(Buttons.green(buttonID, buttonText, ownedUnit.getUnitEmoji()));
            }
        }

        return buttons;
    }

    @ButtonHandler("spendStratNReadyAgent_")
    public static void resolveAbsolHyperAgentReady(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String agent = StringUtils.substringAfterLast(buttonID, "_");
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(
                player, game, event, TechEmojis.BioticTech + "Hypermetabolism" + SourceEmojis.Absol);
        ButtonHelper.deleteMessage(event);
        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            String relic =
                    agent.contains("titanprototype") ? "titanprototype" : agent.contains("absol") ? "absol_jr" : null;
            if (relic != null) {
                player.removeExhaustedRelic(relic);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji()
                                + " exhausted _Hypermetabolism_ and spent a command token from strategy pool to ready "
                                + agent + ".");
            }
            return;
        }
        RefreshLeaderService.refreshLeader(player, playerLeader, game);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " exhausted "
                        + "_Hypermetabolism_ and spent a command token from their strategy pool to ready " + agent
                        + ".");
    }

    @ButtonHandler("belkoseaYellowTechReady_")
    public static void belkoseaYellowTechReady(
            String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTech("dsbelky") || !player.getExhaustedTechs().contains("dsbelky")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttonID = buttonID.replace("belkoseaYellowTechReady_", "");
        String thing = buttonID.split("_")[0];
        String detail = buttonID.replace(thing + "_", "");
        String msg = player.getFactionEmoji() + " exhausted _Synchrony Matrix_ to ready " + detail + ".";
        if ("leader".equalsIgnoreCase(thing)) {
            Leader playerLeader = player.getLeader(detail).orElse(null);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            if (playerLeader == null) {
                return;
            }
            RefreshLeaderService.refreshLeader(player, playerLeader, game);
        } else if ("monument".equalsIgnoreCase(thing)) {
            if (!MonumentsService.readyMonument(game, player, detail)) {
                return;
            }
            UnitModel monument = Mapper.getUnit(detail);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted _Synchrony Matrix_ to ready "
                            + (monument == null ? detail : monument.getName() + " Monument") + ".");
        } else {
            if ("planet".equalsIgnoreCase(thing)) {
                player.refreshPlanet(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            } else if ("ability".equalsIgnoreCase(thing)) {
                player.removeExhaustedAbility(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            } else if ("breakthrough".equalsIgnoreCase(thing)) {
                if (player.isBreakthroughUnlocked(detail) && player.isBreakthroughExhausted(detail)) {
                    player.setBreakthroughExhausted(detail, false);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                }
            } else {
                player.removeExhaustedRelic(detail);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("umbatTile_")
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pharadnAgentSelect_")
    public static void pharadnAgentSelect(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.replace("pharadnAgentSelect_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        String message =
                p2.getRepresentation() + ", please choose the planet that you wish to kill all the infantry on. ";
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanetsAllianceMode()) {
            buttons.add(Buttons.gray("pharadnAgentKill_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pharadnAgentKill_")
    public static void pharadnAgentKill(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.replace("pharadnAgentKill_", "");

        String message = player.getRepresentation() + " chose to destroy all infantry on "
                + Helper.getPlanetRepresentation(planet, game);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amountToKill = uH.getUnitCount(UnitType.Infantry, player.getColor());

        int comms = Math.min(player.getCommoditiesTotal(), amountToKill);
        message += ". " + amountToKill + " infantry were destroyed. " + comms + " commodit" + (comms == 1 ? "y" : "ies")
                + " were gained, and then all commodities were converted into trade goods.";
        player.setTg(player.getTg() + Math.min(player.getCommoditiesTotal(), player.getCommodities() + amountToKill));
        player.setCommodities(0);
        if (amountToKill > 0) {
            ButtonHelper.resolveInfantryDestroy(player, amountToKill, game.getTileFromPlanet(planet));
            RemoveUnitService.removeUnits(
                    event, game.getTileFromPlanet(planet), game, player.getColor(), amountToKill + " inf " + planet);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hacanAgentRefresh_")
    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        String message;
        if (p2 == player) {
            message = player.getRepresentationUnfogged() + " increased your commodities by two";
            ButtonHelperStats.gainComms(event, game, player, 2, false, true);
        } else {
            message =
                    player.getFactionEmojiOrColor() + " replenished " + p2.getFactionEmojiOrColor() + "'s commodities";
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + " your commodities were replenished by "
                            + agentRef(player, "Carth of Golden Sands, the Hacan") + ".");
            ButtonHelperStats.replenishComms(event, game, p2, true);
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mercerMove_")
    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] parts = buttonID.split("_");
        String planetDestination = parts[1];
        Tile tileDestination = game.getTileFromPlanet(planetDestination);
        String pos = parts[2];
        Tile tileRemoval = game.getTileByPosition(pos);
        String planetRemoval = parts[3];
        String unit = parts[4];
        UnitHolder uH = tileRemoval.getUnitHolders().get(planetRemoval);
        String message;
        String removalLocation = planetRemoval;
        if ("space".equalsIgnoreCase(planetRemoval)) {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from the space area of "
                    + tileRemoval.getRepresentation() + " to "
                    + Helper.getPlanetRepresentation(planetDestination, game);
            removalLocation = "";
        } else {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from "
                    + Helper.getPlanetRepresentation(planetRemoval, game) + " to "
                    + Helper.getPlanetRepresentation(planetDestination, game);
        }

        List<RemoveUnitService.RemovedUnit> removedUnits = RemoveUnitService.removeUnits(
                event, tileRemoval, game, player.getColor(), unit + " " + removalLocation);
        if (buttonID.contains("lizhobt")) {
            ButtonHelper.deleteMessage(event);
            message += " into coexistence. They used _Professional Intrigue_ to do this";
            game.setStoredValue("coexistFlag", "yes");
        }
        AddUnitService.addUnits(
                event, tileDestination, game, player.getColor(), unit + " " + planetDestination, removedUnits);
        game.removeStoredValue("coexistFlag");
        if ("mech".equalsIgnoreCase(unit)) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "naaz");
        } else {
            if ("pds".equalsIgnoreCase(unit)) {
                if (uH.getUnitCount(UnitType.Pds, player.getColor()) < 1) {
                    ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                }
            } else if (uH.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
        }
        if (tileDestination != null && tileDestination.isFracture()) {
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message + ".");
    }

    private static void addArgentAgentButtons(Tile tile, Player player, Game game) {
        Set<String> tiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        List<Button> unitButtons = new ArrayList<>();
        for (String pos : tiles) {
            Tile tile2 = game.getTileByPosition(pos);

            for (Planet unitHolder : tile2.getPlanetUnitHolders()) {
                Planet planetReal = unitHolder;
                String planet = planetReal.getName();
                if (player.getPlanetsAllianceMode().contains(planet)) {
                    String pp = unitHolder.getName();
                    Button inf1Button = Buttons.green(
                            player.factionButtonChecker() + "place_infantry_" + pp,
                            "Produce 1 Infantry on " + Helper.getPlanetRepresentation(pp, game),
                            UnitEmojis.infantry);
                    unitButtons.add(inf1Button);
                    Button mfButton = Buttons.green(
                            player.factionButtonChecker() + "place_mech_" + pp,
                            "Produce Mech on " + Helper.getPlanetRepresentation(pp, game),
                            UnitEmojis.mech);
                    unitButtons.add(mfButton);
                }
            }
        }
        unitButtons.add(Buttons.red("deleteButtons_spitItOut", "Done With Argent Agent"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use buttons to place ground forces via "
                        + agentRef(player, "Trillossa Aun Mirik, the Argent") + ".",
                unitButtons);
    }

    @ButtonHandler("vaylerianAgent_")
    public static void resolveVaylerianAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player) {

        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        ActionCardHelper.drawActionCards(p2, 1);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    p2.getFactionEmojiOrColor() + " gained 1 action card from using "
                            + agentRef(player, "Yvin Korduul, the Vaylerian") + ".");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static String agentRef(boolean ssruu, String nameAndFaction) {
        return (ssruu ? "Clever Clever " : "") + nameAndFaction + (ssruu ? "/Yssaril" : "") + " agent";
    }

    public static String agentRef(Player player, String nameAndFaction) {
        return agentRef(player.hasUnexhaustedLeader("yssarilagent"), nameAndFaction);
    }

    private static void sendExhausted(MessageChannel channel, Player player, boolean ssruu, String nameAndFaction) {
        MessageHelper.sendMessageToChannel(
                channel, player.getRepresentation() + " has exhausted " + agentRef(ssruu, nameAndFaction) + ".");
    }

    private static void offerAgentTargetChoice(
            Game game, MessageChannel channel, String trueIdentity, boolean ssruu, String agentName, String idPrefix) {
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                trueIdentity + ", please choose the faction on which you wish to use " + agentRef(ssruu, agentName)
                        + ".",
                AgendaRiderHelper.getPlayerOutcomeButtons(game, null, idPrefix, null));
    }

    @ButtonHandler("exhaustAgent_")
    public static void exhaustAgent(String buttonID, GenericInteractionCreateEvent event, Game game, Player player) {
        String agent = buttonID.replace("exhaustAgent_", "");
        String rest = agent;
        String[] restParts = rest.split("_");
        String trueIdentity = player.getRepresentationUnfogged();
        if (agent.contains("_")) {
            agent = agent.substring(0, agent.indexOf('_'));
        }
        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            return;
        }
        if (!GameEventDraft.stage(game, new GameSubEvent.LeaderPlayed(player.getFaction(), "AGENT", agent))) {
            GameEventService.commit(game, GameEventType.CARD_PLAY_AGENT, player, Map.of("cardId", agent));
        }

        ExhaustLeaderService.exhaustLeader(game, player, playerLeader);
        playerLeader
                .getLeaderModel()
                .ifPresent(agentModel -> SpringContext.getBean(CombatReplayService.class)
                        .mirrorLeaderPlayed(
                                game,
                                player,
                                agentModel.getAlias(),
                                player.getCorrectChannel().getName()));

        MessageChannel channel = player.getCorrectChannel();
        String message;

        boolean ssruu = "yssarilagent".equalsIgnoreCase(playerLeader.getId());
        String ssruuClever = ssruu ? "Clever Clever " : "";
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Exhausted the "
                        + playerLeader.getLeaderModel().get().getAlias() + " leader.");

        switch (agent.toLowerCase()) {
            case "nomadagentartuno" -> {
                sendExhausted(channel, player, ssruu, "Artuno the Betrayer, a Nomad");
                if (!rest.contains("_")) return;
                int tgCount = Integer.parseInt(restParts[1]);
                playerLeader.setTgCount(tgCount);
                String messageText =
                        player.getRepresentation() + " placed " + StringHelper.pluralize(tgCount, "trade good")
                                + " on top of " + agentRef(ssruu, "Artuno the Betrayer, a Nomad") + ".";
                MessageHelper.sendMessageToChannel(channel, messageText);
                int randomJokeChance = ThreadLocalRandom.current().nextInt(1, 11);
                if (randomJokeChance == 10) {
                    File audioFile = ResourceHelper.getFile("voices/nomad/", "Artuno.mp3");
                    if (audioFile.exists()) {
                        MessageHelper.sendFileToChannel(player.getCorrectChannel(), audioFile);
                    }
                }
            }
            case "naazagent" -> {
                String exhaustText = player.getRepresentation() + " has exhausted "
                        + agentRef(ssruu, "Garv and Gunn, the Naaz-Rokha") + "s.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(p2, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(),
                        p2.getRepresentation() + ", please choose which planet you wish to explore.",
                        buttons);
            }
            case "researchagent" -> {
                String exhaustText = player.getRepresentationNoPing()
                        + " has used the _Research Genome_ to add 3 cards to the draft.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                game.setStoredValue("researchagentSplice" + player.getFaction(), "Yes");
            }
            case "augersagent" -> {
                sendExhausted(channel, player, ssruu, "Clodho, the Ilyxum");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                int oldTg = p2.getTg();
                p2.setTg(oldTg + 2);
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getFactionEmojiOrColor() + " gained 2 trade goods from "
                                + agentRef(ssruu, "Clodho, the Ilyxum") + ", being used (" + oldTg + "->" + p2.getTg()
                                + ").");
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            p2.getFactionEmojiOrColor() + " gained 2 trade goods due to agent usage.");
                }
                ButtonHelperAbilities.pillageCheck(p2, game);
                resolveArtunoCheck(p2, 2);
            }
            case "vaylerianagent" -> {
                sendExhausted(channel, player, ssruu, "Yvin Korduul, the Vaylerian");
                if (rest.contains("_")) {
                    Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                    ActionCardHelper.drawActionCards(p2, 1);
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(),
                                p2.getFactionEmojiOrColor() + " gained 1 action card due to agent usage.");
                    }
                } else {
                    offerAgentTargetChoice(
                            game, channel, trueIdentity, ssruu, "Yvin Korduul, the Vaylerian", "vaylerianAgent");
                }
            }
            case "kjalengardagent" -> {
                sendExhausted(channel, player, ssruu, "Merkismathr Asvand, the Kjalengard");
                Player activePlayer = game.getActivePlayer();
                if (activePlayer != null) {
                    int oldComms = activePlayer.getCommodities();
                    int newComms = oldComms + activePlayer.getNeighbourCount();
                    activePlayer.setCommodities(newComms);
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getFactionEmoji()
                                    + " exhausted " + agentRef(ssruu, "Merkismathr Asvand, the Kjalengard")
                                    + ", to potentially move a **Glory** token into the system. "
                                    + activePlayer.getFactionEmoji() + " commodities went from " + oldComms + " -> "
                                    + newComms + ".");
                }
                if (!getGloryTokenTiles(game).isEmpty()) {
                    offerMoveGloryOptions(game, player, event);
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getFactionEmoji()
                                    + " there were no **Glory** tokens on the game board to move. Go win some battles and earn some, or your ancestors will laugh at ya when "
                                    + (ThreadLocalRandom.current().nextInt(20) == 0 ? "(if) " : "")
                                    + "you reach Valhalla.");
                }
            }
            case "cabalagent" -> {
                sendExhausted(channel, player, ssruu, "The Stillness of Stars, the Vuil'Raith");
                startCabalAgent(player, game, rest.replace("cabalagent_", ""), event);
            }
            case "toldaragent" -> {
                sendExhausted(channel, player, ssruu, "Maertin Donaais, the Toldar");
            }
            case "uydaiagent" -> {
                MessageHelper.sendMessageToChannel(
                        channel,
                        "This is not automated. The following command allows you to place a card back on the bottom of a deck: `/game place_drawable_card`. Any immediate effects of the already drawn card will need to be undone manually. Use the search function to find a cards ID if unknown.");
            }
            case "zephyrionagent" -> {
                sendExhausted(channel, player, ssruu, "Rhino the Adventurer, the Zephyrion");
                ZephyrionLeaderHandler.postAgentTargetButtons(game, player);
            }
            case "tyrisagent" -> {
                sendExhausted(channel, player, ssruu, "Echo-Weaver Tzara, the Tyris");
                List<Button> buttons = new ArrayList<>(ButtonHelper.getEchoAvailableSystems(game, player));
                buttons.add(Buttons.DELETE_BUTTONS);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + " use buttons to place a frontier token in a system with no planets.",
                        buttons);
            }
            case "jolnaragent" -> {
                sendExhausted(channel, player, ssruu, "Doctor Sucaban, the Jol-Nar");
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                String msg = p2.getRepresentationUnfogged() + " you may use the buttons to remove infantry.";
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(), msg, getJolNarAgentButtons(p2, game));
            }
            case "experimentalagent" -> {
                String exhaustText =
                        player.getRepresentation() + " has exhausted the " + ssruuClever + "_Experimental Genome_.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                String msg = p2.getRepresentationUnfogged() + " you may use the buttons to remove infantry.";
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(), msg, getJolNarAgentButtons(p2, game));
            }
            case "empyreanagent" -> {
                sendExhausted(channel, player, ssruu, "Acamar, the Empyrean");
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                List<Button> buttons = ButtonHelper.getGainCCButtons(p2);
                // TODO: this also appears to route to the exhausting player's channel instead of p2's own
                // channel in normal (non-FoW) games - likely a bug there too, but keeping this fix scoped to
                // FoW mode only for now.
                if (game.isFowMode()) {
                    String message2 = p2.getRepresentationUnfogged() + ", your current command tokens are "
                            + p2.getCCRepresentation() + ". Use buttons to gain command tokens.";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
                } else {
                    String message2 = p2.getRepresentation() + ", your current command tokens are "
                            + p2.getCCRepresentation() + ". Use buttons to gain command tokens.";
                    MessageHelper.sendMessageToChannelWithButtons(channel, message2, buttons);
                }
                game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
            }
            case "keleresagent" -> {
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                String exhaustText = player.getRepresentation() + " has exhausted " + agentRef(ssruu, " the Keleres")
                        + " to target " + p2.getRepresentationNoPing() + ".";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                if (p2 != player) {
                    game.setStoredValue("keleresAgentTarget", p2.getFaction());
                }
            }
            case "mykomentoriagent" -> {
                sendExhausted(channel, player, ssruu, "Lactarius Indigo, the Myko-Mentori");
                ButtonHelperAbilities.offerOmenDiceButtons2(game, player, "yes");
            }
            case "gledgeagent" -> {
                String exhaustText =
                        player.getRepresentation() + " has exhausted " + agentRef(ssruu, "Durran, the Gledge") + ".";
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) {
                    MessageHelper.sendMessageToChannel(channel, exhaustText);
                    return;
                }
                MessageHelper.sendMessageToChannel(
                        channel, exhaustText + " for use on " + p2.getRepresentationNoPing());
                p2.addSpentThing("Exhausted " + agentRef(ssruu, "Durran, the Gledge") + ", for +3 PRODUCTION value.");
            }
            case "khraskagent" -> {
                sendExhausted(channel, player, ssruu, "Udosh B'rtul, the Khrask");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) return;
                p2.addSpentThing("Exhausted " + agentRef(ssruu, "Udosh B'rtul, the Khrask")
                        + ", to spend 1 non-home planet's resources as additional influence.");
            }
            case "rohdhnaagent" -> {
                sendExhausted(channel, player, ssruu, "Rond Bri'ay, the Roh'Dhna");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) return;
                p2.addSpentThing(
                        "Exhausted " + agentRef(ssruu, "Rond Bri'ay, the Roh'Dhna") + ", for 1 command token.");
                List<Button> buttons = ButtonHelper.getGainCCButtons(p2);
                String trueIdentity2 = p2.getRepresentationUnfogged();
                String message2 = trueIdentity2 + ", your current command tokens are " + p2.getCCRepresentation()
                        + ". Use buttons to gain command tokens.";
                game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
            }
            case "veldyragent" -> {
                sendExhausted(channel, player, ssruu, "Solis Morden, the Veldyr");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) return;
                p2.addSpentThing("Exhausted " + agentRef(ssruu, "Solis Morden, the Veldyr")
                        + ", to pay with one planets influence instead of resources.");
            }
            case "winnuagent" -> {
                Player p2 = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                String exhaustText =
                        player.getRepresentation() + " has exhausted " + agentRef(ssruu, "Berekar Berekon, the Winnu")
                                + " to use on " + p2.getRepresentationNoPing() + ".";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                p2.addSpentThing("winnuagent");
                String exhaustedMessage = Helper.buildSpentThingsMessage(p2, game, "res");
                if (event instanceof ButtonInteractionEvent buttonEvent && p2 == player) {
                    buttonEvent
                            .getMessage()
                            .editMessage(exhaustedMessage)
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                    ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(buttonEvent);
                }
                return;
            }
            case "lizhoagent" -> {
                sendExhausted(channel, player, ssruu, "Vasra Ivo, the Li-Zho");
                List<Button> buttons = new ArrayList<>(
                        Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
                message = "Please choose the system containing your ships where you wish to place your fighters.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "nekroagent" -> {
                sendExhausted(channel, player, ssruu, "Nekro Malleon, the Nekro");
                offerAgentTargetChoice(game, channel, trueIdentity, ssruu, "Nekro Malleon, the Nekro", "nekroAgentRes");
            }
            case "kolleccagent" -> {
                sendExhausted(channel, player, ssruu, "Captain Dust, the Kollecc");
                offerAgentTargetChoice(
                        game, channel, trueIdentity, ssruu, "Captain Dust, the Kollecc", "kolleccAgentRes");
            }
            case "hacanagent" -> {
                sendExhausted(channel, player, ssruu, "Carth of Golden Sands, the Hacan");
                offerAgentTargetChoice(
                        game, channel, trueIdentity, ssruu, "Carth of Golden Sands, the Hacan", "hacanAgentRefresh");
            }
            case "pharadnagent" -> {
                sendExhausted(channel, player, ssruu, "Avhkan, the Pharadn");
                offerAgentTargetChoice(game, channel, trueIdentity, ssruu, "Avhkan, the Pharadn", "pharadnAgentSelect");
            }
            case "fogallianceagent" -> {
                fogAllianceAgentStep1(game, player);
            }
            case "xxchaagent" -> {
                sendExhausted(channel, player, ssruu, "Ggrocuto Rinn, the Xxcha");
                String faction = rest.replace("xxchaagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                message = player.getRepresentationUnfogged() + ", please choose the planet you wish to ready. ";
                List<Button> buttons = new ArrayList<>();
                for (String planet : p2.getExhaustedPlanets()) {
                    buttons.add(Buttons.gray(
                            "khraskHeroStep4Ready_" + p2.getFaction() + "_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                }
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            }
            case "onyxxaagent" -> {
                sendExhausted(channel, player, ssruu, "the Onyxxa");
                String faction = rest.replace("onyxxaagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                OnyxxaLeaderHandler.postAgentMoveShipButtons(game, p2);
            }
            case "redcreussagent", "crimsonagent" -> {
                sendExhausted(channel, player, ssruu, "Ahk Ravin, the Rebellion");
                String faction = rest.replace("redcreussagent_", "").replace("crimsonagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                message = ", please choose the first system that you wish to swap a ship between (and transport).";
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = ButtonHelper.getTilesWithUnitsInTheSpaceArea(p2, game);
                for (Tile tile : tiles) {
                    buttons.add(Buttons.gray(
                            p2.factionButtonChecker() + "redcreussAgentPart1_" + tile.getPosition(),
                            tile.getRepresentationForButtons()));
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(), p2.getRepresentationUnfogged() + message, buttons);
            }
            case "yinagent" -> {
                sendExhausted(channel, player, ssruu, "Brother Milor, the Yin");
                if (!rest.contains("_")) return;
                String posNFaction = rest.replace("yinagent_", "");
                String pos = posNFaction.split("_")[0];
                String faction = posNFaction.split("_")[1];
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                MessageChannel yinChannel = event.getMessageChannel();
                if (game.isFowMode()) {
                    yinChannel = p2.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the chosen player");
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        yinChannel,
                        p2.getRepresentationUnfogged() + ", use buttons to resolve "
                                + agentRef(ssruu, "Brother Milor, the Yin") + ".",
                        getYinAgentButtons(p2, game, pos));
            }
            case "naaluagent" -> {
                sendExhausted(channel, player, ssruu, "Z'eu, the Naalu");
                String faction = rest.replace("naaluagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                game.setNaaluAgent(true);
                channel = event.getMessageChannel();
                if (game.isFowMode()) {
                    channel = p2.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the chosen player");
                }
                message =
                        "Doing a tactical action. Please choose the ring of the map that the system you wish to activate is located in.";
                if (!game.isFowMode()) {
                    message +=
                            " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
                }
                List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        p2.getRepresentationUnfogged()
                                + ", use buttons to resolve tactical action from " + agentRef(ssruu, "Z'eu, the Naalu")
                                + ". Reminder that you cannot do a tactical action in a home system this way.\n"
                                + message,
                        ringButtons);
            }
            case "olradinagent" -> {
                sendExhausted(channel, player, ssruu, "Baggil Wildpaw, the Olradin");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                resolveOlradinAgentStep2(game, p2);
            }
            case "solagent" -> {
                sendExhausted(channel, player, ssruu, "Evelyn Delouis, the Sol");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        p2.getFactionEmojiOrColor() + " will receive " + agentRef(ssruu, "Evelyn Delouis, the Sol")
                                + ", on their next roll.");
                game.setCurrentReacts("solagent", p2.getFaction());
            }
            case "bastionagent" -> {
                sendExhausted(channel, player, ssruu, " Dame Briar, the Bastion");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                MessageChannel channel2 = p2.getCorrectChannel();
                if (game.isFowMode()) {
                    channel2 = p2.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the chosen player");
                }
                List<Button> buttons = GalvanizeService.getToggleGalvanizeButtons(
                        p2, game, game.getTileByPosition(game.getActiveSystem()));
                MessageHelper.sendMessageToChannel(
                        channel2,
                        p2.getRepresentation() + ", please choose the unit that should be galvanized.",
                        buttons);
            }
            case "obsidianagent" -> {
                String exhaustText =
                        player.getRepresentation() + " has exhausted the " + ssruuClever + "_Obsidian Agent_.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Just tell the relevant player to destroy the unit with the assign hits button available under the picture of the combat.");
            }
            case "valiantagent" -> {
                String exhaustText =
                        player.getRepresentation() + " has exhausted the " + ssruuClever + "_Valiant Genome_.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                String msg = p2.getRepresentationNoPing()
                        + ", please choose the recently deceased unit that you wish to use the genome on.";
                MessageHelper.sendMessageToChannelWithButtons(
                        p2.getCorrectChannel(),
                        msg,
                        ButtonHelperActionCards.getCourageousOptions(p2, game, true, "courageous"));
                ButtonHelper.deleteMessage(event);
            }
            case "letnevagent" -> {
                sendExhausted(channel, player, ssruu, "Viscount Unlenn, the Letnev");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        p2.getFactionEmojiOrColor() + " will receive " + agentRef(ssruu, "Viscount Unlenn, the Letnev")
                                + ", on their next roll.");
                game.setCurrentReacts("letnevagent", p2.getFaction());
            }
            case "deepwroughtagent" -> {
                if (rest.contains("_")) {
                    Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                    if (p2 == null) return;
                    String exhaustText = player.getRepresentation() + " has exhausted "
                            + agentRef(ssruu, "Doctor Carrina, the Deepwrought") + ", to allow "
                            + p2.getRepresentation() + " to skip a prerequisite.";
                    MessageHelper.sendMessageToChannel(channel, exhaustText);
                    List<Button> buttons = new ArrayList<>();
                    if (game.isFowMode()) {
                        buttons = PlanetTargetService.targetButtons(
                                game,
                                player,
                                PlanetTargetSpec.of(player.factionButtonChecker() + "exchangeProgramPart3")
                                        .where(p -> !p.isHomePlanet(game)),
                                buttons);
                    } else {
                        for (String planet : p2.getPlanets()) {
                            if (game.getUnitHolderFromPlanet(planet) != null
                                    && !game.getUnitHolderFromPlanet(planet).isHomePlanet(game)
                                    && FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                                buttons.add(Buttons.gray(
                                        player.factionButtonChecker() + "exchangeProgramPart3_" + planet,
                                        Helper.getPlanetRepresentation(planet, game)));
                            }
                        }
                    }
                    String msg = player.getRepresentation()
                            + " after the target for Doctor Carrina skips a prerequisite, please choose the planet of theirs that you will coexist on.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                }
            }
            case "cymiaeagent" -> {
                sendExhausted(channel, player, ssruu, "Skhot Unit X-12, the Cymiae");
                if (!rest.contains("_")) return;
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) return;
                ActionCardHelper.drawActionCards(p2, 1);
            }
            case "mentakagent" -> {
                sendExhausted(channel, player, ssruu, "Suffi An, the Mentak");
                String faction = rest.replace("mentakagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                ActionCardHelper.drawActionCards(player, 1);
                ActionCardHelper.drawActionCards(p2, 1);
            }
            case "hyperagent" -> {
                String exhaustText =
                        player.getRepresentation() + " has exhausted the " + ssruuClever + " _Hyper Genome_.";
                MessageHelper.sendMessageToChannel(channel, exhaustText);
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2 == null) return;
                String successMessage = player.getRepresentation() + " drew 1 action card";
                if (player.hasAbility("scheming"))
                    successMessage = player.getRepresentation() + " drew 2 action cards (Scheming).";
                String successMessage2 = p2.getRepresentation() + " drew 1 action card";
                // TODO: this branch also has a variable-name bug in normal (non-FoW) games - p2's
                // Yssaril-exclusive "Scheming" ability leaks into the attacker's own message there too.
                // Only fixing the FoW-mode case here.
                if (p2.hasAbility("scheming")) {
                    if (game.isFowMode()) {
                        successMessage2 = p2.getRepresentation() + " drew 2 action cards (Scheming) via _Hyper Genome_";
                    } else {
                        successMessage = p2.getRepresentation() + " drew 2 action cards (Scheming)";
                    }
                } else if (game.isFowMode()) {
                    successMessage2 += " via _Hyper Genome_";
                }
                if (p2.getTg() > 0) {
                    p2.setTg(p2.getTg() - 1);
                    player.gainTG(1, true);
                    successMessage2 += game.isFowMode()
                            ? " and gave 1 TG."
                            : " and gave 1 TG to " + player.getFactionEmojiOrColor() + ".";
                    successMessage += " and took 1 TG from " + p2.getFactionEmojiOrColor() + ".";
                } else {
                    successMessage += ".";
                    successMessage2 += ".";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), successMessage2);
                ActionCardHelper.drawActionCardsSilent(player, 1);
                ActionCardHelper.drawActionCardsSilent(p2, 1);
            }
            case "sardakkagent" -> {
                sendExhausted(channel, player, ssruu, "T'ro An, the N'orr");
                String posNPlanet = rest.replace("sardakkagent_", "");
                if (posNPlanet.split("_").length < 2) {
                    Player p2 = player;
                    if (!posNPlanet.isEmpty()) {
                        p2 = game.getPlayerFromColorOrFaction(posNPlanet);
                    }
                    List<Button> buttons = new ArrayList<>(
                            Helper.getPlanetPlaceUnitButtons(p2, game, "2gf", "placeOneNDone_skipbuild"));
                    String message2 = p2.getRepresentationUnfogged() + ", use buttons to resolve "
                            + agentRef(ssruu, "T'ro An, the N'orr") + ".";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
                } else {
                    String pos = posNPlanet.split("_")[0];
                    String planetName = posNPlanet.split("_")[1];
                    AddUnitService.addUnits(
                            event, game.getTileByPosition(pos), game, player.getColor(), "2 gf " + planetName);
                    String successMessage = player.getFactionEmoji() + " placed " + UnitEmojis.infantry
                            + UnitEmojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, game) + ".";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
                }
            }
            case "argentagent" -> {
                sendExhausted(channel, player, ssruu, "Trillossa Aun Mirik, the Argent");
                String pos = rest.replace("argentagent_", "");
                Tile tile = game.getTileByPosition(pos);
                if (tile != null) {
                    addArgentAgentButtons(tile, player, game);
                }
            }
            case "nomadagentmercer" -> {
                sendExhausted(channel, player, ssruu, "Field Marshal Mercer, a Nomad");
                if (!rest.contains("_")) return;
                String posNPlanet = rest.replace("nomadagentmercer_", "");
                String planetName = posNPlanet.split("_")[1];
                List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(game, planetName, player);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", use buttons to resolve move of ground forces to this planet with "
                                + agentRef(ssruu, "Field Marshal Mercer, a Nomad") + ".",
                        buttons);
            }
            case "l1z1xagent" -> {
                sendExhausted(channel, player, ssruu, "I48S, the L1Z1X");
                String posNPlanet = rest.replace("l1z1xagent_", "");
                if (posNPlanet.split("_").length < 2) {
                    Player p2 = player;
                    if (!posNPlanet.isEmpty()) {
                        p2 = game.getPlayerFromColorOrFaction(posNPlanet);
                    }
                    List<Button> buttons = new ArrayList<>(
                            ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(p2, game, "l1z1x"));
                    String message2 = p2.getRepresentationUnfogged() + ", use buttons to resolve "
                            + agentRef(ssruu, "I48S, the L1Z1X") + ".";
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
                } else {
                    String pos = posNPlanet.split("_")[0];
                    String planetName = posNPlanet.split("_")[1];
                    RemoveUnitService.removeUnits(
                            event, game.getTileByPosition(pos), game, player.getColor(), "1 infantry " + planetName);
                    AddUnitService.addUnits(
                            event, game.getTileByPosition(pos), game, player.getColor(), "1 mech " + planetName);
                    String successMessage = player.getFactionEmoji() + " replaced 1 " + UnitEmojis.infantry + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + " with 1 mech.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
                }
            }
            case "muaatagent" -> {
                sendExhausted(channel, player, ssruu, "Umbat, the Muaat");
                String faction = rest.replace("muaatagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                channel = p2.getCorrectChannel();
                message = ", please choose which system to " + agentRef(ssruu, "Umbat, the Muaat") + ", in.";
                List<Tile> tiles = CheckUnitContainmentService.getTilesContainingPlayersUnits(
                        game, p2, UnitType.Warsun, UnitType.Flagship);
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : tiles) {
                    Button starTile = Buttons.green(
                            "umbatTile_" + tile.getPosition(), tile.getRepresentationForButtons(game, p2));
                    buttons.add(starTile);
                }
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, p2.getRepresentationUnfogged() + message, buttons);
            }
            case "bentoragent" -> {
                sendExhausted(channel, player, ssruu, "C.O.O. Mgur, the Bentor");
                resolveBentorAgentStep2(player, game, event, rest);
            }
            case "kortaliagent" -> {
                sendExhausted(channel, player, ssruu, "Queen Lucreia, the Kortali");
                resolveKortaliAgentStep2(player, game, rest);
            }
            case "mortheusagent" -> {
                sendExhausted(channel, player, ssruu, "Walik, the Mortheus");
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                List<Button> buttons = new ArrayList<>(ButtonHelper.getDomnaStepOneTiles(p2, game));
                message = p2.getRepresentationUnfogged()
                        + ", please choose which system the ship you just produced is in.\nYou need to tell the bot which system the unit was produced in first, after which it will give tiles to move it to. ";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);
            }
            case "cheiranagent" -> {
                sendExhausted(channel, player, ssruu, "Operator Kkavras, the Cheiran");
                resolveCheiranAgentStep1(player, game, rest);
            }
            case "freesystemsagent" -> {
                sendExhausted(channel, player, ssruu, "Cordo Haved, the Free Systems");
                resolveFreeSystemsAgentStep1(player, game, rest);
            }
            case "florzenagent" -> {
                sendExhausted(channel, player, ssruu, "Sal Gavda, the Florzen");
                resolveFlorzenAgentStep1(player, game, rest);
            }
            case "dihmohnagent" -> {
                sendExhausted(channel, player, ssruu, "Jgin Faru, the Dih-Mohn");
                String planet = restParts[1];
                AddUnitService.addUnits(
                        event, game.getTileFromPlanet(planet), game, player.getColor(), "1 inf " + planet);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + " landed 1 extra infantry on "
                                + Helper.getPlanetRepresentation(planet, game) + " using "
                                + agentRef(ssruu, "Jgin Faru, the Dih-Mohn")
                                + ".\n-# You must have committed another unit to this planet before you get this infantry. Please do this if you have not already done so.");
            }
            case "tnelisagent" -> {
                sendExhausted(channel, player, ssruu, "Davish S’Norri, the Tnelis");
                game.setStoredValue("tnelisAgentFaction", player.getFaction());
                ButtonHelper.resolveCombatRoll(
                        player, game, event, "combatRoll_" + game.getActiveSystem() + "_space_bombardment");
                game.setStoredValue("tnelisAgentFaction", "");
            }
            case "vadenagent" -> {
                sendExhausted(channel, player, ssruu, "Yudri Sukhov, the Vaden");
                if (!rest.contains("_")) return;
                resolveVadenAgentStep2(player, game, event, rest);
            }
            case "celdauriagent" -> {
                sendExhausted(channel, player, ssruu, "George Nobin, the Celdauri");
                resolveCeldauriAgentStep2(player, game, event, rest);
            }
            case "zealotsagent" -> {
                sendExhausted(channel, player, ssruu, "Priestess Tuh, the Rhodun");
                resolveZealotsAgentStep2(player, game, rest);
            }
            case "nokaragent" -> {
                sendExhausted(channel, player, ssruu, "Sal Sparrow, the Nokar");
                resolveNokarAgentStep2(player, game, event, rest);
            }
            case "zelianagent" -> {
                sendExhausted(channel, player, ssruu, "Zelian A, the Zelian");
                resolveZelianAgentStep2(player, game, event, rest);
            }
            case "kyroagent" -> {
                sendExhausted(channel, player, ssruu, " the Kyro");
                if (event instanceof ButtonInteractionEvent bEvent) {
                    resolveKyroAgentStep2(player, game, bEvent, rest);
                }
            }
            case "mirvedaagent" -> {
                sendExhausted(channel, player, ssruu, "Logic Machina, the Mirveda");
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                if (p2.getStrategicCC() < 1) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Target does not have any command tokens in their strategy pool, and so nothing has happend.");
                    return;
                }
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.GET_A_TECH);
                buttons.add(Buttons.red("deleteButtons", "Delete This"));
                p2.setStrategicCC(p2.getStrategicCC() - 1);
                channel = p2.getCorrectChannel();
                ButtonHelperCommanders.resolveMuaatCommanderCheck(
                        p2, game, event, "used " + FactionEmojis.mirveda + " Logic Machina");
                String message0 = p2.getRepresentationUnfogged()
                        + ", 1 command token has been removed from your strategy pool due to use of "
                        + agentRef(ssruu, "Logic Machina, the Mirveda")
                        + ". You may add it back if you didn't agree to the agent.";
                message = p2.getRepresentationUnfogged()
                        + ", please research a technology of a color which matches one of the prerequisites on the unit upgrade you just gained.";
                MessageHelper.sendMessageToChannel(channel, message0);
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
            case "ghotiagent" -> {
                sendExhausted(channel, player, ssruu, "Becece, the Ghoti");
                Player p2 = game.getPlayerFromColorOrFaction(restParts[1]);
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("ghotiATG", "Get 1 Trade Good"));
                buttons.add(Buttons.gray("ghotiAProd", "Produce 2 Additional Units"));
                buttons.add(Buttons.red("deleteButtons", "Delete This"));
                channel = p2.getCorrectChannel();
                message = p2.getRepresentationUnfogged() + ", please choose how you wish to use " + ssruuClever
                        + "Becece.";
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
            case "arborecagent" -> {
                sendExhausted(channel, player, ssruu, "Letani Ospha, the Arborec");
                String faction = rest.replace("arborecagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                channel = p2.getCorrectChannel();
                message =
                        ", please choose which system to use " + agentRef(ssruu, "Letani Ospha, the Arborec") + ", in.";
                List<Button> buttons = getTilesToArboAgent(p2, game);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel, p2.getRepresentationUnfogged() + message, buttons);
            }
            case "kolumeagent" -> {
                sendExhausted(channel, player, ssruu, "Disciple Fran, the Kolume");
                String faction = rest.replace("kolumeagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                channel = p2.getCorrectChannel();
                List<Button> redistributeButton = new ArrayList<>();
                Button deleButton =
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete These Buttons");
                redistributeButton.add(Buttons.REDISTRIBUTE_CCs);
                redistributeButton.add(deleButton);
                MessageHelper.sendMessageToChannelWithButtons(
                        channel,
                        p2.getRepresentationUnfogged()
                                + ", use buttons to redistribute 1 command token (the bot allows more but "
                                + agentRef(ssruu, "Disciple Fran, the Kolume")
                                + ", is restricted to redistributing 1).",
                        redistributeButton);
            }
            case "axisagent" -> {
                sendExhausted(channel, player, ssruu, "Shipmonger Zsknck, the Axis");
                String faction = rest.replace("axisagent_", "");
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 == null) return;
                channel = p2.getCorrectChannel();
                message = p2.getRepresentationUnfogged()
                        + ", please choose whether you wish to place 1 cruiser or 1 destroyer in a system with your ships.";
                List<Button> buttons = new ArrayList<>();
                if (p2 != player) {
                    ButtonHelperStats.gainComms(event, game, player, 2, false, true);
                }
                buttons.add(Buttons.green("step2axisagent_cruiser", "Place 1 cruiser"));
                buttons.add(Buttons.green("step2axisagent_destroyer", "Place 1 destroyer"));
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
            case "ghostagent" -> {
                Set<String> currentWhs =
                        game.isFowMode() ? FoWHelper.getTileWHs(game, game.getActiveSystem()) : Set.of();
                if (!currentWhs.isEmpty() && !currentWhs.contains(Constants.DELTA)) {
                    game.setStoredValue("ghostagent_active", game.getActiveSystem());
                }
            }
            case "ralnelagent" -> {
                TeHelperAgents.postRalNelAgentStep1(game, player);
            }
            case "ardentiaagent" -> {
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Ardentia Agent target.");
                    return;
                }
                ArdentiaLeadersHandler.startArdentiaAgentStep1(game, target);
            }
            case "kryxosagent" -> {
                if (!rest.contains("_")) {
                    MessageHelper.sendMessageToChannel(
                            channel, "Kryxos Agent needs a player target before it can resolve.");
                    return;
                }
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Kryxos Agent target.");
                    return;
                }
                KryxosLeadersHandler.startKryxosAgent(game, target);
            }
            case "aeternaagent" -> {
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Aeterna Agent target.");
                    return;
                }
                AeternaLeadersHandler.startAeternaAgent(game, target);
            }
            case "veyloragent" -> {
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Veylor Agent target.");
                    return;
                }
                VeylorLeadersHandler.startVeylorAgent(game, target);
            }
            case "taagent" -> {
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Ta Agent target.");
                    return;
                }
                TaLeadersHandler.resolveTaAgentTarget(game, target);
            }
            case "vanguardagent" -> {
                Player target = game.getPlayerFromColorOrFaction(rest.substring(rest.indexOf('_') + 1));
                if (target == null) {
                    MessageHelper.sendMessageToChannel(channel, "Could not find the selected Vanguard Agent target.");
                    return;
                }
                VanguardLeadersHandler.resolveVanguardAgentTarget(game, target);
            }
            case "sarcosaagent" -> {
                Player target = rest.contains("_") ? game.getPlayerFromColorOrFaction(restParts[1]) : player;
                if (target != null) {
                    SarcosaAgentHandler.offerSarcosaAgentSystemButtons(game, target, event);
                }
                return;
            }
            default -> {}
        }

        TkHelperGenomes.onExhaust(event, game, player, agent, ssruuClever, rest);

        if (event instanceof ButtonInteractionEvent buttonEvent) {
            String exhaustedMessage = buttonEvent.getMessage().getContentRaw();
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Updated";
            }
            int buttons = 0;
            List<ActionRow> actionRow2 = new ArrayList<>();

            for (ActionRow row : buttonEvent.getMessage().getComponentTree().findAll(ActionRow.class)) {
                List<ActionRowChildComponentUnion> buttonRow = row.getComponents();
                if (!buttonRow.isEmpty()) {
                    buttons += buttonRow.size();
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if (!actionRow2.isEmpty()
                    && !exhaustedMessage.contains("choose the user of the agent")
                    && !exhaustedMessage.toLowerCase().contains("wanna ")
                    && !exhaustedMessage.contains("please choose the target")
                    && !exhaustedMessage.contains("please choose the faction to give")
                    && !exhaustedMessage.contains("choose the target of the agent")) {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(buttonEvent);

            } else {
                buttonEvent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                List<Button> buttons2 = new ArrayList<>();
                String msg;
                if (game.isTwilightsFallMode()) {
                    buttons2.add(Buttons.green(
                            p2.factionButtonChecker() + "useTCS_" + agent + "_" + player.getFaction(),
                            "Spend A Command Token to Ready " + agent));
                    buttons2.add(Buttons.red(p2.factionButtonChecker() + "deleteButtons", "Decline"));
                    msg = p2.getRepresentationNoPing()
                            + " you have the opportunity to spend a command token via _Temporal Command Suite_ to ready "
                            + agent
                            + ", and potentially resolve a transaction.";
                } else {
                    buttons2.add(Buttons.green(
                            p2.factionButtonChecker() + "exhaustTCS_" + agent + "_" + player.getFaction(),
                            "Exhaust Temporal Command Suite to Ready " + agent));
                    buttons2.add(Buttons.red(p2.factionButtonChecker() + "deleteButtons", "Decline"));
                    msg = p2.getRepresentationNoPing()
                            + " you have the opportunity to exhaust _Temporal Command Suite_ to ready " + agent
                            + ", and potentially resolve a transaction.";
                }
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons2);
            }
        }
    }

    @ButtonHandler("presetEdynAgentStep1")
    public static void presetEdynAgentStep1(Game game, Player player) {
        List<Button> buttons = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep2", null);
        String msg = player.getRepresentationUnfogged()
                + ", please choose the player who you wish to take the action when the time comes (probably yourself).";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("redcreussAgentPart1_")
    public static void redcreussAgentPart1(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String message =
                ", please choose the second system that you wish to swap a ship between (and transport). The first system is position "
                        + pos + ".";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "redcreussAgentPart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("redcreussAgentPart2_")
    public static void redcreussAgentPart2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String pos2 = buttonID.split("_")[2];
        Tile tile2 = game.getTileByPosition(pos2);
        String message = ", please choose the units you wish to move from " + tile.getRepresentation() + " to "
                + tile2.getRepresentation();
        String message2 = ", please choose the units you wish to move from " + tile2.getRepresentation() + " to "
                + tile.getRepresentation();
        List<Button> buttons = ButtonHelperHeroes.getArgentHeroStep3Buttons(game, player, "spoof_" + pos2 + "_" + pos);
        List<Button> buttons2 = ButtonHelperHeroes.getArgentHeroStep3Buttons(game, player, "spoof_" + pos + "_" + pos2);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message2, buttons2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("presetEdynAgentStep2_")
    public static void presetEdynAgentStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        List<Button> buttons =
                AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep3_" + faction, null);
        String msg = player.getRepresentationUnfogged()
                + ", please choose the passing player who will set off the trigger. When this player passes, the player you chose in the last step will get an action.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("presetEdynAgentStep3_")
    public static void presetEdynAgentStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        String msg = player.getRepresentationUnfogged() + " you set " + faction1 + " up to take an action once "
                + faction2 + " passes";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        ButtonHelper.deleteMessage(event);
        String messageID = "edynAgentPreset";
        String part2 = faction1 + "_" + faction2 + "_" + player.getFaction();
        game.setStoredValue(messageID, part2);
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("removePreset_" + messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " you may use this button to undo the preset. Ignore it otherwise.",
                buttons);
    }

    public static boolean checkForEdynAgentPreset(
            Game game, Player passedPlayer, Player upNextPlayer, GenericInteractionCreateEvent event) {
        Player edyn = Helper.getPlayerFromUnlockedLeader(game, "edynagent");
        if (edyn != null && edyn.hasUnexhaustedLeader("edynagent")) {
            String preset = game.getStoredValue("edynAgentPreset");
            if (!preset.isEmpty()) {
                if (preset.split("_")[1].equalsIgnoreCase(passedPlayer.getFaction())) {
                    Player edyn2 = game.getPlayerFromColorOrFaction(preset.split("_")[2]);
                    Player newActivePlayer = game.getPlayerFromColorOrFaction(preset.split("_")[0]);
                    exhaustAgent("exhaustAgent_edynagent", event, game, edyn2);
                    game.setStoredValue("edynAgentPreset", "");
                    game.setStoredValue(
                            "edynAgentInAction",
                            newActivePlayer.getFaction() + "_" + edyn2.getFaction() + "_" + upNextPlayer.getFaction());
                    List<Button> buttons = StartTurnService.getStartOfTurnButtons(newActivePlayer, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(
                            newActivePlayer.getCorrectChannel(),
                            newActivePlayer.getRepresentationUnfogged()
                                    + " you may take 1 action now due to "
                                    + agentRef(edyn, "Allant, the Edyn") + ".",
                            buttons);
                    game.updateActivePlayer(newActivePlayer);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkForEdynAgentActive(Game game, GenericInteractionCreateEvent event) {
        String preset = game.getStoredValue("edynAgentInAction");
        if (!preset.isEmpty()) {
            Player edyn2 = game.getPlayerFromColorOrFaction(preset.split("_")[1]);
            if (edyn2 == null) return false;
            AgendaHelper.drawAgenda(1, false, game, edyn2, true);
            Player newActivePlayer = game.getPlayerFromColorOrFaction(preset.split("_")[2]);
            game.setStoredValue("edynAgentInAction", "");
            if (newActivePlayer != null) {
                StartTurnService.turnStart(event, game, newActivePlayer);
            }
            return true;
        }
        return false;
    }

    public static boolean doesTileHaveAStructureInIt(Player player, Tile tile) {
        boolean present = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0
                    || uH.getUnitCount(UnitType.Pds, player.getColor()) > 0
                    || uH.getUnitCount(UnitType.Monument, player.getColor()) > 0) {
                return true;
            }
            if (player.hasAbility("byssus")
                    && uH instanceof Planet
                    && uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                return true;
            }
            for (String token : uH.getTokenList()) {
                if (player.getPlanets().contains(uH.getName()) && token.contains("superweapon")) {
                    return true;
                }
            }
        }
        return present;
    }

    @ButtonHandler("lanefirAgentRes_")
    public static void resolveLanefirAgent(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        if (buttonID.contains("Decline")) {
            if (buttonID.contains("frontier")) {
                String cardChosen = buttonID.split("_")[3];
                String pos = buttonID.split("_")[4];
                ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player, cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String cardID = buttonID.split("_")[3];
                String planetName = buttonID.split("_")[4];
                Tile tile = game.getTileFromPlanet(planetName);
                String messageText = player.getRepresentation() + " explored the planet "
                        + ExploreEmojis.getTraitEmoji(drawColor)
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                        + tile.getPosition() + ":";
                ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                ExploreService.postPlanetExploreChecks(event, game, player, planetName, event.getChannel());
            }
        } else {
            exhaustAgent("exhaustAgent_lanefiragent", event, game, player);
            if (buttonID.contains("frontier")) {
                String cardChosen = game.drawExplore(Constants.FRONTIER);
                String pos = buttonID.split("_")[3];
                ExploreModel card = Mapper.getExplore(cardChosen);
                String name = card.getName();
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Found a " + name + " in " + game.getTileByPosition(pos).getRepresentation());
                ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player, cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String planetName = buttonID.split("_")[3];
                Tile tile = game.getTileFromPlanet(planetName);
                String cardID = game.drawExplore(drawColor);
                String messageText = player.getRepresentation() + " explored " + ExploreEmojis.getTraitEmoji(drawColor)
                        + "Planet "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile "
                        + tile.getPosition() + ")*:";
                ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                ExploreService.postPlanetExploreChecks(event, game, player, planetName, event.getChannel());
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Tile> getAdjacentTilesWithStructuresInThemAndNoCC(Player player, Game game, Tile origTile) {
        return getAdjacentTilesWithStructuresInThem(player, game, origTile).stream()
                .filter(adjTile -> !CommandCounterHelper.hasCC(player, adjTile))
                .toList();
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThem(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles =
                new ArrayList<>(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
        for (String posTile : adjTiles) {
            Tile adjTile = game.getTileByPosition(posTile);
            if (adjTile != null && doesTileHaveAStructureInIt(player, adjTile)) {
                tiles.add(adjTile);
            }
        }
        return tiles;
    }

    public static List<String> getAttachments(Game game, Player player) {
        List<String> legendaries = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (uh == null) {
                continue;
            }
            for (String token : uh.getTokenList()) {
                if (!token.contains("attachment")) {
                    continue;
                }
                token = token.replace(".png", "").replace("attachment_", "").replace("_", ";");

                String s = uh.getName() + "_" + token;
                if (!token.contains("sleeper") && !token.contains("control") && !legendaries.contains(s)) {
                    legendaries.add(s);
                }
            }
        }
        return legendaries;
    }

    private static List<String> getAllControlledPlanetsInThisSystemAndAdjacent(Game game, Player player, Tile tile) {
        List<String> legendaries = new ArrayList<>();
        List<String> adjTiles =
                new ArrayList<>(FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
        adjTiles.add(tile.getPosition());
        for (String adjTilePos : adjTiles) {
            Tile adjTile = game.getTileByPosition(adjTilePos);
            if (adjTile.isHomeSystem(game)) {
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getPlanetUnitHolders()) {
                if (player.getPlanets().contains(unitHolder.getName())) {
                    legendaries.add(unitHolder.getName());
                }
            }
        }
        return legendaries;
    }

    public static List<String> getAvailableLegendaryAbilities(Game game) {
        List<String> legendaries = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            for (String planet : player.getPlanets()) {
                if (planet.contains("custodia") || planet.contains("ghoti")) {
                    continue;
                }
                PlanetModel model = Mapper.getPlanet(planet);
                if (model.getLegendaryAbilityText() != null
                        && !model.getLegendaryAbilityText().isEmpty()) {
                    legendaries.add(planet);
                }
            }
        }
        return legendaries;
    }

    private static Player resolveAgentTarget(Player agentOwner, Game game, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    agentOwner.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
        }
        return target;
    }

    private static Tile activeSystemOrNotify(Player agentOwner, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(agentOwner.getCorrectChannel(), "Could not find the active system");
        }
        return tile;
    }

    private static void resolveCheiranAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = resolveAgentTarget(cheiran, game, buttonID);
        if (player == null) return;
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getCheiranAgentTiles(player, game)) {
            buttons.add(Buttons.green(
                    "cheiranAgentStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        String msg =
                player.getRepresentationUnfogged() + ", choose the system you wish to remove your command token from.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    private static void resolveFreeSystemsAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = cheiran;
        if (buttonID.contains("_")) {
            player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        }
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : getAvailableLegendaryAbilities(game)) {
            buttons.add(Buttons.green("freeSystemsAgentStep2_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged()
                + ", please choose the legendary planet ability that you wish to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("refreshWithOlradinAgent_")
    public static void resolveRefreshWithOlradinAgent(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        PlanetService.refreshPlanet(player, planetName);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " readied " + Helper.getPlanetRepresentation(planetName, game) + " with "
                        + agentRef(player, "Baggil Wildpaw, the Olradin") + ".");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void resolveOlradinAgentStep2(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green(
                    "refreshWithOlradinAgent_" + planet, "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", please choose the planet, of a __different__ trait from the one you just exhausted, that you wish to ready.",
                buttons);
    }

    private static void resolveFlorzenAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = resolveAgentTarget(cheiran, game, buttonID);
        if (player == null) return;
        List<Button> buttons = new ArrayList<>();
        for (String attachment : getAttachments(game, player)) {
            String planet = attachment.split("_")[0];
            String attach = attachment.split("_")[1];
            buttons.add(Buttons.green(
                    "florzenAgentStep2_" + attachment, attach + " on " + Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged() + ", please choose the attachment you wish to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("florzenAgentStep2_")
    public static void resolveFlorzenAgentStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String attachment = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet2 :
                getAllControlledPlanetsInThisSystemAndAdjacent(game, player, game.getTileFromPlanet(planet))) {
            buttons.add(Buttons.green(
                    "florzenAgentStep3_" + planet + "_" + planet2 + "_" + attachment,
                    Helper.getPlanetRepresentation(planet2, game)));
        }
        String msg = player.getRepresentationUnfogged()
                + ", please choose the adjacent planet you wish to put the attachment on.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("florzenAgentStep3_")
    public static void resolveFlorzenAgentStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        String attachment = buttonID.replace("florzenAgentStep3_" + planet + "_" + planet2 + "_", "");
        attachment = attachment.replace(";", "_");
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        UnitHolder uH2 = ButtonHelper.getUnitHolderFromPlanetName(planet2, game);
        uH.removeToken("attachment_" + attachment + ".png");
        uH2.addToken("attachment_" + attachment + ".png");
        String msg = player.getRepresentationUnfogged() + " removed " + attachment + " from "
                + Helper.getPlanetRepresentation(planet, game) + " and put it on "
                + Helper.getPlanetRepresentation(planet2, game) + " using Florzen powers";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("freeSystemsAgentStep2_")
    public static void resolveFreeSystemsAgentStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        PlanetExhaustAbility.doAction(event, player, planet, game, false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " chose to replicate the "
                        + Helper.getPlanetRepresentation(planet, game)
                        + " legendary ability by exhausting "
                        + agentRef(player, "Cordo Haved, the Free Systems") + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cheiranAgentStep2_")
    public static void resolveCheiranAgentStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        RemoveCommandCounterService.fromTile(player.getColor(), origTile, game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " removed 1 command token from "
                        + origTile.getRepresentationForButtons(game, player) + " using "
                        + agentRef(player, "Operator Kkavras, the Cheiran") + ".");
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile)) {
            buttons.add(Buttons.green(
                    "cheiranAgentStep3_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            if (getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile)
                            .size()
                    == 1) {
                resolveCheiranAgentStep3(player, game, event, "cheiranAgentStep3_" + tile.getPosition());
                return;
            }
        }
        String msg = player.getRepresentationUnfogged()
                + ", please choose the adjacent tile that you wish to place your command token in.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cheiranAgentStep3_")
    private static void resolveCheiranAgentStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        CommandCounterHelper.addCC(event, player, origTile, true);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " placed 1 command token in "
                        + origTile.getRepresentationForButtons(game, player) + " using "
                        + agentRef(player, "Operator Kkavras, the Cheiran") + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Tile> getCheiranAgentTiles(Player player, Game game) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (CommandCounterHelper.hasCC(player, tile)) {
                if (!getAdjacentTilesWithStructuresInThemAndNoCC(player, game, tile)
                        .isEmpty()) {
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    @ButtonHandler("ghotiATG")
    public static void ghotiAgentForTg(ButtonInteractionEvent event, Game game, Player player) {
        int cTG = player.getTg();
        int fTG = cTG + 1;
        player.setTg(fTG);
        ButtonHelperAbilities.pillageCheck(player, game);
        resolveArtunoCheck(player, 1);
        String msg = player.getRepresentation() + " Used "
                + agentRef(player, "Becece, the Ghoti") + ", to gain 1 trade good ("
                + cTG + "->" + fTG + "). ";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ghotiAProd")
    public static void ghotiAgentForProduction(Game game, ButtonInteractionEvent event, Player player) {
        String msg =
                "Used " + agentRef(player, "Becece, the Ghoti") + ", to gain the ability to produce 2 more units. ";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        List<String> uniquePlaces = new ArrayList<>();
        int count = 0;
        String tilePos = "";
        for (String unit : producedUnits.keySet()) {
            tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if (!uniquePlaces.contains(tilePos + "_" + planetOrSpace)) {
                uniquePlaces.add(tilePos + "_" + planetOrSpace);
            }
            count++;
        }
        if (count == 1) {
            ArborecButtonHandlers.resolveArboHeroBuild(game, player, event, "arboHeroBuild_" + tilePos);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void resolveVadenAgentStep2(
            Player vaden, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = resolveAgentTarget(vaden, game, buttonID);
        if (player == null) return;

        int initComm = player.getCommodities();
        int maxInfluence = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null && p.getInfluence() > maxInfluence) {
                maxInfluence = p.getInfluence();
            }
        }
        ButtonHelperStats.gainComms(event, game, player, maxInfluence, false, true);
        int finalComm = player.getCommodities();
        int commGain = finalComm - initComm;

        String msg = player.getFactionEmojiOrColor() + " max influence planet has " + maxInfluence
                + " influence, so they gained " + commGain + " commodit" + (commGain == 1 ? "y" : "ies") + " ("
                + initComm + "->"
                + player.getCommodities() + ") due to "
                + agentRef(player, "Yudri Sukhov, the Vaden") + ".";

        FoWHelper.notifyPlayerAndAffectedInFog(
                game, player, msg, vaden, player.getFactionEmojiOrColor() + " has finished resolving");
    }

    private static void resolveKortaliAgentStep2(Player bentor, Game game, String buttonID) {
        Player player = resolveAgentTarget(bentor, game, buttonID);
        if (player == null) return;
        int size = player.getFragments().size();
        int rand = ThreadLocalRandom.current().nextInt(size);
        String frag = player.getFragments().get(rand);
        player.removeFragment(frag);
        bentor.addFragment(frag);
        ExploreModel cardInfo = Mapper.getExplore(frag);
        String msg = player.getFactionEmojiOrColor() + " lost a " + cardInfo.getName() + " to "
                + bentor.getFactionEmojiOrColor() + " due to "
                + agentRef(bentor, "Queen Lucreia, the Kortali") + ".";
        FoWHelper.notifyPlayerAndAffectedInFog(game, player, bentor, msg);
    }

    private static void resolveZealotsAgentStep2(Player zealots, Game game, String buttonID) {
        Player player = resolveAgentTarget(zealots, game, buttonID);
        if (player == null) return;
        List<Button> buttons = new ArrayList<>();
        Set<String> addedPositions = new HashSet<>();

        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planet);
            if (tile == null || addedPositions.contains(tile.getPosition())) {
                continue;
            }
            boolean techSkipSystemWithoutEnemyShips = !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                    && ButtonHelper.checkForTechSkips(game, planet);
            if (techSkipSystemWithoutEnemyShips || tile.isHomeSystem(game)) {
                addedPositions.add(tile.getPosition());
                buttons.add(Buttons.green(
                        "produceOneUnitInTile_" + tile.getPosition() + "_ZealotsAgent",
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        String msg = player.getFactionEmojiOrColor()
                + " may produce a unit in their home system or in a system with a technology specialty planet due to "
                + agentRef(zealots, "Priestess Tuh, the Rhodun") + ".";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (game.isFowMode() && zealots != player) {
            MessageHelper.sendMessageToChannel(zealots.getCorrectChannel(), msg);
        }
    }

    private static void resolveNokarAgentStep2(
            Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = resolveAgentTarget(bentor, game, buttonID);
        if (player == null) return;
        Tile tile = activeSystemOrNotify(bentor, game);
        if (tile == null) return;
        if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(
                    bentor.getCorrectChannel(), "Player did not have a ship in the active system, no destroyer placed");
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 destroyer");
        String msg = player.getFactionEmojiOrColor() + " place 1 destroyer in "
                + tile.getRepresentationForButtons(game, player)
                + " due to " + agentRef(bentor, "Sal Sparrow, the Nokar")
                + ". A transaction may be done with transaction buttons.";
        FoWHelper.notifyPlayerAndAffectedInFog(game, player, bentor, msg);
    }

    private static void resolveZelianAgentStep2(
            Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = resolveAgentTarget(bentor, game, buttonID);
        if (player == null) return;
        Tile tile = activeSystemOrNotify(bentor, game);
        if (tile == null) return;
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            MessageHelper.sendMessageToChannel(
                    bentor.getCorrectChannel(),
                    "Player did not have any infantry in the space area of the active system, no mech placed.");
            return;
        }
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 inf");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech");
        String msg = player.getFactionEmojiOrColor() + " replace 1 infantry with 1 mech in "
                + tile.getRepresentationForButtons(game, player)
                + " due to " + agentRef(bentor, "Zelian A, the Zelian") + ".";
        FoWHelper.notifyPlayerAndAffectedInFog(game, player, bentor, msg);

        if (event instanceof ButtonInteractionEvent event2) {
            if (event2.getButton().getLabel().contains("Yourself")) {
                List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);
                event2.getMessage()
                        .editMessage(event2.getMessage().getContentRaw())
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
    }

    private static void resolveKyroAgentStep2(Player kyro, Game game, ButtonInteractionEvent event, String buttonID) {
        Player player = resolveAgentTarget(kyro, game, buttonID);
        if (player == null) return;
        String msg = player.getFactionEmojiOrColor() + " replenished commodities due to "
                + agentRef(kyro, "Tox, the Kyro") + ".";
        int commoditiesTotal = player.getCommoditiesTotal();
        player.setCommodities(player.getCommodities() + commoditiesTotal);
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        cabalAgentInitiation(game, player);
        FoWHelper.notifyPlayerAndAffectedInFog(game, player, kyro, msg);

        int infAmount = commoditiesTotal - 1;
        List<Button> buttons = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(kyro, game, infAmount + "gf", "placeOneNDone_skipbuild"));
        String message = kyro.getRepresentationUnfogged() + ", please choose the planet you wish to drop " + infAmount
                + " infantry upon.";
        MessageHelper.sendMessageToChannelWithButtons(kyro.getCorrectChannel(), message, buttons);
    }

    private static void resolveCeldauriAgentStep2(
            Player celdauri, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = resolveAgentTarget(celdauri, game, buttonID);
        if (player == null) return;
        if (player.getCommodities() < 2 && player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    celdauri.getCorrectChannel(), "Player did not have the money, please resolve manually.");
            return;
        }
        if (game.getActivePlayer() != player) {
            MessageHelper.sendMessageToChannel(
                    celdauri.getCorrectChannel(), "Target player is not active player, please resolve manually.");
            return;
        }
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        if (tileAS == null) {
            MessageHelper.sendMessageToChannel(
                    celdauri.getCorrectChannel(), "Active system is null, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String option = "";
        for (UnitHolder planet : tileAS.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(planet.getName())) {
                buttons.add(Buttons.green(
                        "celdauriAgentStep3_" + planet.getName(),
                        "Place 1 Space Dock on " + Helper.getPlanetRepresentation(planet.getName(), game)));
                option = "celdauriAgentStep3_" + planet.getName();
            }
        }
        if (buttons.size() == 1) {
            resolveCeldauriAgentStep3(player, game, event, option);
            return;
        }

        String msg =
                player.getRepresentationUnfogged() + ", please choose the planet you wish to place 1 space dock on.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler("celdauriAgentStep3_")
    private static void resolveCeldauriAgentStep3(
            Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " put 1 space dock on "
                + Helper.getPlanetRepresentation(planet, game) + " using "
                + agentRef(player, "George Nobin, the Celdauri") + ".";
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 sd " + planet);
        if (player.getCommodities() > 1) {
            player.setCommodities(player.getCommodities() - 2);
            msg += "\n" + player.getFactionEmoji() + " Paid 2 commodities";
        } else {
            msg += "\n" + player.getFactionEmoji() + " Paid 2 trade goods " + player.gainTG(-2) + ".";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
        if (tile != null && tile.isFracture()) {
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
        }
        AgendaHelper.ministerOfIndustryCheck(player, game, game.getTileFromPlanet(planet), event);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static void resolveBentorAgentStep2(
            Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = resolveAgentTarget(bentor, game, buttonID);
        if (player == null) return;

        int numOfBPs = bentor.getNumberOfBluePrints();
        int oldTg = player.getTg();
        int tgGain = numOfBPs + player.getCommodities() - player.getCommoditiesTotal();
        if (tgGain < 0) tgGain = 0;
        int commGain = numOfBPs - tgGain;

        ButtonHelperStats.gainComms(event, game, player, commGain, false, true);
        ButtonHelperStats.gainTGs(event, game, player, tgGain, true);

        String msg = player.getFactionEmojiOrColor() + " gained " + StringHelper.pluralize(tgGain, "trade good")
                + " (" + oldTg + "->"
                + player.getTg() + ") and " + commGain + " commodit" + (commGain == 1 ? "y" : "ies") + " due to "
                + agentRef(bentor, "C.O.O. Mgur, the Bentor") + ".";

        FoWHelper.notifyPlayerAndAffectedInFog(
                game, player, msg, bentor, player.getRepresentation() + " has finished resolving.");
    }

    private static void fogAllianceAgentStep1(Game game, Player player) {
        String msg =
                player.getRepresentationUnfogged() + ", please choose the system that you wish to move ships from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !CommandCounterHelper.hasCC(player, tile)) {
                buttons.add(Buttons.green(
                        "fogAllianceAgentStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("moveShipToAdjacentSystemStep1")
    public static void moveShipToAdjacentSystemStep1(Game game, Player player, ButtonInteractionEvent event) {
        String msg =
                player.getRepresentationUnfogged() + ", please choose the system that you wish to move a ship from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("moveShipToAdjacentSystemStep2_")
    public static void moveShipToAdjacentSystemStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String msg = player.getRepresentationUnfogged()
                + ", please choose the adjacent system you wish to move the ship to.";
        List<Button> buttons = new ArrayList<>();
        String ogTile = buttonID.split("_")[1];
        for (String pos : FoWHelper.getAdjacentTilesAndNotThisTile(game, ogTile, player, false)) {
            Tile tile = game.getTileByPosition(pos);
            if (tile.getTileModel() != null && tile.getTileModel().isHyperlane()) continue;

            if ((tile.isAsteroidField()
                            && !tile.isZelianAsteroidField()
                            && !player.getTechs().contains("amd")
                            && !player.getTechs().contains("wavelength")
                            && !player.getRelics().contains("circletofthevoid"))
                    || (tile.isSupernova()
                            && !player.hasTech("mr")
                            && !player.getRelics().contains("circletofthevoid"))
                    || (!buttonID.contains("combat") && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game))) {
                continue;
            }
            String og = ogTile;
            if (buttonID.contains("agent") && game.getTileByPosition(ogTile).hasPlayerCC(player)) {
                og += "_agent";
            }

            buttons.add(Buttons.green(
                    "fogAllianceAgentStep3_" + tile.getPosition() + "_" + og,
                    tile.getRepresentationForButtons(game, player)));
        }
        if (buttonID.contains("combat")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else {
            if (!buttonID.contains("_hero")) {
                ButtonHelper.deleteMessage(event);
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("fogAllianceAgentStep2_")
    public static void fogAllianceAgentStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String msg = player.getRepresentationUnfogged() + ", please choose the system you wish to move ships to.";
        List<Button> buttons = new ArrayList<>();
        String ogTile = buttonID.split("_")[1];
        for (Tile tile : game.getTileMap().values()) {
            for (Player p2 : game.getRealPlayers()) {
                if (!player.getAllianceMembers().contains(p2.getFaction()) || player == p2) {
                    continue;
                }
                if (FoWHelper.playerHasShipsInSystem(p2, tile) && !nextToOrInHostileHome(game, player, tile)) {
                    buttons.add(Buttons.green(
                            "fogAllianceAgentStep3_" + tile.getPosition() + "_" + ogTile,
                            tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    private static boolean nextToOrInHostileHome(Game game, Player player, Tile tile) {
        for (Player p2 : game.getRealPlayers()) {
            if (player.getAllianceMembers().contains(p2.getFaction()) || player == p2) {
                continue;
            }
            for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                if (p2.getHomeSystemTile().getPosition().equalsIgnoreCase(pos2)) {
                    return true;
                }
            }
        }
        return false;
    }

    @ButtonHandler("moveGloryStart_")
    private static void offerMoveGloryOptions(Game game, Player player, GenericInteractionCreateEvent event) {
        String msg = player.getRepresentationUnfogged() + " please choose the system to move a **Glory** token from.";
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getGloryTokenTiles(game)) {
            buttons.add(Buttons.green(
                    "moveGlory_" + tile.getPosition() + "_" + tileAS.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("moveGlory_")
    public static void moveGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tileAS = game.getTileByPosition(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitHolder spaceAS = tileAS.getSpaceUnitHolder();
        String tokenToMove = "";
        for (String token : space.getTokenList()) {
            if (token.contains("glory")) {
                tokenToMove = token;
            }
        }
        if (space.getTokenList().contains(tokenToMove)) {
            space.removeToken(tokenToMove);
        }
        spaceAS.addToken(tokenToMove);
        String msg = player.getFactionEmoji() + " moved a **Glory** token from " + tile.getRepresentation() + " to "
                + tileAS.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeGlory_")
    public static void placeGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getSpaceUnitHolder();
        List<String> gloryTokens = getGloryTokensLeft(game);
        if (gloryTokens.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " cannot place more **Glory** tokens, you've hit the limit.");
            return;
        }
        space.addToken(gloryTokens.getFirst());

        String msg = player.getFactionEmoji() + " added a **Glory** token to " + tile.getRepresentation();
        ButtonHelperAbilities.oceanBoundCheck(game);
        CommanderUnlockCheckService.checkPlayer(player, "kjalengard");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static List<String> getGloryTokensLeft(Game game) {
        List<String> gloryTokens = new ArrayList<>();
        gloryTokens.add("token_ds_glory.png");
        gloryTokens.add("token_ds_glory2.png");
        gloryTokens.add("token_ds_glory3.png");
        for (Tile tile : game.getTileMap().values()) {
            List<String> gloryTokens2 = new ArrayList<>(gloryTokens);
            for (String glory : gloryTokens2) {
                if (tile.getSpaceUnitHolder().getTokenList().contains(glory)) {
                    gloryTokens.remove(glory);
                }
            }
        }
        return gloryTokens;
    }

    public static List<Tile> getGloryTokenTiles(Game game) {
        List<Tile> gloryTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getSpaceUnitHolder().getTokenList().contains("token_ds_glory.png")
                    || tile.getSpaceUnitHolder().getTokenList().contains("token_ds_glory2.png")
                    || tile.getSpaceUnitHolder().getTokenList().contains("token_ds_glory3.png")) {
                gloryTiles.add(tile);
            }
        }
        return gloryTiles;
    }

    public static List<Button> getSardakkAgentButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            if (!player.getPlanetsAllianceMode().contains(planetId)) continue;
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);

            String buttonID = "exhaustAgent_sardakkagent_" + game.getActiveSystem() + "_" + planetId;
            buttons.add(Buttons.green(buttonID, "Use N'orr Agent on " + planetRepresentation, FactionEmojis.Sardakk));
        }
        return buttons;
    }

    public static List<Button> getMercerAgentInitialButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        String msgStart = "Use Field Marshal Mercer, a Nomad"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, on ";
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetID = planet.getName();
            String planetRepresentation = Helper.getPlanetRepresentation(planetID, game);
            if (player.getPlanetsAllianceMode().contains(planetID)) {
                String buttonID = "exhaustAgent_nomadagentmercer_" + game.getActiveSystem() + "_" + planetID;
                buttons.add(Buttons.green(buttonID, msgStart + planetRepresentation, FactionEmojis.Nomad));
            }
        }
        return buttons;
    }

    public static List<Button> getL1Z1XAgentButtons(Game game, Tile tile, Player player) {
        return getInfantryPlanetButtons(
                game, tile, player, "exhaustAgent_l1z1xagent_", "Use L1Z1X Agent on ", FactionEmojis.L1Z1X);
    }

    public static List<Button> getTFAwakenButtons(Game game, Tile tile, Player player) {
        return getInfantryPlanetButtons(game, tile, player, "tfawaken_", "Use Awaken on ", FactionEmojis.Titans);
    }

    private static List<Button> getInfantryPlanetButtons(
            Game game, Tile tile, Player player, String idPrefix, String labelPrefix, FactionEmojis emoji) {
        List<Button> buttons = new ArrayList<>();
        if (tile == null) return buttons;

        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            if (player.getPlanetsAllianceMode().contains(planetId)
                    && FoWHelper.playerHasInfantryOnPlanet(player, tile, planetId)) {
                String buttonID = idPrefix + game.getActiveSystem() + "_" + planetId;
                buttons.add(
                        Buttons.green(buttonID, labelPrefix + Helper.getPlanetRepresentation(planetId, game), emoji));
            }
        }
        return buttons;
    }

    public static List<Button> getYinAgentButtons(Player player, Game game, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Buttons.green(
                player.factionButtonChecker() + placePrefix + "_2ff_" + tp, "Place 2 Fighters", UnitEmojis.fighter);
        buttons.add(ff2Button);
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String pp = planet.getName();
            Button inf2Button = Buttons.green(
                    player.factionButtonChecker() + placePrefix + "_2gf_" + pp,
                    "Place 2 Infantry on " + Helper.getPlanetRepresentation(pp, game),
                    UnitEmojis.infantry);
            buttons.add(inf2Button);
        }
        return buttons;
    }

    @ButtonHandler("step2axisagent_")
    public static void resolveStep2OfAxisAgent(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String unit = "cruiser".equalsIgnoreCase(buttonID.split("_")[1]) ? "cruiser" : "destroyer";
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " Chose to place 1 " + unit + " with their ships from "
                        + agentRef(player, "Shipmonger Zsknck, the Axis") + ".");
        List<Button> buttons =
                new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuild"));
        String message = player.getRepresentationUnfogged()
                + ", please choose the system containing your ships where you wish to place a " + unit + ".";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveArtunoCheck(Player player, int tg) {
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    "exhaustAgent_nomadagentartuno_" + tg, "Exhaust Artuno With " + StringHelper.pluralize(tg, "TG")));
            buttons.add(Buttons.DECLINE);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " you have the opportunity to exhaust "
                            + agentRef(player, "Artuno the Betrayer, a Nomad") + ", and place "
                            + StringHelper.pluralize(tg, "trade good") + " on her.",
                    buttons);
        }
    }

    @ButtonHandler("yinagent_")
    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the faction to give "
                        + agentRef(player, "Brother Milor, the Yin") + " to.",
                buttons);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if (exhaustedMessage.isEmpty()) {
            exhaustedMessage = "Combat";
        }
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    private static List<Button> getJolNarAgentButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    buttons.add(Buttons.green(
                            "jolNarAgentRemoval_" + tile.getPosition() + "_" + unitHolder.getName(),
                            "Remove infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
                }
            }
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    @ButtonHandler("jolNarAgentRemoval_")
    public static void resolveJolNarAgentRemoval(
            Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " removed 1 infantry from "
                        + ButtonHelper.getUnitHolderRep(unitHolder, tile, game) + " using "
                        + agentRef(player, "Doctor Sucaban, the Jol-Nar") + ".");
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + unitHName);
        ButtonHelper.resolveInfantryRemoval(player, 1, tile);
        if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }
}
