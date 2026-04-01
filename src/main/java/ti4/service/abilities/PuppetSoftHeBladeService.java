package ti4.service.abilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.game.Leader;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.BreakthroughModel;
import ti4.model.FactionModel;
import ti4.model.GenericCardModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.HeroUnlockCheckService;
import ti4.service.planet.AddPlanetService;

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
        String factionName = player.getDisplayName();
        StringBuilder plotInfo = new StringBuilder("## __" + factionName + " plots are now revealed:__");
        for (String plotID : player.getPlotCards().keySet()) {
            GenericCardModel plot = Mapper.getPlot(plotID);
            plotInfo.append('\n').append(plot.getRepresentation());

            List<String> puppetedFactions = player.getPuppetedFactionsForPlot(plotID);
            if (puppetedFactions != null && !puppetedFactions.isEmpty()) {
                StringBuilder factions = new StringBuilder();
                for (String faction : puppetedFactions) {
                    Player p2 = game.getPlayerFromColorOrFaction(faction);
                    if (p2 != null) factions.append(p2.fogSafeEmoji()).append(' ');
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
            String label = p.getFactionNameOrColor();
            seetheButtons.add(Buttons.red(prefix + p.getFaction(), label, p.fogSafeEmoji()));
        }
        String message = obsidian.getRepresentation() + " Resolve the Seethe plot:";
        MessageHelper.sendMessageToChannelWithButtons(obsidian.getCorrectChannel(), message, seetheButtons);
    }

    private static void revealPlotExtract(Player obsidian, List<Player> puppets) {
        List<Button> extractButtons = new ArrayList<>();
        String prefix = obsidian.finChecker() + "revealExtract_";
        for (Player p : puppets) {
            if (p == null) continue;
            String label = p.getFactionNameOrColor();
            extractButtons.add(Buttons.red(prefix + p.getFaction(), label, p.fogSafeEmoji()));
        }
        String message = obsidian.getRepresentation() + " Resolve the Extract plot:";
        MessageHelper.sendMessageToChannelWithButtons(obsidian.getCorrectChannel(), message, extractButtons);
    }

    private static void flipFactionToObsidian(Game game, Player player) {
        List<String> outputStrings = new ArrayList<>();
        outputStrings.addAll(flipFirmamentComponentsToObsidianComponents(game, player));
        String msg = "### " + player.getRepresentationUnfogged();
        msg += " the following components have been updated:\n> ";
        msg += String.join("\n> ", outputStrings);

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        resolveFirmamentMechFlip(game, player);
    }

    private static List<String> flipFirmamentComponentsToObsidianComponents(Game game, Player player) {
        List<String> output = new ArrayList<>();
        output.add(flipFirmamentHomeSystem(player));
        output.addAll(flipFirmamentFactionTechs(player));
        output.addAll(flipFirmamentLeaders(player));
        output.add(flipFirmamentMech(player));
        output.add(flipFirmamentFlagship(player));
        output.add(flipFirmamentPromissoryNote(player));
        output.add(flipFirmamentBreakthrough(player));

        if (!game.isFrankenGame()) {
            output.addAll(replaceFirmamentMetadata(game, player));
        }

        return output.stream().filter(Objects::nonNull).toList();
    }

    @Nullable
    private static String flipFirmamentHomeSystem(Player player) {
        Tile oldHome = player.getHomeSystemTile();
        if (!"96a".equals(oldHome.getTileID())) return null;

        // Replace the home system
        Tile newHome = new Tile("96b", oldHome.getPosition(), oldHome.getSpaceUnitHolder());
        newHome.inheritFogData(oldHome);

        // Move stuff on cronos to cronos hollow
        Planet cronos = oldHome.getUnitHolderFromPlanet("cronos");
        Planet cronosH = newHome.getUnitHolderFromPlanet("cronoshollow");
        cronosH.inheritEverythingFrom(cronos);

        // Move stuff on tallin to tallin hollow
        Planet tallin = oldHome.getUnitHolderFromPlanet("tallin");
        Planet tallinH = newHome.getUnitHolderFromPlanet("tallinhollow");
        tallinH.inheritEverythingFrom(tallin);

        player.removePlanet("cronos");
        player.removePlanet("tallin");
        player.addPlanet("cronoshollow");
        player.addPlanet("tallinhollow");

        return "Sucessfully replaced home system tile.";
    }

    private static List<String> flipFirmamentFactionTechs(Player player) {
        List<String> outputs = new ArrayList<>();

        Map<String, String> techs = Map.of("planesplitter-firm", "planesplitter-obs", "parasite-firm", "parasite-obs");
        for (Entry<String, String> e : techs.entrySet()) {
            String oldTech = e.getKey();
            String newTech = e.getValue();

            if (player.getFactionTechs().contains(oldTech)) {
                TechnologyModel oldModel = Mapper.getTech(oldTech);
                TechnologyModel newModel = Mapper.getTech(newTech);
                player.removeFactionTech(oldTech);
                player.addFactionTech(newTech);
                outputs.add("Flipped _" + oldModel.getName() + "_ to _" + newModel.getName() + "_.");
            }
            for (Player p2 : player.getGame().getRealPlayers()) {
                if (p2.hasTech(oldTech)) {
                    p2.removeTech(oldTech);
                    p2.addTech(newTech);
                }
            }
        }
        return outputs;
    }

    private static List<String> flipFirmamentLeaders(Player player) {
        String fmt = "Successfully replaced %s with %s.";
        List<String> output = new ArrayList<>();
        player.getLeaderByID("firmamentagent").ifPresent(oldLeader -> {
            player.removeLeader(oldLeader);
            Leader newLeader = new Leader("obsidianagent");
            player.addLeader(newLeader);
            output.add(String.format(fmt, oldLeader.getName(), newLeader.getName()));
        });
        player.getLeaderByID("firmamentcommander").ifPresent(oldLeader -> {
            player.removeLeader(oldLeader);
            Leader newLeader = new Leader("obsidiancommander");
            player.addLeader(newLeader);
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
            output.add(String.format(fmt, oldLeader.getName(), newLeader.getName()));
        });
        player.getLeaderByID("firmamenthero").ifPresent(oldLeader -> {
            player.removeLeader(oldLeader);
            Leader newLeader = new Leader("obsidianhero");
            player.addLeader(newLeader);
            HeroUnlockCheckService.checkIfHeroUnlocked(player.getGame(), player);
            output.add(String.format(fmt, oldLeader.getName(), newLeader.getName()));
        });
        return output;
    }

    @Nullable
    private static String flipFirmamentMech(Player player) {
        if (!player.hasUnit("firmament_mech")) return null;
        player.removeOwnedUnitByID("firmament_mech");
        player.addOwnedUnitByID("obsidian_mech");
        String emojis = FactionEmojis.Firma_Obs + " " + UnitEmojis.mech;
        return "Successfully flipped " + emojis + " _Viper EX-23_ to _Viper Hollow_.";
    }

    @Nullable
    private static String flipFirmamentFlagship(Player player) {
        if (!player.hasUnit("firmament_flagship")) return null;
        player.removeOwnedUnitByID("firmament_flagship");
        player.addOwnedUnitByID("obsidian_flagship");
        String emojis = FactionEmojis.Firma_Obs + " " + UnitEmojis.flagship;
        return "Successfully flipped " + emojis + " _Heaven's Eye_ to _Heaven's Hollow_.";
    }

    @Nullable
    private static String flipFirmamentPromissoryNote(Player player) {
        // Black Ops is automated by removing it from the list of notes owned,
        // which makes it annoying to resolve in franken.
        boolean modelOwnsPN = player.getFactionModel().getPromissoryNotes().contains("blackops");
        boolean frankenPN = player.getStoredList("appliedFrankenItems").contains("PN:blackops");
        if (!modelOwnsPN && !frankenPN) return null;

        boolean stillHas = player.getPromissoryNotesOwned().contains("blackops");
        player.removePromissoryNote("blackops");
        player.removeOwnedPromissoryNoteByID("blackops");
        player.setPromissoryNote("malevolency");
        player.addOwnedPromissoryNoteByID("malevolency");

        PromissoryNoteModel oldPN = Mapper.getPromissoryNote("blackops");
        PromissoryNoteModel newPN = Mapper.getPromissoryNote("malevolency");
        if (stillHas) {
            return "Successfully flipped _" + oldPN.getName() + "_ to _" + newPN.getName() + "_.";
        }
        return "Successfully gained _" + newPN.getName() + "_.";
    }

    @Nullable
    private static String flipFirmamentBreakthrough(Player player) {
        if (!player.hasBreakthrough("firmamentbt")) return null;
        if (!player.changeBreakthrough("firmamentbt", "obsidianbt")) return null;
        BreakthroughModel firma = Mapper.getBreakthrough("firmamentbt");
        BreakthroughModel obs = Mapper.getBreakthrough("obsidianbt");
        return "Successfully flipped " + firma.getNameRepresentation() + " to " + obs.getNameRepresentation() + ".";
    }

    private static void resolveFirmamentMechFlip(Game game, Player player) {
        if (!player.hasUnit("obsidian_mech")) return;
        int count = 0;
        StringBuilder output = new StringBuilder("### " + player.getRepresentationUnfogged());
        output.append(", the following planet has been taken control of by your Viper Hollow:");
        for (String planet : game.getPlanetsPlayerIsCoexistingOn(player)) {
            UnitHolder uH = game.getUnitHolderFromPlanet(planet);
            if (uH != null && uH.getUnitCount(UnitType.Mech, player) > 0) {
                count++;
                output.append("\n> ").append(Helper.getPlanetRepresentation(planet, game));
                AddPlanetService.addPlanet(player, planet, game);
            }
        }

        if (count > 0) {
            String msg = output.toString();
            if (count > 1) {
                String plural = "planets have been taken control of by Vipers";
                String singular = "planet has been taken control of by your Viper";
                msg = msg.replace(singular, plural);
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), output.toString());
        }
    }

    private static List<String> replaceFirmamentMetadata(Game game, Player player) {
        // we do not want to update meta things in franken, because the faction name is not changing
        if (game.isFrankenGame()) return null;
        List<String> outputs = new ArrayList<>();

        // Replace faction sheet stuff
        FactionModel obsidian = Mapper.getFaction("obsidian");
        player.setFaction("obsidian");
        player.setFactionEmoji(null);
        player.setCommoditiesBase(obsidian.getCommodities());
        outputs.add("Successfully updated faction sheet to _Obsidian_.");

        // Replace laws and stuff
        outputs.add(replaceLaws(game, "firmament", "obsidian"));

        // Replace stored value map data
        outputs.add(replaceStoredValues(game, "firmament", "obsidian"));

        return outputs;
    }

    @Nullable
    private static String replaceLaws(Game game, String oldFaction, String newFaction) {
        List<String> laws = new ArrayList<>(game.getLawsInfo().keySet());
        List<String> updated = new ArrayList<>();
        for (String law : laws) {
            if (game.getLawsInfo().get(law).equalsIgnoreCase(oldFaction)) {
                game.reviseLaw(game.getLaws().get(law), newFaction);
                AgendaModel model = Mapper.getAgenda(law);
                updated.add(model.getName());
            }
        }
        if (updated.isEmpty()) return null;

        return "Successfully updated laws: _[" + String.join(", ", updated) + "]_";
    }

    private static String replaceStoredValues(Game game, String oldAlias, String newAlias) {
        Map<String, String> storedValues = new HashMap<>(game.getStoredValueMap());

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
                String oldFormat = template.replace("<f>", oldAlias);
                if (keyRx.contains(oldFormat)) {
                    String newFormat =
                            template.replace("<f>", newAlias).replace("$", "").replace("^", "");
                    oldFormat = oldFormat.replace("$", "").replace("^", "");
                    newKey = entry.getKey().replaceAll(oldFormat, newFormat);
                    break;
                }
            }

            String newValue = null;
            for (String template : legitValueFormats) {
                String oldFormat = template.replace("<f>", oldAlias);
                if (valueRx.contains(oldFormat)) {
                    String newFormat =
                            template.replace("<f>", newAlias).replace("$", "").replace("^", "");
                    oldFormat = oldFormat.replace("$", "").replace("^", "");
                    newValue = entry.getValue().replaceAll(oldFormat, newFormat);
                    break;
                }
            }

            if (newKey != null || newValue != null) {
                newKey = newKey == null ? entry.getKey() : newKey;
                newValue = newValue == null ? entry.getValue() : newValue;
                game.getStoredValueMap().remove(entry.getKey());
                game.getStoredValueMap().put(newKey, newValue);
            }
        }

        // Update Expedition Tokens
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

        return "Successfully updated game stored values.";
    }
}
