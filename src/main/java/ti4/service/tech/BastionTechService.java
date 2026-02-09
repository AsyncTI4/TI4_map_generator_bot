package ti4.service.tech;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.UnitModel;
import ti4.model.enums.CombatMod.CombatModType;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class BastionTechService {

    public String proxima() {
        return Mapper.getTech("proxima").getNameRepresentation();
    }

    public String helios() {
        return Mapper.getTech("helios2").getNameRepresentation();
    }

    public static void checkHeliosAttachment(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (planet.getTokenList().contains(Constants.HELIOS_ATTACHMENT_1))
                        planet.removeToken(Constants.HELIOS_ATTACHMENT_1);
                    if (planet.getTokenList().contains(Constants.HELIOS_ATTACHMENT_2))
                        planet.removeToken(Constants.HELIOS_ATTACHMENT_2);
                    for (Player player : game.getRealPlayers()) {

                        boolean hasSD = planet.getUnitCount(UnitType.Spacedock, player.getColorID()) > 0;
                        boolean hasHelios = player.hasUnit("bastion_spacedock");
                        boolean hasHeliosUpgrade =
                                player.hasUnit("bastion_spacedock2") || player.hasUnit("tf-heliosentity");

                        if (hasSD && hasHelios && !planet.getTokenList().contains(Constants.HELIOS_ATTACHMENT_1)) {
                            planet.addToken(Constants.HELIOS_ATTACHMENT_1);
                        } else if (hasSD
                                && hasHeliosUpgrade
                                && !planet.getTokenList().contains(Constants.HELIOS_ATTACHMENT_2)) {
                            planet.addToken(Constants.HELIOS_ATTACHMENT_2);
                        }
                    }
                }
            }
        }
    }

    public static void addProximaCombatButton(
            Game game, Player p1, Player p2, Tile tile, UnitHolder holder, List<Button> combatButtons) {
        if ((p1.hasTech("proxima") || (p2.hasTech("proxima")) && !game.isFowMode())) {
            String id = "resolveProxima_" + tile.getPosition() + "_" + holder.getName();
            String label = "Use Proxima Targeting VI On " + holder.getRepresentation(game);
            combatButtons.add(Buttons.red(id, label, FactionEmojis.Bastion));
        }
    }

    @ButtonHandler("resolveProxima_")
    public static void rollProxima(ButtonInteractionEvent event, Game game, Player p1, String buttonID) {
        String rx = "resolveProxima_" + RegexHelper.posRegex(game) + "_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(rx, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            if (tile == null) {
                RegexService.throwFailure("Tile at position `" + matcher.group("pos") + "` cannot be resolved");
                return;
            }

            Planet planet = tile.getUnitHolderFromPlanet(matcher.group("planet"));
            if (planet == null) {
                RegexService.throwFailure("Planet `" + matcher.group("planet")
                        + "` cannot be resolved for tile at position `" + matcher.group("pos") + "`");
                return;
            }
            if (planet.getPlanetTypes().contains(PlanetType.CULTURAL.toString())
                    && ButtonHelper.anyLawInPlay(game, "conventions", "absol_conventionswar")) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Cannot use BOMBARDMENT against " + planet.getRepresentation(game)
                                + " because _Conventions of War_ is in play, and the planet is cultural.");
                return;
            }

            Player p2 = null;
            for (Player p : game.getRealPlayersExcludingThis(p1)) {
                if (p1.isPlayerMemberOfAlliance(p)) continue;
                if (FoWHelper.playerHasUnitsOnPlanet(p, planet)) {
                    p2 = p;
                    break;
                }
            }

            var units = CombatRollService.getProximaBombardUnit(p1);
            Player player = p1;
            String planetN = planet.getName();
            game.setStoredValue("bombardmentTarget" + player.getFaction(), planetN);
            for (Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
                for (int x = 0; x < entry.getValue(); x++) {
                    String name = entry.getKey().getAsyncId() + "_" + x;

                    String assignedUnit = name + "_" + planetN;
                    game.setStoredValue(
                            "assignedBombardment" + p1.getFaction(),
                            game.getStoredValue("assignedBombardment" + p1.getFaction()) + assignedUnit + ";");
                }
            }
            if (player.hasTech("ps") || player.hasTech("absol_ps")) {
                game.setStoredValue(
                        "assignedBombardment" + player.getFaction(),
                        game.getStoredValue("assignedBombardment" + player.getFaction()) + "plasma_99_" + planetN
                                + ";");
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
                game.setStoredValue(
                        "assignedBombardment" + player.getFaction(),
                        game.getStoredValue("assignedBombardment" + player.getFaction()) + "argentcommander_99_"
                                + planetN + ";");
            }

            var rollMods = CombatModHelper.getModifiers(
                    p1,
                    p2,
                    units,
                    units,
                    tile.getTileModel(),
                    game,
                    CombatRollType.bombardment,
                    CombatModType.extra_rolls.toString());
            var flatMods = CombatModHelper.getModifiers(
                    p1,
                    p2,
                    units,
                    units,
                    tile.getTileModel(),
                    game,
                    CombatRollType.bombardment,
                    CombatModType.result_modifier.toString());

            // Temp modifiers (bunker)

            CombatTempModHelper.EnsureValidTempMods(p1, tile.getTileModel(), planet);
            CombatTempModHelper.InitializeNewTempMods(p1, tile.getTileModel(), planet);
            List<NamedCombatModifierModel> tempMods = new ArrayList<>();
            tempMods.addAll(CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(
                    p1, tile.getTileModel(), planet, false, CombatRollType.bombardment));
            tempMods.addAll(CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(
                    p2, tile.getTileModel(), planet, true, CombatRollType.bombardment));

            String message = CombatMessageHelper.displayCombatSummary(player, tile, planet, CombatRollType.bombardment);
            message += CombatRollService.rollForUnits(
                    units, rollMods, flatMods, tempMods, p1, p2, game, CombatRollType.bombardment, event, tile, planet);
            String hits = substringAfter(message, "Total hits ");
            hits = hits.split(" ")[0].replace("*", "");
            int h = Integer.parseInt(hits);
            if (message != null && message.endsWith(";\n")) {
                message = message.substring(0, message.length() - 2);
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), message + "\nRolled against " + p2.getRepresentationNoPing() + ".");
            if (h > 0) {
                String msg = p2.getRepresentationUnfogged() + ", you may auto-assign " + h + " hit"
                        + (h == 1 ? "" : "s") + ".";
                List<Button> buttons = new ArrayList<>();
                String finChecker = "FFCC_" + p2.getFaction() + "_";
                buttons.add(Buttons.green(
                        finChecker + "autoAssignGroundHits_" + planetN + "_" + h,
                        "Auto-Assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
            message = CombatMessageHelper.displayCombatSummary(player, tile, planet, CombatRollType.bombardment);
            message += CombatRollService.rollForUnits(
                    units, rollMods, flatMods, tempMods, p1, p1, game, CombatRollType.bombardment, event, tile, planet);
            hits = substringAfter(message, "Total hits ");
            hits = hits.split(" ")[0].replace("*", "");
            h = Integer.parseInt(hits);
            if (message.endsWith(";\n")) {
                message = message.substring(0, message.length() - 2);
            }
            if (player.hasTech("tf-proxima") && h > 0) {
                message += "\n_Proxima Targeting VI_ canceled 1 hit automatically.";
                h--;
            } else {
                if (planet.getGalvanizedUnitCount(player.getColorID()) > 0 && h > 0) {
                    int oldH = h;
                    h = Math.max(0, h - planet.getGalvanizedUnitCount(player.getColorID()));
                    message += "\n_Proxima Targeting VI_ canceled " + (oldH - h) + " hit" + (oldH - h == 1 ? "" : "s")
                            + " automatically.";
                }
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), message + "\nRolled against " + p1.getRepresentationNoPing() + ".");
            if (h > 0) {
                String msg = p1.getRepresentationUnfogged() + ", you may autoassign " + h + " hit" + (h == 1 ? "" : "s")
                        + ".";
                List<Button> buttons = new ArrayList<>();
                String finChecker = "FFCC_" + p1.getFaction() + "_";
                buttons.add(Buttons.green(
                        finChecker + "autoAssignGroundHits_" + planetN + "_" + h,
                        "Auto-Assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
        });
    }
}
