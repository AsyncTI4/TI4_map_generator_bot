package ti4.helpers;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.planet.PlanetExhaust;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.PlanetModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.service.agenda.LookAgendaService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperCommanders {

    @ButtonHandler("cheiranCommanderBlock_")
    public static void cheiranCommanderBlock(Player player, Game game, ButtonInteractionEvent event) {
        String msg2;
        int oldThing;
        int newThing;
        if (player.getCommodities() > 0) {
            oldThing = player.getCommodities();
            player.setCommodities(oldThing - 1);
            newThing = player.getCommodities();
            msg2 = "commodity (" + oldThing + "->" + newThing + ")";
        } else if (player.getTg() > 0) {
            oldThing = player.getTg();
            player.setTg(oldThing - 1);
            newThing = player.getTg();
            msg2 = "trade good (" + oldThing + "->" + newThing + ")";
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You can't afford the cost of Spc. Phquaiset, the Cheiran commander, right now. Get more money, ya broke crab.");
            return;
        }
        String msg = player.getFactionEmojiOrColor() + " used Spc. Phquaiset, the Cheiran commander, spending 1 " + msg2
            + " to cancel 1 hit.\n-# They may do this once per round of combat.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("kortaliCommanderBlock_")
    public static void kortaliCommanderBlock(Player player, Game game, ButtonInteractionEvent event) {
        String msg = player.getFactionEmojiOrColor()
            + " used Queen Lorena, the Kortali commander, to cancel 1 hit in the first round of combat.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void olradinCommanderStep1(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getReadiedPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (Constants.MECATOLS.contains(planet) || tile == null || tile.isHomeSystem()) {
                continue;
            }
            buttons.add(Buttons.green("olradinCommanderStep2_" + planet,
                Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.getRepresentationUnfogged()
            + " you may exhaust 1 planet to gain trade goods equal to its influence or resources.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("mentakCommander_")
    public static void mentakCommander(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String color = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(color);
        if (p2 != null) {
            List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
            String message = p2.getRepresentationUnfogged()
                + " You've been hit by"
                + (RandomHelper.isOneInX(1000) ? ", you've been struck by" : "")
                + " S'ula Mentarion, the Mentak commander. Please select the promissory note you would most like to send and/or least like to keep.";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Sent " + color + " the buttons for resolving S'ula Mentarion, the Mentak commander.");
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    @ButtonHandler("arboCommanderBuild_")
    public static void arboCommanderBuild(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("arboCommanderBuild_", "");
        List<Button> buttons;
        Tile tile = TileHelper.getTile(event, planet, game);
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "arboCommander", "placeOneNDone_dontskiparboCommander");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit. " + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);

    }

    @ButtonHandler("olradinCommanderStep2_")
    public static void olradinCommanderStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetID = buttonID.split("_")[1];
        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
        int count = Math.max(planet.getInfluence(), planet.getResources());
        PlanetExhaust.doAction(player, planetID, game);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, count);
        String msg = player.getRepresentationUnfogged() + " used Knak Halfear, the Olradin Commander, to exhaust "
            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game) + " and gain "
            + count + " trade good" + (count == 1 ? "" : "s") + " " + player.gainTG(count);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("cymiaeCommanderRes_")
    public static void cymiaeCommanderRes(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " will discard 1 action card to move or place 1 " + UnitEmojis.mech + "mech on " + Helper.getPlanetRepresentation(planet, game) + ".";
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "mech " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));
        event.getMessage().delete().queue();
    }

    public static void yinCommanderSummary(Player player, Game game) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            return;
        }
        StringBuilder summary = new StringBuilder(player.getRepresentation() + " you could potentially use Brother Omar, the Yin Commander, to sacrifice 1 infantry"
            + " and ignore the prerequisites for these technologies (the bot did not check if you have the prerequisites otherwise):\n");
        List<String> techsSummed = getVeldyrCommanderTechs(player, game, true);
        for (String tech : techsSummed) {
            TechnologyModel model = Mapper.getTech(tech);
            summary.append(model.getRepresentation(false)).append("\n");
        }
        if (!techsSummed.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), summary.toString());
        }
    }

    public static List<String> getVeldyrCommanderTechs(Player player, Game game, boolean yin) {
        List<String> techsSummed = new ArrayList<>();
        if (yin) {
            if (!game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
                return techsSummed;
            }
        } else {
            if (!game.playerHasLeaderUnlockedOrAlliance(player, "veldyrcommander")) {
                return techsSummed;
            }
        }
        for (Player p2 : ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player)) {
            for (String tech : p2.getTechs()) {
                if (!player.getTechs().contains(tech) && !techsSummed.contains(tech)) {
                    TechnologyModel model = Mapper.getTech(tech);
                    if (model.getFaction().isPresent() || model.getFaction().isPresent() || model.getRequirements().isEmpty() || model.getRequirements().isEmpty()) {
                        continue;
                    }
                    techsSummed.add(tech);
                }
            }
        }
        return techsSummed;
    }

    public static void veldyrCommanderSummary(Player player, Game game) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "veldyrcommander")) {
            return;
        }
        StringBuilder summary = new StringBuilder(player.getRepresentation() + " you could potentially use Vera Khage, the Veldyr Commander,"
            + " to ignore one prerequisite for these technologies (the bot did not check if you have the prerequisites otherwise):\n");
        List<String> techsSummed = getVeldyrCommanderTechs(player, game, false);
        for (String tech : techsSummed) {
            TechnologyModel model = Mapper.getTech(tech);
            summary.append(model.getRepresentation(false)).append("\n");
        }
        if (!techsSummed.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), summary.toString());
        }
    }

    @ButtonHandler("yinCommanderStep1_")
    public static void yinCommanderStep1(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    buttons
                        .add(
                            Buttons.green(
                                "yinCommanderRemoval_" + tile.getPosition() + "_" + unitHolder.getName(),
                                "Remove 1 infantry from "
                                    + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
                }
            }
        }
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " use buttons to remove 1 infantry", buttons);
    }

    @ButtonHandler("yinCommanderRemoval_")
    public static void resolveYinCommanderRemoval(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " removed 1 infantry from "
                + ButtonHelper.getUnitHolderRep(unitHolder, tile, game) + " using Brother Omar, the Yin Commander.");
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 infantry " + unitHName);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("resolveMykoCommander")
    public static void mykoCommanderUsage(Player player, Game game, ButtonInteractionEvent event) {
        String msg = player.getFactionEmoji() + " spent 1 ";
        if (player.getCommodities() > 0) {
            msg += MiscEmojis.comm + " commodity (" + player.getCommodities() + "->" + (player.getCommodities() - 1) + ") ";
            player.setCommodities(player.getCommodities() - 1);
        } else {
            msg += " trade good (" + player.gainTG(-1) + ") ";
        }
        msg += " to cancel one hit.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @ButtonHandler("ravenMigration")
    public static void handleRavenCommander(ButtonContext context) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        String buttonID = context.getButtonID();

        String part1 = "ravenMigration";
        String part2 = part1 + "_" + RegexHelper.posRegex(game, "posfrom");
        String part3 = part2 + "_" + RegexHelper.unitHolderRegex(game, "planetfrom") + "_" + RegexHelper.unitTypeRegex();
        String part4 = part3 + "_" + RegexHelper.posRegex(game, "posto");
        String part5 = part4 + "_" + RegexHelper.unitHolderRegex(game, "planetto");

        Matcher matcher;
        String newMessage = null;
        List<Button> newButtons = new ArrayList<>();
        if (Pattern.compile(part1).matcher(buttonID).matches()) {
            String message = player.getRepresentation() + " Choose a tile to migrate from:";
            Predicate<Tile> pred = t -> t.containsPlayersUnitsWithModelCondition(player, um -> !um.getIsStructure());
            List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(player, game, buttonID, pred, false);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteTheOneButton(context.getEvent());

        } else if ((matcher = Pattern.compile(part2).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("posfrom"));
            newMessage = player.getRepresentation() + " You are migrating from " + from.getRepresentationForButtons(game, player) + ". Choose a unit you'd like to move:";
            for (UnitHolder uh : from.getUnitHolders().values()) {
                PlanetModel planet = Mapper.getPlanet(uh.getName());
                String planetName = planet == null ? uh.getName() : planet.getName();

                Set<UnitKey> keys = uh.getUnits().keySet().stream()
                    .filter(unit -> uh.getUnits().get(unit) > 0)
                    .filter(player::unitBelongsToPlayer)
                    .collect(Collectors.toSet());
                String prefix = player.getFinsFactionCheckerPrefix() + "ravenMigration_" + from.getPosition() + "_" + uh.getName() + "_";
                keys.stream().filter(uk -> !player.getUnitFromUnitKey(uk).getIsStructure())
                    .map(uk -> Buttons.gray(prefix + uk.asyncID(), uk.getUnitType().humanReadableName() + " " + planetName, uk.unitEmoji()))
                    .forEach(newButtons::add);
            }

        } else if ((matcher = Pattern.compile(part3).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("posfrom"));
            String unitHolderFrom = matcher.group("planetfrom");
            PlanetModel planet = Mapper.getPlanet(unitHolderFrom);
            String planetName = planet == null ? unitHolderFrom : planet.getName();
            UnitType unitType = Units.findUnitType(matcher.group("unittype"));
            boolean ship = player.getUnitFromAsyncID(unitType.getValue()).getIsShip();

            String prefix = player.getFinsFactionCheckerPrefix() + "ravenMigration_" + from.getPosition() + "_" + unitHolderFrom + "_" + unitType.value + "_";
            String suffix = ship ? "_space" : "";

            newMessage = player.getRepresentation() + " You are migrating a " + unitType.humanReadableName() + " from " + planetName + " in " + from.getRepresentationForButtons(game, player) + ".";
            newMessage += "\nChoose your destination:";
            for (Tile t : game.getTileMap().values()) {
                boolean hasPlanet = t.getPlanetUnitHolders().stream().anyMatch(uh -> player.hasPlanet(uh.getName()));
                if (t.containsPlayersUnits(player) || hasPlanet) {
                    newButtons.add(Buttons.green(prefix + t.getPosition() + suffix, t.getRepresentationForButtons(game, player)));
                }
            }

        } else if ((matcher = Pattern.compile(part4).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("posfrom"));
            String unitHolderFrom = matcher.group("planetfrom");
            PlanetModel planet = Mapper.getPlanet(unitHolderFrom);
            String planetName = planet == null ? unitHolderFrom : planet.getName();
            UnitType unitType = Units.findUnitType(matcher.group("unittype"));
            Tile to = game.getTileByPosition(matcher.group("posto"));

            String prefix = player.getFinsFactionCheckerPrefix() + "ravenMigration_" + from.getPosition() + "_" + unitHolderFrom + "_" + unitType.value + "_" + to.getPosition() + "_";

            newMessage = player.getRepresentation() + " You are migrating a " + unitType.humanReadableName() + " from " + planetName + " in " + from.getRepresentationForButtons(game, player) + ".";
            newMessage += "\nYour destination is " + to.getRepresentationForButtons(game, player);
            newMessage += "\nChoose the planet where your unit will go:";
            for (UnitHolder uh : to.getUnitHolders().values()) {
                PlanetModel planetTo = Mapper.getPlanet(uh.getName());
                String planetNameTo = planetTo == null ? uh.getName() : planetTo.getName();
                newButtons.add(Buttons.green(prefix + uh.getName(), planetNameTo));
            }

        } else if ((matcher = Pattern.compile(part5).matcher(buttonID)).matches()) {
            Tile from = game.getTileByPosition(matcher.group("posfrom"));
            String unitHolderFrom = matcher.group("planetfrom");
            PlanetModel planet = Mapper.getPlanet(unitHolderFrom);
            String planetName = planet == null ? unitHolderFrom : planet.getName();
            UnitType unitType = Units.findUnitType(matcher.group("unittype"));
            Tile to = game.getTileByPosition(matcher.group("posto"));
            String unitHolderTo = matcher.group("planetto");
            PlanetModel planetTo = Mapper.getPlanet(unitHolderTo);
            String planetNameTo = planetTo == null ? unitHolderFrom : planetTo.getName();

            String unitStringFrom = unitType.value + " " + unitHolderFrom;
            RemoveUnitService.removeUnits(context.getEvent(), from, game, player.getColor(), unitStringFrom);
            String unitStringTo = unitType.value + " " + unitHolderTo;
            AddUnitService.addUnits(context.getEvent(), to, game, player.getColor(), unitStringTo);

            String fromLang = "from " + planetName + (planetName.equals("space") ? " in " + from.getRepresentationForButtons(game, player) : "");
            String toLang = planetNameTo.equals("space") ? "in space of tile " + to.getRepresentationForButtons(game, player) : "on " + planetNameTo;
            String message = player.getRepresentation() + " your " + unitType.humanReadableName() + " successfully migrated " + fromLang + ", and has landed itself " + toLang;

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(context.getEvent());
        }

        if (newMessage != null) {
            // edit the message with the new partX buttons
            context.getEvent().getMessage().editMessage(newMessage).setComponents(ButtonHelper.turnButtonListIntoActionRowList(newButtons)).queue();
        }
    }

    @ButtonHandler("titansCommanderUsage")
    public static void titansCommanderUsage(ButtonInteractionEvent event, Game game, Player player) {
        String msg = player.getFactionEmojiOrColor() + " Automatically used Tungstantus, the Ul commander, to gain 1 trade good " + player.gainTG(1) + ".";
        if (Helper.getPlayerFromAbility(game, "pillage") != null) {
            msg += "\nThis trade good can be spent before " + FactionEmojis.Mentak + "**Pillage** resolves, since it is a \"when\".";
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        player.addSpentThing(msg);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void resolveLetnevCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "letnevcommander")) {
            if (!ButtonHelperAbilities.canBePillaged(player, game, player.getTg() + 1) || game.isFowMode()) {
                String mMessage = player.getRepresentationUnfogged() + " Since you have Rear Admiral Farran, the Letnev commander, unlocked,"
                    + " 1 trade good has been added automatically " + player.gainTG(1) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, 1);
            } else {
                String mMessage = player.getRepresentationUnfogged() + ", you have Rear Admiral Farran, the Letnev commander, unlocked,"
                    + " so you __may__ gain 1 trade good, but since you are in **Pillage** range, this has not been done automatically.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("gain1tgFromLetnevCommander", "Gain 1 Trade Good", MiscEmojis.tg));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), mMessage, buttons);
            }
        }
    }

    @ButtonHandler("placeGhostCommanderFF_")
    public static void resolveGhostCommanderPlacement(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "fighter");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " placed 1 fighter in "
            + tile.getRepresentation() + " using Sai Seravus, the Creuss commander.");
    }

    @ButtonHandler("placeKhraskCommanderInf_")
    public static void resolveKhraskCommanderPlacement(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "inf");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " placed 1 infantry in " + tile.getRepresentation()
                + " using Hkot Tokal, the Khrask Commander.");
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static List<Button> resolveFlorzenCommander(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            Planet planetReal = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (planetReal != null && isNotBlank(planetReal.getOriginalPlanetType()) && player.getPlanetsAllianceMode().contains(planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static List<Button> getUydaiCommanderButtons(Game game, boolean ableToBottom, Player player) {
        List<Button> buttons = new ArrayList<>();
        String abletobot = "_no";
        if (ableToBottom) {
            abletobot = "_yes";
        }
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_industrial" + abletobot, "Industrial", ExploreEmojis.Industrial));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_hazardous" + abletobot, "Hazardous", ExploreEmojis.Hazardous));
        buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_cultural" + abletobot, "Cultural", ExploreEmojis.Cultural));
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_frontier" + abletobot, "Frontier", ExploreEmojis.Frontier));
        buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_relics" + abletobot, "Relic", ExploreEmojis.Relic));
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_secrets" + abletobot, "Secret", CardEmojis.SecretObjective));
        buttons.add(Buttons.blue(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_agenda" + abletobot, "Agenda", CardEmojis.Agenda));
        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "uydaiCommanderLook_acs" + abletobot, "Action Card", CardEmojis.ActionCard));
        return buttons;
    }

    @ButtonHandler("uydaiCommander")
    public static void uydaiCommander(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        if (player.getTg() < 1) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() + " you need at least 1 tg to use this ability");
            return;
        }
        if (game.getActivePlayer() != player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() + " you need to be the active player to use this ability");
            return;
        }
        player.setTg(player.getTg() - 1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() + " is paying 1tg to look at the top card of a deck");
        List<Button> buttons = ButtonHelperCommanders.getUydaiCommanderButtons(game, false, player);
        String message = player.getRepresentationUnfogged() + " select which deck you wish to look at the top of.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("uydaiCommanderLook_")
    public static void uydaiCommanderLook(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String target = buttonID.split("_")[1];
        String ableToBot = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() + " is choosing to look at the top of the " + event.getButton().getLabel() + " deck");
        event.getMessage().delete().queue();
        switch (target) {
            case "industrial", "hazardous", "frontier", "cultural" -> {
                ButtonHelperFactionSpecific.resolveExpLook(player, game, event, target);
            }
            case "agenda" -> {
                LookAgendaService.lookAtAgendas(game, player, 1, false);
            }
            case "relics" -> {
                List<String> relicDeck = game.getAllRelics();
                if (relicDeck.isEmpty()) {
                    MessageHelper.sendMessageToEventChannel(event, "Relic deck is empty");
                    return;
                }
                String relicID = relicDeck.getFirst();
                RelicModel relicModel = Mapper.getRelic(relicID);
                String sb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n" + relicModel.getSimpleRepresentation();
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);
            }
            case "secrets" -> {
                List<String> secretDeck = game.getSecretObjectives();
                String secretID = secretDeck.getFirst();
                SecretObjectiveModel secretModel = Mapper.getSecretObjective(secretID);
                String sb = "**Secret - Look at Top**\n" + player.getRepresentation() + "\n" + secretModel.getName() + "\n" + secretModel.getText();
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);
            }
            case "acs" -> {
                List<String> acDeck = game.getActionCards();
                String acID = acDeck.getFirst();
                ActionCardModel acModel = Mapper.getActionCard(acID);
                String sb = "**Action Card - Look at Top**\n" + player.getRepresentation() + "\n" + acModel.getRepresentation();
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb);
            }
        }
        if (ableToBot.equalsIgnoreCase("yes")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "uydaiCommanderBottom_" + target, "Bottom It"));
            buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Leave it on top"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation() + " would you like to bottom the card or leave it on top?", buttons);
        }
    }

    @ButtonHandler("uydaiCommanderBottom_")
    public static void uydaiCommanderBottom(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String target = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationNoPing() + " is choosing to bottom the card they saw.");
        event.getMessage().delete().queue();
        switch (target) {
            case "industrial", "hazardous", "frontier", "cultural" -> {
                game.putExploreBottom(game.getExploreDeck(target).getFirst());
            }
            case "agenda" -> {
                AgendaHelper.putBottom(game.getAgendas().getFirst(), game);
            }
            case "relics" -> {
                List<String> relicDeck = game.getAllRelics();
                if (relicDeck.isEmpty()) {
                    MessageHelper.sendMessageToEventChannel(event, "Relic deck is empty");
                    return;
                }
                String relicID = relicDeck.getFirst();
                game.putRelicBottom(relicID);
            }
            case "secrets" -> {
                List<String> secretDeck = game.getSecretObjectives();
                String secretID = secretDeck.getFirst();
                game.putSOBottom(secretID);
            }
            case "acs" -> {
                List<String> acDeck = game.getActionCards();
                String acID = acDeck.getFirst();
                game.putACBottom(acID);
            }
        }
    }

    public static void resolveMuaatCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event) {
        resolveMuaatCommanderCheck(player, game, event, "unknown trigger");
    }

    public static List<Button> getPharadnCommanderUnlockButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            if (uH != null) {
                int amountToKill = uH.getUnitCount(UnitType.Infantry, player.getColor());
                if (amountToKill > 1) {
                    buttons.add(Buttons.gray("pharadnCommanderUnlockKill_" + planet, Helper.getPlanetRepresentation(planet, game)));
                }
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        return buttons;
    }

    public static void resolveMuaatCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event, String reason) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander")) {
            if (!ButtonHelperAbilities.canBePillaged(player, game, player.getTg() + 1) || game.isFowMode()) {
                String message = player.getRepresentationUnfogged() + " you gained a trade good from Magmus, the Muaat Commander, " + player.gainTG(1) + " (" + reason + ")";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, 1);
            } else {
                String mMessage = player.getRepresentationUnfogged() + ", you have Magmus, the Muaat Commander, unlocked,"
                    + " so you __may__ gain 1 trade good, but since you are in **Pillage** range, this has not been done automatically.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("gain1tgFromMuaatCommander", "Gain 1 Trade Good", MiscEmojis.tg));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), mMessage, buttons);
            }
        }
        if (player.hasUnit("kolume_mech")) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (uH.getDamagedUnitCount(UnitType.Mech, player.getColorID()) > 0) {
                        uH.removeDamagedUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), uH.getDamagedUnitCount(UnitType.Mech, player.getColorID()));
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " repaired damaged mech in " + tile.getRepresentation() + " due to spending a strategy token");
                    }
                }
            }
        }
        if (player.hasUnit("kolume_mech")) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (uH.getDamagedUnitCount(UnitType.Mech, player.getColor()) > 0) {
                        uH.removeDamagedUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), uH.getDamagedUnitCount(UnitType.Mech, player.getColorID()));
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " repaired damaged mech in " + tile.getRepresentation() + " due to spending a strategy token");
                    }
                }
            }
        }
    }

    public static void resolveNekroCommanderCheck(Player player, String tech, Game game) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "nekrocommander")) {
            if (Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse("").isEmpty()
                || !player.hasAbility("technological_singularity")) {
                List<Button> buttons = new ArrayList<>();
                if (player.hasAbility("scheming")) {
                    buttons.add(Buttons.green("draw_2_ACDelete", "Draw 2 Action Cards"));
                    buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + ", you gained a technology while having Nekro Acidos, the Nekro commander."
                            + " Use the buttons to draw 2 action cards (**Scheming** increases this from the normal 1 action card).",
                        buttons);
                } else {
                    buttons.add(Buttons.green("draw_1_ACDelete", "Draw 1 Action Card"));
                    buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + ", you gained a technology while having Nekro Acidos, the Nekro commander. Use the buttons to draw 1 action card.",
                        buttons);
                }
            } else {
                if (player.hasAbility("technological_singularity")) {
                    int count = 0;
                    for (String nekroTech : player.getTechs()) {
                        if ("vax".equalsIgnoreCase(nekroTech) || "vay".equalsIgnoreCase(nekroTech)) {
                            continue;
                        }
                        if (!Mapper.getTech(AliasHandler.resolveTech(nekroTech)).getFaction().orElse("").isEmpty()) {
                            count += 1;
                        }

                    }
                    if (count > 2) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", heads up, that was your third faction technology, and so you may wish to lose one with `/tech remove`.");
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + ", you acquired access to a technology while having Nekro Acidos, the Nekro commander, but since it is a faction technology and so you used one of your Valefar Assimilators,"
                            + " the number of technologies you owned did not increase, and therefore you do not draw an action card.");
                }
            }
        }
    }

    @ButtonHandler("unlockPharadnCommander")
    public static void unlockPharadnCommander(Player player, Game game, ButtonInteractionEvent event) {
        CommanderUnlockCheckService.checkPlayer(player, "pharadn");
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation() + " use buttons to destroy infantry on 5 planets in order to unlock the commander", getPharadnCommanderUnlockButtons(player, game));
    }

    @ButtonHandler("pharadnCommanderUnlockKill_")
    public static void pharadnAgentKill(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planet = buttonID.split("_")[1];
        String message = player.getRepresentation() + " chose to destroy 2 infantry on " + Helper.getPlanetRepresentation(planet, game) + " as part of the Pharadn Commander unlock";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        int amountToKill = 2;
        if (player.hasInf2Tech()) {
            ButtonHelper.resolveInfantryDeath(player, amountToKill);
        }
        RemoveUnitService.removeUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), amountToKill + " inf " + planet);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("utilizeSolCommander_")
    public static void resolveSolCommander(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        ButtonHelper.deleteTheOneButton(event);
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 inf " + planet);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 infantry on "
                + Helper.getPlanetRepresentation(planet, game) + " using Claire Gibson, the Sol Commander.");
    }

    @ButtonHandler("utilizePharadnCommander_")
    public static void utilizePharadnCommander(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        for (Player p2 : game.getPlayers().values()) {
            if (p2.getColor() == null || p2 == player || player.getAllianceMembers().contains(p2.getFaction())) {
                continue; // fix indoctrinate vs neutral
            }
            if (FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet) && !player.getAllianceMembers().contains(p2.getFaction())) {
                RemoveUnitService.removeUnits(event, tile, game, p2.getColor(), "1 infantry " + planet);
                if (player.hasInf2Tech()) {
                    ButtonHelper.resolveInfantryDeath(p2, 1);
                }
                break;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " destroyed 1 opposing infantry on "
                + Helper.getPlanetRepresentation(planet, game) + " using the Pharadn Commander.");
    }

    @ButtonHandler("yssarilcommander_")
    public static void yssarilCommander(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("yssarilcommander_", "");
        String enemyFaction = buttonID.split("_")[1];
        Player enemy = game.getPlayerFromColorOrFaction(enemyFaction);
        if (enemy == null)
            return;
        String message = player.getFactionEmoji() + " used So Ata, the Yssaril commander, to look at the ";
        String type = buttonID.split("_")[0];
        if ("ac".equalsIgnoreCase(type)) {
            ActionCardHelper.showAll(enemy, player, game);
            message += "action card";
        }
        if ("so".equalsIgnoreCase(type)) {
            SecretObjectiveHelper.showAll(enemy, player, game);
            message += "secret objective";
        }
        if ("pn".equalsIgnoreCase(type)) {
            PromissoryNoteHelper.showAll(enemy, player, game);
            message += "promissory note";
        }
        message += " hand of " + enemy.getRepresentation(false, false) + ".";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("pay1tgforKeleres")
    public static void pay1tgToUnlockKeleres(Player player, Game game, ButtonInteractionEvent event) {
        CommanderUnlockCheckService.checkPlayer(player, "keleres");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " paid 1 trade good to unleash Suffi An, the Keleres commander "
            + player.gainTG(-1) + ".");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("sardakkcommander_")
    public static void resolveSardakkCommander(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String mechorInf = buttonID.split("_")[1];
        String planet1 = buttonID.split("_")[2];
        String planet2 = buttonID.split("_")[3];
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, game);
        String planetRepresentation = Helper.getPlanetRepresentation(planet1, game);

        String message = player.getFactionEmojiOrColor() + " moved 1 " + mechorInf + " from " + planetRepresentation2 + " to "
            + planetRepresentation + " using G'hom Sek'kus, the N'orr Commander.";
        RemoveUnitService.removeUnits(event, game.getTileFromPlanet(planet2), game, player.getColor(), "1 " + mechorInf + " " + planet2);

        FlipTileService.flipTileIfNeeded(event, game.getTileFromPlanet(planet1), game);
        planet1 = planet1.replace("lockedm", "m");
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet1), game, player.getColor(), "1 " + mechorInf + " " + planet1);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static List<Button> getSardakkCommanderButtons(Game game, Player player, GenericInteractionCreateEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planetId = planetReal.getName();
            String planetName = Helper.getPlanetName(planetId);

            for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                Tile tile2 = game.getTileByPosition(pos2);
                if (CommandCounterHelper.hasCC(event, player.getColor(), tile2) && !game.isDominusOrb()
                    && tile2 != tile) {
                    continue;
                }
                for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                    if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                        continue;
                    }
                    Planet planetReal2 = (Planet) planetUnit2;
                    int numMechs = 0;
                    int numInf = 0;
                    String colorID = Mapper.getColorID(player.getColor());
                    if (planetUnit2.getUnits() != null) {
                        numMechs = planetUnit2.getUnitCount(UnitType.Mech, colorID);
                        numInf = planetUnit2.getUnitCount(UnitType.Infantry, colorID);
                    }
                    String planetId2 = planetReal2.getName();
                    String planetName2 = Helper.getPlanetName(planetId2);
                    if (numInf > 0 && !planetId.equalsIgnoreCase(planetId2)) {
                        String id = "sardakkcommander_infantry_" + planetId + "_" + planetId2;
                        String label = "1 Infantry From " + planetName2 + " To " + planetName + " With G'hom Sek'kus";
                        buttons.add(Buttons.green(id, label, FactionEmojis.Sardakk));
                    }
                    if (numMechs > 0 && !planetId.equalsIgnoreCase(planetId2)) {
                        String id = "sardakkcommander_mech_" + planetId + "_" + planetId2;
                        String label = "1 Mech From " + planetName2 + " To " + planetName + " With G'hom Sek'kus";
                        buttons.add(Buttons.blue(id, label, FactionEmojis.Sardakk));
                    }
                }
            }
        }
        return buttons;
    }

}
