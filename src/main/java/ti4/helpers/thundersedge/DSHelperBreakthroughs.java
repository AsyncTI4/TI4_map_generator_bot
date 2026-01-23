package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.UnitModel;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class DSHelperBreakthroughs {
    // @ButtonHandler("componentActionRes_")

    public static void dihmohnBTExhaust(Game game, Player p1) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        AddTokenCommand.addToken(null, tile, Constants.FRONTIER, game);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(), "Added a frontier token to " + tile.getRepresentationForButtons());
    }

    public static void cheiranBTExhaust(Game game, Player p1) {
        String message = p1.getRepresentation() + ", please choose which system the ship you wish to replace is in.";
        String finChecker = "FFCC_" + p1.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(p1, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(
                        finChecker + "cheiranBTIn_" + tileEntry.getKey(), tile.getRepresentationForButtons(game, p1));
                buttons.add(validTile);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("useVaylerianBT_")
    public static void useVaylerianBT(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is discarding an action card to move 1 ship (possibly transporting) to an adjacent system containing no other players' ships.");
        ButtonHelperAgents.moveShipToAdjacentSystemStep2(game, player, event, buttonID + "_hero");
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, "Discard an Action Card", ActionCardHelper.getDiscardActionCardButtons(player, false));
    }

    @ButtonHandler("cheiranBTIn_")
    public static void cheiranBTIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        Tile tile = game.getTileByPosition(pos);
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

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
                    String buttonID2 = finChecker + "cheiranBTOn_" + tile.getPosition() + "_" + unitName + "damaged";
                    Button validTile2 = Buttons.red(buttonID2, "Remove A Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(
                            finChecker + "cheiranBTOn_" + tile.getPosition() + "_" + unitName,
                            "Remove " + x + " " + prettyName,
                            unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to replace.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cheiranBTOn_")
    public static void cheiranBTOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
        String msg = "A " + (damaged ? "damaged " : "") + unitKey.unitEmoji() + " was removed by "
                + player.getFactionEmoji()
                + ". Units costing up to its cost can now be placed in the space area.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "solBtBuild", "place");
        String message = player.getRepresentation() + ", use these buttons to place units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lizhoBtStep1")
    public static void lizhoBtStep1(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        String planetName = buttonID.split("_")[1];
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                if (uH.getName().equalsIgnoreCase(planetName)) {
                    continue;
                }
                if (uH.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    if (uH instanceof Planet) {
                        buttons.add(Buttons.green(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                        + "_infantry_lizhobt",
                                "Move Infantry From " + Helper.getPlanetRepresentation(uH.getName(), game) + " to "
                                        + Helper.getPlanetRepresentation(planetName, game)));
                    } else {

                        buttons.add(Buttons.green(
                                "mercerMove_" + planetName + "_" + tile.getPosition() + "_" + uH.getName()
                                        + "_infantry_lizhobt",
                                "Move Infantry From Space of " + tile.getPosition() + " To "
                                        + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), "Choose an infantry to move into coexistence.", buttons);
    }

    @ButtonHandler("readyBT")
    public static void readyBT(Game game, Player p1, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        String btID = buttonID.split("_")[1];
        BreakthroughModel btModel = Mapper.getBreakthrough(btID);
        p1.getBreakthroughExhausted().put(btID, false);
        String message = p1.getRepresentation() + " readied _" + btModel.getName() + "_.";
        MessageHelper.sendMessageToChannelWithEmbed(p1.getCorrectChannel(), message, btModel.getRepresentationEmbed());
        if (btID.equalsIgnoreCase("dihmohnbt")) {
            Tile tile = game.getTileByPosition(game.getActiveSystem());
            if (tile != null) {
                List<Button> buttons =
                        Helper.getPlaceUnitButtons(event, p1, game, tile, "sling", "placeOneNDone_dontskip");
                String message2 = p1.getRepresentation()
                        + ", please use these buttons to produce 1 non-fighter ship\n> "
                        + ButtonHelper.getListOfStuffAvailableToSpend(p1, game);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        p1.getFactionEmoji()
                                + " is using _Exodus Engineering_ to produce 1 non-fighter ship (they may do this once per combat).");
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
        }
    }

    @ButtonHandler("exhaustBT")
    public static void exhaustBT(Game game, Player p1, String buttonID, ButtonInteractionEvent event) {
        ButtonHelper.deleteTheOneButton(event);
        String btID = buttonID.split("_")[1];
        BreakthroughModel btModel = Mapper.getBreakthrough(btID);
        p1.getBreakthroughExhausted().put(btID, true);
        String message = p1.getRepresentation() + " exhausted _" + btModel.getName() + "_.";
        MessageHelper.sendMessageToChannelWithEmbed(p1.getCorrectChannel(), message, btModel.getRepresentationEmbed());
        boolean implemented = TeHelperBreakthroughs.handleBreakthroughExhaust(event, game, p1, buttonID);

        if (!implemented) {
            String unimplemented =
                    "IDK how to do this yet. " + Constants.jazzPing() + " please implement this breakthrough.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), unimplemented);
        }
    }

    public static void edynBTStep1(Game game, Player p1) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            buttons.add(Buttons.blue(
                    p1.getFinsFactionCheckerPrefix() + "edynbtSelect_" + p2.getFaction(),
                    p2.getFactionNameOrColor(),
                    p2.getFactionEmojiOrColor()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), "Please choose a player to target with _Arms Brokerage_.", buttons);
    }

    public static void kolumeBTStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("gain_CC_deleteThisMessage", "Gain 1 Command Token"));
        buttons.add(Buttons.gray("acquireATech_deleteThisMessage", "Spend 3 Influence To Research A Technology"));

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is resolving _Synchronicity VI_, either to spend 3 influence to research 1 technology (that's the same colour as one of their exhausted technologies), or to or gain 1 command token.");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose whether you are researching a technology or gaining a command token.",
                buttons);
    }

    public static void bentorBTStep1(Game game, Player p1) {
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("acceptBentorBT_" + p1.getFaction(), "Explore 1 Planet"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", " + p1.getFactionNameOrColor()
                            + " has _Historian Conclave_. This allows to to explore a planet you control. If you do, they will gain 1 commodity.",
                    buttons);
        }
        MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), "Sent buttons to every player to resolve.");
    }

    @ButtonHandler("acceptBentorBT")
    public static void acceptBentorBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);

        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentation() + ", your _Historian Conclave_ offer to " + p1.getFactionNameOrColor()
                        + " has been accepted. "
                        + (p2.getCommodities() >= p2.getCommoditiesTotal()
                                ? "You would gain 1 commodity, but you are already at maximum commodities."
                                : "You have gained 1 commodity."));
        p2.gainCommodities(1);
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(p1, game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose which planet you wish to explore.", buttons);
    }

    @ButtonHandler("useAxisBT")
    public static void useAxisBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("axisbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation()
                        + " has used _Arms Brokerage_ to move any number of ships between two systems with their space docks.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        String message = ", please choose the first system that you wish to swap ships between (and transport).";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Spacedock);
        for (Tile tile : tiles) {
            if (FoWHelper.otherPlayersHaveShipsInSystem(p1, tile, game)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "axisBTStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("useFlorzenBT")
    public static void useFlorzenBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("florzenbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation()
                        + " has used _Arms Brokerage_."
                        + " They will choose another player, and both players will secretly choose to spend 0, 1, or 2 trade goods."
                        + " If both players spent the same, the chosen player must give " + p1.getRepresentationNoPing()
                        + " a random promissory note.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        String message = ", please choose the target player.";
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "florzenBTStep2_" + p2.getFaction(),
                    p2.getFactionNameOrColor(),
                    p2.getFactionEmojiOrColor()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep2")
    public static void florzenBTStep2(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(), p1.getRepresentation() + " has targeted " + p2.getRepresentation());
        ButtonHelper.deleteMessage(event);
        String message = ", please choose the amount of trade goods you wish to spend.";
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 3 && x <= p1.getTg(); x++) {
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "florzenBTStep3_" + p2.getFaction() + "_" + x, x + " tg"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCardsInfoThread(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep3")
    public static void florzenBTStep3(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String originalBid = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has locked in their trade goods, and buttons have been sent to "
                        + p2.getRepresentation() + " for them to choose an amount of trade goods.");
        ButtonHelper.deleteMessage(event);
        String message = ", please choose the amount of tg you want to spend.";
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 3 && x <= p2.getTg(); x++) {
            buttons.add(Buttons.gray(
                    p2.getFinsFactionCheckerPrefix() + "florzenBTStep4_" + p1.getFaction() + "_" + originalBid + "_"
                            + x,
                    x + " tg"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCardsInfoThread(), p2.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep4")
    public static void florzenBTStep4(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String originalBid = buttonID.split("_")[2];
        String originalBidFlorz = buttonID.split("_")[3];
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has chosen to spend " + originalBidFlorz + " trade good"
                        + ("1".equals(originalBidFlorz) ? "" : "s") + " and " + p2.getRepresentation()
                        + " has chosen to spend " + originalBid + " trade good" + ("1".equals(originalBid) ? "" : "s")
                        + ". These trade goods have been returned to the supply."
                        + (originalBid.equalsIgnoreCase(originalBidFlorz)
                                ? "\nAs both players spend the same number of trade goods, " + p1.getRepresentation()
                                        + " has sent a random promissory note to " + p2.getRepresentation() + "."
                                : ""));
        ButtonHelper.deleteMessage(event);
        if (StringUtils.isNumeric(originalBidFlorz) && Integer.parseInt(originalBidFlorz) > 0) {
            p1.setTg(p1.getTg() - Integer.parseInt(originalBidFlorz));
        }
        if (StringUtils.isNumeric(originalBid) && Integer.parseInt(originalBid) > 0) {
            p2.setTg(p2.getTg() - Integer.parseInt(originalBid));
        }
        if (originalBid.equalsIgnoreCase(originalBidFlorz)) {
            PromissoryNoteHelper.sendRandom(event, game, p1, p2);
        }
    }

    @ButtonHandler("useLanefirBt")
    public static void useLanefirBt(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("lanefirbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has used _Erasure Corps_ to explore 1 planet they control.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(p1, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose which planet you wish to explore.", buttons);
    }

    public static void doLanefirBtCheck(Game game, Player player) {
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            if (p2.hasUnlockedBreakthrough("lanefirbt")) {
                List<Button> buttons = new ArrayList<>();
                if (p2.isBreakthroughExhausted("lanefirbt")) {
                    buttons.add(Buttons.green("readyLanefirBt", "Ready Erasure Corps"));
                }
                buttons.add(Buttons.green("gain_CC_deleteThisMessage", "Gain 1 Command Token"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        "Another player has purged a component (that isn't an action card). As such, "
                                + p2.getRepresentation()
                                + " is resolving _Erasure Corps_, either to ready the breakthrough, or to gain 1 command token.");
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentation()
                                + ", pleas choose whether you wish to ready _Erasure Corps_ or gain 1 command token.",
                        buttons);
            }
        }
    }

    @ButtonHandler("axisBTStep2_")
    public static void axisBTStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String message =
                ", please choose the second system that you wish to swap ships between (and transport). The first system is position "
                        + pos + ".";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos)) {
                continue;
            }
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.getFinsFactionCheckerPrefix() + "redcreussAgentPart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("edynbtSelect_")
    public static void edynbtSelect(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p1.getSecretsUnscored().size() > 0) {
            for (String soID : p1.getSecretsUnscored().keySet()) {
                buttons.add(Buttons.blue(
                        p1.getFinsFactionCheckerPrefix() + "edynbtTarget_" + p2.getFaction() + "_" + soID,
                        Mapper.getSecretObjective(soID).getName()));
            }
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                    event, "Choose a secret objective to show.", buttons);
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(),
                    "Sent " + p2.getFactionNameOrColor() + " resolution buttons in their `#cards-info` channel.");
            edynbtTarget(game, p1, event, "edynbtTarget_" + p2.getFaction() + "_none");
        }
    }

    @ButtonHandler("edynbtTarget_")
    public static void edynbtTarget(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        SecretObjectiveModel so = Mapper.getSecretObjective(buttonID.split("_")[2]);
        if (so != null) {
            MessageHelper.sendMessageToChannel(
                    p1.getCardsInfoThread(),
                    "You have shown " + so.getName() + " to " + p2.getFactionNameOrColor() + ".");
            MessageHelper.sendMessageToChannelWithEmbed(
                    p2.getCardsInfoThread(),
                    p1.getFactionNameOrColor() + " has shown you the secret objective: " + so.getName() + ".",
                    so.getRepresentationEmbed());
        }
        if (p2.getSecretsUnscored().size() > 0) {
            buttons.add(Buttons.green(
                    "edynbtFinal_showSecret_" + p1.getFaction(),
                    "Show Random Secret Objective to " + p1.getFactionNameOrColor()));
        }
        buttons.add(Buttons.blue(
                "edynbtFinal_noShowSecret_" + p1.getFaction(), "Allow Coexistence to " + p1.getFactionNameOrColor()));
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentation() + " , please choose whether to show a secret objective to "
                        + p1.getFactionNameOrColor() + " or to allow coexistence.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("edynbtFinal_")
    public static void edynbtFinal(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        String action = buttonID.split("_")[1];
        if ("showSecret".equals(action)) {
            List<String> unscoredSOs = new ArrayList<>(p1.getSecretsUnscored().keySet());
            Collections.shuffle(unscoredSOs);
            String randomSOID = unscoredSOs.get(0);
            SecretObjectiveModel so = Mapper.getSecretObjective(randomSOID);
            if (so != null) {
                MessageHelper.sendMessageToChannelWithEmbed(
                        p2.getCardsInfoThread(),
                        p2.getRepresentation() + " " + p1.getFactionNameOrColor()
                                + " has shown you the secret objective: " + so.getName() + ".",
                        so.getRepresentationEmbed());
                MessageHelper.sendMessageToChannel(
                        p1.getCorrectChannel(), p2.getFactionNameOrColor() + " was shown a random secret objective.");
            } else {
                MessageHelper.sendMessageToChannel(
                        p1.getCorrectChannel(), p1.getFactionNameOrColor() + " has no unscored secret objectives.");
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(),
                    p2.getRepresentation() + " " + p1.getFactionNameOrColor()
                            + " has chosen to allow you to coexist on one of their planets.");
            List<Button> buttons = new ArrayList<>();
            Player target = p1;
            Player player = p2;
            for (String planet : target.getPlanetsAllianceMode()) {
                if (game.getUnitHolderFromPlanet(planet) != null
                        && game.getUnitHolderFromPlanet(planet).hasGroundForces(target)) {
                    buttons.add(Buttons.gray(
                            player.getFinsFactionCheckerPrefix() + "exchangeProgramPart3_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                }
            }
            buttons.add(Buttons.red("deleteButtons", "Cancel"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", please choose a planet to coexist on with "
                            + target.getFactionNameOrColor() + ".",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }
}
