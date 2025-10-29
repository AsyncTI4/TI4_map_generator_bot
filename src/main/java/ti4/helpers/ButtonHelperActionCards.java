package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.helpers.DiceHelper.Die;
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
import ti4.model.ActionCardModel;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.BlindSelectionService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperActionCards {

    private static List<Button> getTilesToScuttle(Player player, Game game, int tgAlready) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Buttons.green(
                        finChecker + "scuttleIn_" + tileEntry.getKey() + "_" + tgAlready,
                        tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        // Button validTile2 = Buttons.red(finChecker + "deleteButtons", "Decline");
        // buttons.add(validTile2);
        return buttons;
    }

    private static List<Button> getTilesToLuckyShot(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tileEntry.getValue(), game)
                    && FoWHelper.playerHasPlanetsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                UnitHolder space = tile.getUnitHolders().get("space");
                boolean rightKindPresent = false;
                for (Player p2 : game.getRealPlayers()) {
                    if (space.getUnitCount(UnitType.Dreadnought, p2.getColor()) > 0
                            || space.getUnitCount(UnitType.Cruiser, p2.getColor()) > 0
                            || space.getUnitCount(UnitType.Destroyer, p2.getColor()) > 0) {
                        rightKindPresent = true;
                    }
                }
                if (rightKindPresent) {
                    Button validTile = Buttons.green(
                            finChecker + "luckyShotIn_" + tileEntry.getKey(),
                            tile.getRepresentationForButtons(game, player));
                    buttons.add(validTile);
                }
            }
        }
        return buttons;
    }

    private static List<Button> getUnitsToScuttle(Player player, Tile tile, int tgAlready) {
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
                    String buttonID = finChecker + "scuttleOn_" + tile.getPosition() + "_" + unitName + "damaged" + "_"
                            + tgAlready;
                    Button validTile2 = Buttons.red(buttonID, "Remove A Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(
                            finChecker + "scuttleOn_" + tile.getPosition() + "_" + unitName + "_" + tgAlready,
                            "Remove " + x + " " + prettyName,
                            unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    private static List<Button> getUnitsToLuckyShot(Player player, Game game, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Dreadnought);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet) continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (player.unitBelongsToPlayer(unitKey)) continue;

                if (!allowedUnits.contains(unitKey.getUnitType())) {
                    continue;
                }
                Player p2 = game.getPlayerFromColorOrFaction(unitKey.getColor());
                if (p2 == null) {
                    continue;
                }

                UnitModel unitModel = p2.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "luckyShotOn_" + tile.getPosition() + "_" + unitName + "damaged"
                            + "_" + unitKey.getColor();
                    Button validTile2 = Buttons.red(buttonID, "Destroy A Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(
                            finChecker + "luckyShotOn_" + tile.getPosition() + "_" + unitName + "_"
                                    + unitKey.getColor(),
                            "Destroy " + x + " " + prettyName,
                            unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("startToScuttleAUnit_")
    public static void resolveScuttleStart(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        List<Button> buttons = getTilesToScuttle(player, game, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the system containing the unit you wish to _Scuttle_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startToLuckyShotAUnit_")
    public static void resolveLuckyShotStart(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getTilesToLuckyShot(player, game);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationUnfogged()
                            + " no systems to _Lucky Shot_ at found. Remember you can't _Lucky Shot_ your own units."
                            + " Report bug if in error. If not an error, please take a different action.");
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to _Lucky Shot_ at.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("luckyShotOn_")
    public static void resolveLuckyShotRemoval(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile tile = game.getTileByPosition(pos);
        Player p2 = game.getPlayerFromColorOrFaction(color);
        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p2.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        DestroyUnitService.destroyUnit(event, tile, game, parsedUnit, damaged);
        String msg = (damaged ? "A damaged " : "") + unitKey.unitEmoji() + " owned by "
                + p2.getFactionEmojiOrColor() + " in tile " + tile.getRepresentationForButtons(game, player)
                + " was removed via the _Lucky Shot_ action card. How lucky!";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("endScuttle_")
    public static void resolveScuttleEnd(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int tgAlready = Integer.parseInt(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " trade goods increased by " + tgAlready + " (" + player.getTg() + "->"
                        + (player.getTg() + tgAlready) + ").");
        player.setTg(player.getTg() + tgAlready);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, tgAlready);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("scuttleIn_")
    public static void resolveScuttleTileSelection(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        int tgAlready = Integer.parseInt(buttonID.split("_")[2]);
        List<Button> buttons = getUnitsToScuttle(player, tile, tgAlready);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to _Scuttle_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("luckyShotIn_")
    public static void resolveLuckyShotTileSelection(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = getUnitsToLuckyShot(player, game, tile);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose the ship you wish to hit with _Lucky Shot_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("scuttleOn_")
    public static void resolveScuttleRemoval(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        int tgAlready = Integer.parseInt(buttonID.split("_")[3]);
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
        String msg = (damaged ? "A damaged " : "") + unitKey.unitEmoji() + " in tile "
                + tile.getRepresentation() + " was removed via the _Scuttle_ action card by "
                + player.getFactionEmoji() + ".";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
        if (tgAlready > 0) {
            tgAlready += (int) removedUnit.getCost();
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + " trade goods increased by " + tgAlready + " (" + player.getTg() + "->"
                            + (player.getTg() + tgAlready) + ").");
            player.setTg(player.getTg() + tgAlready);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, tgAlready);
        } else {
            tgAlready += (int) removedUnit.getCost();
            buttons.add(Buttons.green("startToScuttleAUnit_" + tgAlready, "Scuttle Another Unit"));
            buttons.add(Buttons.red("endScuttle_" + tgAlready, "Finished Scuttling"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(),
                    player.getRepresentationUnfogged()
                            + ", please choose if you wish to _Scuttle_ another unit or to end Scuttling.",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("checkForAllACAssignments")
    public static void checkForAllAssignmentACs(Game game, Player player) {
        checkForAssigningCoup(game, player);
        checkForAssigningPublicDisgrace(game, player);
        checkForPlayingManipulateInvestments(game, player);
        checkForPlayingSummit(game, player);
        checkForPlayingBountyContracts(game, player);
    }

    @ButtonHandler("resolveCounterStroke")
    public static void resolveCounterStroke(Game game, Player player, ButtonInteractionEvent event) {
        RemoveCommandCounterService.fromTile(player.getColor(), game.getTileByPosition(game.getActiveSystem()), game);
        String message = player.getFactionEmoji() + " removed their command token from tile " + game.getActiveSystem()
                + " using _Counterstroke_ and gained it to their tactic pool.";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCounterStroke_")
    public static void resolveCounterStroke(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        RemoveCommandCounterService.fromTile(player.getColor(), game.getTileByPosition(buttonID.split("_")[1]), game);
        String message = player.getFactionEmoji() + " removed their command token from tile " + buttonID.split("_")[1]
                + " using _Counterstroke_ and gained it to their tactic pool.";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveSummit")
    public static void resolveSummit(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentation() + "! Your current command tokens are "
                + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveWarEffort")
    public static void resolveWarEffort(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
        String message = "Use buttons to place 1 cruiser with your other ships.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveFreeTrade")
    public static void resolveFreeTrade(Game game, Player player, ButtonInteractionEvent event) {
        Button convert2CommButton =
                Buttons.green("convert_2_comms_stay", "Convert 2 Commodities Into Trade Goods", MiscEmojis.Wash);
        Button get2CommButton = Buttons.blue("gain_2_comms_stay", "Gain 2 Commodities", MiscEmojis.comm);
        List<Button> buttons =
                List.of(convert2CommButton, get2CommButton, Buttons.red("deleteButtons", "Done Resolving"));
        String message =
                "Use buttons to gain or convert commodities as appropriate. You may trade in this window/in between gaining commodities.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolvePreparation")
    public static void resolvePreparation(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String message;
        if (player.hasAbility("scheming")) {
            message = "Use button to draw 2 action cards (**Scheming** increases this from the normal 1 action card).";
            buttons.add(Buttons.green("draw_2_ACDelete", "Draw 2 Action Cards"));
        } else {
            message = "Use button to draw 1 action card.";
            buttons.add(Buttons.green("draw_1_ACDelete", "Draw 1 Action Card"));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("resolveRally")
    public static void resolveRally(Game game, Player player, ButtonInteractionEvent event) {
        String message = player.getFactionEmoji() + " gained command tokens to their fleet pool (" + player.getFleetCC()
                + "->" + (player.getFleetCC() + 2) + ") using _Rally_.";
        player.setFleetCC(player.getFleetCC() + 2);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
        if (game.getLaws().containsKey("regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", reminder that _Fleet Regulations_ is a law, which is limiting fleet pool to 4 tokens.");
        }
    }

    public static List<Button> getArcExpButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(game, player);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Buttons.green("arcExp_industrial", "Explore Industrials Thrice"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Buttons.blue("arcExp_cultural", "Explore Culturals Thrice"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Buttons.red("arcExp_hazardous", "Explore Hazardous Thrice"));
            }
        }
        return buttons;
    }

    @ButtonHandler("arcExp_")
    public static void resolveArcExpButtons(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String type = buttonID.replace("arcExp_", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String cardID = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardID);
            sb.append(card.textRepresentation()).append(System.lineSeparator());
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(player.getRepresentationUnfogged()).append(" gained a relic fragment.\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
        }
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getRepealLawButtons")
    public static void getRepealLawButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please choose the law you wish to repeal.", getRepealLawButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRepealLawButtons(Game game, Player player) {
        List<Button> lawButtons = new ArrayList<>();
        for (String law : game.getLaws().keySet()) {
            lawButtons.add(Buttons.green("repealLaw_" + game.getLaws().get(law), Mapper.getAgendaTitle(law)));
        }
        return lawButtons;
    }

    @ButtonHandler("getDivertFundingButtons")
    public static void getDivertFundingButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                "Please choose the technology you wish to return.",
                getDivertFundingLoseTechOptions(player, game));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDivertFundingLoseTechOptions(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.isUnitUpgrade()
                    && (techM.getFaction().isEmpty()
                            || techM.getFaction().orElse("").isEmpty())) {
                buttons.add(Buttons.gray(finChecker + "divertFunding@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    @ButtonHandler("divertFunding@")
    public static void divertFunding(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.removeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " removed the technology " + techM1.getName() + ".");
        resolveResearch(game, player, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forwardSupplyBaseStep2_")
    public static void resolveForwardSupplyBaseStep2(
            Player hacan, Game game, ButtonInteractionEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    hacan.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained 1 trade good due to _Forward Supply Base_ "
                        + player.gainTG(1) + ".");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    hacan.getCorrectChannel(),
                    player.getFactionEmojiOrColor() + " gained 1 trade good due to _Forward Supply Base_.");
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("courageousStarter")
    public static void resolveCourageousStarter(Player player, Game game, ButtonInteractionEvent event) {
        boolean nekro = ButtonHelper.doesPlayerHaveFSHere(
                "nekro_flagship", player, game.getTileByPosition(game.getActiveSystem()));
        String msg = player.getRepresentationNoPing()
                + ", please choose the recently deceased ship that you wish to have been _Courageous To The End_.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), msg, getCourageousOptions(player, game, nekro, "courageous"));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("courageousFire")
    public static void resolveCourageousFire(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String baseType = buttonID.split("_")[1];
        String numHit = buttonID.split("_")[2];
        String type = buttonID.split("_")[3];
        String msg = player.getRepresentationNoPing() + " has chosen the recently deceased " + baseType
                + ", which hits on a " + numHit + ", to have been _Courageous To The End_.";
        if (game.isTwilightsFallMode()) {
            msg = player.getRepresentationNoPing() + " has chosen the recently deceased " + baseType
                    + ", which hits on a " + numHit + ", to have been the target of the Valient Genome.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        String result = player.getFactionEmojiOrColor() + " rolling for " + type + ":\n";
        Tile tile = game.getTileFromPositionOrAlias(game.getActiveSystem());
        // Actually roll for each unit
        int totalHits = 0;
        UnitModel unit = player.getUnitByBaseType(baseType);
        int toHit = unit.getCombatDieHitsOnForAbility(CombatRollType.combatround);
        int modifierToHit = 0;
        int extraRollsForUnit = 0;
        int numRollsPerUnit = 1;

        if ("courageous".equalsIgnoreCase(type)) {
            StringBuilder resultBuilder = new StringBuilder(result);

            int numOfUnit = 2;
            if (game.isTwilightsFallMode()) {
                numOfUnit = 1;
            }

            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
            int hitRolls = DiceHelper.countSuccesses(resultRolls);
            totalHits += hitRolls;
            String unitRoll = CombatMessageHelper.displayUnitRoll(
                    unit, toHit, modifierToHit, numOfUnit, numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
            resultBuilder.append(unitRoll);

            result = resultBuilder.toString();
            result += CombatMessageHelper.displayHitResults(totalHits);
            player.setActualHits(player.getActualHits() + totalHits);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);

            if (hitRolls > 0) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
                        continue;
                    }
                    if (FoWHelper.playerHasShipsInSystem(p2, game.getTileFromPositionOrAlias(game.getActiveSystem()))) {
                        List<Button> buttons =
                                ButtonHelper.getButtonsForRemovingAllUnitsInSystem(p2, game, tile, "courageouscombat");
                        MessageHelper.sendMessageToChannelWithButtons(
                                p2.getCorrectChannel(),
                                p2.getRepresentation() + ", you can use the buttons to destroy your units.",
                                buttons);
                    }
                }
            }
        } else {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                for (UnitKey key : uH.getUnitKeys()) {
                    Player player_ = game.getPlayerFromColorOrFaction(key.getColor());
                    if (player_ == player || player.getAllianceMembers().contains(player_.getFaction())) {
                        continue;
                    }
                    int numOfUnit = uH.getUnitCount(key);
                    totalHits = 0;
                    result = player.getFactionEmojiOrColor() + " rolling for " + type + ":\n";
                    StringBuilder resultBuilder = new StringBuilder(result);
                    resultBuilder
                            .append("Rolling against ")
                            .append(numOfUnit)
                            .append(" ")
                            .append(key.getUnitType().getUnitTypeEmoji())
                            .append(" owned by ")
                            .append(key.getColor())
                            .append(".\n");
                    int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
                    List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                    player.setExpectedHitsTimes10(
                            player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
                    int hitRolls = DiceHelper.countSuccesses(resultRolls);
                    totalHits += hitRolls;
                    String unitRoll = CombatMessageHelper.displayUnitRoll(
                            unit,
                            toHit,
                            modifierToHit,
                            numOfUnit,
                            numRollsPerUnit,
                            extraRollsForUnit,
                            resultRolls,
                            hitRolls);
                    resultBuilder.append(unitRoll);

                    result = resultBuilder.toString();
                    result += CombatMessageHelper.displayHitResults(totalHits);
                    player.setActualHits(player.getActualHits() + totalHits);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
                    uH.removeUnit(key, hitRolls);
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getCourageousOptions(Player player, Game game, boolean nekro, String type) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

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

        if (nekro) {
            allowedUnits = Stream.of(
                            UnitType.Destroyer,
                            UnitType.Cruiser,
                            UnitType.Carrier,
                            UnitType.Dreadnought,
                            UnitType.Flagship,
                            UnitType.Warsun,
                            UnitType.Fighter,
                            UnitType.Mech,
                            UnitType.Infantry)
                    .map(UnitType::getValue)
                    .toList();
        }
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null) {
                String buttonID = finChecker + "courageousFire_" + ownedUnit.getBaseType() + "_"
                        + ownedUnit.getCombatHitsOn() + "_" + type;
                String buttonText = "Choose " + ownedUnit.getName() + " (Hits On " + ownedUnit.getCombatHitsOn() + ")";
                buttons.add(Buttons.gray(buttonID, buttonText, ownedUnit.getUnitEmoji()));
            }
        }

        return buttons;
    }

    @ButtonHandler("forwardSupplyBase")
    public static void resolveForwardSupplyBaseStep1(Player player, Game game, ButtonInteractionEvent event) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained 3 trade goods due to _Forward Supply Base_ "
                        + player.gainTG(3) + ".");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("forwardSupplyBaseStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("forwardSupplyBaseStep2_" + p2.getFaction(), null, p2.getFactionEmoji());
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose who should get the other trade good.",
                buttons);
    }

    @ButtonHandler("resolveReparationsStep1")
    public static void resolveReparationsStep1(Player player, Game game, ButtonInteractionEvent event) {
        String message = player.getRepresentationUnfogged() + ", please choose the planet you wish to ready.";
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Buttons.gray(
                    "khraskHeroStep4Ready_" + player.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("reparationsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("reparationsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please tell the bot who took the planet from you.",
                buttons);
    }

    @ButtonHandler("resolveParleyStep1")
    public static void resolveParleyStep1(Player player, Game game, ButtonInteractionEvent event) {
        String message =
                player.getRepresentationUnfogged() + ", please choose the planet you wish to resolve _Parley_ on."
                        + " If it's not present (because the opponent took it already), try pressing UNDO, then `/planet add` it back to yourself, then try again.";
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.gray(
                    player.getFinsFactionCheckerPrefix() + "resolveParleyStep2_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveParleyStep2")
    public static void resolveParleyStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String message = player.getRepresentationUnfogged() + " has _Parley_'d the planet of "
                + Helper.getPlanetRepresentationNoResInf(planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        Tile tile = game.getTileFromPlanet(planet);
        if (tile != null) {
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            UnitHolder planetUnitHolder = unitHolders.get(planet);
            UnitHolder spaceUnitHolder = unitHolders.get(Constants.SPACE);
            if (planetUnitHolder != null && spaceUnitHolder != null) {

                for (UnitKey key : planetUnitHolder.getUnitKeys()) {
                    Player player_ = game.getPlayerFromColorOrFaction(key.getColor());
                    if (player_ == player || player.getAllianceMembers().contains(player_.getFaction())) {
                        continue;
                    }
                    int amt = planetUnitHolder.getUnitCount(key);
                    var removed = planetUnitHolder.removeUnit(key, amt);
                    spaceUnitHolder.addUnitsWithStates(key, removed);
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveDiplomaticPressureStep1")
    public static void resolveDiplomaticPressureStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("diplomaticPressureStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("diplomaticPressureStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose who you wish to force to give you a promissory note.",
                buttons);
    }

    @ButtonHandler("resolveReactorMeltdownStep1")
    public static void resolveReactorMeltdownStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("reactorMeltdownStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("reactorMeltdownStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose who you wish to play _Reactor Meltdown_ on.",
                buttons);
    }

    @ButtonHandler("resolveFrontline")
    public static void resolveFrontlineDeployment(Player player, Game game, ButtonInteractionEvent event) {
        String message =
                player.getRepresentationUnfogged() + ", please choose which planet you wish to drop 3 infantry on.";
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "3gf", "placeOneNDone_skipbuild"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveUnexpected")
    public static void resolveUnexpectedAction(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "unexpected");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to remove your command token from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveDistinguished")
    public static void resolveDistinguished(Player player, Game game, ButtonInteractionEvent event) {
        player.addSpentThing("distinguished_5");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + ", added 5 votes to your vote total.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveUprisingStep1")
    public static void resolveUprisingStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("uprisingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("uprisingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player's planet you wish to _Uprise_ against their oppressors.",
                buttons);
    }

    @ButtonHandler("resolveAssRepsStep1") // don't skip ass day
    public static void resolveAssRepsStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("assRepsStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("assRepsStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose who you wish to be assassinated (in game).",
                buttons);
    }

    @ButtonHandler("resolveSignalJammingStep1")
    public static void resolveSignalJammingStep1(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("signalJammingStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("signalJammingStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player you wish to have a command token placed.",
                buttons);
    }

    @ButtonHandler("resolveSeizeArtifactStep1")
    public static void resolveSeizeArtifactStep1(Player player, Game game, ButtonInteractionEvent event) {
        resolveSeizeArtifactStep1(player, game, event, "no");
    }

    public static void resolveSeizeArtifactStep1(
            Player player, Game game, ButtonInteractionEvent event, String kolleccTech) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || !player.getNeighbouringPlayers(true).contains(p2)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("seizeArtifactStep2_" + p2.getFaction() + "_" + kolleccTech, p2.getColor()));
            } else {
                Button button = Buttons.gray("seizeArtifactStep2_" + p2.getFaction() + "_" + kolleccTech, " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which neighbor you wish to loot.",
                buttons);
    }

    @ButtonHandler("resolvePlagueStep1")
    public static void resolvePlagueStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("plagueStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("plagueStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose who controls the planet that you wish to target.",
                buttons);
    }

    @ButtonHandler("resolveEBSStep1_")
    public static void resolveEBSStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("EBSFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(
                    player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_spacecannonoffence");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("EBSFaction", "");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveBlitz_")
    public static void resolveBlitz(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("BlitzFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(
                    player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_bombardment");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("BlitzFaction", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveMicrometeoroidStormStep1")
    public static void resolveMicrometeoroidStormStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("micrometeoroidStormStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("micrometeoroidStormStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player's fighters you wish to experience inclement space weather.",
                buttons);
    }

    @ButtonHandler("resolveGhostShipStep1")
    public static void resolveGhostShipStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getGhostShipButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which system you wish to place the _Ghost Ship_ in.",
                buttons);
    }

    @ButtonHandler("resolveProbeStep1")
    public static void resolveProbeStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getProbeButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to _Exploration Probe_.",
                buttons);
    }

    @ButtonHandler("ghostShipStep2_")
    public static void resolveGhostShipStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "destroyer");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed 1 destroyer into " + tile.getRepresentation() + ".");

        // If Empyrean Commander is in game check if unlock condition exists
        Player p2 = game.getPlayerFromLeader("empyreancommander");
        CommanderUnlockCheckService.checkPlayer(p2, "empyrean");
        CommanderUnlockCheckService.checkPlayer(player, "ghost", "ghoti");
    }

    @ButtonHandler("probeStep2_")
    public static void resolveProbeStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        ButtonHelper.resolveFullFrontierExplore(game, player, tile, event);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " explored the frontier token in " + tile.getRepresentation() + ".");
    }

    @ButtonHandler("resolveCrippleDefensesStep1")
    public static void resolveCrippleDefensesStep1(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("crippleStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("crippleStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player controls the planet whose Defenses you wish to Cripple.",
                buttons);
    }

    @ButtonHandler("resolveInfiltrateStep1")
    public static void resolveInfiltrateStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("infiltrateStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("infiltrateStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player controls the planet you are Infiltrating.",
                buttons);
    }

    @ButtonHandler("resolveSpyStep1")
    public static void resolveSpyStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("spyStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("spyStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player you wish to purloin a random action card from.",
                buttons);
    }

    @ButtonHandler("psionicHammerStep1")
    public static void resolvepPsionicHammerStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : AgendaHelper.getPlayersWithMostPoints(game)) {
            if (p2 == player) continue;

            if (game.isFowMode()) {
                buttons.add(Buttons.gray("psionicHammerStep2_" + p2.getFaction(), p2.getColorIfCanSeeStats(player)));
            } else {
                Button button = Buttons.gray("psionicHammerStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There are no valid targets for _Neural Hammer_.");
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", please choose which player you wish to see a random secret objective of.",
                    buttons);
        }
    }

    @ButtonHandler("psionicHammerStep2_")
    public static void resolvepPsionicHammerStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String message = p2.getRepresentationUnfogged()
                + ", you have been _Neural Hammer_'d to show a random unscored secret objective. Please press the button to resolve.";
        MessageHelper.sendMessageToChannelWithButton(
                p2.getCardsInfoThread(),
                message,
                Buttons.green("psionicHammerStep3_" + player.getFaction(), "Show A Random Unscored Secret Objective"));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", button to resolve _Neural Hammer_ has been sent to "
                        + p2.getColorIfCanSeeStats(player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("psionicHammerStep3_")
    public static void resolvepPsionicHammerStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        SecretObjectiveHelper.resolveShowRandomSecretObjective(game, player, p2, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolvePSStep1")
    public static void resolvePSStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        game.setStoredValue("politicalStabilityFaction", player.getFaction());
        for (Integer sc : game.getSCList()) {
            if (sc <= 0) continue; // some older games have a 0 in the list of SCs
            Button button;
            String label = Helper.getSCName(sc, game);
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                button = Buttons.gray("psStep2_" + sc, label, scEmoji);
            } else {
                button = Buttons.gray("psStep2_" + sc, sc + " " + label);
            }
            buttons.add(button);
        }
        if (game.getRealPlayers().size() < 5) {
            buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please tell the bot which strategy card(s) you used to have.",
                buttons);
    }

    @ButtonHandler("resolveImpersonation")
    public static void resolveImpersonation(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        String message = player.getFactionEmoji() + " drew a secret objective";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ", and then drew a second secret objective due to **Plausible Deniability**";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), message + ".");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentation() + ", please pay the 3 influence for _Impersonation_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("psStep2_")
    public static void resolvePSStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int scNum = Integer.parseInt(buttonID.split("_")[1]);
        player.addSC(scNum);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " you retained " + Helper.getSCName(scNum, game) + ".");
        if (game.getRealPlayers().size() < 5) {
            ButtonHelper.deleteTheOneButton(event);
        } else {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("resolveInsubStep1")
    public static void resolveInsubStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || p2.getTacticalCC() < 1) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("insubStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("insubStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player you wish to remove a command token from the tactic pool of.",
                buttons);
    }

    @ButtonHandler("resolveUnstableStep1")
    public static void resolveUnstableStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersNDummies()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("unstableStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("unstableStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player owns the soon-to-be _Unstable Planet_.",
                buttons);
    }

    @ButtonHandler("resolveABSStep1")
    public static void resolveABSStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("absStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("absStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which player will have discovered some _Ancient Burial Sites_ upon their cultural planets.",
                buttons);
    }

    @ButtonHandler("resolveSalvageStep1")
    public static void resolveSalvageStep1(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("salvageStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("salvageStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose who you're playing _Salvage_ on.",
                buttons);
    }

    @ButtonHandler("insubStep2_")
    public static void resolveInsubStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        p2.setTacticalCC(p2.getTacticalCC() - 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you have removed 1 command from the tactic pool of "
                        + p2.getFactionEmojiOrColor() + ".");
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged()
                        + ", you lost a command token from your tactic pool due to _Insubordination_ ("
                        + (p2.getTacticalCC() + 1) + "->" + p2.getTacticalCC() + ").");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("diplomaticPressureStep2_")
    public static void resolveDiplomaticPressureStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
        String message = p2.getRepresentationUnfogged() + ", you are being forced to give a promissory note to "
                + player.getFactionEmojiOrColor() + ". Please choose which promissory note you wish to send.";
        if (game.isFowMode()) {
            message = p2.getRepresentationUnfogged()
                    + ", you are being forced to give a promissory note to somebody. Please choose which promissory note you wish to send.";
        }
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
        if (game.isWildWildGalaxyMode()) {
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", buttons to send a promissory note have been given to "
                        + p2.getFactionEmojiOrColor() + ".");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("absStep2_")
    public static void resolveABSStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String planet : p2.getPlanetsAllianceMode()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null && p.getPlanetTypes().contains("cultural")) {
                p2.exhaustPlanet(planet);
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you exhausted all the cultural planets of "
                        + p2.getFactionEmojiOrColor() + ".");
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged()
                        + ", your cultural planets were exhausted due to _Ancient Burial Sites_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("salvageStep2_")
    public static void resolveSalvageStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int comm = p2.getCommodities();
        p2.setCommodities(0);
        player.setTg(player.getTg() + comm);
        if (comm == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " attempted to steal commodities from "
                            + p2.getFactionEmojiOrColor() + ", but fortuitously, it turns out they didn't have any.");
        } else {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + " has stolen " + comm + " commodit"
                                + (comm == 1 ? "y" : "ies") + " from " + p2.getFactionEmojiOrColor()
                                + " with _Salvage_.");
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + ", your commodities were somehow stolen with _Salvage_.");
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged() + " has somehow managed to use _Salvage_ to steal " + comm
                                + " commodit" + (comm == 1 ? "y" : "ies") + " from " + p2.getRepresentationUnfogged()
                                + ".");
            }
            DisasterWatchHelper.sendMessageInDisasterWatch(
                    game,
                    player.getRepresentationUnfogged() + " has stolen " + comm + " commodit" + (comm == 1 ? "y" : "ies")
                            + " from " + p2.getRepresentationUnfogged() + " in " + game.getName() + " with _Salvage_. "
                            + switch (ThreadLocalRandom.current().nextInt(0, 5)) {
                                case 0 -> "Amazing";
                                case 1 -> "Incredible";
                                case 2 -> "Unbelievable";
                                case 3 -> "Remarkable";
                                default -> "Phenomenal";
                            } + "!");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("spyStep2_")
    public static void resolveSpyStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", since stealing 1 action card reveals hidden information, extra precaution has been taken and a button has been sent to "
                        + p2.getFactionEmojiOrColor()
                        + " `#cards-info` thread. They may press this button to send a random action card to you.");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("spyStep3_" + player.getFaction(), "Send Random Action Card"));
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCardsInfoThread(),
                p2.getRepresentationUnfogged()
                        + ", you've been hit by" + (RandomHelper.isOneInX(1000) ? ", you've been struck by" : "")
                        + " an ability which forces you to send a random action card to another player. Press the button to send a random action card to that player.",
                buttons);
        if (p2.isNpc()) {
            ActionCardHelper.sendRandomACPart2(event, game, p2, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("spyStep3_")
    public static void resolveSpyStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        ActionCardHelper.sendRandomACPart2(event, game, player, p2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reparationsStep2_")
    public static void resolveReparationsStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Chosen player had no readied planets. This is fine and nothing more needs to be done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            buttons.add(Buttons.gray(
                    "reparationsStep3_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to exhaust.",
                buttons);
    }

    @ButtonHandler("resolveInsiderInformation")
    public static void resolveInsiderInformation(Player player, Game game, ButtonInteractionEvent event) {
        AgendaHelper.sendTopAgendaToCardsInfoSkipCovert(game, player, 3);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Sent info for the top 3 cards of the agenda deck to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveEmergencyMeeting")
    public static void resolveEmergencyMeeting(Player player, Game game, ButtonInteractionEvent event) {
        game.shuffleAllAgendasBackIntoDeck();
        AgendaHelper.drawAgenda(3, game, player);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Sent info for the top three cards of the agenda deck to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("seizeArtifactStep2_")
    public static void resolveSeizeArtifactStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        List<String> playerFragments = p2.getFragments();
        for (String fragid : playerFragments) {
            if (fragid.contains("crf")) {
                buttons.add(Buttons.blue(
                        "seizeArtifactStep3_" + p2.getFaction() + "_" + fragid, "Seize Cultural (" + fragid + ")"));
            }
            if (fragid.contains("irf")) {
                buttons.add(Buttons.green(
                        "seizeArtifactStep3_" + p2.getFaction() + "_" + fragid, "Seize Industrial (" + fragid + ")"));
            }
            if (fragid.contains("hrf")) {
                buttons.add(Buttons.red(
                        "seizeArtifactStep3_" + p2.getFaction() + "_" + fragid, "Seize Hazardous (" + fragid + ")"));
            }
            if (fragid.contains("urf")) {
                buttons.add(Buttons.gray(
                        "seizeArtifactStep3_" + p2.getFaction() + "_" + fragid, "Seize Unknown (" + fragid + ")"));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the relic fragment you wish to nab.",
                buttons);
        if (buttonID.split("_").length > 2 && buttonID.split("_")[2].contains("yes")) {
            p2.setTg(p2.getTg() + 2);
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAgents.resolveArtunoCheck(p2, 2);
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentation() + ", you gained 2 trade goods due to being hit by  _Seeker Drones_.");
        }
    }

    @ButtonHandler("uprisingStep2_")
    public static void resolveUprisingStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2.getReadiedPlanets().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Chosen player had no readied planets. Nothing has been done.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getReadiedPlanets()) {
            if (game.getTileFromPlanet(planet) != null
                    && game.getTileFromPlanet(planet).isHomeSystem(game)) {
                continue;
            }
            if (planet.equalsIgnoreCase("triad")
                    || (game.getUnitHolderFromPlanet(planet) != null
                            && game.getUnitHolderFromPlanet(planet).isSpaceStation())) {
                continue;
            }
            buttons.add(Buttons.gray(
                    "uprisingStep3_" + p2.getFaction() + "_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to exhaust.",
                buttons);
    }

    @ButtonHandler("assRepsStep2_")
    public static void resolveAssRepsStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor()
                        + " successfully assassinated all the representatives of "
                        + p2.getFactionEmojiOrColor() + ".");
        String message =
                switch (ThreadLocalRandom.current().nextInt(10)) {
                    case 1 -> ", your representatives (all of them) fell out of some windows.";
                    case 2 ->
                        ", your representatives got the Rasputin treatment. Unfortunately, they were not Rasputin.";
                    case 3 ->
                        ", your representatives were \"invited\" to \"experience\" the \"sight-seeing\" Sea of Desolation \"tour\".";
                    case 4 ->
                        ", your representatives have died of natural causes (assassination is considered a perfectly natural cause of death on Mecatol Rex).";
                    case 5 ->
                        ", your representatives have followed in a great tradition, and so have been stabbed 23 times.";
                    case 6 ->
                        ", your representatives weren't paying their bodyguards enough, judging by empirical evidence.";
                    case 7 ->
                        ", your representatives has discovered that the phrase \"cut-throat politics\" is quite literal on Mecatol Rex.";
                    case 8 ->
                        ", your representatives have met with some fellow ambassadors. Sorry, I meant to say \"met with some grisly ends\".";
                    case 9 ->
                        ", your representatives were up to date on their vaccinations, which is very prudent on an ecumenopolis such as Mecatol Rex. However, they were not up to date on their antidotes, which is very imprudent on an ecumenopolis such as Mecatol Rex.";
                    default -> ", your representatives got sent to the headsman.";
                };
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + message);
        game.setStoredValue("AssassinatedReps", game.getStoredValue("AssassinatedReps") + p2.getFaction());
        game.setStoredValue("preVoting" + p2.getFaction(), "");
    }

    @ButtonHandler("signalJammingStep2_")
    public static void resolveSignalJammingStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "signalJammingStep3_" + p2.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", the map is a big place, too big for the bot to offer all the options. Please choose a system that contains your ships. __This will not place the command token yet.__"
                        + " This tile you're choosing is the origin of _Signal Jamming_. After this, you will be offered buttons to choose that system or an adjacent system, where the command token will be placed.",
                buttons);
    }

    @ButtonHandler("signalJammingStep3_")
    public static void resolveSignalJammingStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String tilePos : FoWHelper.getAdjacentTilesAndNotThisTile(game, pos, player, false)) {
            Tile tile = game.getTileByPosition(tilePos);
            if (!tile.isHomeSystem(game)) {
                buttons.add(Buttons.gray(
                        "signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        Tile tile = game.getTileByPosition(pos);
        if (!tile.isHomeSystem(game)) {
            buttons.add(Buttons.gray(
                    "signalJammingStep4_" + p2.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the system you wish to Jam.",
                buttons);
    }

    @ButtonHandler("signalJammingStep4_")
    public static void resolveSignalJammingStep4(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        CommandCounterHelper.addCC(event, p2, tile);
        ButtonHelper.deleteMessage(event);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you've _Signal Jam_'d the system: "
                            + tile.getRepresentationForButtons(game, player) + ".");
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", you've been _Signal Jam_'d in system: "
                            + tile.getRepresentationForButtons(game, p2) + ".");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has _Signal Jam_'d " + p2.getRepresentationUnfogged()
                            + " in tile " + tile.getRepresentationForButtons(game, p2) + ".");
        }
    }

    @ButtonHandler("reactorMeltdownStep2_")
    public static void resolveReactorMeltdownStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                if (!game.getTileFromPlanet(planet).isHomeSystem(game)) {
                    Tile tile = game.getTileFromPlanet(planet);
                    buttons.add(Buttons.gray(
                            "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                }
            }
        }
        if (p2.hasUnit("absol_saar_spacedock")
                || p2.hasUnit("saar_spacedock")
                || p2.hasTech("ffac2")
                || p2.hasTech("absol_ffac2")) {
            for (Tile tile : game.getTileMap().values()) {
                if (tile.getUnitHolders().get("space").getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                    buttons.add(Buttons.gray(
                            "reactorMeltdownStep3_" + p2.getFaction() + "_" + tile.getPosition() + "_space",
                            tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the space dock you wish to melt.",
                buttons);
    }

    @ButtonHandler("reactorMeltdownStep3_")
    public static void resolveReactorMeltdownStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        Tile tile = game.getTileByPosition(buttonID.split("_")[2]);
        String unitHolderName = buttonID.split("_")[3];
        if ("space".equalsIgnoreCase(unitHolderName)) {
            unitHolderName = "";
        }
        RemoveUnitService.removeUnits(event, tile, game, p2.getColor(), "sd " + unitHolderName);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you melted the space dock in " + tile.getRepresentation());
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your space dock in " + tile.getRepresentation()
                            + " has been melted.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has melted the space dock that used to belong to "
                            + p2.getRepresentationUnfogged() + " in " + tile.getRepresentation() + ".");
        }
        ButtonHelper.checkFleetAndCapacity(p2, game, tile);
        if (p2.hasAbility("data_recovery")) {
            ButtonHelperAbilities.dataRecovery(p2, game, event, "dataRecovery_" + player.getColor());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("plagueStep2_")
    public static void resolvePlagueStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Buttons.gray(
                    "plagueStep3_" + p2.getFaction() + "_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to target.",
                buttons);
    }

    @ButtonHandler("micrometeoroidStormStep2_")
    public static void resolveMicrometeoroidStormStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasFightersInSystem(p2, tile)) {
                buttons.add(Buttons.gray(
                        "micrometeoroidStormStep3_" + p2.getFaction() + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, p2)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the system you wish to storm.",
                buttons);
    }

    @ButtonHandler("resolveRefitTroops")
    public static void resolveRefitTroops(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons =
                new ArrayList<>(ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, "refit"));
        String message = player.getRepresentationUnfogged() + ", use buttons to replace 1 infantry with 1 mech.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        List<Button> buttons2 =
                new ArrayList<>(ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, "refit"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crippleStep2_")
    public static void resolveCrippleStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Buttons.gray(
                    "crippleStep3_" + p2.getFaction() + "_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to Cripple the Defenses of.",
                buttons);
    }

    @ButtonHandler("infiltrateStep2_")
    public static void resolveInfiltrateStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            buttons.add(Buttons.gray(
                    "infiltrateStep3_" + p2.getFaction() + "_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", plase choose the planet you wish to _Infiltrate_.",
                buttons);
    }

    @ButtonHandler("resolveUpgrade_")
    public static void resolveUpgrade(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "cruiser");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "dread");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " has replaced 1 cruiser with 1 dreadnought in "
                        + tile.getRepresentation() + ".");
    }

    public static void checkForAssigningCoup(Game game, Player player) {
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("coup")) {
            game.setStoredValue("Coup", "");
            String msg = player.getRepresentation()
                    + ", you have the option to pre-assign which strategy card you wish to Coup."
                    + " _Coup D'etat_ is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : game.getSCList()) {
                if (sc <= 0) continue; // some older games have a 0 in the list of SCs
                Button button;
                String label = "Coup " + Helper.getSCName(sc, game);
                TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
                if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                    button = Buttons.gray("resolvePreassignment_Coup_" + sc, label, scEmoji);
                } else {
                    button = Buttons.gray("resolvePreassignment_Coup_" + sc, sc + " " + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    public static void checkForPlayingSummit(Game game, Player player) {
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("summit")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Summit_."
                    + " Start-of-strategy-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_Summit", "Pre-Play Summit"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForPlayingBountyContracts(Game game, Player player) {
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("bounty_contracts")
                && game.getStoredValue("BountyContracts").isEmpty()) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Bounty Contracts_."
                    + " Start-of-status-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_BountyContracts", "Pre-Play Bounty Contracts"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void checkForPlayingManipulateInvestments(Game game, Player player) {
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("investments")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Manipulate Investments_."
                    + " Start-of-strategy-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_Investments", "Pre-Play Manipulate Investments"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
        if (player.getActionCards().containsKey("special_session")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Special Session_."
                    + " End-of-agenda-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_SpecialSession", "Pre-play Special Session"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
        if (player.getActionCards().containsKey("revolution")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Revolution_."
                    + " Start-of-strategy-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_PreRevolution", "Pre-Play Revolution"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
        if (player.getActionCards().containsKey("deflection")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-play _Deflection_."
                    + " Start-of-strategy-phase is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("resolvePreassignment_Deflection", "Pre-Play Deflection"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    @ButtonHandler("resolveProfessionalArcheologists")
    public static void resolveProfessionalArcheologists(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        ButtonHelper.deleteMessage(event);
        buttons.add(Buttons.green("olradinPreserveStep2_industrial_prof", "Explore Industrial"));
        buttons.add(Buttons.blue("olradinPreserveStep2_cultural_prof", "Explore Cultural"));
        buttons.add(Buttons.red("olradinPreserveStep2_hazardous_prof", "Explore Hazardous"));
        buttons.add(Buttons.gray("olradinPreserveStep2_frontier_prof", "Explore Frontier"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please use buttons to resolve the action card.",
                buttons);
    }

    public static void checkForAssigningPublicDisgrace(Game game, Player player) {
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            return;
        }
        if (player.getActionCards().containsKey("disgrace")) {
            String msg = player.getRepresentation()
                    + ", you have the option to pre-assign which strategy card you will Publicly Disgrace."
                    + " _Public Disgrace_ is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now."
                    + " Feel free to ignore this message if you don't intend to play it any time soon or are unsure of the target. "
                    + " If you use these buttons, you will then be given the option for it to only trigger on a particular player.";
            List<Button> scButtons = new ArrayList<>();
            for (Integer sc : game.getSCList()) {
                if (sc <= 0) continue; // some older games have a 0 in the list of SCs
                Button button;
                String label = Helper.getSCName(sc, game);
                TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
                if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                    button = Buttons.gray("resolvePreassignment_Public Disgrace_" + sc, label, scEmoji);
                } else {
                    button = Buttons.gray("resolvePreassignment_Public Disgrace_" + sc, sc + " " + label);
                }
                scButtons.add(button);
            }
            scButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, scButtons);
        }
    }

    @ButtonHandler("resolveDecoyOperationStep1_")
    public static void resolveDecoyOperationStep1(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                buttons.add(Buttons.green(
                        "decoyOperationStep2_" + uH.getName(), Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose which planet you wish to resolve _Decoy Operations_ on.",
                buttons);
    }

    @ButtonHandler("decoyOperationStep2_")
    public static void resolveDecoyOperationStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(game, planet, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please use the buttons to move up to 2 ground forces.",
                buttons);
    }

    @ButtonHandler("resolveEmergencyRepairs_")
    public static void resolveEmergencyRepairs(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        tile.removeAllUnitDamage(player.getColor());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " has repaired all their damaged units in " + tile.getRepresentation()
                        + ".");
    }

    @ButtonHandler("resolveTacticalBombardmentStep1")
    public static void resolveTacticalBombardmentStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getTacticalBombardmentButtons(game, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to Tactically Bombard in.",
                buttons);
    }

    @ButtonHandler("tacticalBombardmentStep2_")
    public static void resolveTacticalBombardmentStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int exhaustCount = 0;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPlanets().contains(uH.getName())) {
                        p2.exhaustPlanet(uH.getName());
                        MessageHelper.sendMessageToChannel(
                                p2.getCorrectChannel(),
                                p2.getRepresentation() + ", your planets in " + tile.getRepresentation()
                                        + " were exhausted.");
                        exhaustCount++;
                    }
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        if (game.isFowMode() && exhaustCount == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "In what must be a stroke of unfathomable genius, " + player.getRepresentationUnfogged()
                            + " has tactically bombarded the interstellar medium in " + tile.getRepresentation() + ".");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted all enemy planets in " + tile.getRepresentation() + ".");
        }
    }

    @ButtonHandler("unstableStep2_")
    public static void resolveUnstableStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("hazardous")) {
                buttons.add(Buttons.gray(
                        "unstableStep3_" + p2.getFaction() + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
            }
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose the planet you wish to exhaust.",
                buttons);
    }

    @ButtonHandler("unstableStep3_")
    public static void resolveUnstableStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        boolean didExhaust = false;
        if (p2.getReadiedPlanets().contains(planet)) {
            p2.exhaustPlanet(planet);
            didExhaust = true;
        }
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amountToKill = uH.getUnitCount(UnitType.Infantry, p2.getColor());
        if (amountToKill > 3) {
            amountToKill = 3;
        }
        if (amountToKill > 0) {
            if (p2.hasAbility("data_recovery")) {
                ButtonHelperAbilities.dataRecovery(p2, game, event, "dataRecovery_" + player.getColor());
            }
        }
        DestroyUnitService.destroyUnit(
                event,
                game.getTileFromPlanet(planet),
                game,
                Units.getUnitKey(UnitType.Infantry, p2.getColor()),
                amountToKill,
                uH,
                false);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you exhausted " + planetRep + " and killed " + amountToKill
                            + " infantry there.");
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your planet " + planetRep + " was exhausted and " + amountToKill
                            + " infantry were destroyed.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has destabilised " + planetRep + ", "
                            + (didExhaust ? "exhausting it" : "which was already exhausted") + ", and "
                            + (amountToKill == 0 ? "did not kill any" : "killed " + amountToKill)
                            + " infantry belonging to " + p2.getRepresentationUnfogged() + ".");
        }
    }

    @ButtonHandler("seizeArtifactStep3_")
    public static void resolveSeizeArtifactStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String fragID = buttonID.split("_")[2];
        ButtonHelper.deleteMessage(event);
        p2.removeFragment(fragID);
        player.addFragment(fragID);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you gained the fragment " + fragID + ".");
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your fragment " + fragID + " was seized.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " swiped the fragment " + fragID + " from the collection of "
                            + p2.getRepresentationUnfogged() + ".");
        }
    }

    @ButtonHandler("uprisingStep3_")
    public static void resolveUprisingStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        p2.exhaustPlanet(planet);
        int resValue = Helper.getPlanetResources(planet, game);
        int oldTg = player.getTg();
        player.setTg(oldTg + resValue);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " gained " + resValue + " trade good" + (resValue == 1 ? "" : "s") + " ("
                        + oldTg + "->" + player.getTg() + ").");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, resValue);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationUnfogged() + ", you exhausted " + planetRep);
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your planet " + planetRep + " was exhausted.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " instigated an _Uprising_ on " + planetRep
                            + ", exhausting it, much to the dismay of " + p2.getRepresentationUnfogged() + ".");
        }
    }

    @ButtonHandler("plagueStep3_")
    public static void resolvePlagueStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amount = uH.getUnitCount(UnitType.Infantry, p2.getColor());
        int hits = 0;
        if (amount > 0) {
            StringBuilder msg = new StringBuilder(UnitEmojis.infantry + " rolled ");
            if (game.isTwilightsFallMode()) {
                hits = (amount + 1) / 2;
                msg = new StringBuilder(hits + " infantry were killed.");
            } else {

                for (int x = 0; x < amount; x++) {
                    Die d1 = new Die(6);
                    msg.append(d1.getResult()).append(", ");
                    if (d1.isSuccess()) {
                        hits++;
                    }
                }
                msg = new StringBuilder(msg.substring(0, msg.length() - 2) + "\n Total hits were " + hits);
            }
            UnitKey key = Units.getUnitKey(UnitType.Infantry, p2.getColor());
            DestroyUnitService.destroyUnit(event, game.getTileFromPlanet(planet), game, key, hits, uH, false);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg.toString());
            if (hits > 0) {
                if (p2.hasAbility("data_recovery")) {
                    ButtonHelperAbilities.dataRecovery(p2, game, event, "dataRecovery_" + player.getColor());
                }
            }
        }
        String adjective = "";
        if (amount >= 5) {
            if (hits == 0) {
                adjective = "n inconsequential";
            } else if (hits == amount) {
                adjective = " catastrophic";
            } else if (hits <= amount / 3) {
                adjective = " minor";
            } else if (hits >= 2 * (amount + 1) / 3) {
                adjective = " major";
            } else if (hits * 2 == amount) {
                adjective = " typical";
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you _Plague_'d " + planetRep + " and got " + hits + " hit"
                            + (hits == 1 ? "" : "s"));
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your planet " + planetRep + " suffered a" + adjective
                            + " _Plague_ and you lost " + hits + " infantry.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has released a _Plague_ upon " + planetRep + ".\n"
                            + p2.getRepresentationUnfogged() + ", your planet " + planetRep + " suffered a"
                            + adjective + " _Plague_ and you lost " + hits + " infantry.");
        }
    }

    @ButtonHandler("micrometeoroidStormStep3_")
    public static void resolveMicrometeoroidStormStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String tilePos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(tilePos);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = tile.getUnitHolders().get("space");
        int amount = uH.getUnitCount(UnitType.Fighter, p2.getColor());
        int hits = 0;
        if (amount > 0) {
            StringBuilder msg = new StringBuilder(UnitEmojis.fighter + " rolled ");
            int threshold = 6;
            for (int x = 0; x < amount; x++) {
                Die d1 = new Die(threshold);
                msg.append(d1.getResult()).append(", ");
                if (d1.isSuccess()) {
                    hits++;
                }
            }
            msg = new StringBuilder(msg.substring(0, msg.length() - 2) + "\n Total hits were " + hits);
            UnitKey key = Units.getUnitKey(UnitType.Fighter, p2.getColor());
            var unitParsed = new ParsedUnit(key, hits, Constants.SPACE);
            RemoveUnitService.removeUnit(event, tile, game, unitParsed);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg.toString());

            if (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile)
                    || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_1", p2, tile)
                    || ButtonHelper.doesPlayerHaveFSHere("sigma_vuilraith_flagship_2", p2, tile)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, hits, "fighter", event);
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you stormed " + tile.getRepresentationForButtons(game, player)
                        + " and got " + hits + " hit" + (hits == 1 ? "" : "s"));
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + ", your fighter" + (amount == 1 ? "" : "s") + " in "
                        + tile.getRepresentationForButtons(game, player) + " were hit by a storm and you lost "
                        + hits + " fighter" + (hits == 1 ? "" : "s") + ".");
    }

    @ButtonHandler("crippleStep3_")
    public static void resolveCrippleStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int amount = uH.getUnitCount(UnitType.Pds, p2.getColor());
        if (amount > 0) {
            UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), p2.getColor());
            var unit = new ParsedUnit(key, amount, planet);
            RemoveUnitService.removeUnit(event, game.getTileFromPlanet(planet), game, unit);
            if (p2.hasAbility("data_recovery")) {
                ButtonHelperAbilities.dataRecovery(p2, game, event, "dataRecovery_" + player.getColor());
            }
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you crippled " + planetRep
                            + (amount > 0 ? " and killed " + amount + " PDS." : ". There were no PDS to kill."));
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your planet " + planetRep + " was crippled"
                            + (amount > 0 ? " killing " + amount + " of your PDS." : ". There were no PDS to kill."));
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you crippled " + planetRep
                            + (amount > 0
                                    ? " and killed " + amount + " of " + p2.getRepresentationUnfogged() + " PDS."
                                    : ". There were no " + p2.getRepresentationUnfogged() + " PDS to kill."));
        }
    }

    @ButtonHandler("infiltrateStep3_")
    public static void resolveInfiltrateStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[2];
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reparationsStep3_")
    public static void resolveReparationsStep3(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        p2.exhaustPlanet(planet);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you exhausted " + planetRep + ".");
            MessageHelper.sendMessageToChannel(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", your planet " + planetRep + " was exhausted.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you exhausted " + planetRep + " belonging to "
                            + p2.getRepresentationUnfogged() + ".");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveResearch")
    public static void resolveResearch(Game game, Player player, ButtonInteractionEvent event) {
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you may use the button to research your technology.",
                    List.of(Buttons.GET_A_TECH));
        } else {
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation()
                    + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                    + " Your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("focusedResearch")
    public static void focusedResearch(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        if (player.getTg() < 4 && (!player.hasUnexhaustedLeader("keleresagent") || player.getCommodities() < 1)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you do not have 4 trade goods, guttersnipe, and thus cannot resolve _Focused Research_.");
            return;
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation(false, false) + " has spent 4 trade goods " + player.gainTG(-4)
                            + " on _Focused Research_.");
        }
        if (!player.hasAbility("propagation")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you may use the button to get your technology.",
                    List.of(Buttons.GET_A_TECH));
        } else {
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentation()
                    + ", you would research a technology, but because of **Propagation**, you instead gain 3 command tokens."
                    + " Your current command tokens are " + player.getCCRepresentation()
                    + ". Use buttons to gain command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("repealLaw_")
    public static void repealLaw(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String numID = buttonID.split("_")[1];
        String name = "";
        for (String law : game.getLaws().keySet()) {
            if (numID.equalsIgnoreCase("" + game.getLaws().get(law))) {
                name = law;
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " repealed " + Mapper.getAgendaTitle(name) + ".");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), Mapper.getAgendaTitle(name) + " was repealed.");
        }
        game.removeLaw(name);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getPlagiarizeButtons")
    public static void getPlagiarizeButtons(ButtonInteractionEvent event, Player player, Game game) {
        game.setComponentAction(true);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                "Please choose the technology you wish to acquire by violating intellectual property law.",
                getPlagiarizeButtons(game, player));
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please choose the planets you wish to exhaust to pay the 5 influence.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getPlagiarizeButtons(Game game, Player player) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(true)) {
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, game);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (Mapper.getTech(AliasHandler.resolveTech(tech))
                    .getFaction()
                    .orElse("")
                    .isEmpty()) {
                if (Mapper.getTech(tech).isUnitUpgrade()) {
                    boolean hasSpecialUpgrade = false;
                    for (String factionTech : player.getNotResearchedFactionTechs()) {
                        TechnologyModel fTech = Mapper.getTech(factionTech);
                        if (fTech != null
                                && !fTech.getAlias()
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                                && fTech.isUnitUpgrade()
                                && fTech.getBaseUpgrade()
                                        .orElse("bleh")
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                            hasSpecialUpgrade = true;
                        }
                    }
                    if (!hasSpecialUpgrade) {
                        techs.add(Buttons.green(
                                "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                                Mapper.getTech(tech).getName()));
                    }
                } else {
                    techs.add(Buttons.green(
                            "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                            Mapper.getTech(tech).getName()));
                }
            }
        }
        return techs;
    }

    public static List<Button> getExtractButtons(Game game, Player player, Player p2) {
        List<String> techToGain = new ArrayList<>();
        techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain, game);
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (Mapper.getTech(AliasHandler.resolveTech(tech))
                    .getFaction()
                    .orElse("")
                    .isEmpty()) {
                if (Mapper.getTech(tech).isUnitUpgrade()) {
                    boolean hasSpecialUpgrade = false;
                    for (String factionTech : player.getNotResearchedFactionTechs()) {
                        TechnologyModel fTech = Mapper.getTech(factionTech);
                        if (fTech != null
                                && !fTech.getAlias()
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                                && fTech.isUnitUpgrade()
                                && fTech.getBaseUpgrade()
                                        .orElse("bleh")
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                            hasSpecialUpgrade = true;
                        }
                    }
                    if (!hasSpecialUpgrade) {
                        techs.add(Buttons.green(
                                "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                                Mapper.getTech(tech).getName()));
                    }
                } else {
                    techs.add(Buttons.green(
                            "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                            Mapper.getTech(tech).getName()));
                }
            }
        }
        return techs;
    }

    private static List<Button> getGhostShipButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition())) {
                boolean hasOtherShip = false;
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                        hasOtherShip = true;
                    }
                }
                if (!hasOtherShip) {
                    buttons.add(Buttons.green(
                            "ghostShipStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        BlindSelectionService.filterForBlindPositionSelection(game, player, buttons, "ghostShipStep2");
        return buttons;
    }

    private static List<Button> getTacticalBombardmentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfUnitsWithBombard(player, game)) {
            buttons.add(Buttons.green(
                    "tacticalBombardmentStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static List<Button> getProbeButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
                boolean hasShips = false;
                for (String tile2pos :
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                    if (FoWHelper.playerHasShipsInSystem(player, game.getTileByPosition(tile2pos))) {
                        hasShips = true;
                    }
                }
                if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                    hasShips = true;
                }
                if (hasShips) {
                    buttons.add(Buttons.green(
                            "probeStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        return buttons;
    }

    public static List<Button> getCircletButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().get("space").getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
                if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                    buttons.add(Buttons.green(
                            "probeStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        BlindSelectionService.filterForBlindPositionSelection(game, player, buttons, "probeStep2");
        return buttons;
    }

    @ButtonHandler("resolveReverse_")
    public static void resolveReverse(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String acName = buttonID.replace("resolveReverse_", "");
        List<String> acStrings = new ArrayList<>(game.getDiscardActionCards().keySet());
        for (String acStringID : acStrings) {
            ActionCardModel actionCard = Mapper.getActionCard(acStringID);
            String actionCardTitle = actionCard.getName();
            if (acName.equalsIgnoreCase(actionCardTitle)) {
                boolean picked = game.pickActionCard(
                        player.getUserID(), game.getDiscardActionCards().get(acStringID));
                if (!picked) {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(), "No such action card ID found, please retry.");
                    return;
                }
                String sb = "Game: " + game.getName() + " " + "Player: "
                        + player.getUserName() + "\n" + "Card picked from discards: "
                        + Mapper.getActionCard(acStringID).getRepresentation()
                        + "\n";
                MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                ActionCardHelper.sendActionCardInfo(game, player);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("economicInitiative")
    public static void economicInitiative(Player player, Game game, ButtonInteractionEvent event) {
        StringBuilder message = new StringBuilder();
        List<String> exhaustedPlanets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : exhaustedPlanets) {
            if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                player.refreshPlanet(planet);
                message.append("\n> ")
                        .append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game));
            }
        }
        if (!"".equalsIgnoreCase(player.getAllianceMembers())) {
            for (Player player2 : game.getRealPlayers()) {
                if (player.getAllianceMembers().contains(player2.getFaction())) {
                    exhaustedPlanets = new ArrayList<>(player2.getExhaustedPlanets());
                    for (String planet : exhaustedPlanets) {
                        if (ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural")) {
                            player2.refreshPlanet(planet);
                            message.append("\n> ")
                                    .append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game));
                        }
                    }
                }
            }
        }
        if (message.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationUnfogged()
                            + " would have readied each of their cultural planets, but they have none.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationUnfogged() + " readied each of their cultural planets." + message);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("industrialInitiative")
    public static void industrialInitiative(Player player, Game game, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true);
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " gained " + count + " trade good" + (count == 1 ? "" : "s") + " (" + oldTg
                        + "->" + player.getTg() + ").");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, count);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("miningInitiative")
    public static void miningInitiative(Player player, Game game, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        int count = 0;
        StringBuilder bestPlanet = new StringBuilder();
        for (String planet : player.getPlanetsAllianceMode()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null && p.getResources() > count) {
                count = p.getResources();
                bestPlanet = new StringBuilder(planet);
            } else if (p != null
                    && p.getResources() == count
                    && !bestPlanet.toString().endsWith(" or whatever")) {
                bestPlanet.append(" or whatever");
            }
        }
        player.setTg(oldTg + count);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " gained " + count + " trade good" + (count == 1 ? "" : "s") + " (" + oldTg
                        + "->" + player.getTg() + ") from mining " + bestPlanet + ".");
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, count);
        ButtonHelper.deleteMessage(event);
    }

    public static int getAllCommsInHS(Player player, Game game) {
        int count = 0;

        for (String planet : player.getPlanets()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null) {
                if (game.getTileFromPlanet(planet) == player.getHomeSystemTile()) {
                    count += game.changeCommsOnPlanet(0, planet);
                }
            }
        }
        return count;
    }

    public static void decrease10CommsinHS(Player player, Game game) {
        int count = 10;

        for (String planet : player.getPlanets()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null) {
                if (game.getTileFromPlanet(planet) == player.getHomeSystemTile() && count > 0) {
                    int amount = game.changeCommsOnPlanet(0, planet);
                    game.changeCommsOnPlanet(-Math.min(amount, count), planet);
                    count -= Math.min(amount, count);
                }
            }
        }
    }

    public static String getBestResPlanetInHomeSystem(Player player, Game game) {
        String planetName = "";
        int count = 0;
        for (String planet : player.getPlanets()) {
            Planet p = game.getPlanetsInfo().get(planet);
            if (p != null && p.getResources() > count) {
                if (game.getTileFromPlanet(planet) == player.getHomeSystemTile()) {
                    count = p.getResources();
                    planetName = planet;
                }
            }
        }
        return planetName;
    }
}
