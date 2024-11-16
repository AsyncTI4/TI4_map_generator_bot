package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.cardspn.ShowAllPN;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.context.ButtonContext;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;

public class ButtonHelperCommanders {

    @ButtonHandler("cheiranCommanderBlock_")
    public static void cheiranCommanderBlock(Player player, Game game, ButtonInteractionEvent event) {
        String msg2 = "";
        int oldThing = 0;
        int newThing = 0;
        if (player.getCommodities() > 0) {
            oldThing = player.getCommodities();
            player.setCommodities(oldThing - 1);
            newThing = player.getCommodities();
            msg2 = "commodity (" + oldThing + "->" + newThing + ")";
        } else if (player.getTg() > 0) {
            oldThing = player.getTg();
            player.setTg(oldThing - 1);
            newThing = player.getTg();
            msg2 = "TG (" + oldThing + "->" + newThing + ")";
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You can't afford the cost of Spc. Phquaiset, the Cheiran Commander, right now. Get more money ya broke crab.");
            return;
        }
        String msg = player.getFactionEmojiOrColor() + " used Spc. Phquaiset, the Cheiran Commander, spending 1 " + msg2
            + " to cancel 1 hit. They may do this once per round of combat.";
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
            + " you may exhaust 1 planet to gain TGs equal to its influence or resources.";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("mentakCommander_")
    public static void mentakCommander(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String color = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(color);
        if (p2 != null) {
            List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p2);
            String message = p2.getRepresentationUnfogged()
                + " You've been hit by"
                + (ThreadLocalRandom.current().nextInt(1000) == 0 ? ", you've been struck by" : "")
                + " S'ula Mentarion, the Mentak commander. Please select the PN you would like to send.";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Sent " + color + " the buttons for resolving S'ula Mentarion.");
            ButtonHelper.deleteTheOneButton(event);
        }

    }

    @ButtonHandler("arboCommanderBuild_")
    public static void arboCommanderBuild(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("arboCommanderBuild_", "");
        List<Button> buttons;
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        if (tile == null) {
            tile = game.getTileByPosition(planet);
        }
        buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "arboCommander",
            "placeOneNDone_dontskiparboCommander");
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
        ButtonHelperAgents.resolveArtunoCheck(player, game, count);
        String msg = player.getRepresentationUnfogged() + " used Knak Halfear, the Olradin Commander, to exhaust "
            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetID, game) + " and gain " + count + "TG" + (count == 1 ? "" : "s") + " " + player.gainTG(count);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("cymiaeCommanderRes_")
    public static void cymiaeCommanderRes(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        String msg = player.getFactionEmoji() + " will discard 1 " + Emojis.ActionCard + " AC to move or place 1 " + Emojis.mech + "mech on " + Helper.getPlanetRepresentation(planet, game);
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet), "mech " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard",
            ActionCardHelper.getDiscardActionCardButtons(player, false));
        event.getMessage().delete().queue();
    }

    public static void yinCommanderSummary(Player player, Game game) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "yincommander")) {
            return;
        }
        StringBuilder summary = new StringBuilder(player.getRepresentation() + " you could potentially use Brother Omar, the Yin Commander, to sacrifice 1 infantry and ignore the prerequisites for these techs (the bot did not check if you have the prerequisites otherwise):\n");
        List<String> techsSummed = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            for (String tech : p2.getTechs()) {
                if (!player.getTechs().contains(tech) && !techsSummed.contains(tech)) {
                    TechnologyModel model = Mapper.getTech(tech);
                    if (model.getFaction().isPresent() || model.getFaction().isPresent() || model.getRequirements().isEmpty() || model.getRequirements().isEmpty()) {
                        continue;
                    }
                    techsSummed.add(tech);
                    summary.append(model.getRepresentation(false)).append("\n");
                }
            }
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
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + unitHName, game);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("resolveMykoCommander")
    public static void mykoCommanderUsage(Player player, Game game, ButtonInteractionEvent event) {
        String msg = player.getFactionEmoji() + " spent 1 ";
        if (player.getCommodities() > 0) {
            msg += Emojis.comm + " commodity (" + player.getCommodities() + "->" + (player.getCommodities() - 1) + ") ";
            player.setCommodities(player.getCommodities() - 1);
        } else {
            msg += Emojis.tg + "TG (" + player.gainTG(-1) + ") ";
        }
        msg += " to cancel one hit";
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
                    .map(uk -> Buttons.gray(prefix + uk.asyncID(), uk.getUnitType().humanReadableName() + " " + planetName).withEmoji(Emoji.fromFormatted(uk.unitEmoji())))
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
            String unitStringTo = unitType.value + " " + unitHolderTo;
            new RemoveUnits().unitParsing(context.getEvent(), player.getColor(), from, unitStringFrom, game);
            new AddUnits().unitParsing(context.getEvent(), player.getColor(), to, unitStringTo, game);

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
        String msg = player.getFactionEmojiOrColor() + " Automatically used Tungstantus, the Ul commander, to gain 1" + Emojis.tg + "TG " + player.gainTG(1) + ".";
        if (Helper.getPlayerFromAbility(game, "pillage") != null) {
            msg += "\nThis tg can be spent before " + Emojis.Mentak + "**Pillage** resolves, since it is a when.";
        }
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        player.addSpentThing(msg);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void resolveLetnevCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "letnevcommander")) {
            if (!ButtonHelperAbilities.canBePillaged(player, game, player.getTg() + 1) || game.isFowMode()) {
                String mMessage = player.getRepresentationUnfogged() + " Since you have Rear Admiral Farran, the Letnev commander, unlocked, 1TG has been added automatically (" + player.gainTG(1) + ").";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            } else {
                String mMessage = player.getRepresentationUnfogged() + " Since you have Rear Admiral Farran, the Letnev commander, unlocked, you may gain 1TG, but you are in pillage range, so this has not been done automatically.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("gain1tgFromLetnevCommander", "Gain 1TG", Emojis.tg));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage, buttons);
            }
        }
    }

    @ButtonHandler("placeGhostCommanderFF_")
    public static void resolveGhostCommanderPlacement(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        new AddUnits().unitParsing(event, player.getColor(), tile, "fighter", game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " placed 1 fighter in " + tile.getRepresentation() + " using Sai Seravus, the Creuss commander");
    }

    @ButtonHandler("placeKhraskCommanderInf_")
    public static void resolveKhraskCommanderPlacement(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        new AddUnits().unitParsing(event, player.getColor(), tile, "inf", game);
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
            if (planetReal != null && planetReal.getOriginalPlanetType() != null
                && player.getPlanetsAllianceMode().contains(planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static void resolveMuaatCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event) {
        resolveMuaatCommanderCheck(player, game, event, "unknown trigger");
    }

    public static void resolveMuaatCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event, String reason) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander")) {
            if (!ButtonHelperAbilities.canBePillaged(player, game, player.getTg() + 1) || game.isFowMode()) {
                String message = player.getRepresentationUnfogged() + " you gained a " + Emojis.tg + " from " + Emojis.MuaatCommander + "Magmus, the Muaat Commander, " + player.gainTG(1) + " (" + reason + ")";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelperAbilities.pillageCheck(player, game);
                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            } else {
                String mMessage = player.getRepresentationUnfogged()
                    + " you have Magmus, the Muaat Commander, unlocked, you may gain 1TG, but you are in Pillage range, so this has not been done automatically.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("gain1tgFromMuaatCommander", "Gain 1TG", Emojis.tg));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage, buttons);
            }
        }
        if (player.hasUnit("kolume_mech")) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (uH.getUnitDamageCount(UnitType.Mech, player.getColorID()) > 0) {
                        uH.removeUnitDamage(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), uH.getUnitDamageCount(UnitType.Mech, player.getColorID()));
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " repaired damaged mech in " + tile.getRepresentation() + " due to spending a strategy token");
                    }
                }
            }
        }
        if (player.hasUnit("kolume_mech")) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (uH.getUnitDamageCount(UnitType.Mech, player.getColor()) > 0) {
                        uH.removeUnitDamage(Mapper.getUnitKey(AliasHandler.resolveUnit("mech"), player.getColorID()), uH.getUnitDamageCount(UnitType.Mech, player.getColorID()));
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
                    buttons.add(Buttons.green("draw_2_ACDelete", "Draw 2 ACs (With Scheming)"));
                } else {
                    buttons.add(Buttons.green("draw_1_ACDelete", "Draw 1 AC"));
                }
                buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " You gained tech while having Nekro Acidos, the Nekro commander, use buttons to resolve. ",
                    buttons);
            } else {
                if (player.hasAbility("technological_singularity")) {
                    int count = 0;
                    for (String nekroTech : player.getTechs()) {
                        if ("vax".equalsIgnoreCase(nekroTech) || "vay".equalsIgnoreCase(nekroTech)) {
                            continue;
                        }
                        if (!Mapper.getTech(AliasHandler.resolveTech(nekroTech)).getFaction().orElse("").isEmpty()) {
                            count = count + 1;
                        }

                    }
                    if (count > 2) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                            "# " + player.getRepresentationUnfogged()
                                + " heads up, that was your 3rd faction tech, you may wanna lose one with /tech remove");
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " You gained tech while having Nekro Acidos, the Nekro commander, but since it is a faction tech and you used one of your Valefars, you didn't technically \"gain\" a new tech and therefore you do not draw an AC.");
                }
            }
        }
    }

    @ButtonHandler("utilizeSolCommander_")
    public static void resolveSolCommander(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        ButtonHelper.deleteTheOneButton(event);
        Tile tile = game.getTileFromPlanet(planet);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planet, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed 1 infantry on "
                + Helper.getPlanetRepresentation(planet, game) + " using Claire Gibson, the Sol Commander.");
    }

    @ButtonHandler("yssarilcommander_")
    public static void yssarilCommander(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("yssarilcommander_", "");
        String enemyFaction = buttonID.split("_")[1];
        Player enemy = game.getPlayerFromColorOrFaction(enemyFaction);
        if (enemy == null)
            return;
        String message = "";
        String type = buttonID.split("_")[0];
        if ("ac".equalsIgnoreCase(type)) {
            ActionCardHelper.showAll(enemy, player, game);
            message = " used So Ata, the Yssaril commander, to look at ACs";
        }
        if ("so".equalsIgnoreCase(type)) {
            new ShowAllSO().showAll(enemy, player, game);
            message = " used So Ata, the Yssaril commander, to look at SOs";
        }
        if ("pn".equalsIgnoreCase(type)) {
            new ShowAllPN().showAll(enemy, player, game, false);
            message = " used So Ata, the Yssaril commander, to look at PNs ";
        }
        message = message + " of " + enemy.getRepresentation(false, false);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + message);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    @ButtonHandler("pay1tgforKeleres")
    public static void pay1tgToUnlockKeleres(Player player, Game game, ButtonInteractionEvent event) {
        boolean unleash = ThreadLocalRandom.current().nextInt(20) == 0;
        CommanderUnlockCheck.checkPlayer(player, "keleres");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " paid 1TG to " + (unleash ? "unleash" : "unlock") + " Suffi An, the Keleres commander " + player.gainTG(-1));
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
        new RemoveUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet2), "1 " + mechorInf + " " + planet2, game);

        Tile tile = game.getTileFromPlanet(planet1);
        tile = MoveUnits.flipMallice(event, tile, game);
        planet1 = planet1.replace("lockedm", "m");
        new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet1), "1 " + mechorInf + " " + planet1, game);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        CommanderUnlockCheck.checkPlayer(player, "naaz");
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
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, game);

            for (String pos2 : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                Tile tile2 = game.getTileByPosition(pos2);
                if (AddCC.hasCC(event, player.getColor(), tile2) && !game.isDominusOrb()
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
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, game);
                    if (numInf > 0 && !planetId.equalsIgnoreCase(planetId2)) {
                        String id = "sardakkcommander_infantry_" + planetId + "_" + planetId2;
                        String label = "Commit 1 infantry from " + planetRepresentation2 + " to " + planetRepresentation;
                        buttons.add(Buttons.green(id, label, Emojis.Sardakk));
                    }
                    if (numMechs > 0 && !planetId.equalsIgnoreCase(planetId2)) {
                        String id = "sardakkcommander_mech_" + planetId + "_" + planetId2;
                        String label = "Commit 1 mech from " + planetRepresentation2 + " to " + planetRepresentation;
                        buttons.add(Buttons.blue(id, label, Emojis.Sardakk));
                    }
                }
            }
        }
        return buttons;
    }

}
