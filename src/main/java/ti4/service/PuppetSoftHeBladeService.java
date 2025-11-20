package ti4.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.GenericCardModel;
import ti4.model.PromissoryNoteModel;
import ti4.service.leader.HeroUnlockCheckService;

@UtilityClass
public class PuppetSoftHeBladeService {

    @ButtonHandler("componentActionRes_ability_puppetsoftheblade") // puppet soft he blade
    private static void convertFactionToObsidian(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility("puppetsoftheblade")) return;

        // ABILITY: Puppets of the Blade [puppetsoftheblade]
        // If you have at least 1 plot card in your play area: ACTION
        //  - Your faction sheet, your leaders, and your faction's promissory note are purged
        //    from the game. Then, gain all of the faction components for 'The Obsidian'

        // ABILITY: The Blade's Orchestra [bladesorchestra]
        // When this faction comes into play:
        //  - flip your home system, double sided faction components, and all of your in-play plot cards.
        //    Then, ready Cronos Hollow and Tallin Hollow if you control them."

        // Update faction stuff
        flipFactionToObsidian(game, player);

        // Announce Plots
        StringBuilder plotInfo = new StringBuilder("## __The Obsidian's plots are now revealed:__");
        for (String plotID : player.getPlotCards().keySet()) {
            GenericCardModel plot = Mapper.getPlot(plotID);
            plotInfo.append("\n").append(plot.getRepresentation());

            List<String> puppetedFactions = player.getPuppetedFactionsForPlot(plotID);
            if (puppetedFactions != null && !puppetedFactions.isEmpty()) {
                StringBuilder factions = new StringBuilder();
                for (String faction : puppetedFactions) {
                    Player p2 = game.getPlayerFromColorOrFaction(faction);
                    if (p2 != null) factions.append(p2.getFactionEmoji()).append(" ");
                }
                plotInfo.append("\n> - Puppeted Factions for ")
                        .append(plot.getName())
                        .append(": [ ")
                        .append(factions)
                        .append(" ]");
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), plotInfo.toString());

        // Serve relevant plot automation on reveal
        for (String plotID : player.getPlotCards().keySet()) {
            List<String> puppetedFactions = player.getPuppetedFactionsForPlot(plotID);
            if (puppetedFactions != null && !puppetedFactions.isEmpty()) {
                List<Player> puppets = new ArrayList<>();
                for (String faction : puppetedFactions) puppets.add(game.getPlayerFromColorOrFaction(faction));

                if ("seethe".equals(plotID)) revealPlotSeethe(player, puppets);
                if ("extract".equals(plotID)) revealPlotExtract(player, puppets);
            }
        }

        // Overrode the componentAction, so serve the end of turn buttons manually
        ButtonHelper.deleteMessage(event);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }

    private static void revealPlotSeethe(Player obsidian, List<Player> puppets) {
        List<Button> seetheButtons = new ArrayList<>();
        String prefix = obsidian.finChecker() + "revealSeethe_";
        for (Player p : puppets) {
            if (p == null) continue;
            String label = p.getFactionModel().getFactionName();
            seetheButtons.add(Buttons.red(prefix + p.getFaction(), label, p.getFactionEmoji()));
        }
        String message = obsidian.getRepresentation() + " Resolve the Seethe plot:";
        MessageHelper.sendMessageToChannelWithButtons(obsidian.getCorrectChannel(), message, seetheButtons);
    }

    private static void revealPlotExtract(Player obsidian, List<Player> puppets) {
        List<Button> extractButtons = new ArrayList<>();
        String prefix = obsidian.finChecker() + "revealExtract_";
        for (Player p : puppets) {
            if (p == null) continue;
            String label = p.getFactionModel().getFactionName();
            extractButtons.add(Buttons.red(prefix + p.getFaction(), label, p.getFactionEmoji()));
        }
        String message = obsidian.getRepresentation() + " Resolve the Extract plot:";
        MessageHelper.sendMessageToChannelWithButtons(obsidian.getCorrectChannel(), message, extractButtons);
    }

    private static void flipFactionToObsidian(Game game, Player player) {
        FactionModel oldFactionModel = player.getFactionModel();
        FactionModel newFactionModel = Mapper.getFaction("obsidian");

        List<String> outputStrings = new ArrayList<>();
        outputStrings.add(replaceHomeSystem(game, player, oldFactionModel, newFactionModel));
        outputStrings.add(replaceFactionSheet(game, player, newFactionModel));
        outputStrings.add(replaceBreakthrough(player, newFactionModel));
        outputStrings.add(replacePN(game, player, oldFactionModel, newFactionModel));
        outputStrings.add(replaceTechAndFactionTech(game, player, oldFactionModel, newFactionModel));
        outputStrings.add(replaceLaws(game, oldFactionModel, newFactionModel));
        outputStrings.add(replaceStoredValues(game, oldFactionModel, newFactionModel));

        String output =
                "### " + player.getRepresentation(false, true) + " the following components have been updated:\n> ";
        output += String.join("\n> ", outputStrings);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), output);
    }

    // Replacing home system MUST be done before changing faction sheets
    private static String replaceHomeSystem(
            Game game, Player player, FactionModel oldFaction, FactionModel newFaction) {
        if (player.getFaction().equals(newFaction.getAlias())) {
            return "**__ERROR: Could not replace home system.__**";
        }

        Tile home = player.getHomeSystemTile();
        UnitHolder origUnits = new Space("orig", null);
        home.getUnitHolders().values().stream()
                .flatMap(uh -> uh.getUnitsByState().entrySet().stream())
                .forEach(entry -> origUnits.addUnitsWithStates(entry.getKey(), entry.getValue()));

        Tile newHome = new Tile(newFaction.getHomeSystem(), home.getPosition());
        newHome.inheritFogData(home);

        String returnString = "Sucessfully replaced home system tile.";
        if ("96a".equals(oldFaction.getHomeSystem()) && "96b".equals(newFaction.getHomeSystem())) { // obsidian
            // Resolve control
            for (Player p : game.getPlayers().values()) {
                if (p.hasPlanet("cronos")) {
                    p.addPlanet("cronoshollow");
                    p.removePlanet("cronos");
                }
                if (p.hasPlanet("tallin")) {
                    p.addPlanet("tallinhollow");
                    p.removePlanet("tallin");
                }
            }

            // Then add units and stuff
            for (UnitHolder unitHolder : home.getUnitHolders().values()) {
                String uh = unitHolder.getName();
                if ("cronos".equals(uh) || "tallin".equals(uh)) {
                    Planet p = newHome.getUnitHolderFromPlanet(uh + "hollow");
                    if (p != null) p.inheritEverythingFrom(unitHolder);
                } else if ("space".equals(uh)) {
                    newHome.getSpaceUnitHolder().inheritEverythingFrom(unitHolder);
                }
            }
        } else {
            returnString =
                    "**__WARNING:__** Replaced home system tile and moved all units to space. **__Resolve gaining/readying planets and moving units manually.__**";
            newHome.getSpaceUnitHolder().inheritEverythingFrom(origUnits);
        }

        // remove the planets that have been removed from the game.
        oldFaction.getHomePlanets().forEach(player::removePlanet);
        game.removeTile(home.getPosition());
        game.setTile(newHome);
        player.setHomeSystemPosition(newHome.getPosition());

        return returnString;
    }

    private static String replaceFactionSheet(Game game, Player player, FactionModel newFaction) {
        player.setFaction(game, newFaction.getAlias());
        HeroUnlockCheckService.checkIfHeroUnlocked(game, player);

        Set<String> playerOwnedUnits = new HashSet<>(newFaction.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        // BASE COMMODITIES
        player.setCommoditiesBase(newFaction.getCommodities());

        // Reset emoji
        if (!game.isFrankenGame()) player.setFactionEmoji(null);

        return "Successfully changed faction sheet.";
    }

    private static String replaceBreakthrough(Player player, FactionModel newFaction) {
        player.setBreakthroughID(newFaction.getAlias() + "bt");
        // automate flipping sowing to reaping
        return "Successfully changed breakthrough.";
    }

    // This will replace the promissory note(s) and put the new ones directly into the players hand
    private static String replacePN(Game game, Player player, FactionModel oldFaction, FactionModel newFaction) {
        oldFaction.getPromissoryNotes().forEach(pn -> {
            player.removeOwnedPromissoryNoteByID(pn);
            for (Player p : game.getRealPlayers()) {
                p.removePromissoryNote(pn);
                p.removePromissoryNoteFromPlayArea(pn);
            }

            // Remove any attachments
            // Does not presently fix custodia vigilia
            PromissoryNoteModel pnModel = Mapper.getPromissoryNote(pn);
            if (pnModel.getAttachment().isPresent()) {
                String attachment = pnModel.getAttachment().get();
                game.getTileMap().values().stream()
                        .flatMap(t -> t.getUnitHolders().values().stream())
                        .forEach(uh -> uh.removeToken(attachment));
            }
        });

        newFaction.getPromissoryNotes().forEach(pn -> {
            player.addOwnedPromissoryNoteByID(pn);
            player.setPromissoryNote(pn);
        });

        return "Successfully updated promissory notes.";
    }

    private static String replaceTechAndFactionTech(
            Game game, Player player, FactionModel oldFaction, FactionModel newFaction) {
        // change over faction techs
        List<String> failed = new ArrayList<>();
        newFaction.getFactionTech().forEach(tech -> {
            if (!tech.endsWith("-obs")) {
                failed.add(tech);
                return;
            }
            String replacableTech = tech.replace("-obs","-firm");
            for (Player p2 : game.getPlayers().values()) {
                // replace tech for all players because nekro i guess
                if (p2.hasTech(replacableTech)) {
                    p2.removeTech(replacableTech);
                    p2.addTech(tech);
                }
            }
            player.removeFactionTech(replacableTech);
            player.addFactionTech(tech);
        });

        // Remove and re-add all techs again (to fix units owned list)
        List<String> techs = new ArrayList<>(player.getTechs());
        for (String tech : techs) {
            boolean exh = player.getExhaustedTechs().contains(tech);
            player.removeTech(tech);
            player.addTech(tech);
            if (exh) {
                player.exhaustTech(tech);
            }
        }

        List<String> oldTech = oldFaction.getFactionTech().stream()
                .filter(tech -> !tech.endsWith("-firm"))
                .toList();
        if (oldTech.isEmpty()) {
            return "Sucessfully changed faction techs.";
        }

        String warning = "**__Warning encountered while changing faction techs:__**\n> - You owned [";
        warning += String.join(", ", oldTech);
        warning += "] and your new faction has the techs [";
        warning += String.join(", ", failed);
        warning += "]. Those techs cannot be automatically replaced,";
        warning += " work with your table to find a suitable solution.";
        return warning;
    }

    private static String replaceLaws(Game game, FactionModel oldFaction, FactionModel newFaction) {
        List<String> laws = new ArrayList<>(game.getLawsInfo().keySet());
        for (String law : laws) {
            if (game.getLawsInfo().get(law).equalsIgnoreCase(oldFaction.getAlias())) {
                game.reviseLaw(game.getLaws().get(law), newFaction.getAlias());
            }
        }

        return "Successfully updated laws.";
    }

    private static String replaceStoredValues(Game game, FactionModel oldFaction, FactionModel newFaction) {
        Map<String, String> storedValues = new HashMap<>(game.getMessagesThatICheckedForAllReacts());

        List<String> legitKeyFormats = new ArrayList<>();
        legitKeyFormats.add("$<f>^"); // standalone faction name
        legitKeyFormats.add("<f>_");
        legitKeyFormats.add("_<f>");
        legitKeyFormats.add("<f>*"); // fin's queue system
        legitKeyFormats.add("For<f>"); // ccs, futuremessages, etc
        legitKeyFormats.add(" <f>");
        legitKeyFormats.add("<f>graviton");
        legitKeyFormats.add("<f>graviton");
        legitKeyFormats.add("<f>planetsExplored");
        legitKeyFormats.add("<f>queued");
        legitKeyFormats.add("<f>latest");
        legitKeyFormats.add("LookedAt<f>");
        for (int x : game.getSCList()) legitKeyFormats.add("Count" + x + "<f>^");
        for (int x = 1; x <= 10; ++x) {
            legitKeyFormats.add("Round" + x + "<f>^");
            legitKeyFormats.add("endofround" + x + "<f>^");
        }

        List<String> legitValueFormats = new ArrayList<>();
        legitValueFormats.add("$<f>^"); // standalone faction name
        legitValueFormats.add("<f>*"); // fin's queue system
        legitValueFormats.add("<f>_");
        legitValueFormats.add("_<f>");

        for (Entry<String, String> entry : storedValues.entrySet()) {
            String keyRx = "$" + entry.getKey() + "^", valueRx = "$" + entry.getValue() + "^";

            String newKey = null;
            for (String template : legitKeyFormats) {
                String oldFormat = template.replace("<f>", oldFaction.getAlias());
                if (keyRx.contains(oldFormat)) {
                    String newFormat = template.replace("<f>", newFaction.getAlias())
                            .replace("$", "")
                            .replace("^", "");
                    oldFormat = oldFormat.replace("$", "").replace("^", "");
                    newKey = entry.getKey().replaceAll(oldFormat, newFormat);
                    break;
                }
            }

            String newValue = null;
            for (String template : legitValueFormats) {
                String oldFormat = template.replace("<f>", oldFaction.getAlias());
                if (valueRx.contains(oldFormat)) {
                    String newFormat = template.replace("<f>", newFaction.getAlias())
                            .replace("$", "")
                            .replace("^", "");
                    oldFormat = oldFormat.replace("$", "").replace("^", "");
                    newValue = entry.getValue().replaceAll(oldFormat, newFormat);
                    break;
                }
            }

            if (newKey != null || newValue != null) {
                newKey = newKey == null ? entry.getKey() : newKey;
                newValue = newValue == null ? entry.getValue() : newValue;
                game.getMessagesThatICheckedForAllReacts().remove(entry.getKey());
                game.getMessagesThatICheckedForAllReacts().put(newKey, newValue);
            }
        }

        // Update Expedition Tokens
        String oldAlias = oldFaction.getAlias();
        String newAlias = newFaction.getAlias();
        if (oldAlias.equals(game.getExpeditions().getTradeGoods()))
            game.getExpeditions().setTradeGoods(newAlias);
        if (oldAlias.equals(game.getExpeditions().getFiveInf()))
            game.getExpeditions().setFiveInf(newAlias);
        if (oldAlias.equals(game.getExpeditions().getFiveRes()))
            game.getExpeditions().setFiveRes(newAlias);
        if (oldAlias.equals(game.getExpeditions().getTechSkip()))
            game.getExpeditions().setTechSkip(newAlias);
        if (oldAlias.equals(game.getExpeditions().getActionCards()))
            game.getExpeditions().setActionCards(newAlias);
        if (oldAlias.equals(game.getExpeditions().getSecret()))
            game.getExpeditions().setSecret(newAlias);

        return "Successfully (?) updated game stored values.";
    }
}
