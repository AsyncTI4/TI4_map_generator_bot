package ti4.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.GenericCardModel;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.regex.RegexService;
import ti4.service.tech.ListTechService;

public class PlotCardsService {

    @ButtonHandler("addFactionTokenToPlot_")
    private static void addFactionToPlot(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String plotRx = RegexHelper.genericCardRegex();
        String factionRx = RegexHelper.optional("_" + RegexHelper.factionRegex(game));
        Matcher plotMatcher =
                Pattern.compile("addFactionTokenToPlot_" + plotRx + factionRx).matcher(buttonID);
        if (plotMatcher.matches()) {
            String plotID = plotMatcher.group("genericcard");
            String faction = plotMatcher.group("faction");
            if (StringUtils.isBlank(faction)) {
                List<Button> buttons =
                        ActionCardHelper.getFactionButtonsForPlot(game, player, plotID, "addFactionTokenToPlot_");
                GenericCardModel plot = Mapper.getPlot(plotID);
                String msg = "Add a faction to " + plot.getName();
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            } else {
                player.setPlotCardFaction(plotID, faction);
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                String player2 = p2 == null ? faction : p2.getRepresentation(false, true);
                String msg = player.getRepresentation() + " added a " + player2 + " token to plot "
                        + player.getPlotCards().get(plotID);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                ButtonHelper.deleteTheOneButton(event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "firmament");
        }
    }

    @ButtonHandler("removeFactionTokenFromPlot_")
    private static void removeFactionFromPlot(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String plotRx = RegexHelper.genericCardRegex();
        String factionRx = RegexHelper.optional("_" + RegexHelper.factionRegex(game));
        Matcher plotMatcher = Pattern.compile("removeFactionTokenFromPlot_" + plotRx + factionRx)
                .matcher(buttonID);
        if (plotMatcher.matches()) {
            String plotID = plotMatcher.group("genericcard");
            String faction = plotMatcher.group("faction");
            if (StringUtils.isBlank(faction)) {
                List<Button> buttons =
                        ActionCardHelper.getFactionButtonsForPlot(game, player, plotID, "removeFactionTokenFromPlot_");
                GenericCardModel plot = Mapper.getPlot(plotID);
                String msg = "Remove a faction from " + plot.getName();
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            } else {
                player.removePlotCardFaction(plotID);
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                String player2 = p2 == null ? faction : p2.getRepresentation(false, true);
                String msg = player.getRepresentation() + " removed a " + player2 + " token from plot "
                        + player.getPlotCards().get(plotID);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                ButtonHelper.deleteTheOneButton(event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "firmament");
        }
    }

    @ButtonHandler("revealSeethe_")
    private static void handleRevealSeethe(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "revealSeethe_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Player puppet = game.getPlayerFromColorOrFaction(matcher.group("faction"));
            if (puppet == null) return;

            List<Button> buttons = new ArrayList<>();
            for (String planet : puppet.getPlanets()) {
                Tile tile = game.getTileFromPlanet(planet);
                if (tile == null) continue;
                if (tile.isHomeSystem()) continue;

                Planet p = tile.getUnitHolderFromPlanet(planet);
                if (p == null) continue;
                if (p.getUnitCount() <= 0) continue;

                String id = player.finChecker() + "resolveSeethe_" + planet;
                String label = Helper.getPlanetRepresentation(planet, game) + " [" + p.getUnitCount() + " units]";
                buttons.add(Buttons.red(id, label));
            }

            String message = player.getRepresentation() + " Choose a non-home planet controlled by "
                    + puppet.getRepresentation(false, false) + " to eradicate:";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteTheOneButton(event);
        });
    }

    @ButtonHandler("revealExtract_")
    private static void handleRevealExtract(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "revealExtract_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Player puppet = game.getPlayerFromColorOrFaction(matcher.group("faction"));
            if (puppet == null) return;

            List<TechnologyModel> techs = new ArrayList<>();
            for (String tech : puppet.getTechs()) {
                TechnologyModel model = Mapper.getTech(tech);
                if (player.hasTech(tech)) continue;
                if (player.getPurgedTechs().contains(tech)) continue;
                if (model.isFactionTech()) continue;
                techs.add(model);
            }
            List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");

            String message = player.getRepresentation() + " Choose a technology to gain from "
                    + puppet.getRepresentation(false, false) + ":";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteTheOneButton(event);
        });
    }

    @ButtonHandler("resolveSeethe_")
    private static void resolveSeethe(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveSeethe_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile t = game.getTileFromPlanet(matcher.group("planet"));
            if (t == null) return;

            Planet planet = t.getUnitHolderFromPlanet(matcher.group("planet"));
            if (planet == null) return;

            boolean disaster = planet.getUnitCount() >= 15;
            if (disaster) {
                String disastermsg = "Moments before disaster in game " + game.getName();
                DisasterWatchHelper.postTileInDisasterWatch(game, event, t, 0, disastermsg);
            }
            String disastermsg = Helper.getPlanetRepresentation(planet.getName(), game)
                        + " has been eradicated with an obsidian plot.";

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), disastermsg);

            planet.getUnitsByState().clear();

            ButtonHelper.deleteMessage(event);

            if (disaster) {
                
                DisasterWatchHelper.postTileInDisasterWatch(game, event, t, 0, disastermsg);
            }
        });
    }
}
