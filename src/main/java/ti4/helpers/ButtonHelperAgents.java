package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.explore.ExploreAndDiscard;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
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
import ti4.model.PlanetModel;
import ti4.model.UnitModel;

public class ButtonHelperAgents {

    public static List<Button> getTilesToArboAgent(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker + "arboAgentIn_" + tileEntry.getKey(), tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static void cabalAgentInitiation(Game activeGame, Player p2) {
        for (Player cabal : activeGame.getRealPlayers()) {
            if (cabal == p2) {
                continue;
            }
            if (cabal.hasUnexhaustedLeader("cabalagent")) {
                List<Button> buttons = new ArrayList<>();
                String msg = cabal.getRepresentation(true, true) + " you have the ability to use cabal agent on " + ButtonHelper.getIdentOrColor(p2, activeGame) + " who has "
                    + p2.getCommoditiesTotal() + " commodities";
                buttons.add(Button.success("exhaustAgent_cabalagent_startCabalAgent_" + p2.getFaction(), "Use Agent"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void startCabalAgent(Player cabal, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String faction = buttonID.split("_")[1];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = getUnitsForCabalAgent(cabal, activeGame, event, p2);
        String msg = cabal.getRepresentation(true, true) + " use buttons to capture a ship";
        MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(), msg, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getUnitsForCabalAgent(Player player, Game activeGame, GenericInteractionCreateEvent event, Player p2) {
        List<Button> buttons = new ArrayList<>();
        int maxComms = p2.getCommoditiesTotal();
        String unit2;
        Button unitButton2;
        unit2 = "destroyer";
        if (maxComms > 0 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 8) {
            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        unit2 = "cruiser";
        if (maxComms > 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 8) {
            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "carrier";
        if (maxComms > 2 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 4) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "dreadnought";
        if (maxComms > 3 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 5) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "flagship";
        if (maxComms > 7 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 1) {

            unitButton2 = Button.danger("cabalAgentCapture_" + unit2 + "_" + p2.getFaction(), "Capture " + unit2).withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        return buttons;
    }

    public static void resolveCabalAgentCapture(String buttonID, Player player, Game activeGame, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Unable to resolve player, please resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
            p2.getRepresentation(true, true) + " a " + unit + " of yours has been captured by a cabal agent. Any comms you had have been washed.");
        p2.setTg(p2.getTg() + p2.getCommodities());
        p2.setCommodities(0);
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, activeGame, player, 1, unit, event, true);
        event.getMessage().delete().queue();
    }

    public static List<Button> getUnitsToArboAgent(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            HashMap<UnitKey, Integer> units = unitHolder.getUnits();
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

                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName + "damaged";
                    Button validTile2 = Button.danger(buttonID, "Remove A Damaged " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName, "Remove " + x + " " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        Button validTile2 = Button.danger(finChecker + "deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile, String unit) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();

        boolean damaged = false;
        if (unit.contains("damaged")) {
            unit = unit.replace("damaged", "");
            damaged = true;
        }
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, player.getColor(), damaged, activeGame);
        String msg = (damaged ? "A damaged " : "") + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " was removed via Arborec agent by " + ButtonHelper.getIdent(player);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);

        List<String> allowedUnits = Stream.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun).map(UnitType::getValue).toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID = finChecker + "arboAgentPutShip_" + ownedUnit.getBaseType() + "_" + tile.getPosition();
                String buttonText = "Place " + ownedUnit.getName();
                buttons.add(Button.danger(buttonID, buttonText).withEmoji(Emoji.fromFormatted(ownedUnit.getUnitEmoji())));
            }
        }

        return buttons;
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
            activeGame.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void nekroAgentRes(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        p2.setTg(p2.getTg() + 2);
        ButtonHelperAbilities.pillageCheck(p2, activeGame);
        resolveArtunoCheck(p2, activeGame, 2);
        String msg2 = ButtonHelper.getIdent(player) + " selected " + ButtonHelper.getIdent(p2) + " as user of Nekro agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        String message = p2.getRepresentation(true, true) + " increased your tgs by 2 (" + (p2.getTg() - 2) + "->" + p2.getTg()
            + "). Use buttons in your cards info thread to discard an AC, or lose a cc";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), p2.getRepresentation(true, true) + " use buttons to discard",
            ACInfo.getDiscardActionCardButtons(activeGame, p2, false));
        event.getMessage().delete().queue();
    }

    public static List<Button> getKolleccAgentButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> types = ButtonHelper.getTypesOfPlanetPlayerHas(activeGame, player);
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

    public static void kolleccAgentResStep1(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        String msg2 = ButtonHelper.getIdent(player) + " selected " + ButtonHelper.getIdent(p2) + " as user of Kollecc agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2);
        List<Button> buttons = getKolleccAgentButtons(activeGame, p2);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), p2.getRepresentation(true, true) + " use buttons to resolve",
            buttons);
        event.getMessage().delete().queue();
    }

    public static void kolleccAgentResStep2(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String type = buttonID.split("_")[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            String cardID = activeGame.drawExplore(type);
            sb.append(new ExploreAndDiscard().displayExplore(cardID)).append(System.lineSeparator());
            String card = Mapper.getExploreRepresentation(cardID);
            String[] cardInfo = card.split(";");
            String cardType = cardInfo[3];
            if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
                sb.append(player.getRepresentation(true, true)).append(" Gained relic fragment\n");
                player.addFragment(cardID);
                activeGame.purgeExplore(cardID);
            }
        }
        MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
        MessageHelper.sendMessageToChannel(channel, sb.toString());
        event.getMessage().delete().queue();
    }

    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not find player, please resolve manually.");
            return;
        }
        String message;
        if (p2 == player) {
            p2.setCommodities(p2.getCommodities() + 2);
            message = trueIdentity + "Increased your commodities by two";
        } else {
            p2.setCommodities(p2.getCommoditiesTotal());
            ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, p2, event);
            cabalAgentInitiation(activeGame, p2);
            message = "Refreshed " + ButtonHelper.getIdentOrColor(p2, activeGame) + "'s commodities";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " your commodities were refreshed by hacan agent");
        }
        if (p2.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame).size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), p2.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame));
        }
        if (p2.getLeaderIDs().contains("mykomentoricommander") && !p2.hasLeaderUnlocked("mykomentoricommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String planetDestination = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        String planetRemoval = buttonID.split("_")[3];
        String unit = buttonID.split("_")[4];
        UnitHolder uH = tile.getUnitHolders().get(planetRemoval);
        String message;
        if ("space".equalsIgnoreCase(planetRemoval)) {
            message = ident + " moved 1 " + unit + " from space area of " + tile.getRepresentation() + " to " + Helper.getPlanetRepresentation(planetDestination, activeGame);
            planetRemoval = "";
        } else {
            message = ident + " moved 1 " + unit + " from " + Helper.getPlanetRepresentation(planetRemoval, activeGame) + " to " + Helper.getPlanetRepresentation(planetDestination, activeGame);

        }
        new RemoveUnits().unitParsing(event, player.getColor(), tile, unit + " " + planetRemoval, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planetDestination), unit + " " + planetDestination, activeGame);
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

    public static void addArgentAgentButtons(Tile tile, Player player, Game activeGame) {
        Set<String> tiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false);
        List<Button> unitButtons = new ArrayList<>();
        for (String pos : tiles) {
            Tile tile2 = activeGame.getTileByPosition(pos);

            for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(unitHolder.getName())) {
                    continue;
                }
                Planet planetReal = (Planet) unitHolder;
                String planet = planetReal.getName();
                if (player.getPlanetsAllianceMode().contains(planet)) {
                    String pp = unitHolder.getName();
                    Button inf1Button = Button.success("FFCC_" + player.getFaction() + "_place_infantry_" + pp, "Produce 1 Infantry on " + Helper.getPlanetRepresentation(pp, activeGame));
                    inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf1Button);
                    Button mfButton = Button.success("FFCC_" + player.getFaction() + "_place_mech_" + pp, "Produce Mech on " + Helper.getPlanetRepresentation(pp, activeGame));
                    mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                    unitButtons.add(mfButton);
                }
            }
        }
        unitButtons.add(Button.danger("deleteButtons_spitItOut", "Done Using Argent Agent"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " use buttons to place ground forces via argent agent", unitButtons);
    }

    public static void resolveVaylerianAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player){
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String message = ButtonHelper.resolveACDraw(p2, activeGame, event);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdentOrColor(p2, activeGame) + " gained an AC due to agent usage");
        }
        event.getMessage().delete().queue();
    }

    public static void exhaustAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
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

        MessageChannel channel2 = activeGame.getMainGameChannel();
        if (activeGame.isFoWMode()) {
            channel2 = player.getPrivateChannel();
        }
        playerLeader.setExhausted(true);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Emojis.getFactionLeaderEmoji(playerLeader));
        String messageText = player.getRepresentation() +
            " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
        if ("nomadagentartuno".equalsIgnoreCase(agent)) {
            playerLeader.setTgCount(Integer.parseInt(rest.split("_")[1]));
            messageText = messageText + "\n" + rest.split("_")[1] + " " + Emojis.getTGorNomadCoinEmoji(activeGame) + " was placed on top of the leader";
        }
        MessageHelper.sendMessageToChannel(channel2, messageText);
        if ("naazagent".equalsIgnoreCase(agent)) {
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        }

        if ("augersagent".equalsIgnoreCase(agent)) {
            Player p2 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[1]);
            int oldTg = p2.getTg();
            p2.setTg(oldTg + 2);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                ButtonHelper.getIdentOrColor(player, activeGame) + " gained 2tg due to Augers Agent being used (" + oldTg + "->" + p2.getTg() + ")");
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdentOrColor(p2, activeGame) + " gained 2tg due to agent usage");
            }
            ButtonHelperAbilities.pillageCheck(p2, activeGame);
            resolveArtunoCheck(p2, activeGame, 2);
        }

        if ("vaylerianagent".equalsIgnoreCase(agent)) {
            if(rest.contains("_")){
                Player p2 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[1]);
                String message = ButtonHelper.resolveACDraw(p2, activeGame, event);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
                if (activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdentOrColor(p2, activeGame) + " gained an AC due to agent usage");
                }
            }else{
                String message = trueIdentity + " select faction you wish to use your agent on";
                List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "vaylerianAgent", null);
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
            }
        }
        if ("kjalengardagent".equalsIgnoreCase(agent)) {
            Player activePlayer = activeGame.getActivePlayerObject();
            if(activePlayer != null){
                int oldComms = activePlayer.getCommodities();
                int newComms = oldComms + activePlayer.getNeighbourCount();
                if(newComms > activePlayer.getCommoditiesTotal()){
                    newComms = activePlayer.getCommoditiesTotal();
                }
                activePlayer.setCommodities(newComms);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " exhausted kjal agent to potentially move a glory token into the system. "+ButtonHelper.getIdent(activePlayer)+" comms went from "+oldComms+" -> "+newComms+".");
            }
            if(getGloryTokenTiles(activeGame).size() > 0){
                offerMoveGloryOptions(activeGame, player, event);
            }else{
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " there were no glory tokens on the board to move. Go win some battles and earn some, or your ancestors will laugh at ya when you reach Valhalla");

            }
        }
        if ("cabalagent".equalsIgnoreCase(agent)) {
            startCabalAgent(player, activeGame, rest.replace("cabalagent_", ""), event);
        }
        if ("jolnaragent".equalsIgnoreCase(agent)) {
            String msg = player.getRepresentation(true, true) + " you can use the buttons to remove infantry.";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg, getJolNarAgentButtons(player, activeGame));
        }

        if ("empyreanagent".equalsIgnoreCase(agent)) {
            Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
            Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
            Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
            Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
            List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
            String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }
        if ("mykomentoriagent".equalsIgnoreCase(agent)) {
            ButtonHelperAbilities.offerOmenDiceButtons(activeGame, player);
        }
        if ("gledgeagent".equalsIgnoreCase(agent)) {
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            p2.addSpentThing("Exhausted Gledge Agent for +3 Production Capacity");
        }
        if ("khraskagent".equalsIgnoreCase(agent)) {
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            p2.addSpentThing("Exhausted Khrask Agent to spend 1 non-Home planets Resources as Influence");
        }
        if ("rohdhnaagent".equalsIgnoreCase(agent)) {
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            p2.addSpentThing("Exhausted Rohdna Agent for a CC");
            Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
            Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
            Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
            Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
            List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
            String trueIdentity2 = p2.getRepresentation(true, true);
            String message2 = trueIdentity + "! Your current CCs are " + p2.getCCRepresentation() + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), message2, buttons);
        }
        if ("veldyragent".equalsIgnoreCase(agent)) {
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            p2.addSpentThing("Exhausted Veldyr Agent to pay with one planets influence instead of resources");
        }
        if ("winnuagent".equalsIgnoreCase(agent)) {
            player.addSpentThing("Exhausted Winnu Agent for 2 resources");
        }
        if ("lizhoagent".equalsIgnoreCase(agent)) {
            List<Button> buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "2ff", "placeOneNDone_skipbuild"));
            String message = "Use buttons to put 2 fighters with your ships";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        }

        if ("nekroagent".equalsIgnoreCase(agent)) {
            String message = trueIdentity + " select faction you wish to use your agent on";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "nekroAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }
        if ("kolleccagent".equalsIgnoreCase(agent)) {
            String message = trueIdentity + " select faction you wish to use your agent on";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "kolleccAgentRes", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }

        if ("hacanagent".equalsIgnoreCase(agent)) {
            String message = trueIdentity + " select faction you wish to use your agent on";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "hacanAgentRefresh", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }

        if ("xxchaagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("xxchaagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            String message = "Use buttons to ready a planet. Removing the infantry is not automated but is an option for you to do.";
            List<Button> ringButtons = ButtonHelper.getXxchaAgentReadyButtons(activeGame, p2);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + message, ringButtons);
        }

        if ("yinagent".equalsIgnoreCase(agent)) {
            String posNFaction = rest.replace("yinagent_", "");
            String pos = posNFaction.split("_")[0];
            String faction = posNFaction.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), p2.getRepresentation(true, true) + " Use buttons to resolve yin agent",
                getYinAgentButtons(p2, activeGame, pos));
        }

        if ("naaluagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("naaluagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            activeGame.setNaaluAgent(true);
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
            List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, activeGame);
            activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true)
                + " Use buttons to resolve tactical action from Naalu agent. Reminder it is not legal to do a tactical action in a home system.\n" + message, ringButtons);
        }

        if("cymiaeagent".equalsIgnoreCase(agent)){
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            String message = "";
            String successMessage2 = ButtonHelper.getIdent(p2) + " drew an AC due to Cymiae Agent.";
            if (p2.hasAbility("scheming")) {
                activeGame.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1";
            }
            if (p2.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, p2, 1);
                message = ButtonHelper.getIdent(p2) + " Triggered Autonetic Memory Option";
            } else {
                activeGame.drawActionCard(p2.getUserID());
            }
            ButtonHelper.checkACLimit(activeGame, event, p2);
            String headerText2 = p2.getRepresentation(true, true) + " you got an AC due to Cymiae Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, activeGame, headerText2);
            ACInfo.sendActionCardInfo(activeGame, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), p2.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(activeGame, p2, false));
            }
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), successMessage2);
        }

        if ("mentakagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("mentakagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            String successMessage = ident + " drew an AC.";
            String successMessage2 = ButtonHelper.getIdent(p2) + " drew an AC.";
            String message = "";
            if (player.hasAbility("scheming")) {
                activeGame.drawActionCard(player.getUserID());
                successMessage += " Drew another AC for scheming. Please discard 1";
            }
            if (p2.hasAbility("scheming")) {
                activeGame.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1";
            }
            if (player.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
            } else {
                activeGame.drawActionCard(player.getUserID());
            }
            if (p2.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(activeGame, p2, 1);
                message = ButtonHelper.getIdent(p2) + " Triggered Autonetic Memory Option";
            } else {
                activeGame.drawActionCard(p2.getUserID());
            }

            ButtonHelper.checkACLimit(activeGame, event, player);
            ButtonHelper.checkACLimit(activeGame, event, p2);
            String headerText = player.getRepresentation(true, true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
            ACInfo.sendActionCardInfo(activeGame, player);
            String headerText2 = p2.getRepresentation(true, true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, activeGame, headerText2);
            ACInfo.sendActionCardInfo(activeGame, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), p2.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(activeGame, p2, false));
            }
            if (player.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(activeGame, player, false));
            }
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), successMessage2);
        }

        if ("sardakkagent".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("sardakkagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "2 gf " + planetName, activeGame);
            String successMessage = ident + " placed 2 " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        }
        if ("argentagent".equalsIgnoreCase(agent)) {
            String pos = rest.replace("argentagent_", "");
            Tile tile = activeGame.getTileByPosition(pos);
            addArgentAgentButtons(tile, player, activeGame);
        }
        if ("nomadagentmercer".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("nomadagentmercer_", "");
            String planetName = posNPlanet.split("_")[1];
            List<Button> buttons = ButtonHelper.getButtonsForMovingGroundForcesToAPlanet(activeGame, planetName, player);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " use buttons to resolve move of mercer units to this planet", buttons);
        }
        if ("l1z1xagent".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("l1z1xagent_", "");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "1 infantry " + planetName, activeGame);
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "1 mech " + planetName, activeGame);
            String successMessage = ident + " replaced 1 " + Emojis.infantry + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with 1 mech.";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        }

        if ("muaatagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("muaatagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            String message = "Use buttons to select which tile to Umbat in";
            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, p2, UnitType.Warsun, UnitType.Flagship);
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : tiles) {
                Button starTile = Button.success("umbatTile_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, p2));
                buttons.add(starTile);
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }
        if("bentoragent".equalsIgnoreCase(agent)){
            resolveBentorAgentStep2(player, activeGame, event, rest);
        }
        if("kortaliagent".equalsIgnoreCase(agent)){
            resolveKortaliAgentStep2(player, activeGame, event, rest);
        }
        if("mortheusagent".equalsIgnoreCase(agent)){
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = new ArrayList<>();
            buttons.addAll(ButtonHelper.getDomnaStepOneTiles(p2, activeGame));
            String message = p2.getRepresentation(true, true)+ " use buttons to select which system the ship you just produced is in";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), message, buttons);
        }
        if("cheiranagent".equalsIgnoreCase(agent)){
            resolveCheiranAgentStep1(player, activeGame, event, rest);
        }
        if("freesystemsagent".equalsIgnoreCase(agent)){
            resolveFreeSystemsAgentStep1(player, activeGame, event, rest);
        }
        if("florzenagent".equalsIgnoreCase(agent)){
            resolveFlorzenAgentStep1(player, activeGame, event, rest);
        }
        if("dihmohnagent".equalsIgnoreCase(agent)){
            String planet = rest.split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planet), "1 inf "+planet, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player)+" landed an extra infantry on "+Helper.getPlanetRepresentation(planet, activeGame) +" using Dihmohn agent");
        }
        if("vadenagent".equalsIgnoreCase(agent)){
            resolveVadenAgentStep2(player, activeGame, event, rest);
        }
        if("celdauriagent".equalsIgnoreCase(agent)){
            resolveCeldauriAgentStep2(player, activeGame, event, rest);
        }
        if("celdauriagent".equalsIgnoreCase(agent)){
            resolveCeldauriAgentStep2(player, activeGame, event, rest);
        }
        if("zealotsagent".equalsIgnoreCase(agent)){
            resolveZealotsAgentStep2(player, activeGame, event, rest);
        }
        if("nokaragent".equalsIgnoreCase(agent)){
            resolveNokarAgentStep2(player, activeGame, event, rest);
        }
        if("zelianagent".equalsIgnoreCase(agent)){
            resolveZelianAgentStep2(player, activeGame, event, rest);
        }
        if("mirvedaagent".equalsIgnoreCase(agent)){
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if(p2.getStrategicCC() < 1){
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Target did not have a strat cc, no action taken");
                return;
            }
            Button getTech = Button.success("acquireATech", "Get a tech");
            List<Button> buttons = new ArrayList<>();
            buttons.add(getTech);
            buttons.add(Button.danger("deleteButtons", "Delete This"));
            p2.setStrategicCC(p2.getStrategicCC()-1);
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(p2, activeGame, event);
            String message0 = p2.getRepresentation(true, true) + "A cc has been subtracted from your strat pool due to use of mirveda agent. You can add it back if you didnt agree to the agent";
            String message = p2.getRepresentation(true, true)+" Use buttons to get a tech of a color which matches one of the unit upgrades pre-reqs";
            MessageHelper.sendMessageToChannel(channel, message0);
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
        if("ghotiagent".equalsIgnoreCase(agent)){
            String faction = rest.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);  
            Button getTech = Button.success("ghotiATG", "Use it for a ");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("ghotiATG", "Use to get Tg"));
            buttons.add(Button.secondary("ghotiAProd", "Use to produce +2 units"));
            buttons.add(Button.danger("deleteButtons", "Delete This"));
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            String message = p2.getRepresentation(true, true)+" Use buttons to decide to get a tg or to get to produce 2 more units";
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }

        if ("arborecagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("arborecagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            String message = "Use buttons to select which tile to use arborec agent in";
            List<Button> buttons = getTilesToArboAgent(p2, activeGame, event);
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }
        if ("kolumeagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("kolumeagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            List<Button> redistributeButton = new ArrayList<>();
            Button redistribute = Button.success("redistributeCCButtons", "Redistribute CCs");
            Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons", "Delete These Buttons");
            redistributeButton.add(redistribute);
            redistributeButton.add(deleButton);
            MessageHelper.sendMessageToChannelWithButtons(channel,
                p2.getRepresentation(true, true) + " use buttons to redistribute 1 CC (the bot allows more but the agent is restricted to 1)", redistributeButton);
        }
        if ("axisagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("axisagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = ButtonHelper.getCorrectChannel(p2, activeGame);
            String message = "Use buttons to select whether you want to place 1 cruiser or 1 destroyer in a system with your ships";
            List<Button> buttons = new ArrayList<>();
            if (p2 != player) {
                player.setCommodities(player.getCommodities() + 2);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + "you gained 2 comms");
                if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                }
            }
            buttons.add(Button.success("step2axisagent_cruiser", "Place a cruiser"));
            buttons.add(Button.success("step2axisagent_destroyer", "Place a destroyer"));
            MessageHelper.sendMessageToChannelWithButtons(channel, p2.getRepresentation(true, true) + message, buttons);
        }

        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1 && !"nomadagentmercer".equalsIgnoreCase(agent)) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent") && !exhaustedMessage.contains("choose the target of your agent")) {
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else {
            event.getMessage().delete().queue();
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(Button.success("exhaustTCS_" + agent + "_" + player.getFaction(), "Exhaust TCS to Ready " + agent));
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                String msg = p2.getRepresentation(true, true) + " you have the opportunity to exhaust your TCS tech to ready " + agent + " and potentially resolve a transaction.";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), msg, buttons2);
            }
        }
    }

    


    public static List<String> getExhaustedPlanetsOfASeperateTrait(Game activeGame, Player player, String planetOrig) {
        List<String> types = new ArrayList<>();
        List<String> planets = new ArrayList<>();
        if (planetOrig.contains("custodia") || planetOrig.contains("ghoti")) {
            return planets;
        }
        UnitHolder unitHolder2 = activeGame.getPlanetsInfo().get(planetOrig);
        Planet planetReal2 = (Planet) unitHolder2;
        boolean oneOfThree2 = planetReal2 != null && planetReal2.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal2.getOriginalPlanetType())
            || "cultural".equalsIgnoreCase(planetReal2.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal2.getOriginalPlanetType()));
        if (planetReal2 != null && oneOfThree2 && !types.contains(planetReal2.getOriginalPlanetType())) {
            types.add(planetReal2.getOriginalPlanetType());
        }
        if (unitHolder2.getTokenList().contains("attachment_titanspn.png")) {
            return planets;
        }
        if(types.size() == 0){
            return planets;
        }
        for (String planet : player.getExhaustedPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            if (unitHolder.getTokenList().contains("attachment_titanspn.png")) {
                continue;
            }
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (planetReal != null && oneOfThree && !types.contains(planetReal.getOriginalPlanetType())) {
                planets.add(planet);
            }
            
        }
        return planets;
    }


    

    public static boolean doesTileHaveAStructureInIt(Player player, Tile tile){
        boolean present = false;
        for(UnitHolder uH : tile.getUnitHolders().values()){
            if(uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0 || uH.getUnitCount(UnitType.Pds, player.getColor()) > 0){
                return true;
            }
            if(player.hasAbility("byssus") && uH instanceof Planet && uH.getUnitCount(UnitType.Mech, player.getColor()) > 0){
                return true;
            }
        }
        return present;
    }

    public static void resolveLanefirAgent(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID){
        if(buttonID.contains("Decline")){
            if(buttonID.contains("frontier")){
                String cardChosen = buttonID.split("_")[3];
                String pos = buttonID.split("_")[4];
                new ExpFrontier().expFrontAlreadyDone(event, activeGame.getTileByPosition(pos), activeGame, player, cardChosen);
            }else{
                String drawColor = buttonID.split("_")[2];
                String cardID = buttonID.split("_")[3];
                String planetName = buttonID.split("_")[4];
                Tile tile = activeGame.getTileFromPlanet(planetName);
                String messageText = player.getRepresentation() + " explored " +
                    Emojis.getEmojiFromDiscord(drawColor) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:\n" +
                    "> " + new ExpPlanet().displayExplore(cardID);
                new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText, false, player, activeGame);
                if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && activeGame.getCurrentPhase().contains("agenda")) {
                    new PlanetRefresh().doAction(player, planetName, activeGame);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been refreshed because of Florzen Commander");
                    ListVoteCount.turnOrder(event, activeGame, activeGame.getMainGameChannel());
                }
                if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                    UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                    activeGame.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added inf to planet because of Lanefir Commander");
                }
                if(player.hasTech("dslaner")){
                    player.setAtsCount(player.getAtsCount()+1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
                }
            }
        }else{
            exhaustAgent("exhaustAgent_lanefiragent", event, activeGame, player, ButtonHelper.getIdent(player));
            if(buttonID.contains("frontier")){
                String cardChosen = activeGame.drawExplore(Constants.FRONTIER);
                String pos = buttonID.split("_")[3];
                String card = Mapper.getExploreRepresentation(cardChosen);
                String[] cardInfo1 = card.split(";");
                String name1 = cardInfo1[0];
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Found a " + name1 + " in " + activeGame.getTileByPosition(pos).getRepresentation());
                new ExpFrontier().expFrontAlreadyDone(event, activeGame.getTileByPosition(pos), activeGame, player, cardChosen);
            }else{
                String drawColor = buttonID.split("_")[2];
                String planetName = buttonID.split("_")[3];
                Tile tile = activeGame.getTileFromPlanet(planetName);
                String cardID = activeGame.drawExplore(drawColor);
                String messageText = player.getRepresentation() + " explored " +
                    Emojis.getEmojiFromDiscord(drawColor) +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:\n" +
                    "> " + new ExpPlanet().displayExplore(cardID);
                new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText, false, player, activeGame);
                if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && activeGame.getCurrentPhase().contains("agenda")) {
                    new PlanetRefresh().doAction(player, planetName, activeGame);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Planet has been refreshed because of Florzen Commander");
                    ListVoteCount.turnOrder(event, activeGame, activeGame.getMainGameChannel());
                }
                if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                    UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                    activeGame.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Added inf to planet because of Lanefir Commander");
                }
                if(player.hasTech("dslaner")){
                    player.setAtsCount(player.getAtsCount()+1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " Put 1 commodity on ATS Armaments");
                }
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Tile> getAdjacentTilesWithStructuresInThem(Player player, Game activeGame, Tile origTile){
        List<Tile> tiles = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>();
        adjTiles.addAll(FoWHelper.getAdjacentTiles(activeGame, origTile.getPosition(), player, false));
        for(String posTile : adjTiles){
            Tile adjTile = activeGame.getTileByPosition(posTile);
            if(adjTile != null && doesTileHaveAStructureInIt(player, adjTile) && !AddCC.hasCC(player, origTile)){
                tiles.add(adjTile);
            }
        }
        return tiles;
    }

    public static List<String> getAttachments(Game activeGame, Player player){
        List<String> legendaries = new ArrayList<>();
        for(String planet : player.getPlanets()){
            if(planet.contains("custodia") || planet.contains("ghoti")){
                continue;
            }
            UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            for(String token : uh.getTokenList()){
                if(!token.contains("attachment")){
                    continue;
                }
                token = token.replace(".png","").replace("attachment_", "");
                
                String s = uh.getName()+"_"+token;
                if(!token.contains("sleeper") && !token.contains("control") && !legendaries.contains(s)){
                    legendaries.add(s);
                }
            }
        }
        return legendaries;
    }

     public static List<String> getAllControlledPlanetsInThisSystemAndAdjacent(Game activeGame, Player player, Tile tile){
        List<String> legendaries = new ArrayList<>();
        List<String> adjTiles = new ArrayList<>();
        adjTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(activeGame, tile.getPosition(), player, false));
        adjTiles.add(tile.getPosition());
        for(String adjTilePos : adjTiles){
            Tile adjTile = activeGame.getTileByPosition(adjTilePos);
            if(ButtonHelper.isTileHomeSystem(adjTile)){
                continue;
            }
            for(UnitHolder unitHolder : adjTile.getPlanetUnitHolders()){
                if(player.getPlanets().contains(unitHolder.getName())){
                    legendaries.add(unitHolder.getName());
                }
            }
        }
        return legendaries;
    }

    public static List<String> getAvailableLegendaryAbilities(Game activeGame){
        List<String> legendaries = new ArrayList<>();
        for(Player player : activeGame.getRealPlayers()){
            for(String planet : player.getPlanets()){
                if(planet.contains("custodia")){
                    continue;
                }
                PlanetModel model = Mapper.getPlanet(planet);
                if(model.getLegendaryAbilityText() != null && model.getLegendaryAbilityText().length() > 0){
                    legendaries.add(planet);
                }
            }
        }
        return legendaries;
    }

    public static void resolveCheiranAgentStep1(Player cheiran, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : getCheiranAgentTiles(player, activeGame)){
            buttons.add(Button.success("cheiranAgentStep2_"+tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
        }
        String msg = player.getRepresentation(true, true)+ " choose the tile you wish to remove a cc from";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
    }

    public static void resolveFreeSystemsAgentStep1(Player cheiran, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for(String planet : getAvailableLegendaryAbilities(activeGame)){
            buttons.add(Button.success("freeSystemsAgentStep2_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
        }
        String msg = player.getRepresentation(true, true)+ " choose the legendary planet ability that you wish to use";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);

        List<Button> buttons2 = Helper.getPlanetExhaustButtons(event, player, activeGame, "both");
        Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        buttons2.add(DoneExhausting);
        String msg2 = player.getRepresentation(true, true)+ " exhaust one planet to pay for the ability";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg2, buttons2);
    }

    public static void resolveFlorzenAgentStep1(Player cheiran, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for(String attachment : getAttachments(activeGame, player)){
            String planet = attachment.split("_")[0];
            String attach = attachment.split("_")[1];
            buttons.add(Button.success("florzenAgentStep2_"+attachment, attach + " on "+Helper.getPlanetRepresentation(planet, activeGame)));
        }
        String msg = player.getRepresentation(true, true)+ " choose the attachment you wish to use";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
    }

    public static void resolveFlorzenAgentStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String attachment = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for(String planet2 : getAllControlledPlanetsInThisSystemAndAdjacent(activeGame, player, activeGame.getTileFromPlanet(planet))){
            buttons.add(Button.success("florzenAgentStep3_"+planet+"_"+planet2+"_"+attachment, Helper.getPlanetRepresentation(planet2, activeGame)));
        }
        String msg = player.getRepresentation(true, true)+ " choose the adjacent planet you wish to put the attachment on";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveFlorzenAgentStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String planet2 = buttonID.split("_")[2];
        String attachment = buttonID.split("_")[3];
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
        UnitHolder uH2 = ButtonHelper.getUnitHolderFromPlanetName(planet2, activeGame);
        uH.removeToken("attachment_"+attachment+".png");
        uH2.addToken("attachment_"+attachment+".png");
        String msg = player.getRepresentation(true, true)+ " removed "+attachment+" from "+Helper.getPlanetRepresentation(planet, activeGame) + " and put it on "+Helper.getPlanetRepresentation(planet2, activeGame) + " using Florzen agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static void resolveFreeSystemsAgentStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        new PlanetExhaustAbility().doAction(player, planet, activeGame, false);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player)+" chose to use "+Helper.getPlanetRepresentation(planet, activeGame)+" ability. This did not exhaust the ability since it was done with free systems agent.");
        event.getMessage().delete().queue();
    }

    public static void resolveCheiranAgentStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = activeGame.getTileByPosition(pos);
        RemoveCC.removeCC(event, player.getColor(), origTile, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " removed a cc from "+origTile.getRepresentationForButtons(activeGame, player)+ " using Cheiran Agent");
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : getAdjacentTilesWithStructuresInThem(player, activeGame, origTile)){
            buttons.add(Button.success("cheiranAgentStep3_"+tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            if(getAdjacentTilesWithStructuresInThem(player, activeGame, origTile).size() == 1){
                resolveCheiranAgentStep3(player, activeGame, event, "cheiranAgentStep3_"+tile.getPosition());
                return;
            }
        }
        String msg = player.getRepresentation(true, true)+ " choose the tile you wish to place a cc in";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveCheiranAgentStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile origTile = activeGame.getTileByPosition(pos);
        AddCC.addCC(event, player.getColor(), origTile, true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " placed a cc in "+origTile.getRepresentationForButtons(activeGame, player)+ " using Cheiran Agent");
        event.getMessage().delete().queue();
    }




    public static List<Tile> getCheiranAgentTiles(Player player, Game activeGame){
        List<Tile> tiles = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            if(AddCC.hasCC(player, tile)){
                if(getAdjacentTilesWithStructuresInThem(player, activeGame, tile).size() > 0){
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    public static void ghotiAgentForTg(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        int cTG = player.getTg();
        int fTG = cTG + 1;
        player.setTg(fTG);
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        String msg = "Used Ghoti Agent to gain a tg (" + cTG + "->" + fTG + "). ";
        player.addSpentThing(msg);
        event.getMessage().delete().queue();
    }

    public static void ghotiAgentForProduction(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player) {
        String msg = "Used Ghoti Agent to gain the ability to produce 2 more units. ";
        player.addSpentThing(msg);
        event.getMessage().delete().queue();
    }

    public static void resolveVadenAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
         Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldComm = player.getCommodities();
        int count = 0;
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                if (planet.toLowerCase().contains("custodia") && 3 > count){
                    count = 3;
                }
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (p != null && p.getInfluence() > count) {
                count = p.getInfluence();
            }
        }
        int tgGain = count+player.getCommodities()-player.getCommoditiesTotal();
        int commGain = count-tgGain;
        player.setCommodities(player.getCommodities()+commGain);
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " max influence planet had "+count+" influence, so they gained "+commGain+" comms (" + oldComm + "->" + player.getCommodities() + ") due to Vaden agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode()&& bentor != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), msg);
        }
        if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1 && commGain > 0) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
        }
    }
    public static void resolveKortaliAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        int size = player.getFragments().size();
        int rand = ThreadLocalRandom.current().nextInt(size);
        String frag = player.getFragments().get(rand);
        player.removeFragment(frag);
        bentor.addFragment(frag);
        String[] cardInfo = Mapper.getExploreRepresentation(frag).split(";");
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " lost a "+cardInfo[1]+" frag to "+ButtonHelper.getIdentOrColor(bentor, activeGame)+" due to Kortali agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode()&& bentor != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), msg);
        }
    }

    public static void resolveZealotsAgentStep2(Player zealots, Game activeGame, ButtonInteractionEvent event, String buttonID) {
         Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        List<Tile> tiles = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Planet p = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            Tile tile = activeGame.getTileFromPlanet(p.getName());
            if (tile != null && !tiles.contains(tile) && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, activeGame) && (Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) || ButtonHelper.checkForTechSkipAttachments(activeGame, planet) || ButtonHelper.isTileHomeSystem(tile)) {
                buttons.add(Button.success("produceOneUnitInTile_" + tile.getPosition() + "_ZealotsAgent", tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " can produce a unit in their HS or in a system with a tech skip planet due to Zealots agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg, buttons);
        if (activeGame.isFoWMode() && zealots != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(zealots, activeGame), msg);
        }
    }

     public static void resolveNokarAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
         Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if(tile == null){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Could not find the active system");
            return;
        }
        if(!FoWHelper.playerHasShipsInSystem(player, tile)){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Player did not have a ship in the active system, no destroyer placed");
            return;
        }
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeGame);
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " place a destroyer in "+tile.getRepresentationForButtons(activeGame, player)+" due to Nokar agent. A transaction can be done with transaction buttons.";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode()  && bentor != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), msg);
        }
    }

     public static void resolveZelianAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
         Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if(tile == null){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Could not find the active system");
            return;
        }
        if(tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) < 1){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Player did not have an infantry in the active system, no mech placed");
            return;
        }
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 inf", activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 mech", activeGame);
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " replace an inf with a mech in "+tile.getRepresentationForButtons(activeGame, player)+" due to Zelian agent.";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode()  && bentor != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), msg);
        }

        if(event.getButton().getLabel().contains("Yourself")){
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
            event.getMessage().editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        }

    }

    public static void resolveKyroAgentStep2(Player kyro, Game activeGame, ButtonInteractionEvent event, String buttonID) {
         Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " replenished comms due to Kyro agent.";
        Player p2 = player;
        p2.setCommodities(p2.getCommoditiesTotal());
        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, p2, event);
        cabalAgentInitiation(activeGame, p2);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode() && kyro != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(kyro, activeGame), msg);
        }

        int infAmount = p2.getCommoditiesTotal()-1;
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, infAmount+"gf", "placeOneNDone_skipbuild"));
        String message = kyro.getRepresentation(true, true)+ "Use buttons to drop "+infAmount+" infantry on a planet";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(kyro, activeGame), message, buttons);

    }

    public static void resolveCeldauriAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        if (player.getCommodities() <2 && player.getTg()< 2) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Player did not have the money, please resolve manually.");
            return;
        }
        if(activeGame.getActivePlayerObject() != player){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Target player is not active player, please resolve manually.");
            return;
        }
        Tile tileAS = activeGame.getTileByPosition(activeGame.getActiveSystem());
        if (tileAS == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), "Active system is null, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String option = "";
        for(UnitHolder planet : tileAS.getPlanetUnitHolders()){
            if(player.getPlanetsAllianceMode().contains(planet.getName())){
                buttons.add(Button.success("celdauriAgentStep3_"+planet.getName(),"Place a SD on "+Helper.getPlanetRepresentation(planet.getName(), activeGame)));
                option = "celdauriAgentStep3_"+planet.getName();
            }
        }
        if(buttons.size() == 1){
            resolveCeldauriAgentStep3(player, activeGame, event, option);
            return;
        }



        String msg = player.getRepresentation(true, true)+ " choose the planet you wish to place an SD on";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCeldauriAgentStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = ButtonHelper.getIdent(player)+ " put a space dock on "+Helper.getPlanetRepresentation(planet, activeGame) +" using Celdauri Agent";
        new AddUnits().unitParsing(event,player.getColor(),activeGame.getTileFromPlanet(planet), "1 sd "+planet, activeGame);
        if(player.getCommodities() > 1){
            player.setCommodities(player.getCommodities()-2);
            msg = msg + "\n Paid 2 commodities";
        }else{
             player.setTg(player.getTg()-2);
            msg = msg + "\n Paid 2 tg";
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "saar", event);
        }
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        event.getMessage().delete().queue();
    }

    public static void resolveBentorAgentStep2(Player bentor, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player player = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(null, activeGame), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        int numOfBPs = bentor.getNumberOfBluePrints();
        int tgGain = numOfBPs+player.getCommodities()-player.getCommoditiesTotal();
        int commGain = numOfBPs-tgGain;
        player.setCommodities(player.getCommodities()+commGain);
        player.setTg(oldTg + tgGain);
        String msg = ButtonHelper.getIdentOrColor(player, activeGame) + " gained "+tgGain+"tg (" + oldTg + "->" + player.getTg() + ") and "+commGain+" commodities due to bentor agent";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg);
        if (activeGame.isFoWMode() && bentor != player) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(bentor, activeGame), msg);
        }
        if(tgGain > 0){
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, tgGain);
        }
        if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1 && commGain > 0) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
        }
        //event.getMessage().delete().queue();
    }


    public static void offerMoveGloryOptions(Game activeGame, Player player, ButtonInteractionEvent event){

        String msg = player.getRepresentation(true, true) +" use buttons to select system to move glory from";
        Tile tileAS = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : getGloryTokenTiles(activeGame)){
            buttons.add(Button.success("moveGlory_"+tile.getPosition()+"_"+tileAS.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }
    public static void moveGlory(Game activeGame, Player player, ButtonInteractionEvent event, String buttonID){
        
        Tile tileAS = activeGame.getTileByPosition(buttonID.split("_")[2]);
        Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        UnitHolder spaceAS = tileAS.getUnitHolders().get(Constants.SPACE);
        if (space.getTokenList().contains("token_ds_glory.png")) {
            space.removeToken("token_ds_glory.png");
        }
        spaceAS.addToken("token_ds_glory.png");
        String msg = ButtonHelper.getIdent(player) +" moved glory token from "+tile.getRepresentation() + " to "+tileAS.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        event.getMessage().delete().queue();

    }

    public static List<Tile> getGloryTokenTiles(Game activeGame){
        List<Tile> gloryTiles = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            if(tile.getUnitHolders().get("space").getTokenList().contains("token_ds_glory.png")){
                gloryTiles.add(tile);
            }
        }
        return gloryTiles;
    }

    public static List<Button> getSardakkAgentButtons(Game activeGame, Player player) {

        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (player.getPlanetsAllianceMode().contains(planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_sardakkagent_" + activeGame.getActiveSystem() + "_" + planetId, "Use Sardakk Agent on " + planetRepresentation)
                    .withEmoji(Emoji.fromFormatted(Emojis.Sardakk)));
            }
        }

        return buttons;

    }

    public static List<Button> getMercerAgentInitialButtons(Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (player.getPlanetsAllianceMode().contains(planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_nomadagentmercer_" + activeGame.getActiveSystem() + "_" + planetId, "Use Nomad Agent General Mercer on " + planetRepresentation)
                    .withEmoji(Emoji.fromFormatted(Emojis.Nomad)));
            }
        }

        return buttons;
    }

    public static List<Button> getL1Z1XAgentButtons(Game activeGame, Player player) {
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planet = planetReal.getName();
            if (player.getPlanetsAllianceMode().contains(planet) && FoWHelper.playerHasInfantryOnPlanet(player, tile, planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_l1z1xagent_" + activeGame.getActiveSystem() + "_" + planetId, "Use L1Z1X Agent on " + planetRepresentation)
                    .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
            }
        }

        return buttons;

    }

    public static void arboAgentPutShip(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String unitNPlace = buttonID.replace("arboAgentPutShip_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage = "";

        switch (unit) {
            case "destroyer" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "destroyer", activeGame);
                successMessage = ident + " Placed 1 " + Emojis.destroyer + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "cruiser" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
                successMessage = ident + " Placed 1 " + Emojis.cruiser + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "carrier" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "carrier", activeGame);
                successMessage = ident + " Placed 1 " + Emojis.carrier + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "dreadnought" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "dreadnought", activeGame);
                successMessage = ident + " Placed 1 " + Emojis.dreadnought + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getYinAgentButtons(Player player, Game activeGame, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = activeGame.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp, "Place 2 Fighters");
        ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
        buttons.add(ff2Button);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                String pp = planet.getName();
                Button inf2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp, "Place 2 Infantry on " + Helper.getPlanetRepresentation(pp, activeGame));
                inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                buttons.add(inf2Button);
            }
        }
        return buttons;
    }

    public static void resolveStep2OfAxisAgent(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String message;
        if ("cruiser".equalsIgnoreCase(buttonID.split("_")[1])) {
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " Chose to place 1 cruiser with their ships from Axis Agent");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 cruiser with your ships";
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " Chose to place 1 destroyer with their ships from Axis Agent");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 destroyer with your ships";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentation(true, true) + message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveArtunoCheck(Player player, Game activeGame, int tg) {
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("exhaustAgent_nomadagentartuno_" + tg, "Exhaust Artuno with " + tg + " tg"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                player.getRepresentation(true, true) + " you have the opportunity to exhaust your agent Artuno and place " + tg + " tg on her.", buttons);
        }
    }

    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeGame, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to select faction to give agent to.", buttons);
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

    public static List<Button> getJolNarAgentButtons(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    buttons
                        .add(Button.success("jolNarAgentRemoval_" + tile.getPosition() + "_" + unitHolder.getName(), "Remove Inf from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, activeGame)));
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Done"));
        return buttons;
    }

    public static void resolveJolNarAgentRemoval(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            ButtonHelper.getIdent(player) + " removed 1 infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, activeGame) + " using Jol Nar agent");
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + unitHName, activeGame);
        if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

}