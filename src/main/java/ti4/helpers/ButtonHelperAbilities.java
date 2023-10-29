package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

import ti4.commands.explore.ExpPlanet;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.special.SleeperToken;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperAbilities {

    public static void pillage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finsFactionCheckerPrefix) {
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = activeGame.getPlayerFromColorOrFaction(colorPlayer);
        if (pillaged == null) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Could not find player, please resolve manually.");
            return;
        }
        if (checkedStatus.contains("unchecked")) {
            List<Button> buttons = new ArrayList<>();
            String message2 = "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checked", "Pillage a TG"));
            if (pillaged.getCommodities() > 0) {
                buttons.add(Button.danger(finsFactionCheckerPrefix + "pillage_" + pillaged.getColor() + "_checkedcomm", "Pillage a Commodity"));
            }
            buttons.add(Button.success(finsFactionCheckerPrefix + "deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        } else {
            MessageChannel channel1 = ButtonHelper.getCorrectChannel(pillaged, activeGame);
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
            String pillagerMessage = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " you pillaged, your tgs have gone from " + player.getTg() + " to "
                + (player.getTg() + 1) + ".";
            String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeGame, activeGame.getGuild(), true) + " you have been pillaged";

            if (pillaged.getCommodities() > 0 && checkedStatus.contains("checkedcomm")) {
                pillagedMessage = pillagedMessage + ", your comms have gone from " + pillaged.getCommodities() + " to " + (pillaged.getCommodities() - 1) + ".";
                pillaged.setCommodities(pillaged.getCommodities() - 1);
            } else {
                pillagedMessage = pillagedMessage + ", your tgs have gone from " + pillaged.getTg() + " to " + (pillaged.getTg() - 1) + ".";
                pillaged.setTg(pillaged.getTg() - 1);
            }
            player.setTg(player.getTg() + 1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasUnexhaustedLeader("mentakagent")) {
                List<Button> buttons = new ArrayList<>();
                Button winnuButton = Button.success("exhaustAgent_mentakagent_" + pillaged.getFaction(), "Use Mentak Agent To Draw ACs for you and pillaged player")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mentak));
                buttons.add(winnuButton);
                buttons.add(Button.danger("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Wanna use Mentak Agent?", buttons);
            }
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getMitosisOptions(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("mitosisInf", "Place an Infantry"));
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4) {
            buttons.add(Button.primary("mitosisMech", "Replace an Infantry With a Mech (DEPLOY)"));
        }
        return buttons;
    }

    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "infantry", "placeOneNDone_skipbuild"));
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " Use buttons to put 1 infantry on a planet";

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finChecker) {
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        String message = ButtonHelper.getTrueIdentity(player, activeGame) + " Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game activeGame, String finChecker) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        List<String> tiles = new ArrayList<>();
        for (String planet : planets) {
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
            if (tiles.contains(tile.getPosition())) {
                continue;
            } else {
                tiles.add(tile.getPosition());
            }
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if ("space".equalsIgnoreCase(unitHolder.getName())) {
                    continue;
                }
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = 0;
                if (unitHolder.getUnits() != null) {
                    numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);
                }
                if (numInf > 0) {
                    String buttonID = finChecker + "mitoMechPlacement_" + unitHolder.getName().toLowerCase().replace("'", "").replace("-", "").replace(" ", "");
                    Button button = Button.success(buttonID, "Place mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame));
                    planetButtons.add(button);
                }
            }
        }
        return planetButtons;
    }

    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = ident + " put a sleeper on " + Helper.getPlanetRepresentation(planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropFollowUp(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success("dropAMechToo", "Spend 3 resource to Drop a Mech Too");
        startButtons.add(tacticalAction);
        Button componentAction = Button.danger("finishComponentAction_spitItOut", "Decline Mech");
        startButtons.add(componentAction);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Decide whether to drop mech",
            startButtons);
        event.getMessage().delete().queue();
    }

    public static void oribtalDropExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        Button DoneExhausting = Button.danger("finishComponentAction", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getMantleCrackingButtons(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<Button>();
        int coreCount = 0;
        for (String planetName : player.getPlanetsAllianceMode()) {
            if (planetName.contains("custodia")|| planetName.contains("ghoti")) {
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
            if (planet == null) {
                continue;
            }
            Tile tile = activeGame.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            if (!planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG) && !planetName.equalsIgnoreCase("mr") && !ButtonHelper.isTileHomeSystem(tile)) {
                buttons.add(Button.secondary("mantleCrack_" + planetName, Helper.getPlanetRepresentation(planetName, activeGame)));
            } else {
                if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                    coreCount = coreCount + 1;
                }
            }
        }
        if (coreCount > 2) {
            return new ArrayList<Button>();
        }
        return buttons;
    }

    public static void resolveAxisOrderExhaust(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String order = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " Chose to Use " + Mapper.getRelic(order).getName());
        List<Button> buttons = new ArrayList<>();
        String message = "";
        String techName = "";
        boolean hasTech = false;
        if (Mapper.getRelic(order).getName().contains("Dreadnought")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "dreadnought", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 dreadnought in a system with your ships and cc";
            techName = "dn2";
        }
        if (Mapper.getRelic(order).getName().contains("Carrier")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "carrier", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 carrier in a system with your ships and cc";
            techName = "cv2";
        }
        if (Mapper.getRelic(order).getName().contains("Cruiser")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 1 cruiser in a system with your ships and cc";
            techName = "cr2";
        }
        if (Mapper.getRelic(order).getName().contains("Destroyer")) {
            buttons.addAll(Helper.getTileWithShipsNTokenPlaceUnitButtons(player, activeGame, "2destroyer", "placeOneNDone_skipbuild", event));
            message = "Use buttons to put 2 destroyers in a system with your ships and cc";
            techName = "dd2";
        }        
        message = ButtonHelper.getTrueIdentity(player, activeGame) + " " + message;
        ButtonHelper.deleteTheOneButton(event);
        player.addExhaustedRelic(order);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "axiscommander") && !p2.hasTech(techName)) {
                activeGame.setComponentAction(true);
                Button getTech = Button.success("acquireATech", "Get a tech");
                List<Button> buttons2 = new ArrayList<>();
                buttons2.add(getTech);
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)
                    + " a player has resolved an Axis Order (" + Mapper.getRelic(order).getName() + ") and you can use the button to gain the corresponding unit upgrade tech if you pay 6r", buttons2);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }

    public static List<Button> getBuyableAxisOrders(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<Button>();
        int maxCost = player.getCommodities();

        String relicName = "axisorderdd";
        String extra = "duplicate";
        int orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercr";
        orderCost = 1;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisordercv";
        orderCost = 2;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        relicName = "axisorderdn";
        orderCost = 3;
        if (orderCost < maxCost + 1) {
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
            relicName = relicName + extra;
            if (!ButtonHelperFactionSpecific.somebodyHasThisRelic(activeGame, relicName)) {
                buttons.add(Button.secondary("buyAxisOrder_" + relicName + "_" + orderCost, "Buy an " + Mapper.getRelic(relicName).getName() + " for " + orderCost + " comms"));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete these buttons"));

        return buttons;
    }

    public static void resolveAxisOrderBuy(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String relicName = buttonID.split("_")[1];
        String cost = buttonID.split("_")[2];
        int lostComms = Integer.parseInt(cost);
        int oldComms = player.getCommodities();
        if (lostComms > oldComms) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " you don't have that many comms");
            return;
        }
        player.addRelic(relicName);
        player.setCommodities(oldComms - lostComms);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " acquired " + Mapper.getRelic(relicName).getName()
            + " and paid " + lostComms + " commodities (" + oldComms + "->" + player.getCommodities() + ")");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void pillageCheck(Player player, Game activeGame) {
        if (player.getPromissoryNotesInPlayArea().contains("pop")) {
            return;
        }
        if (Helper.getPlayerFromAbility(activeGame, "pillage") != null && !Helper.getPlayerFromAbility(activeGame, "pillage").getFaction().equalsIgnoreCase(player.getFaction())) {

            Player pillager = Helper.getPlayerFromAbility(activeGame, "pillage");
            String finChecker = "FFCC_" + pillager.getFaction() + "_";
            if (player.getTg() > 2 && player.getNeighbouringPlayers().contains(pillager)) {
                List<Button> buttons = new ArrayList<>();
                String playerIdent = StringUtils.capitalize(player.getFaction());
                MessageChannel channel = activeGame.getMainGameChannel();
                if (activeGame.isFoWMode()) {
                    playerIdent = StringUtils.capitalize(player.getColor());
                    channel = pillager.getPrivateChannel();
                }
                String message = Helper.getPlayerRepresentation(pillager, activeGame, activeGame.getGuild(), true) + " you may have the opportunity to pillage " + playerIdent
                    + ". Please check this is a valid pillage opportunity, and use buttons to resolve.";
                buttons.add(Button.danger(finChecker + "pillage_" + player.getColor() + "_unchecked", "Pillage " + playerIdent));
                buttons.add(Button.success(finChecker + "deleteButtons", "Decline Pillage Window"));
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
        }
    }

    public static void mantleCracking(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.split("_")[1];
        int oldTg = player.getTg();
        player.setTg(oldTg + 4);
        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
        planet.addToken(Constants.GLEDGE_CORE_PNG);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdentOrColor(player, activeGame) + "cracked the mantle of "
            + Helper.getPlanetRepresentation(planetName, activeGame) + " and gained 4tg (" + oldTg + "->" + player.getTg() + "). This is technically an optional gain");
        pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 4);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Game activeGame) {
        List<Button> techToGain = new ArrayList<>();
        for (String tech : currentList) {
            techToGain.add(Button.success("getTech_" + Mapper.getTech(tech).getName() + "_noPay", Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }

    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim, List<String> currentList, Game activeGame) {
        List<String> techToGain = new ArrayList<>(currentList);
        for (String tech : victim.getTechs()) {
            if (!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)) {
                techToGain.add(tech);
            }
        }
        return techToGain;
    }

    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = ident + " removed a sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }

    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planetName, player);
        if ("mech".equalsIgnoreCase(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a " + Emojis.mech + " and "
                + Emojis.infantry;
        } else {
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a " + Emojis.pds;
            if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void giveKeleresCommsNTg(Game activeGame, GenericInteractionCreateEvent event) {
        for (Player player : activeGame.getRealPlayers()) {
            if (!player.hasAbility("council_patronage")) continue;
            MessageChannel channel = activeGame.getActionsChannel();
            if (activeGame.isFoWMode()) {
                channel = player.getPrivateChannel();
            }
            StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true));
            if(activeGame.getNomadCoin()){
                sb.append(" your **Council Patronage** ability was triggered. Your ").append(Emojis.comm).append(" commodities have been replenished and you have gained 1 ").append(Emojis.nomadcoin).append(" trade good (").append(player.getTg()).append(" -> ").append((player.getTg() + 1)).append(")");

            }else{
                sb.append(" your **Council Patronage** ability was triggered. Your ").append(Emojis.comm).append(" commodities have been replenished and you have gained 1 ").append(Emojis.tg).append(" trade good (").append(player.getTg()).append(" -> ").append((player.getTg() + 1)).append(")");
            }
            player.setTg(player.getTg() + 1);
            player.setCommodities(player.getCommoditiesTotal());
            MessageHelper.sendMessageToChannel(channel, sb.toString());
            pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
            ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
        }
    }

    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String pos = buttonID.replace("starforgeTile_", "");
        List<Button> buttons = new ArrayList<>();
        Button starforgerStroter = Button.danger("starforge_destroyer_" + pos, "Starforge Destroyer")
            .withEmoji(Emoji.fromFormatted(Emojis.destroyer));
        buttons.add(starforgerStroter);
        Button starforgerFighters = Button.danger("starforge_fighters_" + pos, "Starforge 2 Fighters")
            .withEmoji(Emoji.fromFormatted(Emojis.fighter));
        buttons.add(starforgerFighters);
        String message = "Use the buttons to select what you would like to starforge.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void starforge(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
            + (player.getStrategicCC() - 1) + ")";
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
        if ("destroyer".equals(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeGame);
            successMessage = "Produced 1 " + Emojis.destroyer + " in tile "
                + tile.getRepresentationForButtons(activeGame, player) + ".";

        } else {
            new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeGame);
            successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                + tile.getRepresentationForButtons(activeGame, player) + ".";
        }
        if (!activeGame.getLaws().containsKey("articles_war")) {
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeGame, player, event);
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String planetName = buttonID.replace("mitoMechPlacement_", "");
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, activeGame);
        new RemoveUnits().removeStuff(event, activeGame.getTile(AliasHandler.resolveTile(planetName)), 1, planetName, key, player.getColor(), false, activeGame);
        String successMessage = ident + " Replaced an infantry with a mech on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        event.getMessage().delete().queue();
    }

    public static List<Button> getXxchaPeaceAccordsButtons(Game activeGame, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            Tile tile = activeGame.getTileFromPlanet(planet);
            for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
                Tile tile2 = activeGame.getTileByPosition(pos2);
                for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                    if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                        continue;
                    }
                    Planet planetReal2 = (Planet) planetUnit2;
                    String planet2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                    if (!player.getPlanetsAllianceMode().contains(planet2) && !planetRepresentation2.contains("Mecatol") && (planetReal2.getUnits() == null || planetReal2.getUnits().isEmpty())
                        && !planetsChecked.contains(planet2)) {
                        buttons.add(Button.success(finChecker + "peaceAccords_" + planet2, "Use peace accords to take control of " + planetRepresentation2)
                            .withEmoji(Emoji.fromFormatted(Emojis.Xxcha)));
                        planetsChecked.add(planet2);
                    }
                }
            }
        }
        return buttons;
    }

    public static void resolvePeaceAccords(String buttonID, String ident, Player player, Game activeGame, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        new PlanetAdd().doAction(player, planet, activeGame, event);
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet, activeGame);
        String msg = ident + " claimed the planet " + planetRepresentation2 + " using the peace accords ability";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }

    public static void resolveInitialIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Button.success("indoctrinate_" + planet + "_infantry", "Indoctrinate to place an infantry"));
        options.add(Button.success("indoctrinate_" + planet + "_mech", "Indoctrinate to place a mech"));
        options.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to resolve indoctrination", options);
    }

    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = activeGame.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, activeGame);
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)) {
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry " + planet, activeGame);
            }
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        if (player.getLeaderIDs().contains("yincommander") && !player.hasLeaderUnlocked("yincommander")) {
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yin", event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " replaced 1 of their opponent's infantry with 1 " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame) + " using indoctrination");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " pay for indoctrination.", options);
        event.getMessage().delete().queue();
    }

    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = "Rejected Distant Suns Ability";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 1, false);
        } else {
            message = "Exploring twice";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2],
                player, true, activeGame, 2, false);
        }
        
        event.getMessage().delete().queue();
    }

}