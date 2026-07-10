package ti4.service.tech;

import static org.apache.commons.lang3.StringUtils.substringAfter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.BombardmentAssignmentType;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.UnitModel;
import ti4.model.enums.CombatMod.CombatModType;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2Config;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.combat.v2.CombatV2RollData.Resolution;
import ti4.service.combat.v2.CombatV2RollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class BastionTechService {

    private record RollOutput(String message, int hits) {}

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
            for (Player p : game.getRealPlayersNNeutral()) {
                if (p1.isPlayerMemberOfAlliance(p) || p1 == p) continue;
                if (FoWHelper.playerHasUnitsOnPlanet(p, planet)) {
                    p2 = p;
                    break;
                }
            }
            if (p2 == null) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Cannot use " + proxima() + " on " + planet.getRepresentation(game)
                                + " because there are no opposing units there.");
                return;
            }

            var units = CombatRollService.getProximaBombardUnit(p1);
            String planetN = planet.getName();
            game.setStoredValue("bombardmentTarget" + p1.getFaction(), planetN);
            storeProximaAssignments(game, p1, planet, units);

            RollOutput againstOpponent = rollProximaUnits(event, game, p1, p2, tile, planet, units);
            String message = againstOpponent.message();
            int h = againstOpponent.hits();
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), message + "\nRolled against " + p2.getRepresentationNoPing() + ".");
            if (h > 0) {
                String msg = p2.getRepresentationUnfogged() + ", you may auto-assign "
                        + StringHelper.pluralize(h, "hit") + ".";
                List<Button> buttons = new ArrayList<>();
                String factionChecker = "FFCC_" + p2.getFaction() + "_";
                buttons.add(Buttons.green(
                        factionChecker + "autoAssignGroundHits_" + planetN + "_" + h,
                        "Auto-Assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
            RollOutput againstOwner = rollProximaUnits(event, game, p1, p1, tile, planet, units);
            message = againstOwner.message();
            h = againstOwner.hits();
            if (p1.hasTech("tf-proxima") && h > 0) {
                message += "\n_Proxima Targeting VI_ canceled 1 hit automatically.";
                h--;
            } else {
                if (planet.getGalvanizedUnitCount(p1.getColorID()) > 0 && h > 0) {
                    int oldH = h;
                    h = Math.max(0, h - planet.getGalvanizedUnitCount(p1.getColorID()));
                    message += "\n_Proxima Targeting VI_ canceled " + StringHelper.pluralize(oldH - h, "hit")
                            + " automatically.";
                }
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), message + "\nRolled against " + p1.getRepresentationNoPing() + ".");
            if (h > 0) {
                String msg = p1.getRepresentationUnfogged() + ", you may autoassign " + StringHelper.pluralize(h, "hit")
                        + ".";
                List<Button> buttons = new ArrayList<>();
                String factionChecker = "FFCC_" + p1.getFaction() + "_";
                buttons.add(Buttons.green(
                        factionChecker + "autoAssignGroundHits_" + planetN + "_" + h,
                        "Auto-Assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
        });
    }

    private static RollOutput rollProximaUnits(
            ButtonInteractionEvent event,
            Game game,
            Player rollingPlayer,
            Player opponent,
            Tile tile,
            Planet planet,
            Map<UnitModel, Integer> units) {
        if (CombatV2Config.isEnabled(game)) {
            Resolution resolution = CombatV2RollService.rollBombardmentUnits(
                    new Request(rollingPlayer, game, event, tile, planet.getName()), units, opponent);
            return new RollOutput(resolution.message(), resolution.hits());
        }

        var extraRolls = CombatModHelper.getModifiers(
                rollingPlayer,
                opponent,
                units,
                units,
                tile.getTileModel(),
                game,
                CombatRollType.bombardment,
                CombatModType.extra_rolls.toString());
        var modifiers = CombatModHelper.getModifiers(
                rollingPlayer,
                opponent,
                units,
                units,
                tile.getTileModel(),
                game,
                CombatRollType.bombardment,
                CombatModType.result_modifier.toString());
        CombatTempModHelper.ensureValidTempMods(rollingPlayer, tile.getTileModel(), planet);
        CombatTempModHelper.initializeNewTempMods(rollingPlayer, tile.getTileModel(), planet);
        List<NamedCombatModifierModel> temporaries = new ArrayList<>();
        temporaries.addAll(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                rollingPlayer, tile.getTileModel(), planet, false, CombatRollType.bombardment));
        temporaries.addAll(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                opponent, tile.getTileModel(), planet, true, CombatRollType.bombardment));

        String message =
                CombatMessageHelper.displayCombatSummary(rollingPlayer, tile, planet, CombatRollType.bombardment);
        message += CombatRollService.rollForUnits(
                units,
                extraRolls,
                modifiers,
                temporaries,
                rollingPlayer,
                opponent,
                game,
                CombatRollType.bombardment,
                event,
                tile,
                planet);
        String hitText = substringAfter(message, "Total hits ").split(" ")[0].replace("*", "");
        if (message.endsWith(";\n")) message = message.substring(0, message.length() - 2);
        return new RollOutput(message, Integer.parseInt(hitText));
    }

    private static void storeProximaAssignments(
            Game game, Player player, Planet planet, Map<UnitModel, Integer> units) {
        String key = "assignedBombardment" + player.getFaction();
        if (!CombatV2Config.isEnabled(game)) {
            for (Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
                for (int count = 0; count < entry.getValue(); count++) {
                    String assigned = entry.getKey().getAsyncId() + "_" + count + "_" + planet.getName() + ";";
                    game.setStoredValue(key, game.getStoredValue(key) + assigned);
                }
            }
            if (player.hasTech("ps") || player.hasTech("absol_ps")) {
                game.setStoredValue(key, game.getStoredValue(key) + "plasma_99_" + planet.getName() + ";");
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
                game.setStoredValue(key, game.getStoredValue(key) + "argentcommander_99_" + planet.getName() + ";");
            }
            return;
        }

        List<BombardmentAssignment> assignments = new ArrayList<>();
        units.forEach((unit, quantity) -> {
            int galvanized = planet.getGalvanizedUnitCount(unit.getUnitType(), player.getColorID());
            for (int count = 0; count < quantity; count++) {
                assignments.add(new BombardmentAssignment(
                        unit.getAsyncId(), planet.getName(), galvanized-- > 0, BombardmentAssignmentType.UNIT));
            }
        });
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            assignments.add(new BombardmentAssignment(
                    "plasmascoring", planet.getName(), false, BombardmentAssignmentType.TECH));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
            assignments.add(new BombardmentAssignment(
                    "argentcommander", planet.getName(), false, BombardmentAssignmentType.LEADER));
        }
        game.setStoredValue(key, JsonMapperManager.basic().writeValueAsString(assignments));
    }
}
