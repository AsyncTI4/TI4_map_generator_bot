package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.combat.StartCombat;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.ClearDebt;
import ti4.commands.player.SendDebt;
import ti4.commands.player.TurnStart;
import ti4.commands.tech.TechExhaust;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
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
import ti4.model.UnitModel;

public class ButtonHelperFactionSpecific {

    public static void resolveVadenTgForSpeed(Player player, Game game, GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " is paying TGs to boost their non-fighter ships.");
        List<Button> buttons = new ArrayList<>();
        String whatIsItFor = "both";
        if (player.getTg() > 0) {
            Button lost1TG = Button.danger("reduceTG_1_" + whatIsItFor, "Spend 1TG");
            buttons.add(lost1TG);
        }
        if (player.getTg() > 1) {
            Button lost2TG = Button.danger("reduceTG_2_" + whatIsItFor, "Spend 2TGs");
            buttons.add(lost2TG);
        }
        if (player.getTg() > 2) {
            Button lost3TG = Button.danger("reduceTG_3_" + whatIsItFor, "Spend 3TGs");
            buttons.add(lost3TG);
        }
        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 0) {
            Button lost1C = Button.danger("reduceComm_1_" + whatIsItFor, "Spend 1 commodity");
            buttons.add(lost1C);
        }

        if (player.hasUnexhaustedLeader("keleresagent") && player.getCommodities() > 1) {
            Button lost2C = Button.danger("reduceComm_2_" + whatIsItFor, "Spend 2 commodities");
            buttons.add(lost2C);
        }

        buttons.add(Button.secondary("resetSpend_" + whatIsItFor, "Reset Spent Planets and TGs"));
        Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            "Click how many TGs you want to spend", buttons);
        TechExhaust.deleteTheOneButtonIfButtonEvent(event);
    }

    public static void resolveVadenMech(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String opposingFaction = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planet, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 infantry at the end of a round of ground combat on "
                + Helper.getPlanetRepresentation(planet, game) + " using Vaden Mech ability. This consumed 1 debt counter of the opposing factions color");
        player.removeDebtTokens(game.getPlayerFromColorOrFaction(opposingFaction).getColor(), 1);
    }

    public static void collateralizedLoans(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(pos);
        ClearDebt.clearDebt(player, p2, 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " used collateralized loans ability to forgive 1 debt of their opponent to place 1 ship of a type that their opponent just lost");
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentation() + " one debt of yours was forgiven via the collateralized loans ability");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "sling",
            "placeOneNDone_skipbuild");
        String message = player.getRepresentation() + " Use the buttons to place 1 unit that was destroyed. ";

        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    public static void gheminaMechStart(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game), player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " due to your mech ability, you may explore " + Helper.getPlanetRepresentation(planet, game) + " twice now.");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Explore #1", buttons);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " Explore #2", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveEdynAgendaStuffStep1(Player player, Game game, List<Tile> tiles) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : tiles) {
            buttons.add(Button.secondary("edynAgendaStuffStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " Tell the bot where you want to place someone's CC", buttons);
    }

    public static void resolveEdynAgendaStuffStep2(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        List<Button> buttons = new ArrayList<>();
        String pos = buttonID.split("_")[1];
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("edynAgendaStuffStep3_" + p2.getFaction() + "_" + pos, p2.getColor()));
            } else {
                Button button = Button.secondary("edynAgendaStuffStep3_" + p2.getFaction() + "_" + pos, " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " tell the bot who's CC you want to place down",
            buttons);
    }

    public static void resolveEdynAgendaStuffStep3(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        AddCC.addCC(event, p2.getColor(), tile);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you placed " + ButtonHelper.getIdentOrColor(p2, game)
                + " CC in tile: " + tile.getRepresentationForButtons(game, player));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " you were signal jammed in tile: "
                + tile.getRepresentationForButtons(game, p2));

    }

    public static void resolveCavStep1(Game game, Player player) {
        String msg = player.getRepresentation() + " choose the non-fighter ship you wish to use the cav on";
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());

        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!(player.unitBelongsToPlayer(unitEntry.getKey())))
                continue;

            UnitKey unitKey = unitEntry.getKey();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
            // System.out.println(unitKey.asyncID());
            int totalUnits = unitEntry.getValue();
            int damagedUnits = 0;
            if ("fighter".equalsIgnoreCase(unitName)) {
                continue;
            }

            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
            if (damagedUnits > 0) {
                String buttonID = "cavStep2_" + unitName + "damaged";
                String buttonText = "Damaged " + unitKey.unitName();
                Button validTile2 = Button.danger(buttonID, buttonText);
                validTile2 = validTile2.withEmoji(emoji);
                buttons.add(validTile2);
            }

            totalUnits = totalUnits - damagedUnits;
            if (totalUnits > 0) {
                String buttonID = "cavStep2_" + unitName;
                String buttonText = unitKey.unitName();
                Button validTile2 = Button.danger(buttonID, buttonText);
                validTile2 = validTile2.withEmoji(emoji);
                buttons.add(validTile2);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    public static void resolveCavStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String unit = buttonID.split("_")[1];
        boolean damaged = false;
        if (unit.contains("damaged")) {
            damaged = true;
            unit = unit.replace("damaged", "");
        }
        String msg = player.getFactionEmoji() + " choose a " + unit
            + " to use the cav on. It has been temporarily replaced with a Cavalry Unit. It will be automatically put back at the end of the tactical action.";
        game.setStoredValue("nomadPNShip", unit);
        String cav = "cavalry1";
        if (game.getPNOwner("cavalry").hasTech("m2")) {
            cav = "cavalry2";
        }
        event.getMessage().delete().queue();
        Tile tile = game.getTileByPosition(game.getActiveSystem());

        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        player.addOwnedUnitByID(cav);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, unit, game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "cavalry", game);
        if (damaged) {
            unitHolder.removeUnitDamage(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()), 1);
            unitHolder.addUnitDamage(Mapper.getUnitKey("cavalry", player.getColorID()), 1);
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void cleanCavUp(Game game, GenericInteractionCreateEvent event) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasUnit("cavalry1") || player.hasUnit("cavalry2")) {
                String cav = "cavalry1";
                String unit = game.getStoredValue("nomadPNShip");
                if (player.hasUnit("cavalry2")) {
                    cav = "cavalry2";
                }
                for (Tile tile : game.getTileMap().values()) {
                    UnitHolder unitHolder = tile.getUnitHolders().get("space");
                    if (unitHolder.getUnitCount(UnitType.Cavalry, player.getColor()) > 0) {
                        if (unitHolder.getUnitDamage() != null
                            && unitHolder.getUnitDamage()
                                .get(Mapper.getUnitKey("cavalry", player.getColorID())) != null
                            && unitHolder.getUnitDamage()
                                .get(Mapper.getUnitKey("cavalry", player.getColorID())) > 0) {
                            unitHolder.addUnitDamage(
                                Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()), 1);
                            unitHolder.removeUnitDamage(Mapper.getUnitKey("cavalry", player.getColorID()), 1);
                        }
                        new RemoveUnits().unitParsing(event, player.getColor(), tile, "cavalry", game);
                        new AddUnits().unitParsing(event, player.getColor(), tile, unit, game);

                    }
                }
                player.removeOwnedUnitByID(cav);
                game.setStoredValue("nomadPNShip", "");
            }
        }
    }

    public static boolean doesAnyoneElseHaveJr(Game game, Player player) {
        for (Player p1 : game.getRealPlayers()) {
            if (p1 == player) {
                continue;
            }
            if (p1.hasRelic("titanprototype") || p1.hasRelic("absol_jr")) {
                return true;
            }
        }
        return false;
    }

    public static void yssarilAgentAsJr(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons2 = AgendaHelper.getPlayerOutcomeButtons(game, null, "jrResolution", null);
        player.getLeader("yssarilagent").get().setExhausted(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " is using Clever Clever JR-XS455-O, the Relic/Yssaril agent.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Use buttons to decide on whom to use Clever Clever JR-XS455-O, the Relic/Yssaril agent.", buttons2);
        event.getMessage().delete().queue();
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }

    public static void handleTitansConstructionMechDeployStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") > 3) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation(true, true) + " you have all your mechs out and can't deploy more.");
            return;
        }
        for (String planet : player.getPlanets()) {
            buttons.add(Button.success("titansConstructionMechDeployStep2_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        String msg = player.getRepresentation(true, true)
            + " select the planet that you wish to drop 1 mech and 1 infantry on";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
    }

    public static void handleTitansConstructionMechDeployStep2(Game game, Player player,
        ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 mech " + planet, game);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planet, game);
        String msg = player.getRepresentation(true, true) + " deployed 1 mech and 1 infantry on "
            + Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.sendMessageToRightStratThread(player, game, msg, "construction");
        if (!player.getSCs().contains(Integer.parseInt("4"))
            && !game.playerHasLeaderUnlockedOrAlliance(player, "rohdhnacommander")) {
            String color = player.getColor();
            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            ButtonHelper.sendMessageToRightStratThread(player, game,
                player.getFactionEmoji() + " Placed 1 CC from reinforcements in the "
                    + Helper.getPlanetRepresentation(planet, game) + " system",
                "construction");
        }
        event.getMessage().delete().queue();
    }

    public static void checkForStymie(Game game, Player activePlayer, Tile tile) {
        for (Player p2 : ButtonHelper.getOtherPlayersWithUnitsInTheSystem(activePlayer, game, tile)) {
            if (p2.getPromissoryNotes().containsKey("stymie") && game.getPNOwner("stymie") != p2) {
                String msg = p2.getRepresentation(true, true) + " you have the opportunity to stymie "
                    + ButtonHelper.getIdentOrColor(activePlayer, game);
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("stymiePlayerStep1_" + activePlayer.getFaction(), "Play Stymie"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void resolveStymiePlayerStep1(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        Player activePlayer = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String msg = player.getRepresentation(true, true) + " choose the system in which you wish to stymie";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPosition().equalsIgnoreCase(game.getActiveSystem()) && !tile.isHomeSystem()
                && !AddCC.hasCC(event, activePlayer.getColor(), tile)) {
                buttons.add(Button.success("stymiePlayerStep2_" + activePlayer.getFaction() + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        event.getMessage().delete().queue();
        ButtonHelper.resolvePNPlay("stymie", player, game, event);
    }

    public static void resolveStymiePlayerStep2(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        AddCC.addCC(event, p2.getColor(), tile);
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " you stymied the tile: "
                + tile.getRepresentationForButtons(game, player));
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " you were stymied in tile: "
                + tile.getRepresentationForButtons(game, p2));
    }

    public static void offerASNButtonsStep1(Game game, Player player, String warfareOrTactical) {
        String msg = player.getRepresentation(true, true) + " you may have the ability to use Agency Supply Network (ASN). Select the tile you want to build out of, or decline (please decline if you already used ASN)";
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);
        for (Tile tile : tiles) {
            buttons.add(Button.success("asnStep2_" + tile.getPosition() + "_" + warfareOrTactical,
                tile.getRepresentation()));
        }
        buttons.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void resolveASNStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {

        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String msg = player.getFactionEmoji() + " is resolving Agency Supply Network in tile "
            + tile.getRepresentation();

        String warfareOrTactical = buttonID.split("_")[2];
        ButtonHelper.sendMessageToRightStratThread(player, game, msg, warfareOrTactical);
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, warfareOrTactical, "place");
        String message = player.getRepresentation()
            + " Use the buttons to produce."
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        event.getMessage().delete().queue();

    }

    public static boolean somebodyHasThisRelic(Game game, String relic) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasRelic(relic)) {
                return true;
            }
        }
        return false;
    }

    public static Player findPNOwner(String pn, Game game) {
        for (Player player : game.getRealPlayers()) {
            if (player.ownsPromissoryNote(pn)) {
                return player;
            }
        }
        return null;
    }

    public static void delete(ButtonInteractionEvent event) {
        event.getMessage().delete().queue();
    }

    public static void placeSaarMech(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " paid 1TG (" + player.getTg() + "->" + (player.getTg() - 1)
            + ") to place 1 mech on " + Helper.getPlanetRepresentation(planet, game);
        delete(event);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet), "mech " + planet,
            game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        player.setTg(player.getTg() - 1);
    }

    public static void offerKeleresStartingTech(Player player, Game game, ButtonInteractionEvent event) {
        List<String> techToGain = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            techToGain = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, techToGain,
                game);
        }
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success("getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
            }
        }
        event.getMessage().delete().queue();
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use the buttons to get a tech the other players had",
            techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use the buttons to get another tech the other players had",
            techs2);

    }

    public static void offerArgentStartingTech(Player player, Game game) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                techs.add(Button.success(
                    player.getFinsFactionCheckerPrefix() + "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
            }
        }
        List<Button> techs2 = new ArrayList<>(techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use the buttons to get one of the 3 starting argent tech",
            techs);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use the buttons to the second of the 3 starting argent tech",
            techs2);
    }

    public static void offerWinnuStartingTech(Player player, Game game) {
        List<String> techToGain = new ArrayList<>();
        techToGain.add("st");
        techToGain.add("nm");
        techToGain.add("ps");
        techToGain.add("amd");
        techToGain.add("det");
        techToGain.add("aida");
        techToGain.add("pa");
        techToGain.add("sdn");
        List<Button> techs = new ArrayList<>();
        for (String tech : techToGain) {
            if (!player.getTechs().contains(tech)) {
                techs.add(Button.success(
                    player.getFinsFactionCheckerPrefix() + "getTech_" + Mapper.getTech(tech).getAlias() + "__noPay",
                    Mapper.getTech(tech).getName()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " use the buttons to get a tech", techs);
    }

    public static void offerSpyNetOptions(Player player) {
        String msg = player.getRepresentation()
            + " you have a choice now as to how you want to resolve Spy Net. You may do it the traditional way of accepting a card Yssaril chooses, without looking"
            + " at the other cards. Or you may look at all of Yssaril's cards and choose one.";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("spyNetYssarilChooses", "Have Yssaril Choose For You"));
        buttons.add(Button.secondary("spyNetPlayerChooses", "Look And Choose Yourself"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    public static void resolveSpyNetYssarilChooses(Player player, Game game, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", game);
        String buttonID = "transact_ACs_" + player.getFaction();
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            "Sent Yssaril buttons so that they can send you 1AC.");
        ButtonHelper.resolveSpecificTransButtonsOld(game, yssaril, buttonID, event);
        event.getMessage().delete().queue();
    }

    public static void resolveSpyNetPlayerChooses(Player player, Game game, ButtonInteractionEvent event) {
        Player yssaril = findPNOwner("spynet", game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Use Buttons to take 1 AC",
            ACInfo.getToBeStolenActionCardButtons(game, yssaril));
        event.getMessage().delete().queue();
    }

    public static void returnFightersToSpace(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                    int numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
                    new AddUnits().unitParsing(event, player.getColor(), tile, numff + " ff", game);
                    new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " ff " + unitHolder.getName(),
                        game);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " put all fighters in tile " + pos + " back into space");
        event.getMessage().delete().queue();
    }

    public static List<Button> getTradePlanetsWithHacanMechButtons(Player hacan, Player receiver, Game game) {
        List<Button> buttons = new ArrayList<>();
        if (!hacan.hasUnit("hacan_mech")) {
            return buttons;
        }
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (ButtonHelper.getUnitHolderFromPlanetName(planet, game).getUnitCount(UnitType.Mech,
                hacan.getColor()) > 0) {
                buttons.add(Button.secondary("hacanMechTradeStepOne_" + planet + "_" + receiver.getFaction(),
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        return buttons;
    }

    public static List<Button> getRaghsCallButtons(Player player, Game game, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        Player saar = game.getPNOwner("ragh");
        if (saar == player || tile == null) {
            return buttons;
        }
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH instanceof Planet && FoWHelper.playerHasUnitsOnPlanet(saar, tile, uH.getName())) {
                buttons.add(Button.secondary("raghsCallStepOne_" + uH.getName(),
                    "Ragh's Call on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
        }
        return buttons;
    }

    public static void rollAmbush(Player player, Game game, Tile tile, ButtonInteractionEvent event) {
        UnitHolder space = tile.getUnitHolders().get("space");
        int numCruisers = Math.min(2, space.getUnitCount(UnitType.Cruiser, player.getColor()));
        int remaining = 2 - numCruisers;
        int numDestroyers = Math.min(remaining, space.getUnitCount(UnitType.Destroyer, player.getColor()));

        String result = ButtonHelper.getIdentOrColor(player, game) + " rolling for ambush:\n";

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        Map<UnitModel, Integer> playerUnits = CombatHelper.GetUnitsInCombat(tile, space, player, event,
            CombatRollType.combatround, game);
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();
            if ("cruiser".equalsIgnoreCase(unit.getBaseType()) || "destroyer".equalsIgnoreCase(unit.getBaseType())) {
                if ("cruiser".equalsIgnoreCase(unit.getBaseType())) {
                    numOfUnit = numCruisers;
                } else {
                    numOfUnit = numDestroyers;
                }
                if (numOfUnit < 1) {
                    continue;
                }
                int toHit = unit.getCombatDieHitsOnForAbility(CombatRollType.combatround);
                int modifierToHit = 0;
                int extraRollsForUnit = 0;
                int numRollsPerUnit = 1;
                int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
                List<Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
                player.setExpectedHitsTimes10(
                    player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
                int hitRolls = DiceHelper.countSuccesses(resultRolls);
                totalHits += hitRolls;
                String unitRoll = CombatMessageHelper.displayUnitRoll(unit, toHit, modifierToHit, numOfUnit,
                    numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
                resultBuilder.append(unitRoll);
            }

        }
        result = resultBuilder.toString();
        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), result);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void checkForNaaluPN(Game game) {
        game.setStoredValue("Play Naalu PN", "");
        for (Player player : game.getRealPlayers()) {
            boolean naalu = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if ("gift".equalsIgnoreCase(pn) && !player.ownsPromissoryNote("gift")) {
                    naalu = true;
                }
            }
            if (naalu) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-play Naalu PN. Naalu PN is an awkward timing window for async, so if you intend to play it, it's best to pre-play it now. Feel free to ignore this message if you don't intend to play it.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("resolvePreassignment_Play Naalu PN", "Pre-play Naalu PN"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    public static void tnelisDeploy(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        String planet = buttonID.split("_")[1];
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
            "1 mech " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " landed 1 mech on "
                + Helper.getPlanetRepresentation(planet, game) + " using Tnelis mech deploy ability");
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, game.getTileFromPlanet(planet), planet);
        if (players.size() > 1) {
            StartCombat.startGroundCombat(players.get(0), players.get(1), game, event, ButtonHelper.getUnitHolderFromPlanetName(planet, game), game.getTileFromPlanet(planet));
        }
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + " pay 3r for the mech", options);
    }

    public static void resolveRaghsCallStepOne(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        ButtonHelper.resolvePNPlay("ragh", player, game, event);
        List<Button> buttons = new ArrayList<>();
        Player saar = game.getPNOwner("ragh");
        for (String planet : saar.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(Button.secondary("raghsCallStepTwo_" + origPlanet + "_" + planet,
                    "Relocate to " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + "Choose which planet to relocate saar ground forces to",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveHacanMechTradeStepOne(Player hacan, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String planet : hacan.getPlanetsAllianceMode()) {
            if (!planet.equalsIgnoreCase(origPlanet)) {
                buttons.add(
                    Button.secondary("hacanMechTradeStepTwo_" + origPlanet + "_" + receiverFaction + "_" + planet,
                        "Relocate to " + Helper.getPlanetRepresentation(planet, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentation(true, true) + "Choose which planet to relocate your units to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep2(Player hacan, Game game, ButtonInteractionEvent event,
        String buttonID) {
        Player player = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve target player, please resolve manually.");
            return;
        }
        int oldTg = player.getTg();
        player.setTg(oldTg + 2);
        String message = player.getFactionEmojiOrColor() + " gained " + Emojis.tg(2) + " due to " + Emojis.Hacan + Emojis.BioticTech + "Production Biomes (" + oldTg + "->" + player.getTg() + ")";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), message);
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
        event.getMessage().delete().queue();
    }

    public static void resolveProductionBiomesStep1(Player hacan, Game game,
        GenericInteractionCreateEvent event) {
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), hacan.getFactionEmoji() + " did not have enough strategy CCs. #rejected");
            return;
        }

        int oldTg = hacan.getTg();
        hacan.setTg(oldTg + 4);
        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, game, event);
        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), hacan.getFactionEmoji() + " spent a strategy CC and gained " + Emojis.tg(4) + " (" + oldTg + "->" + hacan.getTg() + ")");
        ButtonHelperAbilities.pillageCheck(hacan, game);
        ButtonHelperAgents.resolveArtunoCheck(hacan, game, 4);

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Button.secondary("productionBiomes_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("productionBiomes_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentation(true, true) + " choose who should get 2TGs", buttons);
    }

    public static void resolveQuantumDataHubNodeStep1(Player hacan, Game game, ButtonInteractionEvent event) {
        hacan.exhaustTech("qdn");
        int oldStratCC = hacan.getStrategicCC();
        if (oldStratCC < 1) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
                hacan.getFactionEmoji() + " did not have enough strategy CCs. #rejected");
            return;
        }

        int oldTg = hacan.getTg();
        hacan.setStrategicCC(oldStratCC - 1);
        ButtonHelperCommanders.resolveMuaatCommanderCheck(hacan, game, event);
        hacan.setTg(oldTg - 3);

        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
            hacan.getFactionEmoji() + " lost a strategy CC and 3TGs (" + oldTg + "->" + hacan.getTg() + ")");

        List<Button> buttons = getSwapSCButtons(game, "qdn", hacan);
        MessageHelper.sendMessageToChannelWithButtons(hacan.getCorrectChannel(),
            hacan.getRepresentation(true, true) + " choose who you want to swap SCs with", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getSwapSCButtons(Game game, String type, Player hacan) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == hacan) {
                continue;
            }
            if (p2.getSCs().size() > 1) {
                if (game.isFowMode()) {
                    buttons.add(Button.secondary("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, p2.getColor()));
                } else {
                    Button button = Button.secondary("selectBeforeSwapSCs_" + p2.getFaction() + "_" + type, " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            } else {
                if (game.isFowMode()) {
                    buttons.add(Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_"
                        + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], p2.getColor()));
                } else {
                    Button button = Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_"
                        + p2.getSCs().toArray()[0] + "_" + hacan.getSCs().toArray()[0], " ");
                    String factionEmojiString = p2.getFactionEmoji();
                    button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    buttons.add(button);
                }
            }
        }
        return buttons;
    }

    public static void resolveSelectedBeforeSwapSC(Player player, Game game, String buttonID) {
        String type = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Integer sc : p2.getSCs()) {
            for (Integer sc2 : player.getSCs()) {
                buttons.add(Button.secondary("swapSCs_" + p2.getFaction() + "_" + type + "_" + sc + "_" + sc2,
                    "Swap " + Helper.getSCName(sc2, game) + " with " + Helper.getSCName(sc, game)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " choose which strategy card you want to swap with.",
            buttons);
    }

    public static void resolveSwapSC(Player player1, Game game, ButtonInteractionEvent event, String buttonID) {
        String type = buttonID.split("_")[2];
        Player player2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(player1.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }
        int player1SC = Integer.parseInt(buttonID.split("_")[4]);
        int player2SC = Integer.parseInt(buttonID.split("_")[3]);
        if ("qdn".equalsIgnoreCase(type)) {
            int oldTg = player2.getTg();
            player2.setTg(oldTg + 3);
            ButtonHelperAbilities.pillageCheck(player2, game);
            MessageHelper.sendMessageToChannel(player2.getCorrectChannel(),
                player2.getFactionEmoji() + " gained 3TGs from QDN (" + oldTg + "->" + player2.getTg() + ")");
        }
        player1.addSC(player2SC);
        player1.removeSC(player1SC);
        player2.addSC(player1SC);
        player2.removeSC(player2SC);
        String sb = player1.getRepresentation() + " swapped strategy card with " + player2.getRepresentation() + "\n" +
            "> " + player2.getRepresentation() + Emojis.getSCEmojiFromInteger(player2SC) + " " + ":arrow_right:"
            + " " + Emojis.getSCEmojiFromInteger(player1SC) + "\n" +
            "> " + player1.getRepresentation() + Emojis.getSCEmojiFromInteger(player1SC) + " " + ":arrow_right:"
            + " " + Emojis.getSCEmojiFromInteger(player2SC) + "\n";
        MessageHelper.sendMessageToChannel(player2.getCorrectChannel(), sb);
        event.getMessage().delete().queue();
        ButtonHelper.startActionPhase(event, game);
    }

    public static void resolveRaghsCallStepTwo(Player player, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String newPlanet = buttonID.split("_")[2];
        Player saar = game.getPNOwner("ragh");
        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, game);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            UnitModel unit = saar.getUnitFromUnitKey(unitKey);
            if (saar.unitBelongsToPlayer(unitKey) && unit.getIsGroundForce()) {
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int amount = unitEntry.getValue();
                new RemoveUnits().unitParsing(event, saar.getColor(), game.getTileFromPlanet(origPlanet),
                    amount + " " + unitName + " " + origPlanet, game);
                new AddUnits().unitParsing(event, saar.getColor(), game.getTileFromPlanet(newPlanet),
                    amount + " " + unitName + " " + newPlanet, game);
            }
        }
        String ident = ButtonHelper.getIdentOrColor(player, game);
        String message2 = ident + " moved the ground forces on the planet "
            + Helper.getPlanetRepresentation(origPlanet, game) + " to "
            + Helper.getPlanetRepresentation(newPlanet, game);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(saar.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
        }
        event.getMessage().delete().queue();
    }

    public static void resolveHacanMechTradeStepTwo(Player hacan, Game game, ButtonInteractionEvent event,
        String buttonID) {
        String origPlanet = buttonID.split("_")[1];
        String receiverFaction = buttonID.split("_")[2];
        String newPlanet = buttonID.split("_")[3];
        Player p2 = game.getPlayerFromColorOrFaction(receiverFaction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }

        UnitHolder oriPlanet = ButtonHelper.getUnitHolderFromPlanetName(origPlanet, game);
        Map<UnitKey, Integer> units = new HashMap<>(oriPlanet.getUnits());
        String unitGroupRef = "";
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            UnitKey unitKey = unitEntry.getKey();
            int amount = unitEntry.getValue();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());

            new RemoveUnits().unitParsing(event, hacan.getColor(), game.getTileFromPlanet(origPlanet),
                amount + " " + unitName + " " + origPlanet, game);
            new AddUnits().unitParsing(event, hacan.getColor(), game.getTileFromPlanet(newPlanet),
                amount + " " + unitName + " " + newPlanet, game);
            if (unitGroupRef.equalsIgnoreCase("units")) {
            } else if (!unitName.equalsIgnoreCase("mech")) {
                unitGroupRef = "units";
            } else if (unitGroupRef.equalsIgnoreCase("mech")) {
                unitGroupRef = "mechs";
            } else {
                unitGroupRef = "mech";
            }
        }
        PlanetAdd.doAction(p2, origPlanet, game, event, false);

        List<Button> goAgainButtons = new ArrayList<>();
        Button button = Button.secondary("transactWith_" + p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("finishTransaction_" + p2.getColor(), "Done With This Transaction");
        String ident = hacan.getFactionEmoji();
        String message2 = ident + " traded the planet " + Helper.getPlanetRepresentation(origPlanet, game)
            + " to " + ButtonHelper.getIdentOrColor(p2, game)
            + " and relocated the " + unitGroupRef + " to " + Helper.getPlanetRepresentation(newPlanet, game);
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        goAgainButtons.add(Button.success("demandSomething_" + p2.getColor(), "Expect something in return"));
        MessageHelper.sendMessageToChannel(hacan.getCorrectChannel(), message2);
        if (game.isFowMode() || !game.isNewTransactionMethod()) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(hacan.getPrivateChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
                MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
            } else {

                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    ident + " Use Buttons To Complete Transaction", goAgainButtons);
            }
        }
        event.getMessage().delete().queue();
    }

    public static void resolveReleaseButton(Player cabal, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String faction = buttonID.split("_")[1];
        Player player = game.getPlayerFromColorOrFaction(faction);
        if (player == null) {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                "Could not resolve second player, please resolve manually.");
            return;
        }

        String unit = buttonID.split("_")[2];
        new RemoveUnits().unitParsing(event, player.getColor(), cabal.getNomboxTile(), unit, game);
        MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
            cabal.getRepresentation(true, true) + " released 1 " + ButtonHelper.getIdentOrColor(player, game)
                + " " + unit + " from prison");
        if (cabal != player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation(true, true) + " a " + unit + " of yours was released from prison.");
        }
        if (!cabal.getNomboxTile().getUnitHolders().get("space").getUnits()
            .containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    public static void resolveKolleccReleaseButton(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        new RemoveUnits().unitParsing(event, player.getColor(), player.getNomboxTile(), unit, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentation(true, true) + " put 1 captured " + unit + " in the space area of "
                + tile.getRepresentationForButtons(game, player) + " using Shroud of Lith abiility");
        new AddUnits().unitParsing(event, player.getColor(), tile, unit, game);
        if (!player.getNomboxTile().getUnitHolders().get("space").getUnits()
            .containsKey(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor()))) {
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    public static void checkBlockadeStatusOfEverything(Player player, Game game,
        GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (doesPlayerHaveAnyCapturedUnits(p2, player)) {
                if (isCabalBlockadedByPlayer(player, game, p2)) {
                    releaseAllUnits(p2, game, player, event);
                }
            }
        }
    }

    public static boolean doesPlayerHaveAnyCapturedUnits(Player cabal, Player blockader) {
        if (cabal == blockader) {
            return false;
        }
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void releaseAllUnits(Player cabal, Game game, Player blockader,
        GenericInteractionCreateEvent event) {
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            List<UnitKey> unitKeys = new ArrayList<>(unitHolder.getUnits().keySet());
            for (UnitKey unitKey : unitKeys) {
                if (blockader.unitBelongsToPlayer(unitKey)) {
                    int amount = unitHolder.getUnits().get(unitKey);
                    String unit = ButtonHelper.getUnitName(unitKey.asyncID());
                    new RemoveUnits().unitParsing(event, blockader.getColor(), cabal.getNomboxTile(),
                        amount + " " + unit, game);
                    MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(),
                        cabal.getRepresentation(true, true) + " released " + amount + " "
                            + ButtonHelper.getIdentOrColor(blockader, game) + " " + unit
                            + " from prison due to blockade");
                    if (cabal != blockader) {
                        MessageHelper.sendMessageToChannel(blockader.getCorrectChannel(),
                            blockader.getRepresentation(true, true) + " " + amount + " " + unit
                                + " of yours was released from prison.");
                    }

                }

            }
        }
    }

    public static List<Button> getReleaseButtons(Player cabal, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : cabal.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                for (Player player : game.getRealPlayers()) {
                    if (player.unitBelongsToPlayer(unitKey)) {
                        String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                        String buttonID = "cabalRelease_" + player.getFaction() + "_" + unitName;
                        if (game.isFowMode()) {
                            buttons.add(Button.secondary(buttonID, player.getColor() + " " + unitName));
                        } else {
                            buttons.add(Button.secondary(buttonID, unitName)
                                .withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                        }
                    }
                }
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static List<Button> getKolleccReleaseButtons(Player kollecc, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : kollecc.getNomboxTile().getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnits().keySet()) {
                Player player = kollecc;
                if (player.unitBelongsToPlayer(unitKey)) {
                    String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                    String buttonID = "kolleccRelease_" + unitName;
                    buttons.add(Button.secondary(buttonID, "Release 1  " + unitName)
                        .withEmoji(Emoji.fromFormatted(player.getFactionEmoji())));
                }

            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
        return buttons;
    }

    public static void checkForGeneticRecombination(Player voter, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == voter) {
                continue;
            }
            if (p2.hasTechReady("gr")) {
                if (game.getStoredValue("Genetic Recombination " + p2.getFaction())
                    .contains(voter.getFaction())) {
                    p2.exhaustTech("gr");
                    String msg = p2.getRepresentation(false, true) + " is using genetic recombination to force "
                        + voter.getRepresentation(false, true)
                        + " to vote a particular way. Tech has been exhausted, the owner should elaborate on which way to vote";
                    MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), msg);
                }
            }
        }
    }

    public static void resolveDihmohnFlagship(String buttonID, ButtonInteractionEvent event, Game game,
        Player player,
        String ident) {
        MessageHelper.sendMessageToChannel(event.getChannel(), game.getPing()
            + " the Maximus (the Dih-Mohn flagship) is producing units. They may produce up to 2 units with a combined cost of 4.");
        String pos = buttonID.replace("dihmohnfs_", "");
        List<Button> buttons;
        // Muaat agent works here as it's similar so no need to add more fluff
        buttons = Helper.getPlaceUnitButtons(event, player, game,
            game.getTileByPosition(pos), "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveImpressmentPrograms(String buttonID, ButtonInteractionEvent event, Game game,
        Player player,
        String ident) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " May produce 1 ship in the explored planet system.");
        String pos = buttonID.replace("dsdihmy_", "");
        List<Button> buttons;
        // Sling relay works for this
        buttons = Helper.getPlaceUnitButtons(event, player, game,
            game.getTileByPosition(pos), "sling", "place");
        String message = player.getRepresentation() + " Use the buttons to produce 1 ship. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveVadenSCDebt(Player player, int sc, Game game, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(sc) && p2 != player && p2.hasAbility("fine_print")) {
                SendDebt.sendDebt(player, p2, 1);
                ButtonHelper.fullCommanderUnlockCheck(p2, game, "vaden", event);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation(true, true) + " you sent 1 debt token to "
                        + ButtonHelper.getIdentOrColor(p2, game) + " due to their fine print ability");
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2
                    .getRepresentation(true, true) + " you collected 1 debt token from "
                    + ButtonHelper.getIdentOrColor(player, game)
                    + " due to your fine print ability. This is technically optional, done automatically for conveinance.");
                break;
            }

        }
    }

    public static void increaseMykoMech(Game game) {
        int amount;
        if (!game.getStoredValue("mykoMech").isEmpty()) {
            amount = Integer.parseInt(game.getStoredValue("mykoMech"));
            amount = amount + 1;
        } else {
            amount = 1;
        }
        game.setStoredValue("mykoMech", "" + amount);
    }

    public static void decreaseMykoMech(Game game) {
        int amount = 0;
        if (!game.getStoredValue("mykoMech").isEmpty()) {
            amount = Integer.parseInt(game.getStoredValue("mykoMech"));
            amount = amount - 1;
        }
        if (amount < 0) {
            amount = 0;
        }
        game.setStoredValue("mykoMech", "" + amount);
    }

    public static void resolveMykoMech(Player player, Game game) {
        decreaseMykoMech(game);
        List<Button> buttons = new ArrayList<>(
            ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, ""));
        String message = player.getRepresentation(true, true) + " Use buttons to replace 1 infantry with 1 mech";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
    }

    public static void resolveMykoMechCheck(Player player, Game game) {
        if (player.hasUnit("mykomentori_mech")) {
            if (!game.getStoredValue("mykoMech").isEmpty()) {
                int amount = Integer.parseInt(game.getStoredValue("mykoMech"));
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success("resolveMykoMech", "Replace Infantry With Mech"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                if (amount > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentation(true, true) + " you have " + amount
                            + " mech" + (amount == 1 ? "" : "s") + " that may replace infantry.",
                        buttons);
                }
            }
        }
    }

    public static void deployMykoSD(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        int requiredNum = 4;
        Tile tile = game.getTileFromPlanet(planet);
        if (player.hasTech("dsmykosd")) {
            requiredNum = 3;
        }
        if (ButtonHelper.getNumberOfInfantryOnPlanet(planet, game, player) + 1 > requiredNum) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, requiredNum + " infantry " + planet,
                game);
            new AddUnits().unitParsing(event, player.getColor(), tile, "sd " + planet, game);
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji()
                + " deployed 1 space dock on " + planet + " by removing " + requiredNum + " infantry");
            event.getMessage().delete().queue();
            if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
                player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                    Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " does not have " + requiredNum + " infantry on " + planet
                    + " and therefore cannot deploy the space dock");
        }
    }

    public static void offerMahactInfButtons(Player player, Game game) {
        String message = player.getRepresentation(true, true) + " Resolve Mahact infantry loss using the buttons";
        List<Button> buttons = gainOrConvertCommButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerHoldingCompanyButtons(Player player, Game game) {
        String message = player.getRepresentation(true, true) + " Resolve Holding Company comm gain using the buttons. Remember you get 1 comm per attachment you've given out. ";
        List<Button> buttons = gainOrConvertCommButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerNekrophageButtons(Player player, ButtonInteractionEvent event) {
        String message = player.getRepresentation(true, true) + " Resolve Necrophage ability using buttons. ";
        List<Button> buttons = gainOrConvertCommButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> gainOrConvertCommButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        String ffcc = player.getFinsFactionCheckerPrefix();
        buttons.add(Button.of(ButtonStyle.SUCCESS, ffcc + "convert_1_comms", "Convert 1 comm to TG", Emoji.fromFormatted(Emojis.Wash)));
        buttons.add(Button.of(ButtonStyle.PRIMARY, ffcc + "gain_1_comms_stay", "Gain 1 comm", Emoji.fromFormatted(Emojis.comm)));
        buttons.add(Button.danger("deleteButtons", "Done resolving"));
        return buttons;
    }

    public static void checkIihqAttachment(Game game) {
        Tile tile = game.getMecatolTile();
        if (tile == null) return; // no mecatol tile
        for (Planet mecatol : tile.getPlanetUnitHolders()) {
            if (Constants.MECATOLS.contains(mecatol.getName())) {
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_1)) mecatol.removeToken(Constants.ATTACHMENT_IIHQ_1);
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_2)) mecatol.removeToken(Constants.ATTACHMENT_IIHQ_2);
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_1)) mecatol.removeToken("token_custodiavigilia_1.png");
                if (mecatol.getTokenList().contains(Constants.ATTACHMENT_IIHQ_2)) mecatol.removeToken("token_custodiavigilia_2.png");

                for (Player player : game.getRealPlayers()) {
                    if (!player.hasTech("iihq")) continue;
                    if (player.controlsMecatol(true)) {
                        mecatol.addToken(Constants.ATTACHMENT_IIHQ_1);
                    } else {
                        mecatol.addToken(Constants.ATTACHMENT_IIHQ_2);
                    }
                }
            }
        }
    }

    public static void KeleresIIHQCCGainCheck(Player player, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (p2.hasTech("iihq")) {
                List<Button> buttons = ButtonHelper.getGainCCButtons(p2);
                String trueIdentity = p2.getRepresentation(true, true);
                String message = trueIdentity
                    + " Due to your IIHQ tech, you get to gain 2 commmand counters when someone scores an imperial point.";
                String message2 = trueIdentity + "! Your current CCs are " + p2.getCCRepresentation()
                    + ". Use buttons to gain CCs";
                game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2,
                    buttons);
                break;
            }
        }
    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Game game) {
        if (game.getPNOwner("ra") != null && game.getPNOwner("ra") == player) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse(""))) {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (p2.getPromissoryNotes().containsKey("ra") && !p2.getTechs().contains(tech)) {
                        String msg = p2.getRepresentation(true, true) + " the RA owner has researched the tech "
                            + Mapper.getTech(AliasHandler.resolveTech(tech)).getRepresentation(false)
                            + "\nUse the below button if you want to play RA to get it.";
                        Button transact = Button.success("resolvePNPlay_ra_" + AliasHandler.resolveTech(tech),
                            "Acquire " + Mapper.getTech(AliasHandler.resolveTech(tech)).getName());
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(transact);
                        buttons.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                    }
                }
            }
        }
    }

    public static void resolveMilitarySupportCheck(Player player, Game game) {
        if (game.getPlayerFromColorOrFaction(Mapper.getPromissoryNote("ms").getOwner()) == player) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.getPromissoryNotes().containsKey("ms")) {
                    String msg = p2.getRepresentation(true, true)
                        + " the Military Support owner has started their turn, use the button to play Military Support if you want";
                    Button transact = Button.success("resolvePNPlay_ms", "Play Military Support ");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
                }
            }
        }
    }

    public static void resolveExpLook(Player player, Game game, GenericInteractionCreateEvent event,
        String deckType) {
        List<String> deck = game.getExploreDeck(deckType);
        List<String> discardPile = game.getExploreDiscard(deckType);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " Looked at top of the " + deckType + " deck.");
        String traitNameWithEmoji = Emojis.getEmojiFromDiscord(deckType)
            + deckType;
        String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
        if (deck.isEmpty() && discardPile.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
        String topCard = deck.get(0);
        game.setStoredValue("lastExpLookedAt" + player.getFaction() + deckType, topCard);
        ExploreModel explore = Mapper.getExplore(topCard);
        sb.append(explore.textRepresentation());

        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "top of " + traitNameWithEmoji + " explore deck has been set to " + playerFactionNameWithEmoji + " Cards info thread.");
    }

    public static void resolveExpDiscard(Player player, Game game, ButtonInteractionEvent event,
        String deckType) {
        List<String> deck = game.getExploreDeck(deckType);
        String topCard = deck.get(0);
        ExploreModel top = Mapper.getExplore(topCard);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " Discarded the top of the " + deckType + " deck. The discarded card was " + top.getName());
        game.discardExplore(topCard);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveKolleccAbilities(Player player, Game game) {
        if (player.hasAbility("treasure_hunters")) {
            // resolve treasure hunters
            String msg = "Kollecc player, please choose which exploration deck to look at the top card of";

            String deckType = "industrial";
            List<String> deck = game.getExploreDeck(deckType);
            String msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.get(0))) {
                msg2 = msg2 + " (Same as last time)";
            }
            Button transact1 = Button.success(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_industrial", msg2);
            deckType = "hazardous";
            deck = game.getExploreDeck(deckType);
            msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.get(0))) {
                msg2 = msg2 + " (Same as last time)";
            }
            Button transact2 = Button.success(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_hazardous", msg2);
            deckType = "cultural";
            deck = game.getExploreDeck(deckType);
            msg2 = StringUtils.capitalize(deckType);
            if (game.getStoredValue("lastExpLookedAt" + player.getFaction() + deckType)
                .equalsIgnoreCase(deck.get(0))) {
                msg2 = msg2 + " (Same as last time)";
            }
            Button transact3 = Button.success(player.getFinsFactionCheckerPrefix() + "resolveExp_Look_cultural", msg2);
            List<Button> buttons1 = new ArrayList<>();
            buttons1.add(transact1);
            buttons1.add(transact2);
            buttons1.add(transact3);
            buttons1.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                buttons1);

            Button transact = Button.success(player.getFinsFactionCheckerPrefix() + "relic_look_top", "Look at top of Relic Deck");
            msg2 = "Kollecc may also look at the top card of the relic deck.";
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(transact);
            buttons2.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2,
                buttons2);
        }
        if (player.getPromissoryNotes().containsKey("dspnkoll") && !player.ownsPromissoryNote("dspnkoll")) {
            Player p2 = player;
            String msg = p2.getRepresentation(true, true) + " use the button to play AI Survey if you want";
            Button transact = Button.success("resolvePNPlay_dspnkoll", "Play AI Survey");
            List<Button> buttons = new ArrayList<>();
            buttons.add(transact);
            buttons.add(Button.danger("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }

    public static void offerKolleccPNButtons(Game game, Player player) {
        Button transact1 = Button.success("explore_look_All", "Peek at Industrial/Hazardous/Cultural decks");
        Button transact2 = Button.success("relic_look_top", "Peek at Relic deck");
        List<Button> buttons = new ArrayList<>();
        buttons.add(transact1);
        buttons.add(transact2);
        buttons.add(Button.danger("deleteButtons", "Decline"));
        String message = "Use buttons to select how to use the Kollecc AI Survey PN";
        // System.out.println(player.getFaction() + " is playing PN KOLLEC");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void replacePDSWithFS(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        buttonID = buttonID.replace("replacePDSWithFS_", "");
        String planet = buttonID;
        String message = ident + " replaced " + Emojis.pds + " on " + Helper.getPlanetRepresentation(planet, game)
            + " with a " + Emojis.flagship;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)),
            "flagship", game);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit("pds"), player.getColor());
        game.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet, unitKey, 1);
        event.getMessage().delete().queue();
    }

    public static void firstStepOfChaos(Game game, Player p1, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(p1, game);
        for (Tile tile : tiles) {
            Button tileButton = Button.success("produceOneUnitInTile_" + tile.getPosition() + "_chaosM",
                tile.getRepresentationForButtons(game, p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            p1.getFactionEmoji() + " has chosen to use the chaos mapping technology");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select which tile you would like to chaos map in.", buttons);
    }

    public static boolean isCabalBlockadedByPlayer(Player player, Game game, Player cabal) {
        if (cabal == player) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, cabal, UnitType.CabalSpacedock,
            UnitType.Spacedock);
        if (tiles.isEmpty()) {
            return false;
        }
        for (Tile tile : tiles) {
            if (FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                return true;
            }
        }
        return false;
    }

    public static void cabalEatsUnit(Player player, Game game, Player cabal, int amount, String unit,
        GenericInteractionCreateEvent event) {
        cabalEatsUnit(player, game, cabal, amount, unit, event, false);
    }

    public static void mentakHeroProducesUnit(Player player, Game game, Player mentak, int amount, String unit,
        GenericInteractionCreateEvent event, Tile tile) {
        String unitP = AliasHandler.resolveUnit(unit);
        if (mentak == player || unitP.contains("sd") || unitP.contains("pd")
            || (unitP.contains("ws") && !mentak.hasWarsunTech()) || unitP.contains("mf") || unitP.contains("gf")
            || (mentak.getAllianceMembers().contains(player.getFaction()))) {
            return;
        }
        String msg = mentak.getRepresentation(true, true) + " placed " + amount + " of the " + unit + (amount == 1 ? "" : "s") + " which "
            + player.getRepresentation() + " just had destroyed in the active system using Ipswitch, Loose Cannon, the Mentak Hero.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        new AddUnits().unitParsing(event, mentak.getColor(), tile, amount + " " + unit, game);
    }

    public static void cabalEatsUnit(Player player, Game game, Player cabal, int amount, String unit,
        GenericInteractionCreateEvent event, boolean cabalAgent) {
        String msg = cabal.getRepresentation(true, true) + " has failed to eat " + amount + " of the " + unit
            + "s owned by "
            + player.getRepresentation() + " because they were blockaded. Wah-wah.";
        String unitP = AliasHandler.resolveUnit(unit);
        if (unitP.contains("sd") || unitP.contains("pd")
            || (cabal.getAllianceMembers().contains(player.getFaction()) && !cabalAgent)) {
            return;
        }
        if (!isCabalBlockadedByPlayer(player, game, cabal)) {
            msg = cabal.getFactionEmoji() + " has devoured " + amount + " of the " + unit + "s owned by "
                + player.getColor() + ". Chomp chomp.";
            String color = player.getColor();

            if (unitP.contains("ff") || unitP.contains("gf")) {
                color = cabal.getColor();
            }
            msg = msg.replace("Infantrys", "infantry");

            new AddUnits().unitParsing(event, color, cabal.getNomboxTile(), amount + " " + unit, game);
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(), msg);
        } else {
            MessageHelper.sendMessageToChannel(cabal.getCorrectChannel(), msg);
        }

    }

    public static void resolveLetnevMech(Player player, Game game, String buttonID,
        ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, game);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planet, game);
        List<Button> options = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " replaced 1 of their infantry with 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using the mech's deploy ability");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentation(true, true) + " pay 2r for it please", options);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolveDarkPactCheck(Game game, Player sender, Player receiver, int numOfComms) {
        for (String pn : sender.getPromissoryNotesInPlayArea()) {
            if ("dark_pact".equalsIgnoreCase(pn)
                && game.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())) {
                if (numOfComms == sender.getCommoditiesTotal()) {
                    MessageChannel channel = game.getActionsChannel();
                    if (game.isFowMode()) {
                        channel = sender.getPrivateChannel();
                    }
                    String message = sender.getRepresentation(true, true)
                        + " Dark Pact triggered, your TGs have increased by 1 (" + sender.getTg() + "->"
                        + (sender.getTg() + 1) + ")";
                    sender.setTg(sender.getTg() + 1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message = receiver.getRepresentation(true, true)
                        + " Dark Pact triggered, your TGs have increased by 1 (" + receiver.getTg() + "->"
                        + (receiver.getTg() + 1) + ")";
                    receiver.setTg(receiver.getTg() + 1);
                    if (game.isFowMode()) {
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    // ButtonHelperAbilities.pillageCheck(sender, game);
                    ButtonHelperAgents.resolveArtunoCheck(sender, game, 1);
                    // ButtonHelperAbilities.pillageCheck(receiver, game);
                    ButtonHelperAgents.resolveArtunoCheck(receiver, game, 1);
                }
            }
        }
    }

    public static List<Button> getUnitButtonsForVortex(Player player, Game game,
        GenericInteractionCreateEvent event) {
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.CabalSpacedock,
            UnitType.Spacedock);
        if (tiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Couldn't find any Dimensional Tears");
            return List.of();
        }
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tiles.get(0).getPosition(), player, false);
        for (Tile tile : tiles) {
            adjTiles.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false));
        }

        Set<String> colorsBlockading = tiles.stream()
            .map(tile -> tile.getUnitHolders().get("space"))
            .flatMap(unitHolder -> unitHolder.getUnitColorsOnHolder().stream())
            .collect(Collectors.toSet());
        Set<UnitKey> availableUnits = adjTiles.stream()
            .map(game::getTileByPosition)
            .flatMap(tile -> tile.getUnitHolders().values().stream())
            .flatMap(uh -> uh.getUnits().entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey))
            .filter(unitKey -> !colorsBlockading.contains(unitKey.getColorID()))
            .collect(Collectors.toSet());
        return availableUnits.stream()
            .filter(unitKey -> vortexButtonAvailable(game, unitKey))
            .map(unitKey -> buildVortexButton(game, unitKey))
            .toList();
    }

    public static boolean vortexButtonAvailable(Game game, UnitKey unitKey) {
        int baseUnitCap = switch (unitKey.getUnitType()) {
            case Infantry, Fighter -> 10000;
            case Destroyer, Cruiser -> 8;
            case Dreadnought -> 5;
            case Mech, Carrier -> 4;
            case Warsun -> 2;
            case Flagship -> 1;
            default -> 0; // everything else that can't be captured
        };
        int unitCap = game.getPlayerByColorID(unitKey.getColorID())
            .filter(p -> p.getUnitCap(unitKey.asyncID()) != 0).map(p -> p.getUnitCap(unitKey.asyncID()))
            .orElse(baseUnitCap);
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, unitKey) < unitCap;
    }

    public static Button buildVortexButton(Game game, UnitKey unitKey) {
        String faction = game.getPlayerByColorID(unitKey.getColorID()).map(Player::getFaction).get();
        String buttonID = "cabalVortextCapture_" + unitKey.unitName() + "_" + faction;
        String buttonText = String.format("Capture %s %s", unitKey.getColor(),
            unitKey.getUnitType().humanReadableName());
        return Button.danger(buttonID, buttonText).withEmoji(Emoji.fromFormatted(unitKey.unitEmoji()));
    }

    public static void resolveVortexCapture(String buttonID, Player player, Game game,
        ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
            p2.getRepresentation(true, true) + " a " + unit + " of yours has been captured by vortex.");
        cabalEatsUnit(p2, game, player, 1, unit, event);
        event.getMessage().delete().queue();
    }

    public static void offerTerraformButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null
                && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Button.success("terraformPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Use buttons to select which planet to terraform";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerAutomatonsButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null
                && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Button.success("automatonsPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Use buttons to select which planet to place automatons on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void offerBentorPNButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null
                && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Button.success("bentorPNPlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Use buttons to select which planet to place encryption key on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void resolveOlradinPN(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            buttons.add(Button.secondary("khraskHeroStep4Exhaust_" + player.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " select the planet you want to exhaust", buttons);
        String message = " Use buttons to ready a non-rex planet.";
        buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            if (Constants.MECATOLS.contains(planet)) {
                continue;
            }
            buttons.add(Button.secondary("khraskHeroStep4Ready_" + player.getFaction() + "_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + message, buttons);
    }

    public static void offerGledgeBaseButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null
                && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                    || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType())
                    || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if (oneOfThree || planet.contains("custodiavigilia") || planet.contains("ghoti")) {
                buttons.add(Button.success("gledgeBasePlanet_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Use buttons to select which planet to put a gledge base on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void resolveScour(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " used the Scour ability to discard 1 AC and ready "
            + Helper.getPlanetRepresentation(planet, game);
        player.refreshPlanet(planet);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentation(true, true) + " use buttons to discard",
            ACInfo.getDiscardActionCardButtons(game, player, false));
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void offerVeldyrButtons(Player player, Game game, String pnID) {
        List<String> extraAllowedPlanets = List.of("custodiavigilia", "ghoti");
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Planet unitHolder = game.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null
                && List.of("industrial", "cultural", "hazardous").contains(planetReal.getOriginalPlanetType());
            if (oneOfThree || extraAllowedPlanets.contains(planet.toLowerCase())) {
                buttons.add(Button.success("veldyrAttach_" + planet + "_" + pnID,
                    Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = player.getRepresentation(true, true)
            + " Use buttons to select which planet to put the attachment on";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            buttons);
    }

    public static List<Button> getButtonsToTakeSomeonesAC(Game game, Player thief, Player victim) {
        List<Button> takeACs = new ArrayList<>();
        String secretScoreMsg = "_ _\nClick a button to take 1 Action Card";
        List<Button> acButtons = ACInfo.getToBeStolenActionCardButtons(game, victim);
        if (!acButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = thief.getCardsInfoThread();
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
        return takeACs;
    }

    public static void mageon(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String trueIdentity) {
        buttonID = buttonID.replace("takeAC_", "");
        int acNum = Integer.parseInt(buttonID.split("_")[0]);

        String faction2 = buttonID.split("_")[1];
        Player player2 = game.getPlayerFromColorOrFaction(faction2);
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Could not find player, please resolve manually.");
            return;
        }
        if (!player2.getActionCards().containsValue(acNum)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC added/lost");
            return;
        }
        String ident2 = player2.getRepresentation();
        String message2 = trueIdentity + " took AC #" + acNum + " from " + ident2;
        String acID = null;
        for (Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
            if (so.getValue().equals(acNum)) {
                acID = so.getKey();
            }
        }
        if (game.isFowMode()) {
            message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message2);
        }
        player2.removeActionCard(acNum);
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
            player.getRepresentation(true, true) + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(),
            "# " + player2.getRepresentation(true, true) + " Lost " + acID + " to a players ability");
        ACInfo.sendActionCardInfo(game, player2);
        ACInfo.sendActionCardInfo(game, player);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", event);
        }
        event.getMessage().delete().queue();
    }

    public static void terraformPlanet(Player player, String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("terraformPlanet_", "");
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached terraform to " + Helper.getPlanetRepresentation(planet, game));
        game.setStoredValue("terraformedPlanet", planet);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "sol", event);
        event.getMessage().delete().queue();
    }

    public static void automatonsPlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("automatonsPlanet_", "");
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken("attachment_automatons.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached automatons to " + Helper.getPlanetRepresentation(planet, game));
        event.getMessage().delete().queue();
    }

    public static void bentorPNPlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("bentorPNPlanet_", "");
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken("attachment_encryptionkey.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached encryption key to " + Helper.getPlanetRepresentation(planet, game));
        event.getMessage().delete().queue();
    }

    public static void gledgeBasePlanet(String buttonID, ButtonInteractionEvent event, Game game) {
        String planet = buttonID.replace("gledgeBasePlanet_", "");
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken("attachment_gledgebase.png");
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Attached gledge base to " + Helper.getPlanetRepresentation(planet, game));
        event.getMessage().delete().queue();
    }

    public static void resolveBranchOffice(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String planet = buttonID.split("_")[1];
        String pnID = buttonID.split("_")[2];
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        switch (pnID) {
            case "dspnveld1" -> planetReal.addToken("attachment_veldyrtaxhaven.png");
            case "dspnveld2" -> planetReal.addToken("attachment_veldyrbroadcasthub.png");
            case "dspnveld3" -> planetReal.addToken("attachment_veldyrreservebank.png");
            case "dspnveld4" -> planetReal.addToken("attachment_veldyrorbitalshipyard.png");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "Attached branch office to " + Helper.getPlanetRepresentation(planet, game));
        if (game.getPNOwner(pnID).getLeaderIDs().contains("veldyrcommander")
            && !game.getPNOwner(pnID).hasLeaderUnlocked("veldyrcommander")) {
            ButtonHelper.commanderUnlockCheck(game.getPNOwner(pnID), game, "veldyr", event);
        }
        event.getMessage().delete().queue();
    }

    public static List<Player> getPlayersWithBranchOffices(Game game, Player player) {
        List<Player> players = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            for (String pn : p2.getPromissoryNotes().keySet()) {
                if (pn.contains("dspnveld")) {
                    players.add(p2);
                    break;
                }
            }
        }
        return players;
    }

    public static int getNumberOfBranchOffices(Game game, Player player) {
        int count = 0;
        for (String pn : player.getPromissoryNotes().keySet()) {
            if (pn.contains("dspnveld")) {
                count++;
            }
        }

        return count;
    }

    public static List<Button> getCreussIFFTypeOptions() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("creussIFFStart_beta", "Beta").withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
        buttons.add(Button.danger("creussIFFStart_gamma", "Gamma").withEmoji(Emoji.fromFormatted(Emojis.CreussGamma)));
        buttons.add(
            Button.secondary("creussIFFStart_alpha", "Alpha").withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
        return buttons;
    }

    public static void creussMechStep3(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String tilePos = buttonID.split("_")[1];
        String type = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = game.getTileByPosition(tilePos);
        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved ").append(Emojis.getEmojiFromDiscord(tokenName)).append(" to ")
            .append(tile.getRepresentationForButtons(game, player));
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentationForButtons(game, player)).append(")");
                break;
            }
        }
        boolean removed = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            if (uH.getUnitCount(UnitType.Mech, player.getColor()) > 0 && !removed) {
                removed = true;
                String name = uH.getName();
                if ("space".equals(name)) {
                    name = "";
                }
                new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 mech " + name, game);
                sb.append("\n ").append(player.getFactionEmoji()).append(" removed 1 mech from ")
                    .append(tile.getRepresentation()).append("(").append(uH.getName()).append(")");
            }
        }
        String msg = sb.toString();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    public static void nivynMechStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String tilePos = buttonID.split("_")[1];
        String tokenName = "wound";
        Tile tile = game.getTileByPosition(tilePos);
        StringBuilder sb = new StringBuilder(player.getRepresentation());
        tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
        sb.append(" moved wound token").append(" to ")
            .append(tile.getRepresentationForButtons(game, player));
        for (Tile tile_ : game.getTileMap().values()) {
            if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                sb.append(" (from ").append(tile_.getRepresentationForButtons(game, player)).append(")");
                break;
            }
        }
        boolean removed = false;
        for (UnitHolder uH : tile.getUnitHolders().values()) {
            int count = uH.getUnitCount(UnitType.Mech, player.getColor())
                - uH.getUnitDamageCount(UnitType.Mech, player.getColorID());

            if (count > 0 && !removed) {
                removed = true;
                String name = uH.getName();
                if ("space".equals(name)) {
                    name = "";
                }
                uH.addUnitDamage(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), 1);
                sb.append("\n ").append(player.getFactionEmoji()).append(" damaged 1 mech on ")
                    .append(tile.getRepresentation()).append("(").append(uH.getName()).append(")");
            }
        }
        String msg = sb.toString();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "nivyn", event);
        event.getMessage().delete().queue();
    }

    public static void creussMechStep2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        String tilePos = buttonID.split("_")[1];
        buttons.add(Button.success("creussMechStep3_" + tilePos + "_beta", "Beta")
            .withEmoji(Emoji.fromFormatted(Emojis.CreussBeta)));
        buttons.add(Button.danger("creussMechStep3_" + tilePos + "_gamma", "Gamma")
            .withEmoji(Emoji.fromFormatted(Emojis.CreussGamma)));
        buttons.add(Button.secondary("creussMechStep3_" + tilePos + "_alpha", "Alpha")
            .withEmoji(Emoji.fromFormatted(Emojis.CreussAlpha)));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " choose the type of wormhole you wish to place in " + tilePos,
            buttons);
        event.getMessage().delete().queue();
    }

    public static void creussMechStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            buttons.add(Button.success("creussMechStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " choose the tile where you wish to remove 1 mech to place a Creuss wormhole",
            buttons);
    }

    public static void nivynMechStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            buttons.add(Button.success("nivynMechStep2_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true)
                + " choose the tile where you wish to damage 1 mech and place a wound token",
            buttons);
    }

    public static void resolveWinnuPN(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String scNum = buttonID.split("_")[1];
        int sc = Integer.parseInt(scNum);
        player.addFollowedSC(sc, event);
        ButtonHelper.resolvePNPlay("acq", player, game, event);
        String msg = player.getRepresentation(true, true) + " you will be marked as having followed " + sc
            + " without having needed to spend a CC. Please still use the strategy card buttons to resolve the strategy card effect";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static List<Button> getGreyfireButtons(Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            buttons.add(Button.success("greyfire_" + uH.getName(),
                Helper.getPlanetRepresentation(uH.getName(), game)));
        }
        return buttons;
    }

    public static void resolveGreyfire(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String unit = "infantry";
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + planet, game);
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)) {
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry " + planet, game);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " replaced 1 of their opponent's infantry with 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using greyfire");
        event.getMessage().delete().queue();
    }

    public static void resolveCreussIFFStart(Game game, @NotNull Player player, String buttonID, String ident,
        ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        List<Button> buttons = getCreusIFFLocationOptions(game, player, type);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            ident + " please select the tile you would like to put a wormhole in", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveCreussIFF(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        String tokenName = "creuss" + type;
        Tile tile = game.getTileByPosition(pos);
        String msg;
        if (game.isFowMode() && !isTileCreussIFFSuitable(game, player, tile)) {
            msg = "Tile was not suitable for the iff.";
            if (player.getTg() > 0) {
                player.setTg(player.getTg() - 1);
                msg = msg + " You lost 1TG";
            } else {
                if (player.getTacticalCC() > 0) {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    msg = msg + " You lost a tactic CC";
                } else {
                    if (player.getFleetCC() > 0) {
                        player.setFleetCC(player.getFleetCC() - 1);
                        msg = msg + " You lost a fleet CC";
                    }
                }
            }
        } else {
            StringBuilder sb = new StringBuilder(player.getRepresentation());
            tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
            sb.append(" moved ").append(Emojis.getEmojiFromDiscord(tokenName)).append(" to ")
                .append(tile.getRepresentationForButtons(game, player));
            for (Tile tile_ : game.getTileMap().values()) {
                if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                    sb.append(" (from ").append(tile_.getRepresentationForButtons(game, player)).append(")");
                    break;
                }
            }
            msg = sb.toString();
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        for (Player p2 : game.getRealPlayers()) {
            ButtonHelper.fullCommanderUnlockCheck(p2, game, "ghost", event);
        }
        event.getMessage().delete().queue();
    }

    public static List<Button> getCreusIFFLocationOptions(Game game, @NotNull Player player, String type) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isTileCreussIFFSuitable(game, player, tile) || (game.isFowMode()
                && !FoWHelper.getTilePositionsToShow(game, player).contains(tile.getPosition()))) {
                buttons.add(Button.success("creussIFFResolve_" + type + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    public static boolean isTileCreussIFFSuitable(Game game, Player player, Tile tile) {
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            if (game.getTileFromPlanet(planet) == null) {
                continue;
            }
            if (game.getTileFromPlanet(planet).getPosition().equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile)) {
                return false;
            }
            Tile hs = game.getTile(AliasHandler.resolveTile(p2.getFaction()));
            if (hs == null) {
                hs = p2.getHomeSystemTile();
            }
            if (hs != null && hs.getPosition().equalsIgnoreCase(tile.getPosition())) {
                return false;
            }
            if (tile == null || tile.getRepresentationForButtons(game, player).toLowerCase().contains("hyperlane")) {
                return false;
            }
        }
        return true;
    }

    public static void resolveTCSExhaust(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        buttonID = buttonID.replace("absol_jr", "absoljr");
        String agent = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null)
            return;
        player.exhaustTech("tcs");
        Leader playerLeader = p2.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            if (agent.contains("titanprototype")) {
                p2.removeExhaustedRelic("titanprototype");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted TCS tech to ready " + agent + ", owned by "
                        + p2.getColor());
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentation(true, true) + " the TCS tech was exhausted by " + player.getColor()
                            + " to ready your " + agent);
                }
                event.getMessage().delete().queue();
            }
            if (agent.contains("absol")) {
                p2.removeExhaustedRelic("absol_jr");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji() + " exhausted TCS tech to ready " + agent + ", owned by "
                        + p2.getColor());
                if (p2 != player) {
                    MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                        p2.getRepresentation(true, true) + " the TCS tech was exhausted by " + player.getColor()
                            + " to ready your " + agent);
                }
                event.getMessage().delete().queue();
            }
            return;
        }
        RefreshLeader.refreshLeader(p2, playerLeader, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " exhausted TCS tech to ready " + agent + ", owned by "
                + p2.getColor());

        if (p2 != player) {
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                p2.getRepresentation(true, true) + " the TCS tech was exhausted by " + player.getColor()
                    + " to ready your " + agent);
        }
        event.getMessage().delete().queue();
    }

    public static boolean isNextToEmpyMechs(Game game, Player ACPlayer, Player EmpyPlayer) {
        if (ACPlayer == null || EmpyPlayer == null) {
            return false;
        }
        if (ACPlayer.getFaction().equalsIgnoreCase(EmpyPlayer.getFaction())) {
            return false;
        }
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, EmpyPlayer, UnitType.Mech);
        for (Tile tile : tiles) {
            Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), EmpyPlayer, true);
            for (String adjTile : adjTiles) {
                Tile adjT = game.getTileMap().get(adjTile);
                if (FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Button> getLanefirATSButtons(Player p1, Player p2) {
        List<Button> ats = new ArrayList<>();

        for (int i = 0; i < p1.getAtsCount(); i++) {
            ats.add(Button.secondary("FFCC_" + p1.getFaction() + "_" + "lanefirATS_" + (i + 1), String.valueOf(i + 1)));
        }

        for (int i = 0; i < p2.getAtsCount(); i++) {
            ats.add(Button.secondary("FFCC_" + p2.getFaction() + "_" + "lanefirATS_" + (i + 1), String.valueOf(i + 1)));
        }

        return ats;
    }

    public static void resolveSalvageOps(Player player, ButtonInteractionEvent event, String buttonID,
        Game game) {
        ButtonHelper.deleteTheOneButton(event);
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        player.setTg(player.getTg() + 1);
        if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            String type = "sling";
            List<Button> buttons;
            buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type,
                "placeOneNDone_dontskip");
            buttons.add(Button.danger("deleteButtons", "Decline"));
            String message = player.getRepresentation()
                + " Use the buttons to produce 1 ship that was destroyed in the combat. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        String msg = player.getRepresentation() + " used Salvage Ops to get 1TG (you currently have " + player.getTg() + "TG" + (player.getTg() == 1 ? "" : "s") + ")";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void resolveLanefirATS(Player player, ButtonInteractionEvent event, String buttonID) {
        String count = buttonID.split("_")[1];
        int origATS = player.getAtsCount();

        if (player.getAtsCount() < Integer.parseInt(count)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                player.getRepresentation(true, false) + " does not have " + count
                    + " commodities to remove from ATS Armaments. Current count: " + player.getAtsCount());
            return;
        }

        player.setAtsCount(player.getAtsCount() - Integer.parseInt(count));

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentation(true, false) + " removed " + count + " commodities from ATS Armaments ("
                + origATS + "->" + player.getAtsCount() + ")");
    }

    public static void resolveRohDhnaIndustrious(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String tilePos = buttonID.split("_")[1];
        String toRemove = buttonID.split("_")[2];
        String planet = toRemove.split(" ")[1];
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(tilePos), "warsun",
            game);
        new RemoveUnits().unitParsing(event, player.getColor(), game.getTileByPosition(tilePos), toRemove,
            game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " replaced " + Emojis.spacedock + " on "
                + Helper.getPlanetRepresentationPlusEmoji(planet) + " with a " + Emojis.warsun);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Click the names of the planets you wish to exhaust to pay the 6 resources", buttons);
        event.getMessage().delete().queue();
    }

    public static List<Button> getRohDhnaRecycleButtons(Game game, Player player) {
        List<UnitKey> availableUnits = new ArrayList<>();
        Map<UnitKey, Integer> units = game.getTileByPosition(game.getActiveSystem()).getUnitHolders()
            .get("space").getUnits();
        for (UnitKey unit : units.keySet()) {
            if (unit.getColor() == player.getColor() && (unit.getUnitType() == UnitType.Cruiser
                || unit.getUnitType() == UnitType.Carrier || unit.getUnitType() == UnitType.Dreadnought)) {
                // if unit is not in the list, add it
                if (!availableUnits.contains(unit)) {
                    availableUnits.add(unit);
                }
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : availableUnits) {
            buttons.add(Button.success("FFCC_" + player.getFaction() + "_rohdhnaRecycle_" + unit.unitName(),
                unit.getUnitType().humanReadableName()).withEmoji(Emoji.fromFormatted(unit.unitEmoji())));

        }

        if (!buttons.isEmpty()) {
            buttons.add(Button.danger("FFCC_" + player.getFaction() + "_deleteButtons", "Decline"));
        }

        return buttons;
    }

    public static void resolveRohDhnaRecycle(Game game, Player player, ButtonInteractionEvent event,
        String buttonID) {
        String unitName = buttonID.split("_")[1];
        new RemoveUnits().unitParsing(event, player.getColor(),
            game.getTileByPosition(game.getActiveSystem()), "1 " + unitName, game);
        UnitModel unit = Mapper.getUnit(unitName);
        int toGain = (int) unit.getCost() - 1;
        int before = player.getTg();
        player.setTg(before + toGain);

        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, toGain);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentation(true, false) + " recycled " + unit.getUnitEmoji() + " " + unit.getName()
                + " for " + toGain + " TG" + (toGain == 1 ? "" : "s") + " (" + before + "->" + player.getTg() + ")");

        event.getMessage().delete().queue();
    }
}
