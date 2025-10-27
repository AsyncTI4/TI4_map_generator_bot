package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
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
                        boolean hasHeliosUpgrade = player.hasUnit("bastion_spacedock2");

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
        if (p1.hasTech("proxima") || (p2.hasTech("proxima") && !game.isFowMode())) {
            String id = "resolveProxima_" + tile.getPosition() + "_" + holder.getName();
            String label = "Use Proxima Targeting on " + holder.getRepresentation(game);
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
                        "Cannot bombard " + planet.getRepresentation(game)
                                + " because Conventions of War is in play and the planet is Cultural.");
                return;
            }

            Player p2 = null;
            for (Player p : game.getRealPlayers()) {
                if (p1.isPlayerMemberOfAlliance(p)) continue;
                if (FoWHelper.playerHasUnitsOnPlanet(p1, planet)) {
                    p2 = p;
                    break;
                }
            }

            var units = CombatRollService.getProximaBombardUnit(tile, p1);

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

            CombatRollService.rollForUnits(
                    units, rollMods, flatMods, tempMods, p1, p2, game, CombatRollType.bombardment, event, tile, planet);
            CombatRollService.rollForUnits(
                    units, rollMods, flatMods, tempMods, p1, p1, game, CombatRollType.bombardment, event, tile, planet);
        });
    }
}
