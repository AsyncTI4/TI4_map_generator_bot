package ti4.helpers;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import java.util.*;

import ti4.commands.cardsac.ACInfo;
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
                String msg = ButtonHelper.getTrueIdentity(cabal, activeGame) + " you have the ability to use cabal agent on " + ButtonHelper.getIdentOrColor(p2, activeGame) + " who has "
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
        String msg = ButtonHelper.getTrueIdentity(cabal, activeGame) + " use buttons to capture a ship";
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
            ButtonHelper.getTrueIdentity(p2, activeGame) + " a " + unit + " of yours has been captured by a cabal agent. Any comms you had have been washed.");
        p2.setTg(p2.getTg() + p2.getCommodities());
        p2.setCommodities(0);
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, activeGame, player, 1, unit, event);
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
                    if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(finChecker + "arboAgentOn_" + tile.getPosition() + "_" + unitName, "Remove " + x + " " + prettyName);
                    if (emoji != null) validTile2 = validTile2.withEmoji(emoji);
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

        List<String> allowedUnits = List.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun)
            .stream().map(UnitType::getValue).toList();
        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + 2) {
                String buttonID = finChecker + "arboAgentPutShip_" + ownedUnit.getBaseType()+ "_" + tile.getPosition();
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
            message = "Refreshed " + p2.getColor() + "'s commodities";
        }
        if (p2.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame).size() > 1) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to buy axis orders",
                ButtonHelperAbilities.getBuyableAxisOrders(p2, activeGame));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String planetDestination = buttonID.split("_")[1];
        String planetRemoval = buttonID.split("_")[2];
        String unit = buttonID.split("_")[3];
        new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planetRemoval), unit + " " + planetRemoval, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet(planetDestination), unit + " " + planetDestination, activeGame);

        String message = ident + " moved 1 " + unit + " from " + Helper.getPlanetRepresentation(planetRemoval, activeGame) + " to " + Helper.getPlanetRepresentation(planetDestination, activeGame);
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
            ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to place ground forces via argent agent", unitButtons);
    }

    public static void exhaustAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        String agent = buttonID.replace("exhaustAgent_", "");
        String rest = agent;
        String trueIdentity = ButtonHelper.getTrueIdentity(player, activeGame);
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

            messageText = messageText + "\n" + rest.split("_")[1] + " " + Emojis.tg + " was placed on top of the leader";
            if(activeGame.getNomadCoin()){
                messageText = messageText.replace(Emojis.tg, Emojis.nomadcoin);
            }
        }
        MessageHelper.sendMessageToChannel(channel2, messageText);
        if ("naazagent".equalsIgnoreCase(agent)) {
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        }
        if ("cabalagent".equalsIgnoreCase(agent)) {
            ButtonHelperAgents.startCabalAgent(player, activeGame, rest.replace("cabalagent_", ""), event);
        }
        if ("jolnaragent".equalsIgnoreCase(agent)) {
            String msg = ButtonHelper.getTrueIdentity(player, activeGame) + " you can use the buttons to remove infantry.";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg, ButtonHelperAgents.getJolNarAgentButtons(player, activeGame));
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

        //TODO: Allow choosing someone else for this agent
        if ("nekroagent".equalsIgnoreCase(agent)) {
            player.setTg(player.getTg() + 2);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            resolveArtunoCheck(player, activeGame, 2);
            String message = trueIdentity + " increased your tgs by 2 (" + (player.getTg() - 2) + "->" + player.getTg() + "). Use buttons in your cards info thread to discard an AC";
            MessageHelper.sendMessageToChannel(channel2, message);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), trueIdentity + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
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
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + message, ringButtons);
        }

        if ("yinagent".equalsIgnoreCase(agent)) {
            String posNFaction = rest.replace("yinagent_", "");
            String pos = posNFaction.split("_")[0];
            String faction = posNFaction.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + " Use buttons to resolve yin agent",
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
            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true)
                + " Use buttons to resolve tactical action from Naalu agent. Reminder it is not legal to do a tactical action in a home system.\n" + message, ringButtons);
        }

        if ("mentakagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("mentakagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            String successMessage = ident + " drew an AC.";
            String successMessage2 = ButtonHelper.getIdent(p2) + " drew an AC.";
            activeGame.drawActionCard(player.getUserID());
            activeGame.drawActionCard(p2.getUserID());
            if (player.hasAbility("scheming")) {
                activeGame.drawActionCard(player.getUserID());
                successMessage += " Drew another AC for scheming. Please discard 1";
            }
            if (p2.hasAbility("scheming")) {
                activeGame.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1";

            }
            ButtonHelper.checkACLimit(activeGame, event, player);
            ButtonHelper.checkACLimit(activeGame, event, p2);
            String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
            ACInfo.sendActionCardInfo(activeGame, player);
            String headerText2 = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, activeGame, headerText2);
            ACInfo.sendActionCardInfo(activeGame, p2);
            if (p2.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), ButtonHelper.getTrueIdentity(p2, activeGame) + " use buttons to discard",
                    ACInfo.getDiscardActionCardButtons(activeGame, p2, false));
            }
            if (player.hasAbility("scheming")) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
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
            List<Button> buttons = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                if (planet.equals(planetName) || planet.toLowerCase().contains("custodiavigilia")) {
                    continue;
                }
                if (ButtonHelper.getNumberOfInfantryOnPlanet(planet, activeGame, player) > 0) {
                    buttons.add(Button.success("mercerMove_" + planetName + "_" + planet + "_infantry",
                        "Move Infantry from " + Helper.getPlanetRepresentation(planet, activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                }
                if (ButtonHelper.getNumberOfMechsOnPlanet(planet, activeGame, player) > 0) {
                    buttons.add(Button.success("mercerMove_" + planetName + "_" + planet + "_mech",
                        "Move mech from " + Helper.getPlanetRepresentation(planet, activeGame) + " to " + Helper.getPlanetRepresentation(planetName, activeGame)));
                }
            }
            buttons.add(Button.danger("deleteButtons", "Done moving to this planet"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to resolve move of mercer units to this planet", buttons);
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
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Use buttons to select which tile to Umbat in";
            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, p2, UnitType.Warsun, UnitType.Flagship);
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : tiles) {
                Button starTile = Button.success("umbatTile_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, p2));
                buttons.add(starTile);
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + message, buttons);
        }

        if ("arborecagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("arborecagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Use buttons to select which tile to use arborec agent in";
            List<Button> buttons = getTilesToArboAgent(p2, activeGame, event);
            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + message, buttons);
        }
        if ("axisagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("axisagent_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            if (p2 == null) return;
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Use buttons to select whether you want to place 1 cruiser or 1 destroyer in a system with your ships";
            List<Button> buttons = new ArrayList<>();
            if (p2 != player) {
                player.setCommodities(player.getCommodities() + 2);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + "you gained 2 comms");
                if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                }
            }
            buttons.add(Button.success("step2axisagent_cruiser", "Place a cruiser"));
            buttons.add(Button.success("step2axisagent_destroyer", "Place a destroyer"));
            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + message, buttons);
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
        if (actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")) {
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else {
            event.getMessage().delete().queue();
        }
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.hasTech("tcs") && !p2.getExhaustedTechs().contains("tcs")) {
                List<Button> buttons2 = new ArrayList<Button>();
                buttons2.add(Button.success("exhaustTCS_" + agent + "_" + player.getFaction(), "Exhaust TCS to Ready " + agent));
                buttons2.add(Button.danger("deleteButtons", "Decline"));
                String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " you have the opportunity to exhaust your TCS tech to ready " + agent + " and potentially resolve a transaction.";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), msg, buttons2);
            }
        }
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
        String message = "";
        if ("cruiser".equalsIgnoreCase(buttonID.split("_")[1])) {
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " Chose to place 1 cruiser with their ships from Axis Agent");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 cruiser with your ships";
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + " Chose to place 1 destroyer with their ships from Axis Agent");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
            message = " Use buttons to put 1 destroyer with your ships";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveArtunoCheck(Player player, Game activeGame, int tg) {
        if (player.hasUnexhaustedLeader("nomadagentartuno")) {
            List<Button> buttons = new ArrayList<Button>();
            buttons.add(Button.success("exhaustAgent_nomadagentartuno_" + tg, "Exhaust Artuno with " + tg + " tg"));
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to exhaust your agent Artuno and place " + tg + " tg on her.", buttons);
        }
    }

    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity) {
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeGame, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to select faction to give agent to.", buttons);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if (exhaustedMessage == null || exhaustedMessage.length() < 1) {
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
        List<Button> buttons = new ArrayList<Button>();
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
        if (unitHName.equalsIgnoreCase("space")) {
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