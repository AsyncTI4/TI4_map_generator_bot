package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.regex.RegexService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperPromissories {

    public static void offerShareKnowledgeButtons(Game game, Player player) {
        Player dws = game.getPNOwner("shareknowledge");
        if (dws == null) return;

        List<TechnologyModel> techsToAdd = new ArrayList<>();
        for (String tech : dws.getTechs()) {
            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel.getFaction().isPresent()
                    && techModel.getFaction().orElse("").length() > 0) continue; // no faction techs
            if (techModel.isUnitUpgrade()) continue;
            if (player.hasTech(tech)) continue;
            techsToAdd.add(techModel);
        }
        List<Button> buttons = ListTechService.getTechButtons(techsToAdd, player, "shareKnowledge");
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(), "Choose a tech to copy until the end of status phase:", buttons);
    }

    @ButtonHandler("startCourierTransport_")
    private static void startCourierTransport(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "startCourierTransport_" + RegexHelper.posRegex(game);
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            String destination = matcher.group("pos");
            List<Button> buttons = getCourierTransportButtons(game, player, destination);
            buttons.add(Buttons.DONE_DELETE_BUTTONS);

            String msg = player.getRepresentation() + " Choose which structures to move:";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }
    }

    @ButtonHandler("courierTransport_")
    private static void useCourierTransport(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "courierTransport";
        regex += "_" + RegexHelper.posRegex(game, "destination");
        regex += "_" + RegexHelper.posRegex(game, "source");
        regex += "_" + RegexHelper.unitTypeRegex(); // "unittype"
        regex += "_" + RegexHelper.unitHolderRegex(game, "sourcePlanet");
        regex += "_" + RegexHelper.unitHolderRegex(game, "destPlanet");

        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile destination = game.getTileByPosition(matcher.group("destination"));
            Tile source = game.getTileByPosition(matcher.group("source"));
            UnitType unit = Units.findUnitType(matcher.group("unittype"));
            String planetFrom = matcher.group("sourcePlanet");
            String planetTo = matcher.group("destPlanet");

            String from = unit.getValue() + " " + planetFrom;
            String to = unit.getValue() + " " + planetTo;

            RemoveUnitService.removeUnits(event, source, game, player.getColor(), from);
            AddUnitService.addUnits(event, destination, game, player.getColor(), to);

            String srcStr = Helper.getUnitHolderRepresentation(source, matcher.group("sourcePlanet"), game, player);
            String destStr = Helper.getUnitHolderRepresentation(destination, matcher.group("destPlanet"), game, player);

            PromissoryNoteModel courier = Mapper.getPromissoryNote("nanolink");
            String name = courier.getNameRepresentation();
            if (game.isTwilightsFallMode()) {
                name = "Courier Transport";
            } else {
                PromissoryNoteHelper.resolvePNPlay("nanolink", player, game, event);
            }
            String msg = String.format(
                    "%s moved %s from %s to %s using %s.",
                    player.getRepresentation(), unit.humanReadableName(), srcStr, destStr, name);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteTheOneButton(event);
        });
    }

    public static List<Button> getCourierTransportButtons(Game game, Player player, String destination) {
        List<Button> buttons = new ArrayList<>();
        String prefixID = player.finChecker() + "courierTransport_" + destination + "_";
        Tile dest = game.getTileByPosition(destination);
        if (dest == null) return buttons;

        for (String adj : FoWHelper.getAdjacentTilesAndNotThisTile(game, destination, player, false)) {
            Tile tile = game.getTileByPosition(adj);
            if (tile == null || tile.hasPlayerCC(player)) continue;

            for (UnitHolder uh : tile.getUnitHolders().values()) {
                String uhName = "space";
                if (!uh.getName().equals("space")) {
                    uhName = Helper.getPlanetRepresentation(uh.getName(), game);
                }

                for (UnitKey uk : uh.getUnits().keySet()) {
                    UnitModel model = player.getUnitFromUnitKey(uk);
                    if (!player.unitBelongsToPlayer(uk) || model == null || !model.getIsStructure()) continue;

                    String fromID = prefixID + adj + "_" + uk.asyncID() + "_" + uh.getName();
                    for (Planet p : dest.getPlanetUnitHolders()) {
                        String pname = Helper.getPlanetRepresentation(p.getName(), game);
                        String id = fromID + "_" + p.getName();
                        String label = uhName + " -> " + pname;
                        buttons.add(Buttons.gray(id, label, uk.unitEmoji()));
                    }
                }
            }
        }
        return buttons;
    }
}
