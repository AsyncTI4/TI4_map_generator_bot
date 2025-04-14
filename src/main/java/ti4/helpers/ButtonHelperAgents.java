package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.agenda.VoteButtonHandler;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;
import ti4.service.PlanetService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperAgents {

    public static List<Button> getTilesToArboAgent(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(finChecker + "arboAgentIn_" + tileEntry.getKey(),
                    tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Buttons.red(finChecker + "deleteButtons", "Decline");
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
                String msg = cabal.getRepresentationUnfogged() + " you have the ability to use " + (cabal.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + " The Stillness of Stars, the Vuil'raith" + (cabal.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, on "
                    + p2.getFactionEmojiOrColor() + " who has "
                    + p2.getCommoditiesTotal() + " commodities";
                buttons.add(Buttons.green("exhaustAgent_cabalagent_startCabalAgent_" + p2.getFaction(),
                    "Use Vuil'raith Agent"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    @ButtonHandler("startCabalAgent_")
    public static void startCabalAgent(Player cabal, Game game, String buttonID, GenericInteractionCreateEvent event) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = getUnitsForCabalAgent(game, p2);
        String msg = cabal.getRepresentationUnfogged() + " use buttons to capture a ship";
        MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }

    public static List<Button> getUnitsForCabalAgent(Game game, Player p2) {
        List<Button> buttons = new ArrayList<>();
        int maxComms = p2.getCommoditiesTotal();
        String unit2;
        Button unitButton2;
        unit2 = "destroyer";
        if (maxComms > 0 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 8) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.destroyer);
            buttons.add(unitButton2);
        }

        unit2 = "mech";
        if (maxComms > 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 4) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.mech);
            buttons.add(unitButton2);
        }

        unit2 = "cruiser";
        if (maxComms > 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 8) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.cruiser);
            buttons.add(unitButton2);
        }
        unit2 = "carrier";
        if (maxComms > 2 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 4) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.carrier);
            buttons.add(unitButton2);
        }
        unit2 = "dreadnought";
        if (maxComms > 3 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 5) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.dreadnought);
            buttons.add(unitButton2);
        }
        unit2 = "flagship";
        if (maxComms > 7 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 1) {
            unitButton2 = Buttons.red("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2, UnitEmojis.flagship);
            buttons.add(unitButton2);
        }
        return buttons;
    }

    public static List<Button> getUnitsToArboAgent(Player player, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier,
            UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet)
                continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey))
                    continue;

                if (!allowedUnits.contains(unitKey.getUnitType())) {
                    continue;
                }

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName + "damaged";
                    Button validTile2 = Buttons.red(buttonID, "Remove A Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName,
                        "Remove " + x + " " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        Button validTile2 = Buttons.red(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unit) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
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

        List<String> allowedUnits = Stream
            .of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship,
                UnitType.Warsun, UnitType.Fighter)
            .map(UnitType::getValue).toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID = finChecker + "arboAgentPutShip_" + ownedUnit.getBaseType() + "_" + tile.getPosition();
                String buttonText = "Place " + ownedUnit.getName();
                buttons.add(Buttons.red(buttonID, buttonText, ownedUnit.getUnitEmoji()));
            }
        }

        return buttons;
    }

    @ButtonHandler("spendStratNReadyAgent_")
    public static void resolveAbsolHyperAgentReady(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String agent = StringUtils.substringAfterLast(buttonID, "_");
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, TechEmojis.BioticTech + "Hypermetabolism" + SourceEmojis.Absol);
        ButtonHelper.deleteMessage(event);
        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                player.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " exhausted "
                    + "_Hypermetabolism_ and spent a command token from strategy pool to ready " + agent + ".");
            }
            if (agent.contains("absol")) {
                player.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " exhausted "
                    + "_Hypermetabolism_ and spent a command token from strategy pool to ready " + agent + ".");
            }
            return;
        }
        RefreshLeaderService.refreshLeader(player, playerLeader, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " exhausted "
            + "_Hypermetabolism_ and spent a command token from their strategy pool to ready " + agent + ".");
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
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        String message = p2.getRepresentation() + " use buttons to select the planet that you wish to kill all the infantry on. ";
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

        String message = player.getRepresentation() + " chose to destroy all infantry on " + Helper.getPlanetRepresentation(planet, game);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amountToKill = uH.getUnitCount(UnitType.Infantry, player.getColor());
        if (player.hasInf2Tech()) {
            ButtonHelper.resolveInfantryDeath(player, amountToKill);
        }
        message += ". " + amountToKill + " infantry were destroyed. " + Math.min(player.getCommoditiesTotal(), amountToKill) + " comms were gained and then converted to tgs";
        player.setTg(player.getTg() + Math.min(player.getCommoditiesTotal(), amountToKill));
        player.setCommodities(Math.max(0, player.getCommodities() - Math.min(player.getCommoditiesTotal(), amountToKill)));
        RemoveUnitService.removeUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), amountToKill + " inf " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hacanAgentRefresh_")
    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not find player, please resolve manually.");
            return;
        }
        String message;
        if (p2 == player) {
            message = player.getRepresentationUnfogged() + " increased your commodities by two";
            ButtonHelperStats.gainComms(event, game, player, 2, false, true);
        } else {
            message = player.getFactionEmojiOrColor() + " replenished " + p2.getFactionEmojiOrColor() + "'s commodities";
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " your commodities were replenished by " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Carth of Golden Sands, the Hacan" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            ButtonHelperStats.replenishComms(event, game, p2, true);
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mercerMove_")
    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planetDestination = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        String planetRemoval = buttonID.split("_")[3];
        String unit = buttonID.split("_")[4];
        UnitHolder uH = tile.getUnitHolders().get(planetRemoval);
        String message;
        if ("space".equalsIgnoreCase(planetRemoval)) {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from space area of " + tile.getRepresentation() + " to "
                + Helper.getPlanetRepresentation(planetDestination, game);
            planetRemoval = "";
        } else {
            message = player.getFactionEmojiOrColor() + " moved 1 " + unit + " from " + Helper.getPlanetRepresentation(planetRemoval, game)
                + " to " + Helper.getPlanetRepresentation(planetDestination, game);

        }
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), unit + " " + planetRemoval);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planetDestination), game, player.getColor(), unit + " " + planetDestination);
        if ("mech".equalsIgnoreCase(unit)) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
                ButtonHelper.deleteTheOneButton(event);
            }
        } else {
            if ("pds".equalsIgnoreCase(unit)) {
                if (uH.getUnitCount(UnitType.Pds, player.getColor()) < 1) {
                    ButtonHelper.deleteTheOneButton(event);
                }
            } else if (uH.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
                ButtonHelper.deleteTheOneButton(event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    public static void addArgentAgentButtons(Tile tile, Player player, Game game) {
        Set<String> tiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        List<Button> unitButtons = new ArrayList<>();
        for (String pos : tiles) {
            Tile tile2 = game.getTileByPosition(pos);

            for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(unitHolder.getName())) {
                    continue;
                }
                Planet planetReal = (Planet) unitHolder;
                String planet = planetReal.getName();
                if (player.getPlanetsAllianceMode().contains(planet)) {
                    String pp = unitHolder.getName();
                    Button inf1Button = Buttons.green("FFCC_" + player.getFaction() + "_place_infantry_" + pp,
                        "Produce 1 Infantry on " + Helper.getPlanetRepresentation(pp, game), UnitEmojis.infantry);
                    unitButtons.add(inf1Button);
                    Button mfButton = Buttons.green("FFCC_" + player.getFaction() + "_place_mech_" + pp,
                        "Produce Mech on " + Helper.getPlanetRepresentation(pp, game), UnitEmojis.mech);
                    unitButtons.add(mfButton);
                }
            }
        }
        unitButtons.add(Buttons.red("deleteButtons_spitItOut",
            "Done With Argent Agent"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use buttons to place ground forces via " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Trillossa Aun Mirik, the Argent" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.",
            unitButtons);
    }

    @ButtonHandler("vaylerianAgent_")
    public static void resolveVaylerianAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String message = ButtonHelper.resolveACDraw(p2, game, event);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                p2.getFactionEmojiOrColor() + " gained 1 action card from using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Yvin Korduul, the Vaylerian" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("exhaustAgent_")
    public static void exhaustAgent(String buttonID, GenericInteractionCreateEvent event, Game game, Player player) {
        String agent = buttonID.replace("exhaustAgent_", "");
        String rest = agent;
        String trueIdentity = player.getRepresentationUnfogged();
        if (agent.contains("_")) {
            agent = agent.substring(0, agent.indexOf("_"));
        }

        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, player, playerLeader);

        MessageChannel channel = player.getCorrectChannel();
        String message;

        // Clever Clever Ssruu
        String ssruuClever = "";
        String ssruuSlash = "";
        if ("yssarilagent".equalsIgnoreCase(playerLeader.getId())) {
            ssruuClever = "Clever Clever ";
            ssruuSlash = "/Yssaril";
        }

        if ("nomadagentartuno".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Artuno the Betrayer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            int tgCount = Integer.parseInt(rest.split("_")[1]);
            playerLeader.setTgCount(tgCount);
            String messageText = player.getRepresentation() + " placed " + tgCount + " trade good" + (tgCount == 1 ? "" : "s")
                + " on top of " + ssruuClever + "Artuno the Betrayer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, messageText);
        }
        if ("naazagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Garv and Gunn, the Naaz-Rokha" + ssruuSlash + " agents.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        }

        if ("augersagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Clodho, the Ilyxum" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
            int oldTg = p2.getTg();
            p2.setTg(oldTg + 2);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getFactionEmojiOrColor() + " gained 2 trade goods from " + ssruuClever + "Clodho, the Ilyxum" + ssruuSlash + " agent, being used ("
                    + oldTg + "->" + p2.getTg() + ").");
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    p2.getFactionEmojiOrColor() + " gained 2 trade goods due to agent usage.");
            }
            ButtonHelperAbilities.pillageCheck(p2, game);
            resolveArtunoCheck(p2, 2);
        }

        if ("vaylerianagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Yvin Korduul, the Vaylerian" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            if (rest.contains("_")) {
                Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
                message = ButtonHelper.resolveACDraw(p2, game, event);
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        p2.getFactionEmojiOrColor() + " gained 1 action card due to agent usage.");
                }
            } else {
                message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Yvin Korduul, the Vaylerian" + ssruuSlash + " agent.";
                List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "vaylerianAgent", null);
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
        }
        if ("kjalengardagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Merkismathr Asvand, the Kjalengard" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            Player activePlayer = game.getActivePlayer();
            if (activePlayer != null) {
                int oldComms = activePlayer.getCommodities();
                int newComms = oldComms + activePlayer.getNeighbourCount();
                if (newComms > activePlayer.getCommoditiesTotal()) {
                    newComms = activePlayer.getCommoditiesTotal();
                }
                activePlayer.setCommodities(newComms);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmoji()
                        + " exhausted " + ssruuClever + "Merkismathr Asvand, the Kjalengard" + ssruuSlash + " agent, to potentially move a **Glory** token into the system. "
                        + activePlayer.getFactionEmoji() + " commodities went from " + oldComms + " -> "
                        + newComms + ".");
            }
            if (!getGloryTokenTiles(game).isEmpty()) {
                offerMoveGloryOptions(game, player, event);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmoji()
                        + " there were no **Glory** tokens on the game board to move. Go win some battles and earn some, or your ancestors will laugh at ya when "
                        + (ThreadLocalRandom.current().nextInt(20) == 0 ? "(if) " : "") + "you reach Valhalla.");

            }
        }
        if ("cabalagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "The Stillness of Stars, the Vuil'Raith" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            startCabalAgent(player, game, rest.replace("cabalagent_", ""), event);
        }
        if ("jolnaragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Doctor Sucaban, the Jol-Nar" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String msg = player.getRepresentationUnfogged() + " you may use the buttons to remove infantry.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                getJolNarAgentButtons(player, game));
        }

        if ("empyreanagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Acamar, the Empyrean" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = trueIdentity + ", your current command tokens are " + player.getCCRepresentation()
                + ". Use buttons to gain command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        if ("mykomentoriagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Lactarius Indigo, the Myko-Mentori" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            ButtonHelperAbilities.offerOmenDiceButtons2(game, player, "yes");
        }
        if ("gledgeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Durran, the Gledge" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Durran, the Gledge" + ssruuSlash + " agent, for +3 PRODUCTION value.");
        }
        if ("uydaiagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Garstil, the Uydai" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Garstil, the Uydai" + ssruuSlash + " agent, for up to 3 infantry not to count towards production limit.");
        }
        if ("khraskagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Udosh B'rtul, the Khrask" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Udosh B'rtul, the Khrask" + ssruuSlash + " agent, to spend 1 non-home planet's resources as additional influence.");
        }
        if ("rohdhnaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Rond Bri'ay, the Roh'Dhna" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Rond Bri'ay, the Roh'Dhna" + ssruuSlash + " agent, for 1 command token.");
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String trueIdentity2 = p2.getRepresentationUnfogged();
            String message2 = trueIdentity2 + ", your current command tokens are " + p2.getCCRepresentation()
                + ". Use buttons to gain command tokens.";
            game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2,
                buttons);
        }
        if ("veldyragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Solis Morden, the Veldyr" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Solis Morden, the Veldyr" + ssruuSlash + " agent, to pay with one planets influence instead of resources.");
        }
        if ("winnuagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Berekar Berekon, the Winnu" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            player.addSpentThing("Exhausted Winnu Agent, for 2 resources.");
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                buttonEvent.getMessage().editMessage(exhaustedMessage).queue();
                ButtonHelper.deleteTheOneButton(buttonEvent);
            }
            return;

        }
        if ("lizhoagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Vasra Ivo, the Li-Zho" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 2 fighters with your ships.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                buttons);
        }

        if ("nekroagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Nekro Malleon, the Nekro" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Nekro Malleon, the Nekro" + ssruuSlash + " agent.";
            List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "nekroAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("kolleccagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Captain Dust, the Kollecc" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Captain Dust, the Kollecc" + ssruuSlash + " agent.";
            List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "kolleccAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }

        if ("hacanagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Carth of Golden Sands, the Hacan" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Carth of Golden Sands, the Hacan" + ssruuSlash + " agent.";
            List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "hacanAgentRefresh", null);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("pharadnagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Avhkan, The Crow, the Pharadn" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Avhkan, The Crow, the Pharadn" + ssruuSlash + " agent.";
            List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "pharadnAgentSelect", null);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("fogallianceagent".equalsIgnoreCase(agent)) {
            fogAllianceAgentStep1(game, player);
        }

        if ("xxchaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Ggrocuto Rinn, the Xxcha" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("xxchaagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            message = " Use buttons to ready a planet. Removing the infantry from your own planets is not automated but is an option for you to do.";
            List<Button> buttons = new ArrayList<>();
            for (String planet : p2.getExhaustedPlanets()) {
                buttons.add(Buttons.gray("khraskHeroStep4Ready_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged() + message, buttons);
        }

        if ("yinagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Brother Milor, the Yin" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String posNFaction = rest.replace("yinagent_", "");
            String pos = posNFaction.split("_")[0];
            String faction = posNFaction.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                p2.getRepresentationUnfogged() + " Use buttons to resolve " + ssruuClever + "Brother Milor, the Yin" + ssruuSlash + " agent.",
                getYinAgentButtons(p2, game, pos));
        }

        if ("naaluagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Z'eu, the Naalu" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("naaluagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            game.setNaaluAgent(true);
            channel = event.getMessageChannel();
            if (game.isFowMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            message = "Doing a tactical action. Please select the ring of the map that the system you wish to activate is located in.";
            if (!game.isFowMode()) {
                message += " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
            }
            List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, game);
            game.resetCurrentMovedUnitsFrom1TacticalAction();
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentationUnfogged()
                + " Use buttons to resolve tactical action from " + ssruuClever + "Z'eu, the Naalu" + ssruuSlash + " agent. Reminder that you cannot do a tactical action in a home system this way.\n"
                + message, ringButtons);
        }

        if ("olradinagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Baggil Wildpaw, the Olradin" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            resolveOlradinAgentStep2(game, p2);
        }
        if ("solagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Evelyn Delouis, the Sol" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), p2.getFactionEmojiOrColor() + " will receive " + ssruuClever + "Evelyn Delouis, the Sol" + ssruuSlash + " agent, on their next roll.");
            game.setCurrentReacts("solagent", p2.getFaction());
        }
        if ("letnevagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Viscount Unlenn, the Letnev" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), p2.getFactionEmojiOrColor() + " will receive " + ssruuClever + "Viscount Unlenn, the Letnev" + ssruuSlash + " agent, on their next roll.");
            game.setCurrentReacts("letnevagent", p2.getFaction());
        }

        if ("cymiaeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;

            String successMessage2 = p2.getFactionEmoji() + " drew 1 action card due to " + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent";
            if (p2.hasAbility("scheming")) {
                game.drawActionCard(p2.getUserID());
                successMessage2 += ", then drew another action card for **Scheming**. Please now discard 1 action card";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard an action card.",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
            }
            successMessage2 += ". ";
            if (p2.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, p2, 1);
                successMessage2 += p2.getFactionEmoji() + " triggered **Autonetic Memory Option**.";
            } else {
                game.drawActionCard(p2.getUserID());
            }
            ButtonHelper.checkACLimit(game, p2);
            String headerText2 = p2.getRepresentationUnfogged() + " you drew 1 action card due to "
                + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent";
            if (p2.hasAbility("scheming")) {
                headerText2 += ", then drew another action card for **Scheming**. Please now discard 1 action card";
            }
            headerText2 += ". ";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, headerText2);
            ActionCardHelper.sendActionCardInfo(game, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentationUnfogged() + " use buttons to discard an action card.",
                    ActionCardHelper.getDiscardActionCardButtons(p2, false));
            }
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), successMessage2);
        }

        if ("mentakagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("mentakagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            String successMessage = player.getFactionEmoji() + " drew 1 action card";
            String successMessage2 = p2.getFactionEmoji() + " drew 1 action card";
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                successMessage += ", then drew another action card for **Scheming**. Please now discard 1 action card";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard an action card.",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
            }
            successMessage += ". ";
            if (p2.hasAbility("scheming")) {
                game.drawActionCard(p2.getUserID());
                successMessage2 += ", then drew another action card for **Scheming**. Please now discard 1 action card";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentationUnfogged() + " use buttons to discard an action card.",
                    ActionCardHelper.getDiscardActionCardButtons(p2, false));
            }
            successMessage2 += ". ";
            if (player.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            } else {
                game.drawActionCard(player.getUserID());
            }

            if (p2.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, p2, 1);
            } else {
                game.drawActionCard(p2.getUserID());
            }

            ButtonHelper.checkACLimit(game, player);
            ButtonHelper.checkACLimit(game, p2);
            String headerText = player.getRepresentationUnfogged() + " you got 1 action card from "
                + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent";
            headerText += player.hasAbility("scheming") ? ", then drew another action card for **Scheming**. Please now discard 1 action card." : ".";
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
            ActionCardHelper.sendActionCardInfo(game, player);
            String headerText2 = p2.getRepresentationUnfogged() + " you got 1 action card from "
                + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent";
            headerText2 += p2.hasAbility("scheming") ? ", then drew another action card for **Scheming**. Please now discard 1 action card." : ".";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, headerText2);
            ActionCardHelper.sendActionCardInfo(game, p2);
            if (player.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
            }
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentationUnfogged() + " use buttons to discard",
                    ActionCardHelper.getDiscardActionCardButtons(p2, false));
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), successMessage2);
        }

        if ("sardakkagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "T'ro An, the N'orr" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String posNPlanet = rest.replace("sardakkagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), "2 gf " + planetName);
            String successMessage = player.getFactionEmoji() + " placed " + UnitEmojis.infantry + UnitEmojis.infantry + " on "
                + Helper.getPlanetRepresentation(planetName, game) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        }
        if ("argentagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Trillossa Aun Mirik, the Argent" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String pos = rest.replace("argentagent_", "");
            Tile tile = game.getTileByPosition(pos);
            addArgentAgentButtons(tile, player, game);
        }
        if ("nomadagentmercer".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Field Marshal Mercer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String posNPlanet = rest.replace("nomadagentmercer_", "");
            String planetName = posNPlanet.split("_")[1];
            List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(game, planetName,
                player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " use buttons to resolve move of ground forces to this planet with " + ssruuClever + "Field Marshal Mercer, a Nomad" + ssruuSlash + " agent.",
                buttons);
        }
        if ("l1z1xagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "I48S, the L1Z1X" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String posNPlanet = rest.replace("l1z1xagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            RemoveUnitService.removeUnits(event, game.getTileByPosition(pos), game, player.getColor(), "1 infantry " + planetName);
            AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), "1 mech " + planetName);
            String successMessage = player.getFactionEmoji() + " replaced 1 " + UnitEmojis.infantry + " on "
                + Helper.getPlanetRepresentation(planetName, game) + " with 1 mech.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        }

        if ("muaatagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Umbat, the Muaat" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("muaatagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            channel = p2.getCorrectChannel();
            message = "Use buttons to select which tile to " + ssruuClever + "Umbat, the Muaat" + ssruuSlash + " agent, in";
            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, UnitType.Warsun,
                UnitType.Flagship);
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : tiles) {
                Button starTile = Buttons.green("umbatTile_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, p2));
                buttons.add(starTile);
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentationUnfogged() + message, buttons);
        }
        if ("bentoragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "C.O.O. Mgur, the Bentor" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveBentorAgentStep2(player, game, event, rest);
        }
        if ("kortaliagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Queen Lucreia, the Kortali" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveKortaliAgentStep2(player, game, rest);
        }
        if ("mortheusagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Walik, the Mortheus" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = new ArrayList<>(ButtonHelper.getDomnaStepOneTiles(p2, game));
            message = p2.getRepresentationUnfogged()
                + " use buttons to select which system the ship you just produced is in.. \n\n You need to tell the bot which system the unit was produced in first, after which it will give tiles to move it to. ";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message,
                buttons);

        }
        if ("cheiranagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Operator Kkavras, the Cheiran" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveCheiranAgentStep1(player, game, rest);
        }
        if ("freesystemsagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Cordo Haved, the Free Systems" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveFreeSystemsAgentStep1(player, game, rest);
        }
        if ("florzenagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Sal Gavda, the Florzen" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveFlorzenAgentStep1(player, game, rest);
        }
        if ("dihmohnagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Jgin Faru, the Dih-Mohn" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String planet = rest.split("_")[1];
            AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 inf " + planet);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " landed 1 extra infantry on "
                    + Helper.getPlanetRepresentation(planet, game) + " using " + ssruuClever + "Jgin Faru, the Dih-Mohn" + ssruuSlash + " agent [Note, you need to commit something else to the planet besides this extra infantry in order to use this agent].");
        }
        if ("tnelisagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Davish S’Norri, the Tnelis" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            game.setStoredValue("tnelisAgentFaction", player.getFaction());
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + game.getActiveSystem() + "_space_bombardment");
            game.setStoredValue("tnelisAgentFaction", "");
        }
        if ("vadenagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Yudri Sukhov, the Vaden" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveVadenAgentStep2(player, game, event, rest);
        }
        if ("celdauriagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "George Nobin, the Celdauri" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveCeldauriAgentStep2(player, game, event, rest);
        }
        if ("zealotsagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Priestess Tuh, the Rhodun" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveZealotsAgentStep2(player, game, rest);
        }
        if ("nokaragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Sal Sparrow, the Nokar" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveNokarAgentStep2(player, game, event, rest);
        }
        if ("zelianagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Zelian A, the Zelian" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            resolveZelianAgentStep2(player, game, event, rest);
        }
        if ("mirvedaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Logic Machina, the Mirveda" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Target does not have any command tokens in their strategy pool, and so nothing has happend.");
                return;
            }
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.GET_A_TECH);
            buttons.add(Buttons.red("deleteButtons", "Delete This"));
            p2.setStrategicCC(p2.getStrategicCC() - 1);
            channel = p2.getCorrectChannel();
            ButtonHelperCommanders.resolveMuaatCommanderCheck(p2, game, event, FactionEmojis.mirveda + " Agent");
            String message0 = p2.getRepresentationUnfogged()
                + ", 1 command token has been removed from your strategy pool due to use of " + ssruuClever + "Logic Machina, the Mirveda"
                + ssruuSlash + " agent. You may add it back if you didn't agree to the agent.";
            message = p2.getRepresentationUnfogged()
                + " Use buttons to get a technology of a color which matches one of the prerequisites on the unit upgrade you just gained.";
            MessageHelper.sendMessageToChannel(channel, message0);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("ghotiagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Becece, the Ghoti" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("ghotiATG", "Get 1 Trade Good"));
            buttons.add(Buttons.gray("ghotiAProd", "Produce 2 Additional Units"));
            buttons.add(Buttons.red("deleteButtons", "Delete This"));
            channel = p2.getCorrectChannel();
            message = p2.getRepresentationUnfogged()
                + " Use buttons to decide how to use the agent.";
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("arborecagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Letani Ospha, the Arborec" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("arborecagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            channel = p2.getCorrectChannel();
            message = "Use buttons to select which tile to use " + ssruuClever + "Letani Ospha, the Arborec" + ssruuSlash + " agent, in";
            List<Button> buttons = getTilesToArboAgent(p2, game);
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentationUnfogged() + message, buttons);
        }
        if ("kolumeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Disciple Fran, the Kolume" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("kolumeagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            channel = p2.getCorrectChannel();
            List<Button> redistributeButton = new ArrayList<>();
            Button deleButton = Buttons.red("FFCC_" + player.getFaction() + "_" + "deleteButtons",
                "Delete These Buttons");
            redistributeButton.add(Buttons.REDISTRIBUTE_CCs);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(channel,
                p2.getRepresentationUnfogged()
                    + " use buttons to redistribute 1 command token (the bot allows more but " + ssruuClever + "Disciple Fran, the Kolume"
                    + ssruuSlash + " agent, is restricted to redistributing 1).",
                redistributeButton);
        }
        if ("axisagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Shipmonger Zsknck, the Axis" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel, exhaustText);
            String faction = rest.replace("axisagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            channel = p2.getCorrectChannel();
            message = p2.getRepresentationUnfogged() + ", please choose whether you wish to place 1 cruiser or 1 destroyer in a system with your ships.";
            List<Button> buttons = new ArrayList<>();
            if (p2 != player) {
                ButtonHelperStats.gainComms(event, game, player, 2, false, true);
            }
            buttons.add(Buttons.green("step2axisagent_cruiser", "Place 1 cruiser"));
            buttons.add(Buttons.green("step2axisagent_destroyer", "Place 1 destroyer"));
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            String exhaustedMessage = buttonEvent.getMessage().getContentRaw();
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Updated";
            }
            int buttons = 0;
            List<ActionRow> actionRow2 = new ArrayList<>();

            for (ActionRow row : buttonEvent.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(buttonEvent.getButton());
                if (buttonIndex > -1 && !"nomadagentmercer".equalsIgnoreCase(agent)) {
                    buttonRow.remove(buttonIndex);
                }
                if (!buttonRow.isEmpty()) {
                    buttons += buttonRow.size();
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if (!actionRow2.isEmpty() && !exhaustedMessage.contains("select the user of the agent")
                && !exhaustedMessage.contains("choose the target of your agent")) {
                if (exhaustedMessage.contains("buttons to do an end of turn ability") && buttons == 1) {
                    buttonEvent.getMessage().delete().queue();
                } else {
                    buttonEvent.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
                }

            } else {
                buttonEvent.getMessage().delete().queue();
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Buttons.green("exhaustTCS_" + agent + "_" + player.getFaction(), "Exhaust Temporal Command Suite to Ready " + agent));
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                String msg = p2.getRepresentationUnfogged()
                    + " you have the opportunity to exhaust _ Temporal Command Suite_ to ready " + agent
                    + " and potentially resolve a transaction.";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons2);
            }
        }
    }

    @ButtonHandler("presetEdynAgentStep1")
    public static void presetEdynAgentStep1(Game game, Player player) {
        List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep2", null);
        String msg = player.getRepresentationUnfogged()
            + ", please select the player who you wish to take the action when the time comes (probably yourself).";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("presetEdynAgentStep2_")
    public static void presetEdynAgentStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        List<Button> buttons = VoteButtonHandler.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep3_" + faction,
            null);
        String msg = player.getRepresentationUnfogged()
            + " select the passing player who will set off the trigger. When this player passes, the player you selected in the last step will get an action.";
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
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation() + " you may use this button to undo the preset. Ignore it otherwise.",
            buttons);
    }

    public static boolean checkForEdynAgentPreset(Game game, Player passedPlayer, Player upNextPlayer, GenericInteractionCreateEvent event) {
        Player edyn = Helper.getPlayerFromUnlockedLeader(game, "edynagent");
        if (edyn != null && edyn.hasUnexhaustedLeader("edynagent")) {
            String preset = game.getStoredValue("edynAgentPreset");
            if (!preset.isEmpty()) {
                if (preset.split("_")[1].equalsIgnoreCase(passedPlayer.getFaction())) {
                    Player edyn2 = game.getPlayerFromColorOrFaction(preset.split("_")[2]);
                    Player newActivePlayer = game.getPlayerFromColorOrFaction(preset.split("_")[0]);
                    exhaustAgent("exhaustAgent_edynagent", event, game, edyn2);
                    game.setStoredValue("edynAgentPreset", "");
                    game.setStoredValue("edynAgentInAction", newActivePlayer.getFaction() + "_" + edyn2.getFaction() + "_" + upNextPlayer.getFaction());
                    List<Button> buttons = StartTurnService.getStartOfTurnButtons(newActivePlayer, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(newActivePlayer.getCorrectChannel(),
                        newActivePlayer.getRepresentationUnfogged()
                            + " you may take 1 action now due to " + (edyn.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Allant, the Edyn" + (edyn.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.",
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
            if (edyn2 == null)
                return false;
            AgendaHelper.drawAgenda(1, false, game, edyn2, true);
            Player newActivePlayer = game.getPlayerFromColorOrFaction(preset.split("_")[2]);
            game.setStoredValue("edynAgentInAction", "");
            if (newActivePlayer != null) {
                ButtonHelper.startMyTurn(event, game, newActivePlayer);
            }
            return true;
        }
        return false;

    }

    public static boolean doesTileHaveAStructureInIt(Player player, Tile tile) {
        boolean present = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0
                || uH.getUnitCount(UnitType.Pds, player.getColor()) > 0) {
                return true;
            }
            if (player.hasAbility("byssus") && uH instanceof Planet
                && uH.getUnitCount(UnitType.Mech, player.getColor()) > 0) {
                return true;
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
                ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player,
                    cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String cardID = buttonID.split("_")[3];
                String planetName = buttonID.split("_")[4];
                Tile tile = game.getTileFromPlanet(planetName);
                String messageText = player.getRepresentation() + " explored the planet " + ExploreEmojis.getTraitEmoji(drawColor)
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                    + tile.getPosition() + ":";
                ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && game.getPhaseOfGame().contains("agenda")) {
                    PlanetService.refreshPlanet(player, planetName);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Planet has been readied because of Quaxdol Junitas, the Florzen Commander.");
                    AgendaHelper.listVoteCount(game, game.getMainGameChannel());
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                    UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                    game.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
                }
                if (player.hasTech("dslaner")) {
                    player.setAtsCount(player.getAtsCount() + 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
                }
            }
        } else {
            exhaustAgent("exhaustAgent_lanefiragent", event, game, player);
            if (buttonID.contains("frontier")) {
                String cardChosen = game.drawExplore(Constants.FRONTIER);
                String pos = buttonID.split("_")[3];
                ExploreModel card = Mapper.getExplore(cardChosen);
                String name = card.getName();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Found a " + name + " in " + game.getTileByPosition(pos).getRepresentation());
                ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player,
                    cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String planetName = buttonID.split("_")[3];
                Tile tile = game.getTileFromPlanet(planetName);
                String cardID = game.drawExplore(drawColor);
                String messageText = player.getRepresentation() + " explored " + ExploreEmojis.getTraitEmoji(drawColor) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile "
                    + tile.getPosition() + ")*:";
                ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && game.getPhaseOfGame().contains("agenda")) {
                    PlanetService.refreshPlanet(player, planetName);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Planet has been readied because of Quaxdol Junitas, the Florzen Commander.");
                    AgendaHelper.listVoteCount(game, game.getMainGameChannel());
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                    UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                    game.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
                }
                if (player.hasTech("dslaner")) {
                    player.setAtsCount(player.getAtsCount() + 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThemAndNoCC(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
        for (String posTile : adjTiles) {
            Tile adjTile = game.getTileByPosition(posTile);
            if (adjTile != null && doesTileHaveAStructureInIt(player, adjTile) && !CommandCounterHelper.hasCC(player, adjTile)) {
                tiles.add(adjTile);
            }
        }
        return tiles;
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThem(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
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

    public static List<String> getAllControlledPlanetsInThisSystemAndAdjacent(Game game, Player player, Tile tile) {
        List<String> legendaries = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>(
            FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
        adjTiles.add(tile.getPosition());
        for (String adjTilePos : adjTiles) {
            Tile adjTile = game.getTileByPosition(adjTilePos);
            if (adjTile.isHomeSystem()) {
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
                if (model.getLegendaryAbilityText() != null && !model.getLegendaryAbilityText().isEmpty()) {
                    legendaries.add(planet);
                }
            }
        }
        return legendaries;
    }

    public static void resolveCheiranAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getCheiranAgentTiles(player, game)) {
            buttons.add(Buttons.green("cheiranAgentStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        String msg = player.getRepresentationUnfogged() + ", choose the tile you wish to remove your command token from.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveFreeSystemsAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : getAvailableLegendaryAbilities(game)) {
            buttons.add(Buttons.green("freeSystemsAgentStep2_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged() + " choose the legendary planet ability that you wish to use.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("refreshWithOlradinAgent_")
    public static void resolveRefreshWithOlradinAgent(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        PlanetService.refreshPlanet(player, planetName);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                + " with " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Baggil Wildpaw, the Olradin" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        event.getMessage().delete().queue();

    }

    public static void resolveOlradinAgentStep2(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.green("refreshWithOlradinAgent_" + planet,
                "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged()
                + " use buttons to ready a planet of a DIFFERENT trait from the one you just exhausted",
            buttons);
    }

    public static void resolveFlorzenAgentStep1(Player cheiran, Game game, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String attachment : getAttachments(game, player)) {
            String planet = attachment.split("_")[0];
            String attach = attachment.split("_")[1];
            buttons.add(Buttons.green("florzenAgentStep2_" + attachment,
                attach + " on " + Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentationUnfogged() + " choose the attachment you wish to use";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("florzenAgentStep2_")
    public static void resolveFlorzenAgentStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String attachment = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet2 : getAllControlledPlanetsInThisSystemAndAdjacent(game, player,
            game.getTileFromPlanet(planet))) {
            buttons.add(Buttons.green("florzenAgentStep3_" + planet + "_" + planet2 + "_" + attachment,
                Helper.getPlanetRepresentation(planet2, game)));
        }
        String msg = player.getRepresentationUnfogged() + " choose the adjacent planet you wish to put the attachment on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("florzenAgentStep3_")
    public static void resolveFlorzenAgentStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        String attachment = buttonID.split("_")[3];
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
    public static void resolveFreeSystemsAgentStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        PlanetExhaustAbility.doAction(event, player, planet, game, false);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationNoPing() + " chose to replicate the " + Helper.getPlanetRepresentation(planet, game)
                + " legendary ability by exhausting " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Cordo Haved, the Free Systems" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cheiranAgentStep2_")
    public static void resolveCheiranAgentStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        RemoveCommandCounterService.fromTile(event, player.getColor(), origTile, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " removed 1 command token from "
                + origTile.getRepresentationForButtons(game, player) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Operator Kkavras, the Cheiran"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile)) {
            buttons.add(Buttons.green("cheiranAgentStep3_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
            if (getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile).size() == 1) {
                resolveCheiranAgentStep3(player, game, event, "cheiranAgentStep3_" + tile.getPosition());
                return;
            }
        }
        String msg = player.getRepresentationUnfogged() + " choose the adjacent tile that you wish to place your command token in.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cheiranAgentStep3_")
    public static void resolveCheiranAgentStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        CommandCounterHelper.addCC(event, player, origTile, true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 command token in "
                + origTile.getRepresentationForButtons(game, player) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Operator Kkavras, the Cheiran"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Tile> getCheiranAgentTiles(Player player, Game game) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (CommandCounterHelper.hasCC(player, tile)) {
                if (!getAdjacentTilesWithStructuresInThemAndNoCC(player, game, tile).isEmpty()) {
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
        String msg = player.getRepresentation() + " Used " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Becece, the Ghoti"
            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to gain 1 trade good (" + cTG + "->" + fTG + "). ";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ghotiAProd")
    public static void ghotiAgentForProduction(Game game, ButtonInteractionEvent event, Player player) {
        String msg = "Used " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Becece, the Ghoti"
            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to gain the ability to produce 2 more units. ";
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
            ButtonHelperHeroes.resolveArboHeroBuild(game, player, event, "arboHeroBuild_" + tilePos);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveVadenAgentStep2(Player vaden, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }

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
            + " influence, so they gained " + commGain + " commodit" + (commGain == 1 ? "y" : "ies") + " (" + initComm + "->"
            + player.getCommodities() + ") due to " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Yudri Sukhov, the Vaden" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && vaden != player) {
            msg = player.getFactionEmojiOrColor() + " has finished resolving";
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    public static void resolveKortaliAgentStep2(Player bentor, Game game, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        int size = player.getFragments().size();
        int rand = ThreadLocalRandom.current().nextInt(size);
        String frag = player.getFragments().get(rand);
        player.removeFragment(frag);
        bentor.addFragment(frag);
        ExploreModel cardInfo = Mapper.getExplore(frag);
        String msg = player.getFactionEmojiOrColor() + " lost a " + cardInfo.getName() + " to "
            + bentor.getFactionEmojiOrColor() + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Queen Lucreia, the Kortali" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void resolveZealotsAgentStep2(Player zealots, Game game, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(zealots.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();

        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Tile tile = game.getTileFromPlanet(p.getName());
            if (tile != null && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game) && ButtonHelper.checkForTechSkips(game, planet)
                || tile.isHomeSystem()) {
                buttons.add(Buttons.green("produceOneUnitInTile_" + tile.getPosition() + "_ZealotsAgent",
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        String msg = player.getFactionEmojiOrColor()
            + " may produce a unit in their home system or in a system with a technology specialty planet due to " + (zealots.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Priestess Tuh, the Rhodun" + (zealots.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (game.isFowMode() && zealots != player) {
            MessageHelper.sendMessageToChannel(zealots.getCorrectChannel(), msg);
        }
    }

    public static void resolveNokarAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not find the active system");
            return;
        }
        if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Player did not have a ship in the active system, no destroyer placed");
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 destroyer");
        String msg = player.getFactionEmojiOrColor() + " place 1 destroyer in "
            + tile.getRepresentationForButtons(game, player)
            + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Sal Sparrow, the Nokar" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent. "
            + "A transaction may be done with transaction buttons.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void resolveZelianAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not find the active system");
            return;
        }
        if (tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Player did not have any infantry in the space area of the active system, no mech placed");
            return;
        }
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 inf");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech");
        String msg = player.getFactionEmojiOrColor() + " replace 1 infantry with 1 mech in "
            + tile.getRepresentationForButtons(game, player)
            + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Zelian A, the Zelian" + (bentor.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }

        if (event instanceof ButtonInteractionEvent event2) {
            if (event2.getButton().getLabel().contains("Yourself")) {
                List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event2);
                event2.getMessage().editMessage(event2.getMessage().getContentRaw())
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            }
        }

    }

    public static void resolveKyroAgentStep2(Player kyro, Game game, ButtonInteractionEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(kyro.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        String msg = player.getFactionEmojiOrColor() + " replenished commodities due to " + (kyro.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Tox, the Kyro" + (kyro.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        player.setCommodities(player.getCommoditiesTotal());
        ButtonHelper.resolveMinisterOfCommerceCheck(game, player, event);
        cabalAgentInitiation(game, player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && kyro != player) {
            MessageHelper.sendMessageToChannel(kyro.getCorrectChannel(), msg);
        }

        int infAmount = player.getCommoditiesTotal() - 1;
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, infAmount + "gf", "placeOneNDone_skipbuild"));
        String message = kyro.getRepresentationUnfogged() + "Use buttons to drop " + infAmount + " infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(kyro.getCorrectChannel(), message, buttons);
    }

    public static void resolveCeldauriAgentStep2(Player celdauri, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(celdauri.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        if (player.getCommodities() < 2 && player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(celdauri.getCorrectChannel(), "Player did not have the money, please resolve manually.");
            return;
        }
        if (game.getActivePlayer() != player) {
            MessageHelper.sendMessageToChannel(celdauri.getCorrectChannel(), "Target player is not active player, please resolve manually.");
            return;
        }
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        if (tileAS == null) {
            MessageHelper.sendMessageToChannel(celdauri.getCorrectChannel(), "Active system is null, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String option = "";
        for (UnitHolder planet : tileAS.getPlanetUnitHolders()) {
            if (player.getPlanetsAllianceMode().contains(planet.getName())) {
                buttons.add(Buttons.green("celdauriAgentStep3_" + planet.getName(), "Place 1 space dock on " + Helper.getPlanetRepresentation(planet.getName(), game)));
                option = "celdauriAgentStep3_" + planet.getName();
            }
        }
        if (buttons.size() == 1) {
            resolveCeldauriAgentStep3(player, game, event, option);
            return;
        }

        String msg = player.getRepresentationUnfogged() + " choose the planet you wish to place 1 space dock on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }

    }

    @ButtonHandler("celdauriAgentStep3_")
    public static void resolveCeldauriAgentStep3(Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " put 1 space dock on "
            + Helper.getPlanetRepresentation(planet, game) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "George Nobin, the Celdauri" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 sd " + planet);
        if (player.getCommodities() > 1) {
            player.setCommodities(player.getCommodities() - 2);
            msg += "\n" + player.getFactionEmoji() + " Paid 2 commodities";
        } else {
            msg += "\n" + player.getFactionEmoji() + " Paid 2 trade goods " + player.gainTG(-2) + ".";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
        AgendaHelper.ministerOfIndustryCheck(player, game, game.getTileFromPlanet(planet), event);
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }

    public static void resolveBentorAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }

        int numOfBPs = bentor.getNumberOfBluePrints();
        int oldTg = player.getTg();
        int tgGain = numOfBPs + player.getCommodities() - player.getCommoditiesTotal();
        if (tgGain < 0) tgGain = 0;
        int commGain = numOfBPs - tgGain;

        ButtonHelperStats.gainComms(event, game, player, commGain, false, true);
        ButtonHelperStats.gainTGs(event, game, player, tgGain, true);

        String msg = player.getFactionEmojiOrColor() + " gained " + tgGain + " trade good" + (tgGain == 1 ? "" : "s") + " (" + oldTg + "->"
            + player.getTg() + ") and " + commGain + " commodit" + (commGain == 1 ? "y" : "ies") + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "C.O.O. Mgur, the Bentor" + (bentor.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            msg = player.getRepresentation() + " has finished resolving.";
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void fogAllianceAgentStep1(Game game, Player player) {
        String msg = player.getRepresentationUnfogged()
            + ", please choose the system that you wish to move ships from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !CommandCounterHelper.hasCC(player, tile)) {
                buttons.add(Buttons.green("fogAllianceAgentStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
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
                    buttons.add(Buttons.green("fogAllianceAgentStep3_" + tile.getPosition() + "_" + ogTile,
                        tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static boolean nextToOrInHostileHome(Game game, Player player, Tile tile) {
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
    public static void offerMoveGloryOptions(Game game, Player player, GenericInteractionCreateEvent event) {
        String msg = player.getRepresentationUnfogged() + " use buttons to select system to move a **Glory** token from.";
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getGloryTokenTiles(game)) {
            buttons.add(Buttons.green("moveGlory_" + tile.getPosition() + "_" + tileAS.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("moveGlory_")
    public static void moveGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tileAS = game.getTileByPosition(buttonID.split("_")[2]);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        UnitHolder spaceAS = tileAS.getUnitHolders().get(Constants.SPACE);
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
        String msg = player.getFactionEmoji() + " moved a **Glory** token from " + tile.getRepresentation() + " to " + tileAS.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeGlory_")
    public static void placeGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        List<String> gloryTokens = getGloryTokensLeft(game);
        if (gloryTokens.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " cannot place more **Glory** tokens, you've hit the limit.");
            return;
        }
        space.addToken(gloryTokens.getFirst());

        String msg = player.getFactionEmoji() + " added a **Glory** token to " + tile.getRepresentation();
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
                if (tile.getUnitHolders().get("space").getTokenList().contains(glory)) {
                    gloryTokens.remove(glory);
                }
            }
        }
        return gloryTokens;
    }

    public static List<Tile> getGloryTokenTiles(Game game) {
        List<Tile> gloryTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory.png")
                || tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory2.png")
                || tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory3.png")) {
                gloryTiles.add(tile);
            }
        }
        return gloryTiles;
    }

    public static List<Button> getSardakkAgentButtons(Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);

            String buttonID = "exhaustAgent_sardakkagent_" + game.getActiveSystem() + "_" + planetId;
            buttons.add(Buttons.green(buttonID, "Use N'orr Agent on " + planetRepresentation, FactionEmojis.Sardakk));
        }
        return buttons;
    }

    public static List<Button> getMercerAgentInitialButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        String msgStart = "Use Field Marshal Mercer, a Nomad" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, on ";
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

    public static List<Button> getL1Z1XAgentButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            if (player.getPlanetsAllianceMode().contains(planetId) && FoWHelper.playerHasInfantryOnPlanet(player, tile, planetId)) {
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);
                String buttonID = "exhaustAgent_l1z1xagent_" + game.getActiveSystem() + "_" + planetId;
                buttons.add(Buttons.green(buttonID, "Use L1Z1X Agent on " + planetRepresentation, FactionEmojis.L1Z1X));
            }
        }
        return buttons;
    }

    @ButtonHandler("arboAgentPutShip_")
    public static void arboAgentPutShip(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlace = buttonID.replace("arboAgentPutShip_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String successMessage = player.getFactionEmojiOrColor() + " Replaced a ship with 1 ";
        switch (unit) {
            case "destroyer" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "destroyer");
                successMessage += UnitEmojis.destroyer;

            }
            case "cruiser" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "cruiser");
                successMessage += UnitEmojis.cruiser;

            }
            case "carrier" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "carrier");
                successMessage += UnitEmojis.carrier;

            }
            case "dreadnought" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "dreadnought");
                successMessage += UnitEmojis.dreadnought;

            }
            case "fighter" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "fighter");
                successMessage += UnitEmojis.fighter;

            }
            case "warsun" -> {
                AddUnitService.addUnits(event, tile, game, player.getColor(), "warsun");
                successMessage += UnitEmojis.warsun;

            }
        }
        successMessage += " in tile " + tile.getRepresentationForButtons(game, player);

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getYinAgentButtons(Player player, Game game, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp, "Place 2 Fighters", UnitEmojis.fighter);
        buttons.add(ff2Button);
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String pp = planet.getName();
            Button inf2Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp, "Place 2 Infantry on " + Helper.getPlanetRepresentation(pp, game), UnitEmojis.infantry);
            buttons.add(inf2Button);
        }
        return buttons;
    }

    @ButtonHandler("step2axisagent_")
    public static void resolveStep2OfAxisAgent(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String message;
        if ("cruiser".equalsIgnoreCase(buttonID.split("_")[1])) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " Chose to place 1 cruiser with their ships from " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Shipmonger Zsknck, the Axis" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            buttons.addAll(
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 cruiser with your ships";
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " Chose to place 1 destroyer with their ships from " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Shipmonger Zsknck, the Axis" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            buttons.addAll(
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "destroyer", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 destroyer with your ships";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveArtunoCheck(Player player, int tg) {
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("exhaustAgent_nomadagentartuno_" + tg,
                "Exhaust Artuno With " + tg + " TG" + (tg == 1 ? "" : "s")));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " you have the opportunity to exhaust " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Artuno the Betrayer, a Nomad" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                    + " agent, and place " + tg + " trade good" + (tg == 1 ? "" : "s") + " on her.",
                buttons);
        }
    }

    @ButtonHandler("yinagent_")
    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " Use buttons to select faction to give " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Brother Milor, the Yin" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to.",
            buttons);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if (exhaustedMessage.isEmpty()) {
            exhaustedMessage = "Combat";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
    }

    public static List<Button> getJolNarAgentButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    buttons
                        .add(Buttons.green("jolNarAgentRemoval_" + tile.getPosition() + "_" + unitHolder.getName(),
                            "Remove infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler("jolNarAgentRemoval_")
    public static void resolveJolNarAgentRemoval(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFactionEmoji() + " removed 1 infantry from "
                + ButtonHelper.getUnitHolderRep(unitHolder, tile, game) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Doctor Sucaban, the Jol-Nar" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + unitHName);
        if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

}
