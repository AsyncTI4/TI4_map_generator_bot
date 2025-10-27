package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.unit.DestroyUnitService;

public class ButtonHelperTwilightsFallActionCards {

    @ButtonHandler("resolveEngineer")
    public static void resolveEngineer(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("engineerACSplice", "True");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + " added 2 more cards to the splice and you should be prompted to discard 2 cards after choosing yours.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveLocust")
    public static void resolveLocust(Game game, Player player, ButtonInteractionEvent event) {
        List<String> tilesSeen = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(player, tile)) {
                for (String tilePos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true)) {
                    if (tilesSeen.contains(tilePos)) {
                        continue;
                    } else {
                        tilesSeen.add(tilePos);
                    }
                    Tile tile2 = game.getTileByPosition(tilePos);
                    for (UnitHolder uH : tile2.getUnitHolders().values()) {
                        String label;
                        for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                            if (uH.getName().equals("space")) {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                        + tile2.getRepresentationForButtons();
                            } else {
                                label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                        + Helper.getPlanetRepresentation(uH.getName(), game);
                            }
                            if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                                buttons.add(Buttons.gray(
                                        player.getFinsFactionCheckerPrefix() + "locustOn_" + tilePos + "_"
                                                + uH.getName() + "_" + p2.getColor(),
                                        label));
                            }
                        }
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose the infantry to start the locust.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("locustOn")
    public static void locustOn(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String tileP = buttonID.split("_")[1];
        String uHName = buttonID.split("_")[2];
        String color = buttonID.split("_")[3];
        Tile oG = game.getTileByPosition(tileP);

        Die d1 = new Die(3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " choose to hit the infantry on Tile " + tileP + " on " + uHName
                        + " and rolled a " + d1.getResult());
        if (d1.isSuccess()) {
            UnitKey key = Units.getUnitKey(UnitType.Infantry, color);
            DestroyUnitService.destroyUnit(
                    event, oG, game, key, 1, oG.getUnitHolders().get(uHName), false);
            List<Button> buttons = new ArrayList<>();
            for (String tilePos : FoWHelper.getAdjacentTiles(game, tileP, player, false, true)) {
                Tile tile2 = game.getTileByPosition(tilePos);
                for (UnitHolder uH : tile2.getUnitHolders().values()) {
                    String label;

                    for (Player p2 : game.getRealAndEliminatedAndDummyPlayers()) {
                        if (uH.getName().equals("space")) {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") Space Area of "
                                    + tile2.getRepresentationForButtons();
                        } else {
                            label = "(" + StringUtils.capitalize(p2.getColor()) + ") "
                                    + Helper.getPlanetRepresentation(uH.getName(), game);
                        }
                        if (uH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0) {
                            buttons.add(Buttons.gray(
                                    player.getFinsFactionCheckerPrefix() + "locustOn_" + tilePos + "_" + uH.getName()
                                            + "_" + p2.getColor(),
                                    label));
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " choose the next infantry to locust. You must choose a different player to hit if possible.",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveReverseTF")
    public static void resolveReverseTF(Game game, Player player, ButtonInteractionEvent event) {
        game.setStoredValue("reverseSpliceOrder", "True");
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(), player.getRepresentation() + " reversed the order of the splice.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveThieve")
    public static void resolveThieve(Game game, Player player, ButtonInteractionEvent event) {
        ButtonHelperTwilightsFall.sendPlayerSpliceOptions(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCreate")
    public static void resolveCreate(Game game, Player player, ButtonInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveUnravel")
    public static void resolveUnravel(Game game, Player player, ButtonInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTranspose")
    public static void resolveTranspose(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            buttons.add(Buttons.gray("transposeStep2_" + p2.getFaction(), p2.getFactionNameOrColor()));
        }
        String msg = player.getRepresentation() + " choose the player you wish to transpose with.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep2")
    public static void transposeStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String ability : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (!tech.getFaction().isPresent()) {
                continue;
            }
            buttons.add(Buttons.gray("transposeStep3_" + p2.getFaction() + "_" + ability, tech.getAutoCompleteName()));
        }
        String msg = player.getRepresentation() + " choose the ability you wish to lose.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep3")
    public static void transposeStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        for (String ability : p2.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (!tech.getFaction().isPresent()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    "transposeStep4_" + p2.getFaction() + "_" + ability1 + "_" + ability, tech.getAutoCompleteName()));
        }
        String msg = player.getRepresentation() + " choose the ability you wish to steal.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("transposeStep4")
    public static void transposeStep4(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String ability1 = buttonID.split("_")[2];
        String ability2 = buttonID.split("_")[3];
        TechnologyModel tech1 = Mapper.getTech(ability1);
        TechnologyModel tech2 = Mapper.getTech(ability2);
        player.removeTech(ability1);
        p2.removeTech(ability2);
        player.addTech(ability2);
        p2.addTech(ability1);

        String msg = player.getRepresentation() + " choose to exchange " + tech1.getAutoCompleteName() + " with "
                + tech2.getAutoCompleteName() + " via transposing with " + p2.getFactionNameOrColor();
        String msg2 = p2.getRepresentation() + " you exchanged " + tech2.getAutoCompleteName() + " for "
                + tech1.getAutoCompleteName() + " via transposing with " + player.getFactionNameOrColor();
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveScarab")
    public static void resolveScarab(Game game, Player player, ButtonInteractionEvent event) {
        Map<String, Integer> factions = new HashMap<>();
        for (String ability : player.getTechs()) {
            TechnologyModel tech = Mapper.getTech(ability);
            if (!tech.getFaction().isPresent()) {
                continue;
            }
            String faction = tech.getFaction().get();
            if (factions.keySet().contains(faction)) {
                factions.put(faction, factions.get(faction) + 1);
            } else {
                factions.put(faction, 1);
            }
        }
        for (String leaderID : player.getLeaderIDs()) {
            LeaderModel lead = Mapper.getLeader(leaderID);
            String faction = lead.getFaction();
            if (factions.keySet().contains(faction)) {
                factions.put(faction, factions.get(faction) + 1);
            } else {
                factions.put(faction, 1);
            }
        }
        for (String unit : player.getUnitsOwned()) {
            UnitModel unitM = Mapper.getUnit(unit);
            if (!unitM.getFaction().isPresent()) {
                continue;
            }
            String faction = unitM.getFaction().get();
            if (faction.equalsIgnoreCase(player.getFaction())) {
                continue;
            }
            if (factions.keySet().contains(faction)) {
                factions.put(faction, factions.get(faction) + 1);
            } else {
                factions.put(faction, 1);
            }
        }
        int max = 0;
        for (String faction : factions.keySet()) {
            max = Math.max(factions.get(faction), max);
        }

        String gainMsg = player.gainTG(max * 2, true);
        ButtonHelperAgents.resolveArtunoCheck(player, max * 2);
        String msg = player.getRepresentation() + " gained " + (max * 2) + " tgs " + gainMsg + " from having " + max
                + " of the same faction symbols";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveGenophage")
    public static void resolveGenophage(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(false)) {
            buttons.add(Buttons.gray("genophageStep2_" + p2.getFaction(), p2.getFactionNameOrColor()));
        }
        String msg = player.getRepresentation() + " choose the neighbor you wish to genophage.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveLawsHero(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            buttons.add(Buttons.gray("lawsHeroStep2_" + p2.getFaction(), p2.getFactionNameOrColor()));
        }
        String msg = player.getRepresentation() + " choose the player you wish to purge an ability from.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("lawsHeroStep2")
    public static void lawsHeroStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String tech : p2.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!tech.equalsIgnoreCase("wavelength") || !tech.equalsIgnoreCase("antimatter")) {
                continue;
            }
            buttons.add(Buttons.gray("lawsHeroStep3_" + p2.getFaction() + "_" + tech, techM.getName()));
        }
        String msg = player.getRepresentation() + " choose the ability that you wish to purge.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lawsHeroStep3")
    public static void lawsHeroStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String agent = buttonID.split("_")[2];
        game.setStoredValue("purgedAbilities", game.getStoredValue("purgedAbilities") + "_" + agent);
        TechnologyModel lead = Mapper.getTech(agent);
        p2.removeTech(agent);
        String msg = player.getRepresentation() + " choose to remove " + lead.getAutoCompleteName() + " from "
                + p2.getFactionNameOrColor();
        String msg2 = p2.getRepresentation() + " you lost " + lead.getAutoCompleteName() + " to a laws paradigm by "
                + player.getFactionNameOrColor();
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genophageStep2")
    public static void genophageStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        for (String agent : player.getLeaderIDs()) {

            if (!agent.contains("agent")) {
                continue;
            }
            LeaderModel lead = Mapper.getLeader(agent);
            buttons.add(Buttons.gray("genophageStep3_" + p2.getFaction() + "_" + agent, lead.getAutoCompleteName()));
        }
        String msg = player.getRepresentation() + " choose the genome of your neighbor that you wish to remove.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genophageStep3")
    public static void genophageStep3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String agent = buttonID.split("_")[2];
        LeaderModel lead = Mapper.getLeader(agent);
        p2.removeLeader(agent);
        String msg = player.getRepresentation() + " choose to remove " + lead.getAutoCompleteName() + " from "
                + p2.getFactionNameOrColor();
        String msg2 = p2.getRepresentation() + " you lost " + lead.getAutoCompleteName() + " to a genophage by "
                + player.getFactionNameOrColor();
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveMagnificence")
    public static void resolveMagnificence(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = ButtonHelperHeroes.getBenediction1stTileOptions(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose which system you wish to force ships to move from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveIrradiate")
    public static void resolveIrradiate(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        List<String> units = new ArrayList<>(Arrays.asList(
                "mech", "warsun", "dreadnought", "carrier", "fighter", "infantry", "cruiser", "spacedock", "pds"));
        for (String unit : units) {
            buttons.add(Buttons.gray("irradiateStep2_" + unit, StringUtils.capitalize(unit)));
        }
        String msg = player.getRepresentation() + " choose the unit type you wish to search for.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("irradiateStep2")
    public static void irradiateStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        List<MessageEmbed> embeds = new ArrayList<>();
        List<String> allCards = new ArrayList<>();
        String unitT = buttonID.split("_")[1];
        Map<String, UnitModel> allUnits = Mapper.getUnits();
        for (String unitID : allUnits.keySet()) {
            UnitModel mod = allUnits.get(unitID);
            if (mod.getFaction().isPresent() && mod.getSource() == ComponentSource.twilights_fall) {
                FactionModel faction = Mapper.getFaction(mod.getFaction().get());
                if (faction != null && faction.getSource() != ComponentSource.twilights_fall) {
                    allCards.add(unitID);
                }
            }
        }
        for (Player p : game.getRealPlayers()) {
            for (String unit : p.getUnitsOwned()) {
                allCards.remove(unit);
            }
        }
        String found = "nothing applicable";
        Collections.shuffle(allCards);
        for (String card : allCards) {
            embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            if (Mapper.getUnit(card).getBaseType().equalsIgnoreCase(unitT)) {
                UnitModel unitModel = Mapper.getUnit(card);
                String asyncId = unitModel.getAsyncId();
                if (!asyncId.equalsIgnoreCase("fs") && !asyncId.equalsIgnoreCase("mf")) {
                    List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                            .filter(unit -> unit.getFaction().isEmpty()
                                    || unit.getUpgradesFromUnitId().isEmpty())
                            .toList();
                    for (UnitModel u : unitsToRemove) {
                        player.removeOwnedUnitByID(u.getId());
                    }
                }
                player.addOwnedUnitByID(card);
                found = Mapper.getUnit(card).getAutoCompleteName() + "\nIt has been automatically gained";
                break;
            }
        }
        String msg = player.getRepresentation() + " searched through the following cards and found: " + found;
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCorrectChannel(), msg, embeds);
        ButtonHelper.deleteMessage(event);
    }

    public static void sendDestroyButtonsForSpecificTileAndSurrounding(Game game, Tile tile) {
        for (String pos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), null, false, true)) {
            Tile tile2 = game.getTileByPosition(pos);
            for (Player player : game.getRealPlayers()) {
                if (FoWHelper.playerHasUnitsInSystem(player, tile2)) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.red(
                            player.getFinsFactionCheckerPrefix() + "getDamageButtons_" + pos + "_"
                                    + "deleteThis_combat",
                            "Destroy Units in " + tile2.getRepresentationForButtons()));
                    String msg = player.getRepresentation() + " use this button to destroy units.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg, buttons);
                }
            }
        }
    }

    // twinning
    // tf-manifest
    // "tf-flux"
    // Tyrant image
    // "tf-unravel"
    // "tf-atomize"
    // trine
    // pax
    // ignis
    // coerce
    // statis/crisis
    // tartarus
    // timestop
    // starflare
    // converge

    // dragonfreed
    // swa tie up with argent swa2
    // linkship is ralnel FS ability
    // coexistence for the amabassador
    // mageon for yssaril infantry
    // 3 x mech cards
    // exos tie to exos
    // creuss cruisers
    // cabal carrier
    // hcf half capacity
    // morphwing for swappin into infantry
    // triune for watcher powers
    // yin infantry for special revival
    // hacan spacedocks have production biomes
    // bastion spacedocks
    //

}
