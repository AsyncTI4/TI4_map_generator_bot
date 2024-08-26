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
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.TurnStart;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
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

public class ButtonHelperAgents {

    public static void resolveXxchaAgentInfantryRemoval(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet), "1 infantry " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, true) + " you removed 1 infantry from " + planetRep);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation(true, true) + " 1 infantry of yours on " + planetRep + " was removed via the Ggrocuto Rinn, the Xxcha agent.");
    }

    public static List<Button> getTilesToArboAgent(Player player, Game game,
        GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "arboAgentIn_" + tileEntry.getKey(),
                    tile.getRepresentationForButtons(game, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
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
                String msg = cabal.getRepresentation(true, true) + " you have the ability to use " + (cabal.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + " The Stillness of Stars, the Vuil'raith" + (cabal.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, on "
                    + ButtonHelper.getIdentOrColor(p2, game) + " who has "
                    + p2.getCommoditiesTotal() + " commodities";
                buttons.add(Button.success("exhaustAgent_cabalagent_startCabalAgent_" + p2.getFaction(),
                    "Use Vuil'raith Agent"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void startCabalAgent(Player cabal, Game game, String buttonID,
        GenericInteractionCreateEvent event) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = getUnitsForCabalAgent(cabal, game, event, p2);
        String msg = cabal.getRepresentation(true, true) + " use buttons to capture a ship";
        MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }

    }

    public static List<Button> getUnitsForCabalAgent(Player player, Game game,
        GenericInteractionCreateEvent event, Player p2) {
        List<Button> buttons = new ArrayList<>();
        int maxComms = p2.getCommoditiesTotal();
        String unit2;
        Button unitButton2;
        unit2 = "destroyer";
        if (maxComms > 0 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 8) {
            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        unit2 = "mech";
        if (maxComms > 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 4) {
            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        unit2 = "cruiser";
        if (maxComms > 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 8) {
            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "carrier";
        if (maxComms > 2 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 4) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "dreadnought";
        if (maxComms > 3 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 5) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "flagship";
        if (maxComms > 7 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, unit2) < 1) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2)
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        return buttons;
    }

    public static void resolveCabalAgentCapture(String buttonID, Player player, Game game,
        ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Unable to resolve player, please resolve manually.");
            return;
        }
        Integer commodities = p2.getCommodities();
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " a " + unit
                + " of yours has been captured by " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "The Stillness of Stars, the Vuil'raith" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent. "
                + "Rejoice, for your " + commodities.toString() + " commodities been washed.");
        p2.setTg(p2.getTg() + commodities);
        p2.setCommodities(0);
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, player, 1, unit, event, true);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getUnitsToArboAgent(Player player, Game game, GenericInteractionCreateEvent event,
        Tile tile) {
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

                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName + "damaged";
                    Button validTile2 = Button.danger(buttonID, "Remove A Damaged " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName,
                        "Remove " + x + " " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(Player player, Game game,
        GenericInteractionCreateEvent event, Tile tile, String unit) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, player.getColor(), damaged, game);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " was removed by "
            + player.getFactionEmoji()
            + ". A ship costing up to 2 more than it may now be placed.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        List<String> allowedUnits = Stream
            .of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship,
                UnitType.Warsun, UnitType.Fighter)
            .map(UnitType::getValue).toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID = finChecker + "arboAgentPutShip_" + ownedUnit.getBaseType() + "_" + tile.getPosition();
                String buttonText = "Place " + ownedUnit.getName();
                buttons.add(
                    Button.danger(buttonID, buttonText).withEmoji(Emoji.fromFormatted(ownedUnit.getUnitEmoji())));
            }
        }

        return buttons;
    }

    public static void resolveAbsolHyperAgentReady(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String agent = StringUtils.substringAfterLast(buttonID, "_");
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, Emojis.BioticTech + "Hypermetabolism" + Emojis.Absol);
        ButtonHelper.deleteMessage(event);
        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                player.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " spent a strategy CC and readied " + agent);
            }
            if (agent.contains("absol")) {
                player.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + "  spent a strategy CC and readied " + agent);
            }
            return;
        }
        RefreshLeader.refreshLeader(player, playerLeader, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " used " + Emojis.BioticTech + "Hypermetabolism" + Emojis.Absol + " to spend a strategy CC and ready " + agent);
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game,
            game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void nekroAgentRes(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        p2.setTg(p2.getTg() + 2);
        ButtonHelperAbilities.pillageCheck(p2, game);
        resolveArtunoCheck(p2, game, 2);
        String msg2 = ButtonHelper.getIdentOrColor(player, game) + " selected "
            + ButtonHelper.getIdentOrColor(p2, game) + " as user of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Nekro Malleon, the Nekro" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        String message = p2.getRepresentation(true, true) + " increased your TGs by 2 (" + (p2.getTg() - 2) + "->"
            + p2.getTg()
            + "). Use buttons in your cards info thread to discard 1 AC, or lose 1 CC";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
            p2.getRepresentation(true, true) + " use buttons to discard",
            ACInfo.getDiscardActionCardButtons(game, p2, false));
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getKolleccAgentButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(game, player);
        for (String type : types) {
            if ("industrial".equals(type)) {
                buttons.add(Button.success("kolleccAgentResStep2_industrial", "Explore Industrials X 2"));
            }
            if ("cultural".equals(type)) {
                buttons.add(Button.primary("kolleccAgentResStep2_cultural", "Explore Culturals X 2"));
            }
            if ("hazardous".equals(type)) {
                buttons.add(Button.danger("kolleccAgentResStep2_hazardous", "Explore Hazardous X 2"));
            }
        }
        return buttons;
    }

    public static void kolleccAgentResStep1(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String msg2 = ButtonHelper.getIdentOrColor(player, game) + " selected "
            + ButtonHelper.getIdentOrColor(p2, game) + " as user of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Captain Dust, the Kollecc" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        List<Button> buttons = getKolleccAgentButtons(game, p2);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            p2.getRepresentation(true, true) + " use buttons to resolve",
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void kolleccAgentResStep2(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String type = buttonID.split("_")[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            String cardID = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardID);
            sb.append(card.textRepresentation()).append(System.lineSeparator());
            String cardType = card.getResolution();
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(player.getRepresentation(true, true)).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                game.purgeExplore(cardID);
            }
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "kollecc", event);
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        ButtonHelper.deleteMessage(event);
    }

    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Game game, Player player, String ident, String trueIdentity) {
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Could not find player, please resolve manually.");
            return;
        }
        String message;
        if (p2 == player) {
            message = trueIdentity + "Increased your commodities by two";
            ButtonHelperStats.gainComms(event, game, player, 2, false, true);
        } else {
            message = "Refreshed " + ButtonHelper.getIdentOrColor(p2, game) + "'s commodities";
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation(true, true) + " your commodities were refreshed by " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Carth of Golden Sands, the Hacan" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
            ButtonHelperStats.replenishComms(event, game, p2, true);
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String planetDestination = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        String planetRemoval = buttonID.split("_")[3];
        String unit = buttonID.split("_")[4];
        UnitHolder uH = tile.getUnitHolders().get(planetRemoval);
        String message;
        if ("space".equalsIgnoreCase(planetRemoval)) {
            message = ident + " moved 1 " + unit + " from space area of " + tile.getRepresentation() + " to "
                + Helper.getPlanetRepresentation(planetDestination, game);
            planetRemoval = "";
        } else {
            message = ident + " moved 1 " + unit + " from " + Helper.getPlanetRepresentation(planetRemoval, game)
                + " to " + Helper.getPlanetRepresentation(planetDestination, game);

        }
        new RemoveUnits().unitParsing(event, player.getColor(), tile, unit + " " + planetRemoval, game);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planetDestination),
            unit + " " + planetDestination, game);
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
                    Button inf1Button = Button.success("FFCC_" + player.getFaction() + "_place_infantry_" + pp,
                        "Produce 1 Infantry on " + Helper.getPlanetRepresentation(pp, game));
                    inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf1Button);
                    Button mfButton = Button.success("FFCC_" + player.getFaction() + "_place_mech_" + pp,
                        "Produce Mech on " + Helper.getPlanetRepresentation(pp, game));
                    mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                    unitButtons.add(mfButton);
                }
            }
        }
        unitButtons.add(Button.danger("deleteButtons_spitItOut",
            "Argent Agent"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use buttons to place ground forces via " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Trillossa Aun Mirik, the Argent" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.",
            unitButtons);
    }

    public static void resolveVaylerianAgent(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String message = ButtonHelper.resolveACDraw(p2, game, event);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                ButtonHelper.getIdentOrColor(p2, game) + " gained 1AC from using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Yvin Korduul, the Vaylerian" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void exhaustAgent(String buttonID, GenericInteractionCreateEvent event, Game game, Player player, String ident) {
        String agent = buttonID.replace("exhaustAgent_", "");
        String rest = agent;
        String trueIdentity = player.getRepresentation(true, true);
        if (agent.contains("_")) {
            agent = agent.substring(0, agent.indexOf("_"));
        }

        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            return;
        }

        MessageChannel channel2 = game.getMainGameChannel();
        if (game.isFowMode()) {
            channel2 = player.getPrivateChannel();
        }
        playerLeader.setExhausted(true);

        MessageHelper.sendMessageToChannel(channel2, Emojis.getFactionLeaderEmoji(playerLeader));
        String ssruuClever = "";
        String ssruuSlash = "";
        if ("yssarilagent".equalsIgnoreCase(playerLeader.getId())) {
            ssruuClever = "Clever Clever ";
            ssruuSlash = "/Yssaril";
        }
        String messageText2 = player.getRepresentation() + " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
        MessageHelper.sendMessageToChannel(channel2, messageText2);
        if ("nomadagentartuno".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Artuno the Betrayer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            playerLeader.setTgCount(Integer.parseInt(rest.split("_")[1]));
            String messageText = player.getRepresentation() + " placed " + rest.split("_")[1] + " " + Emojis.getTGorNomadCoinEmoji(game)
                + " on top of " + ssruuClever + "Artuno the Betrayer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, messageText);
        }
        if ("naazagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Garv and Gunn, the Naaz-Rokha" + ssruuSlash + " agents.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        }

        if ("augersagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Clodho, the Augers" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
            int oldTg = p2.getTg();
            p2.setTg(oldTg + 2);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                ButtonHelper.getIdentOrColor(player, game) + " gained 2TGs from " + ssruuClever + "Clodho, the Augers" + ssruuSlash + " agent, being used ("
                    + oldTg + "->" + p2.getTg() + ").");
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    ButtonHelper.getIdentOrColor(p2, game) + " gained 2TGs due to agent usage.");
            }
            ButtonHelperAbilities.pillageCheck(p2, game);
            resolveArtunoCheck(p2, game, 2);
        }

        if ("vaylerianagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Yvin Korduul, the Vaylerian" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            if (rest.contains("_")) {
                Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
                String message = ButtonHelper.resolveACDraw(p2, game, event);
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        ButtonHelper.getIdentOrColor(p2, game) + " gained 1AC due to agent usage.");
                }
            } else {
                String message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Yvin Korduul, the Vaylerian" + ssruuSlash + " agent.";
                List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "vaylerianAgent", null);
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
            }
        }
        if ("kjalengardagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Merkismathr Asvand, the Kjalengard" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
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
                        + " exhausted " + ssruuClever + "Merkismathr Asvand, the Kjalengard" + ssruuSlash + " agent, to potentially move a glory token into the system. "
                        + activePlayer.getFactionEmoji() + " commodities went from " + oldComms + " -> "
                        + newComms + ".");
            }
            if (getGloryTokenTiles(game).size() > 0) {
                offerMoveGloryOptions(game, player, event);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    player.getFactionEmoji()
                        + " there were no glory tokens on the board to move. Go win some battles and earn some, or your ancestors will laugh at ya when "
                        + (ThreadLocalRandom.current().nextInt(20) == 0 ? "(if) " : "") + "you reach Valhalla.");

            }
        }
        if ("cabalagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "The Stillness of Stars, the Vuil'Raith" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            startCabalAgent(player, game, rest.replace("cabalagent_", ""), event);
        }
        if ("jolnaragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Doctor Sucaban, the Jol-Nar" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String msg = player.getRepresentation(true, true) + " you may use the buttons to remove infantry.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                getJolNarAgentButtons(player, game));
        }

        if ("empyreanagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Acamar, the Empyrean" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                + ". Use buttons to gain CCs.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        if ("mykomentoriagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Lactarius Indigo, the Myko-Mentori" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            ButtonHelperAbilities.offerOmenDiceButtons(game, player);
        }
        if ("gledgeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Durran, the Gledge" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Durran, the Gledge" + ssruuSlash + " agent, for +3 Production Capacity.");
        }
        if ("khraskagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Udosh B'rtul, the Khrask" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Udosh B'rtul, the Khrask" + ssruuSlash + " agent, to spend 1 non-home planet's resources as additional influence.");
        }
        if ("rohdhnaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Rond Bri'ay, the Roh'Dhna" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Rond Bri'ay, the Roh'Dhna" + ssruuSlash + " agent, for 1 CC");
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String trueIdentity2 = p2.getRepresentation(true, true);
            String message2 = trueIdentity2 + "! Your current CCs are " + p2.getCCRepresentation()
                + ". Use buttons to gain CCs.";
            game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2,
                buttons);
        }
        if ("veldyragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Solis Morden, the Veldyr" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            p2.addSpentThing("Exhausted " + ssruuClever + "Solis Morden, the Veldyr" + ssruuSlash + " agent, to pay with one planets influence instead of resources.");
        }
        if ("winnuagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Berekar Berekon, the Winnu" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            player.addSpentThing("Exhausted " + ssruuClever + "Berekar Berekon, the Winnu" + ssruuSlash + " agent, for 2 resources.");
        }
        if ("lizhoagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Vasra Ivo, the Li-Zho" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            List<Button> buttons = new ArrayList<>(
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
            String message = "Use buttons to put 2 fighters with your ships.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                buttons);
        }

        if ("nekroagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Nekro Malleon, the Nekro" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Nekro Malleon, the Nekro" + ssruuSlash + " agent.";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "nekroAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }
        if ("kolleccagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Captain Dust, the Kollecc" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Captain Dust, the Kollecc" + ssruuSlash + " agent.";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "kolleccAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }

        if ("hacanagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Carth of Golden Sands, the Hacan" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String message = trueIdentity + " select the faction on which you wish to use " + ssruuClever + "Carth of Golden Sands, the Hacan" + ssruuSlash + " agent.";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "hacanAgentRefresh", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }
        if ("fogallianceagent".equalsIgnoreCase(agent)) {
            fogAllianceAgentStep1(game, player, event);
        }

        if ("xxchaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Ggrocuto Rinn, the Xxcha" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("xxchaagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            String message = " Use buttons to ready a planet. Removing the infantry from your own planets is not automated but is an option for you to do.";
            List<Button> buttons = new ArrayList<>();
            for (String planet : p2.getExhaustedPlanets()) {
                buttons.add(Button.secondary("khraskHeroStep4Ready_" + p2.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true) + message, buttons);
        }

        if ("yinagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Brother Milor, the Yin" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String posNFaction = rest.replace("yinagent_", "");
            String pos = posNFaction.split("_")[0];
            String faction = posNFaction.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                p2.getRepresentation(true, true) + " Use buttons to resolve " + ssruuClever + "Brother Milor, the Yin" + ssruuSlash + " agent.",
                getYinAgentButtons(p2, game, pos));
        }

        if ("naaluagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Z'eu, the Naalu" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("naaluagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            game.setNaaluAgent(true);
            MessageChannel channel = event.getMessageChannel();
            if (game.isFowMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in."
                + " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
            List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, game);
            game.resetCurrentMovedUnitsFrom1TacticalAction();
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true)
                + " Use buttons to resolve tactical action from " + ssruuClever + "Z'eu, the Naalu" + ssruuSlash + " agent. Reminder it is not legal to do a tactical action in a home system.\n"
                + message, ringButtons);
        }

        if ("olradinagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Baggil Wildpaw, the Olradin" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            resolveOlradinAgentStep2(game, p2);
        }
        if ("solagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Evelyn Delouis, the Sol" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdentOrColor(p2, game) + " will receive " + ssruuClever + "Evelyn Delouis, the Sol" + ssruuSlash + " agent, on their next roll.");
            game.setCurrentReacts("solagent", p2.getFaction());
        }
        if ("letnevagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Viscount Unlenn, the Letnev" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdentOrColor(p2, game) + " will receive " + ssruuClever + "Viscount Unlenn, the Letnev" + ssruuSlash + " agent, on their next roll.");
            game.setCurrentReacts("letnevagent", p2.getFaction());
        }

        if ("cymiaeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;

            String successMessage2 = p2.getFactionEmoji() + " drew 1AC due to " + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent.";
            if (p2.hasAbility("scheming")) {
                game.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    player.getRepresentation(true, true) + " use buttons to discard.",
                    ACInfo.getDiscardActionCardButtons(game, player, false));
            }
            if (p2.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, p2, 1);
                successMessage2 += p2.getFactionEmoji() + " Triggered Autonetic Memory Option";
            } else {
                game.drawActionCard(p2.getUserID());
            }
            ButtonHelper.checkACLimit(game, event, p2);
            String headerText2 = p2.getRepresentation(true, true) + " you got 1AC due to "
                + ssruuClever + "Skhot Unit X-12, the Cymiae" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, game, headerText2);
            ACInfo.sendActionCardInfo(game, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentation(true, true) + " use buttons to discard.",
                    ACInfo.getDiscardActionCardButtons(game, p2, false));
            }
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), successMessage2);
        }

        if ("mentakagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("mentakagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            String successMessage = ident + " drew 1 AC.";
            String successMessage2 = p2.getFactionEmoji() + " drew 1 AC.";
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                successMessage += " Drew another AC for scheming. Please discard 1";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(game, player, false));
            }
            if (p2.hasAbility("scheming")) {
                game.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1.";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(game, p2, false));
            }
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

            ButtonHelper.checkACLimit(game, event, player);
            ButtonHelper.checkACLimit(game, event, p2);
            String headerText = player.getRepresentation(true, true) + " you got 1AC from "
                + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
            ACInfo.sendActionCardInfo(game, player);
            String headerText2 = p2.getRepresentation(true, true) + " you got 1AC from "
                + ssruuClever + "Suffi An, the Mentak" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, game, headerText2);
            ACInfo.sendActionCardInfo(game, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                    p2.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(game, p2, false));
            }
            if (player.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(game, player, false));
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), successMessage2);
        }

        if ("sardakkagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "T'ro An, the N'orr" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String posNPlanet = rest.replace("sardakkagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos),
                "2 gf " + planetName, game);
            String successMessage = ident + " placed 2 " + Emojis.infantry + " on "
                + Helper.getPlanetRepresentation(planetName, game) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        }
        if ("argentagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Trillossa Aun Mirik, the Argent" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String pos = rest.replace("argentagent_", "");
            Tile tile = game.getTileByPosition(pos);
            addArgentAgentButtons(tile, player, game);
        }
        if ("nomadagentmercer".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Field Marshal Mercer, a Nomad" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String posNPlanet = rest.replace("nomadagentmercer_", "");
            String planetName = posNPlanet.split("_")[1];
            List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(game, planetName,
                player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true)
                    + " use buttons to resolve move of ground forces to this planet with " + ssruuClever + "Field Marshal Mercer, a Nomad" + ssruuSlash + " agent.",
                buttons);
        }
        if ("l1z1xagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "I48S, the L1Z1X" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String posNPlanet = rest.replace("l1z1xagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new RemoveUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos),
                "1 infantry " + planetName, game);
            new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos),
                "1 mech " + planetName, game);
            String successMessage = ident + " replaced 1 " + Emojis.infantry + " on "
                + Helper.getPlanetRepresentation(planetName, game) + " with 1 mech.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        }

        if ("muaatagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Umbat, the Muaat" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("muaatagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageChannel channel = p2.getCorrectChannel();
            String message = "Use buttons to select which tile to " + ssruuClever + "Umbat, the Muaat" + ssruuSlash + " agent, in";
            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p2, UnitType.Warsun,
                UnitType.Flagship);
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : tiles) {
                Button starTile = Button.success("umbatTile_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, p2));
                buttons.add(starTile);
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }
        if ("bentoragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "C.O.O. Mgur, the Bentor" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveBentorAgentStep2(player, game, event, rest);
        }
        if ("kortaliagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Queen Lucreia, the Kortali" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveKortaliAgentStep2(player, game, event, rest);
        }
        if ("mortheusagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Walik, the Mortheus" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = new ArrayList<>();
            buttons.addAll(ButtonHelper.getDomnaStepOneTiles(p2, game));
            String message = p2.getRepresentation(true, true)
                + " use buttons to select which system the ship you just produced is in.. \n\n You need to tell the bot which system the unit was produced in first, after which it will give tiles to move it to. ";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message,
                buttons);

        }
        if ("cheiranagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Operator Kkavras, the Cheiran" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveCheiranAgentStep1(player, game, event, rest);
        }
        if ("freesystemsagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Cordo Haved, the Free Systems" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveFreeSystemsAgentStep1(player, game, event, rest);
        }
        if ("florzenagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Sal Gavda, the Florzen" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveFlorzenAgentStep1(player, game, event, rest);
        }
        if ("dihmohnagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Jgin Faru, the Dih-Mohn" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String planet = rest.split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
                "1 inf " + planet, game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " landed 1 extra infantry on "
                    + Helper.getPlanetRepresentation(planet, game) + " using " + ssruuClever + "Jgin Faru, the Dih-Mohn" + ssruuSlash + " agent [Note, you need to commit something else to the planet besides this extra infantry in order to use this agent].");
        }
        if ("tnelisagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Davish S’Norri, the Tnelis" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            game.setStoredValue("tnelisAgentFaction", player.getFaction());
            ButtonHelper.resolveCombatRoll(player, game, event,
                "combatRoll_" + game.getActiveSystem() + "_space_bombardment");
            game.setStoredValue("tnelisAgentFaction", "");
        }
        if ("vadenagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Yudri Sukhov, the Vaden" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveVadenAgentStep2(player, game, event, rest);
        }
        if ("celdauriagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "George Nobin, the Muaat" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveCeldauriAgentStep2(player, game, event, rest);
        }
        // if ("celdauriagent".equalsIgnoreCase(agent)) {
        // resolveCeldauriAgentStep2(player, game, event, rest);
        // }
        if ("zealotsagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Priestess Tuh, the Rhodun" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveZealotsAgentStep2(player, game, event, rest);
        }
        if ("nokaragent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Sal Sparrow, the Nokar" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveNokarAgentStep2(player, game, event, rest);
        }
        if ("zelianagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Zelian A, the Zelian" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            resolveZelianAgentStep2(player, game, event, rest);
        }
        if ("mirvedaagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Logic Machina, the Mirveda" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Target did not have a strategy CC, no action taken");
                return;
            }
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.GET_A_TECH);
            buttons.add(Button.danger("deleteButtons", "Delete This"));
            p2.setStrategicCC(p2.getStrategicCC() - 1);
            MessageChannel channel = p2.getCorrectChannel();
            ButtonHelperCommanders.resolveMuaatCommanderCheck(p2, game, event, Emojis.mirveda + " Agent");
            String message0 = p2.getRepresentation(true, true)
                + "1 CC has been subtracted from your strat pool due to use of " + ssruuClever + "Logic Machina, the Mirveda" + ssruuSlash + " agent. You may add it back if you didn't agree to the agent.";
            String message = p2.getRepresentation(true, true)
                + " Use buttons to get a tech of a color which matches one of the unit upgrades prerequisites";
            MessageHelper.sendMessageToChannel(channel, message0);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if ("ghotiagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Becece, the Ghoti" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.split("_")[1];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("ghotiATG", "Use to get TG"));
            buttons.add(Button.secondary("ghotiAProd", "Use to produce +2 units"));
            buttons.add(Button.danger("deleteButtons", "Delete This"));
            MessageChannel channel = p2.getCorrectChannel();
            String message = p2.getRepresentation(true, true)
                + " Use buttons to decide to get 1TG or to get to produce 2 more units";
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }

        if ("arborecagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Letani Ospha, the Arborec" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("arborecagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageChannel channel = p2.getCorrectChannel();
            String message = "Use buttons to select which tile to use " + ssruuClever + "Letani Ospha, the Arborec" + ssruuSlash + " agent, in";
            List<Button> buttons = getTilesToArboAgent(p2, game, event);
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }
        if ("kolumeagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Disciple Fran, the Kolume" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("kolumeagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageChannel channel = p2.getCorrectChannel();
            List<Button> redistributeButton = new ArrayList<>();
            Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons",
                "Delete These Buttons");
            redistributeButton.add(Buttons.REDISTRIBUTE_CCs);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(channel,
                p2.getRepresentation(true, true)
                    + " use buttons to redistribute 1 CC (the bot allows more but " + ssruuClever + "Disciple Fran, the Kolume" + ssruuSlash + " agent, is restricted to 1)",
                redistributeButton);
        }
        if ("axisagent".equalsIgnoreCase(agent)) {
            String exhaustText = player.getRepresentation() + " has exhausted " + ssruuClever + "Shipmonger Zsknck, the Axis" + ssruuSlash + " agent.";
            MessageHelper.sendMessageToChannel(channel2, exhaustText);
            String faction = rest.replace("axisagent_", "");
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            if (p2 == null)
                return;
            MessageChannel channel = p2.getCorrectChannel();
            String message = "Use buttons to select whether you want to place 1 cruiser or 1 destroyer in a system with your ships";
            List<Button> buttons = new ArrayList<>();
            if (p2 != player) {
                ButtonHelperStats.gainComms(event, game, player, 2, false, true);
            }
            buttons.add(Button.success("step2axisagent_cruiser", "Place 1 cruiser"));
            buttons.add(Button.success("step2axisagent_destroyer", "Place 1 destroyer"));
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }
        if (event instanceof ButtonInteractionEvent event2) {
            String exhaustedMessage = event2.getMessage().getContentRaw();
            if ("".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Updated";
            }
            int buttons = 0;
            List<ActionRow> actionRow2 = new ArrayList<>();

            for (ActionRow row : event2.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event2.getButton());
                if (buttonIndex > -1 && !"nomadagentmercer".equalsIgnoreCase(agent)) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    buttons = buttons + buttonRow.size();
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if (actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")
                && !exhaustedMessage.contains("choose the target of your agent")) {
                if (exhaustedMessage.contains("buttons to do an end of turn ability") && buttons == 1) {
                    event2.getMessage().delete().queue();
                } else {
                    event2.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
                }

            } else {
                event2.getMessage().delete().queue();
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Button.success("exhaustTCS_" + agent + "_" + player.getFaction(),
                    "Exhaust TCS to Ready " + agent));
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                String msg = p2.getRepresentation(true, true)
                    + " you have the opportunity to exhaust your TCS tech to ready " + agent
                    + " and potentially resolve a transaction.";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg,
                    buttons2);
            }
        }
    }

    public static void presetEdynAgentStep1(Game game, Player player) {
        List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep2", null);
        String msg = player.getRepresentation(true, true)
            + " select the player who you want to take the action when the time comes (probably yourself)";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
    }

    public static void presetEdynAgentStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String faction = buttonID.split("_")[1];
        List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(game, null, "presetEdynAgentStep3_" + faction,
            null);
        String msg = player.getRepresentation(true, true)
            + " select the passing player who will set off the trigger. When this player passes, the player you selected in the last step will get an action.";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void presetEdynAgentStep3(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        String msg = player.getRepresentation(true, true) + " you set " + faction1 + " up to take an action once "
            + faction2 + " passes";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        ButtonHelper.deleteMessage(event);
        String messageID = "edynAgentPreset";
        String part2 = faction1 + "_" + faction2 + "_" + player.getFaction();
        game.setStoredValue(messageID, part2);
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.danger("removePreset_" + messageID, "Remove The Preset"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation() + " you may use this button to undo the preset. Ignore it otherwise.",
            buttons);
    }

    public static boolean checkForEdynAgentPreset(Game game, Player passedPlayer, Player upNextPlayer,
        GenericInteractionCreateEvent event) {
        Player edyn = Helper.getPlayerFromUnlockedLeader(game, "edynagent");
        if (edyn != null && edyn.hasUnexhaustedLeader("edynagent")) {
            String preset = game.getStoredValue("edynAgentPreset");
            if (!preset.isEmpty()) {
                if (preset.split("_")[1].equalsIgnoreCase(passedPlayer.getFaction())) {
                    Player edyn2 = game.getPlayerFromColorOrFaction(preset.split("_")[2]);
                    Player newActivePlayer = game.getPlayerFromColorOrFaction(preset.split("_")[0]);
                    exhaustAgent("exhaustAgent_edynagent", event, game, edyn2, edyn2.getFactionEmoji());
                    game.setStoredValue("edynAgentPreset", "");
                    game.setStoredValue("edynAgentInAction",
                        newActivePlayer.getFaction() + "_" + edyn2.getFaction() + "_" + upNextPlayer.getFaction());
                    List<Button> buttons = TurnStart.getStartOfTurnButtons(newActivePlayer, game, true, event);
                    MessageHelper.sendMessageToChannel(newActivePlayer.getCorrectChannel(),
                        newActivePlayer.getRepresentation(true, true)
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
            DrawAgenda.drawAgenda(1, false, game, edyn2, true);
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

    public static void resolveLanefirAgent(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        if (buttonID.contains("Decline")) {
            if (buttonID.contains("frontier")) {
                String cardChosen = buttonID.split("_")[3];
                String pos = buttonID.split("_")[4];
                new ExpFrontier().expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player,
                    cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String cardID = buttonID.split("_")[3];
                String planetName = buttonID.split("_")[4];
                Tile tile = game.getTileFromPlanet(planetName);
                String messageText = player.getRepresentation() + " explored " +
                    Emojis.getEmojiFromDiscord(drawColor) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile "
                    + tile.getPosition() + ")*:";
                ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && game.getPhaseOfGame().contains("agenda")) {
                    PlanetRefresh.doAction(player, planetName, game);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Planet has been refreshed because of Quaxdol Junitas, the Florzen Commander.");
                    ListVoteCount.turnOrder(event, game, game.getMainGameChannel());
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
                        player.getRepresentation() + " Put 1 commodity on ATS Armaments");
                }
            }
        } else {
            exhaustAgent("exhaustAgent_lanefiragent", event, game, player, player.getFactionEmoji());
            if (buttonID.contains("frontier")) {
                String cardChosen = game.drawExplore(Constants.FRONTIER);
                String pos = buttonID.split("_")[3];
                ExploreModel card = Mapper.getExplore(cardChosen);
                String name = card.getName();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Found a " + name + " in " + game.getTileByPosition(pos).getRepresentation());
                new ExpFrontier().expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player,
                    cardChosen);
            } else {
                String drawColor = buttonID.split("_")[2];
                String planetName = buttonID.split("_")[3];
                Tile tile = game.getTileFromPlanet(planetName);
                String cardID = game.drawExplore(drawColor);
                String messageText = player.getRepresentation() + " explored " +
                    Emojis.getEmojiFromDiscord(drawColor) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile "
                    + tile.getPosition() + ")*:";
                ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
                if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && game.getPhaseOfGame().contains("agenda")) {
                    PlanetRefresh.doAction(player, planetName, game);
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Planet has been refreshed because of Quaxdol Junitas, the Florzen Commander.");
                    ListVoteCount.turnOrder(event, game, game.getMainGameChannel());
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
                        player.getRepresentation() + " Put 1 commodity on ATS Armaments");
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThemAndNoCC(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>();
        adjTiles.addAll(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
        for (String posTile : adjTiles) {
            Tile adjTile = game.getTileByPosition(posTile);
            if (adjTile != null && doesTileHaveAStructureInIt(player, adjTile) && !AddCC.hasCC(player, adjTile)) {
                tiles.add(adjTile);
            }
        }
        return tiles;
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThem(Player player, Game game, Tile origTile) {
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>();
        adjTiles.addAll(FoWHelper.getAdjacentTiles(game, origTile.getPosition(), player, false));
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

    public static List<String> getAllControlledPlanetsInThisSystemAndAdjacent(Game game, Player player,
        Tile tile) {
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
                if (planet.contains("custodia")) {
                    continue;
                }
                PlanetModel model = Mapper.getPlanet(planet);
                if (model.getLegendaryAbilityText() != null && model.getLegendaryAbilityText().length() > 0) {
                    legendaries.add(planet);
                }
            }
        }
        return legendaries;
    }

    public static void resolveCheiranAgentStep1(Player cheiran, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getCheiranAgentTiles(player, game)) {
            buttons.add(Button.success("cheiranAgentStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        String msg = player.getRepresentation(true, true) + " choose the tile you wish to remove a CC from";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveFreeSystemsAgentStep1(Player cheiran, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : getAvailableLegendaryAbilities(game)) {
            buttons.add(Button.success("freeSystemsAgentStep2_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentation(true, true) + " choose the legendary planet ability that you wish to use";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveRefreshWithOlradinAgent(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        PlanetRefresh.doAction(p2, planetName, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                + " with " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Baggil Wildpaw, the Olradin" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        event.getMessage().delete();

    }

    public static void resolveOlradinAgentStep2(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            buttons.add(Button.success("refreshWithOlradinAgent_" + planet,
                "Ready " + Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            player.getRepresentation(true, true)
                + " use buttons to ready a planet of a DIFFERENT trait from the one you just exhausted",
            buttons);
    }

    public static void resolveFlorzenAgentStep1(Player cheiran, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cheiran.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String attachment : getAttachments(game, player)) {
            String planet = attachment.split("_")[0];
            String attach = attachment.split("_")[1];
            buttons.add(Button.success("florzenAgentStep2_" + attachment,
                attach + " on " + Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentation(true, true) + " choose the attachment you wish to use";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveFlorzenAgentStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        String attachment = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet2 : getAllControlledPlanetsInThisSystemAndAdjacent(game, player,
            game.getTileFromPlanet(planet))) {
            buttons.add(Button.success("florzenAgentStep3_" + planet + "_" + planet2 + "_" + attachment,
                Helper.getPlanetRepresentation(planet2, game)));
        }
        String msg = player.getRepresentation(true, true)
            + " choose the adjacent planet you wish to put the attachment on";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveFlorzenAgentStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String planet = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        String attachment = buttonID.split("_")[3];
        attachment = attachment.replace(";", "_");
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        UnitHolder uH2 = ButtonHelper.getUnitHolderFromPlanetName(planet2, game);
        uH.removeToken("attachment_" + attachment + ".png");
        uH2.addToken("attachment_" + attachment + ".png");
        String msg = player.getRepresentation(true, true) + " removed " + attachment + " from "
            + Helper.getPlanetRepresentation(planet, game) + " and put it on "
            + Helper.getPlanetRepresentation(planet2, game) + " using Florzen powers";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveFreeSystemsAgentStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        PlanetExhaustAbility.doAction(event, player, planet, game, false);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " chose to use " + Helper.getPlanetRepresentation(planet, game)
                + " ability. This did not exhaust the ability since it was done with " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Cordo Haved , the Free Systems" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCheiranAgentStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        RemoveCC.removeCC(event, player.getColor(), origTile, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " removed 1 CC from "
                + origTile.getRepresentationForButtons(game, player) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Operator Kkavras, the Cheiran"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile)) {
            buttons.add(Button.success("cheiranAgentStep3_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
            if (getAdjacentTilesWithStructuresInThemAndNoCC(player, game, origTile).size() == 1) {
                resolveCheiranAgentStep3(player, game, event, "cheiranAgentStep3_" + tile.getPosition());
                return;
            }
        }
        String msg = player.getRepresentation(true, true) + " choose the tile you wish to place a CC in";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveCheiranAgentStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = game.getTileByPosition(pos);
        AddCC.addCC(event, player.getColor(), origTile, true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 CC in "
                + origTile.getRepresentationForButtons(game, player) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Operator Kkavras, the Cheiran"
                + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    public static List<Tile> getCheiranAgentTiles(Player player, Game game) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (AddCC.hasCC(player, tile)) {
                if (getAdjacentTilesWithStructuresInThemAndNoCC(player, game, tile).size() > 0) {
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    public static void ghotiAgentForTg(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        int cTG = player.getTg();
        int fTG = cTG + 1;
        player.setTg(fTG);
        ButtonHelperAbilities.pillageCheck(player, game);
        resolveArtunoCheck(player, game, 1);
        String msg = "Used " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Becece, the Ghoti" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to gain 1TG (" + cTG + "->" + fTG + "). ";
        player.addSpentThing(msg);
        ButtonHelper.deleteMessage(event);
    }

    public static void ghotiAgentForProduction(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String msg = "Used " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Becece, the Ghoti"
            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to gain the ability to produce 2 more units. ";
        player.addSpentThing(msg);
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

        String msg = ButtonHelper.getIdentOrColor(player, game) + " max influence planet had " + maxInfluence
            + " influence, so they gained " + commGain + " comms (" + initComm + "->"
            + player.getCommodities() + ") due to " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Yudri Sukhov, the Vaden" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && vaden != player) {
            msg = ButtonHelper.getIdentOrColor(player, game) + " has finished resolving";
            MessageHelper.sendMessageToChannel(vaden.getCorrectChannel(), msg);
        }
    }

    public static void resolveKortaliAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event, String buttonID) {
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
        String msg = ButtonHelper.getIdentOrColor(player, game) + " lost a " + cardInfo.getName() + " to "
            + ButtonHelper.getIdentOrColor(bentor, game) + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Queen Lucreia, the Kortali" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void resolveZealotsAgentStep2(Player zealots, Game game, GenericInteractionCreateEvent event, String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(zealots.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Tile> tiles = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();

        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Tile tile = game.getTileFromPlanet(p.getName());
            if (tile != null && !tiles.contains(tile)
                && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                && ButtonHelper.checkForTechSkips(game, planet)
                || tile.isHomeSystem()) {
                buttons.add(Button.success("produceOneUnitInTile_" + tile.getPosition() + "_ZealotsAgent",
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        String msg = ButtonHelper.getIdentOrColor(player, game)
            + " may produce a unit in their HS or in a system with a tech skip planet due to " + (zealots.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Priestess Tuh, the Zealots" + (zealots.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        if (game.isFowMode() && zealots != player) {
            MessageHelper.sendMessageToChannel(zealots.getCorrectChannel(), msg);
        }
    }

    public static void resolveNokarAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
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
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", game);
        String msg = ButtonHelper.getIdentOrColor(player, game) + " place 1 destroyer in "
            + tile.getRepresentationForButtons(game, player)
            + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "") + "Sal Sparrow, the Nokar" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent. "
            + "A transaction may be done with transaction buttons.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void resolveZelianAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
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
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), "Player did not have any infantry in the active system, no mech placed");
            return;
        }
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 inf", game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 mech", game);
        String msg = ButtonHelper.getIdentOrColor(player, game) + " replace 1 infantry with 1 mech in "
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

    public static void resolveKyroAgentStep2(Player kyro, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(kyro.getCorrectChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        String msg = ButtonHelper.getIdentOrColor(player, game) + " replenished commodities due to " + (kyro.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Tox, the Kyro" + (kyro.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        Player p2 = player;
        p2.setCommodities(p2.getCommoditiesTotal());
        ButtonHelper.resolveMinisterOfCommerceCheck(game, p2, event);
        cabalAgentInitiation(game, p2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && kyro != player) {
            MessageHelper.sendMessageToChannel(kyro.getCorrectChannel(), msg);
        }

        int infAmount = p2.getCommoditiesTotal() - 1;
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, infAmount + "gf", "placeOneNDone_skipbuild"));
        String message = kyro.getRepresentation(true, true) + "Use buttons to drop " + infAmount + " infantry on a planet";
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
                buttons.add(Button.success("celdauriAgentStep3_" + planet.getName(), "Place 1 space dock on " + Helper.getPlanetRepresentation(planet.getName(), game)));
                option = "celdauriAgentStep3_" + planet.getName();
            }
        }
        if (buttons.size() == 1) {
            resolveCeldauriAgentStep3(player, game, event, option);
            return;
        }

        String msg = player.getRepresentation(true, true) + " choose the planet you wish to place 1 space dock on";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }

    }

    public static void resolveCeldauriAgentStep3(Player player, Game game, GenericInteractionCreateEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " put 1 space dock on "
            + Helper.getPlanetRepresentation(planet, game) + " using " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "George Nobin, the Celdauri" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet), "1 sd " + planet,
            game);
        if (player.getCommodities() > 1) {
            player.setCommodities(player.getCommodities() - 2);
            msg = msg + "\n" + player.getFactionEmoji() + "Paid 2 commodities";
        } else {
            player.setTg(player.getTg() - 2);
            msg = msg + "\n" + player.getFactionEmoji() + "Paid 2TGs";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "saar", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "rohdhna", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "cheiran", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "celdauri", event);
        AgendaHelper.ministerOfIndustryCheck(player, game, game.getTileFromPlanet(planet), event);
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (event instanceof ButtonInteractionEvent event2) {
            event2.getMessage().delete().queue();
        }
    }

    public static void resolveBentorAgentStep2(Player bentor, Game game, GenericInteractionCreateEvent event,
        String buttonID) {
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

        String msg = ButtonHelper.getIdentOrColor(player, game) + " gained " + tgGain + "TG" + (tgGain == 1 ? "" : "s") + " (" + oldTg + "->"
            + player.getTg() + ") and " + commGain + " commodit" + (commGain == 1 ? "y" : "ies") + " due to " + (bentor.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "C.O.O. Mgur, the Bentor" + (bentor.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode() && bentor != player) {
            msg = player.getRepresentation() + " has finished resolving.";
            MessageHelper.sendMessageToChannel(bentor.getCorrectChannel(), msg);
        }
    }

    public static void fogAllianceAgentStep1(Game game, Player player, GenericInteractionCreateEvent event) {
        String msg = player.getRepresentation(true, true)
            + " use buttons to select the system you want to move ships from";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !AddCC.hasCC(player, tile)) {
                buttons.add(Button.success("fogAllianceAgentStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    public static void fogAllianceAgentStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String msg = player.getRepresentation(true, true)
            + " use buttons to select the system you want to move ships to";
        List<Button> buttons = new ArrayList<>();
        String ogTile = buttonID.split("_")[1];
        for (Tile tile : game.getTileMap().values()) {
            for (Player p2 : game.getRealPlayers()) {
                if (!player.getAllianceMembers().contains(p2.getFaction()) || player == p2) {
                    continue;
                }
                if (FoWHelper.playerHasShipsInSystem(p2, tile) && !nextToOrInHostileHome(game, player, tile)) {
                    buttons.add(Button.success("fogAllianceAgentStep3_" + tile.getPosition() + "_" + ogTile,
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

    public static void offerMoveGloryOptions(Game game, Player player, GenericInteractionCreateEvent event) {

        String msg = player.getRepresentation(true, true) + " use buttons to select system to move glory from";
        Tile tileAS = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : getGloryTokenTiles(game)) {
            buttons.add(Button.success("moveGlory_" + tile.getPosition() + "_" + tileAS.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

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
        String msg = player.getFactionEmoji() + " moved glory token from " + tile.getRepresentation() + " to "
            + tileAS.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    public static void placeGlory(Game game, Player player, ButtonInteractionEvent event, String buttonID) {

        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        List<String> gloryTokens = getGloryTokensLeft(game);
        if (gloryTokens.size() < 1) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " cannot place more glory, you've hit the limit");
            return;
        }
        space.addToken(gloryTokens.get(0));

        String msg = player.getFactionEmoji() + " added glory token to " + tile.getRepresentation();
        if (player.getLeaderIDs().contains("kjalengardcommander") && !player.hasLeaderUnlocked("kjalengardcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kjalengard", event);
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

        ButtonHelper.deleteMessage(event);
    }

    public static List<String> getGloryTokensLeft(Game game) {
        List<String> gloryTokens = new ArrayList<>();
        gloryTokens.add("token_ds_glory.png");
        gloryTokens.add("token_ds_glory2.png");
        gloryTokens.add("token_ds_glory3.png");
        for (Tile tile : game.getTileMap().values()) {
            List<String> gloryTokens2 = new ArrayList<>();
            gloryTokens2.addAll(gloryTokens);
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

    public static List<Button> getSardakkAgentButtons(Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            String planetId = planet.getName();
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);

            String buttonID = "exhaustAgent_sardakkagent_" + game.getActiveSystem() + "_" + planetId;
            buttons.add(Buttons.green(buttonID, "Use N'orr Agent on " + planetRepresentation, Emojis.Sardakk));
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
                buttons.add(Buttons.green(buttonID, msgStart + planetRepresentation, Emojis.Nomad));
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
                buttons.add(Buttons.green(buttonID, "Use L1Z1X Agent on " + planetRepresentation, Emojis.L1Z1X));
            }
        }
        return buttons;
    }

    public static void arboAgentPutShip(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String unitNPlace = buttonID.replace("arboAgentPutShip_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String successMessage = "";

        switch (unit) {
            case "destroyer" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "destroyer", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.destroyer + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
            case "cruiser" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.cruiser + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
            case "carrier" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "carrier", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.carrier + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
            case "dreadnought" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "dreadnought", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.dreadnought + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
            case "fighter" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "fighter", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.fighter + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
            case "warsun" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "warsun", game);
                successMessage = ident + " Replaced a ship with 1 " + Emojis.warsun + " in tile "
                    + tile.getRepresentationForButtons(game, player);
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getYinAgentButtons(Player player, Game game, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp,
            "Place 2 Fighters");
        ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
        buttons.add(ff2Button);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                String pp = planet.getName();
                Button inf2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp,
                    "Place 2 Infantry on " + Helper.getPlanetRepresentation(pp, game));
                inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                buttons.add(inf2Button);
            }
        }
        return buttons;
    }

    public static void resolveStep2OfAxisAgent(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
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
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 destroyer with your ships";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentation(true, true) + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveArtunoCheck(Player player, Game game, int tg) {
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("exhaustAgent_nomadagentartuno_" + tg,
                "Exhaust Artuno the Betrayer With " + tg + " TG" + (tg == 1 ? "" : "s")));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation(true, true)
                    + " you have the opportunity to exhaust " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                    + "Artuno the Betrayer the Betrayer, a Nomad" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, and place " + tg + " TG" + (tg == 1 ? "" : "s") + " on her.",
                buttons);
        }
    }

    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String trueIdentity) {
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            trueIdentity + " Use buttons to select faction to give " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Brother Milor, the Yin" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent, to.",
            buttons);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if (exhaustedMessage.length() < 1) {
            exhaustedMessage = "Combat";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
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
                        .add(Button.success("jolNarAgentRemoval_" + tile.getPosition() + "_" + unitHolder.getName(),
                            "Remove infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static void resolveJolNarAgentRemoval(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
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
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + unitHName, game);
        if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

}
