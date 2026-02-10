package ti4.service.leader;

import java.util.List;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class CommanderUnlockCheckService {

    public static void checkAllPlayersInGame(Game game, String factionToCheck) {
        for (Player player : game.getRealPlayers()) {
            checkPlayer(player, factionToCheck);
        }
    }

    public static void checkPlayer(Player player, String... factionsToCheck) {
        for (String factionToCheck : factionsToCheck) {
            if (player != null
                    && player.isRealPlayer()
                    && player.hasLeader(factionToCheck + "commander")
                    && !player.hasLeaderUnlocked(factionToCheck + "commander")) {
                checkConditionsAndUnlock(player, factionToCheck);
            }
        }
    }

    public static void checkConditionsAndUnlock(Player player, String faction) {
        Game game = player.getGame();
        boolean shouldBeUnlocked = false;
        switch (faction) {
            // base
            case "arborec" -> {
                int num = ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, game, "infantry");
                num += ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, game, "mech");
                shouldBeUnlocked = (num >= 12);
            }
            case "saar" -> shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock") >= 3);
            case "hacan" -> shouldBeUnlocked = (player.getTg() >= 10);
            case "sol" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    if (planet.equalsIgnoreCase("triad")
                            || (game.getUnitHolderFromPlanet(planet) != null
                                    && (game.getUnitHolderFromPlanet(planet).isSpaceStation()
                                            || game.getUnitHolderFromPlanet(planet)
                                                    .isFake()))) {
                        continue;
                    }
                    resources += Helper.getPlanetResources(planet, game);
                }
                shouldBeUnlocked = (resources >= 12);
            }
            case "ghost" -> shouldBeUnlocked = (ButtonHelper.getAllTilesWithAlphaNBetaNUnits(player, game) >= 3);
            case "l1z1x" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) >= 4);
            case "mentak" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) >= 4);
            case "naalu" -> {
                Tile rex = game.getMecatolTile();
                if (rex != null) {
                    for (String tilePos : FoWHelper.getAdjacentTiles(game, rex.getPosition(), player, false)) {
                        Tile tile = game.getTileByPosition(tilePos);
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            if (unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0
                                    || unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                                shouldBeUnlocked = true;
                            }
                        }
                    }
                }
            }
            case "nekro" -> {
                int threshold = 3;
                if (player.hasTech("vax")) {
                    threshold++;
                }
                if (player.hasTech("vay")) {
                    threshold++;
                }
                shouldBeUnlocked = (player.getTechs().size() >= threshold);
            }
            case "sardakk" -> {
                int count = 0;
                for (String p : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(p);
                    if (tile != null
                            && !tile.isHomeSystem(game)
                            && game.getUnitHolderFromPlanet(p) != null
                            && !game.getUnitHolderFromPlanet(p).isSpaceStation()) {
                        count++;
                    }
                }
                shouldBeUnlocked = (count >= 5);
            }
            case "jolnar" -> shouldBeUnlocked = (player.getTechs().size() >= 8);
            case "xxcha" -> {
                int influence = 0;
                for (String planet : player.getPlanets()) {
                    if (planet.equalsIgnoreCase("triad")
                            || (game.getUnitHolderFromPlanet(planet) != null
                                    && (game.getUnitHolderFromPlanet(planet).isSpaceStation()
                                            || game.getUnitHolderFromPlanet(planet)
                                                    .isFake()))) {
                        continue;
                    }
                    influence += Helper.getPlanetInfluence(planet, game);
                }
                shouldBeUnlocked = (influence >= 12);
            }
            case "yssaril" ->
                shouldBeUnlocked = (player.getActionCards().size() > 7
                        || (player.getExhaustedTechs().contains("mi")
                                && player.getActionCards().size() >= 7));
            case "letnev", "muaat", "winnu", "yin" -> shouldBeUnlocked = true;

            // PoK
            case "argent" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false)
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false)
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
                shouldBeUnlocked = (num >= 6);
            }
            case "empyrean" ->
                shouldBeUnlocked =
                        (player.getNeighbourCount() >= (game.getRealPlayers().size() - 1));
            case "mahact" -> shouldBeUnlocked = (player.getMahactCC().size() >= 2);
            case "naaz" ->
                shouldBeUnlocked =
                        (CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Mech)
                                        .size()
                                >= 3);
            case "nomad" -> shouldBeUnlocked = (player.getSoScored() >= 1);
            case "titans" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds")
                        + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock");
                shouldBeUnlocked = (num >= 5);
            }
            case "cabal" -> {
                int num = ButtonHelper.getNumberOfGravRiftsPlayerIsIn(player, game);
                shouldBeUnlocked = (num >= 3);
            }

            // TE
            case "bastion" -> {
                int totGalvanized = game.getTileMap().values().stream()
                        .flatMap(t -> t.getUnitHolders().values().stream())
                        .mapToInt(UnitHolder::getTotalGalvanizedCount)
                        .sum();
                shouldBeUnlocked = (totGalvanized >= 3);
            }
            case "deepwrought" ->
                shouldBeUnlocked = player.getPlanets().stream().anyMatch(s -> s.startsWith("ocean"));
            case "crimson" -> // This commander unlock is checked in @ResonanceGeneratorService and
                // TODO: (TE) @{wherever crimson destroyers are handled}
                shouldBeUnlocked = true;
            case "firmament" -> {
                for (Entry<String, List<String>> entry :
                        player.getPlotCardsFactions().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        shouldBeUnlocked = true;
                        break;
                    }
                }
            }
            case "obsidian" -> {
                for (Tile t : game.getTileMap().values()) {
                    if (t.getPosition().startsWith("frac") && t.containsPlayersUnits(player)) {
                        shouldBeUnlocked = true;
                        break;
                    }
                }
            }
            case "ralnel" -> shouldBeUnlocked = true;

            // codex
            case "keleres", "redcreuss" -> shouldBeUnlocked = true;

            // DS
            case "axis" ->
                shouldBeUnlocked = (ButtonHelperAbilities.getNumberOfDifferentAxisOrdersBought(player, game) >= 4);
            case "bentor" -> shouldBeUnlocked = (player.getNumberOfBluePrints() >= 3);
            case "celdauri" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfSpacedocksNotInOrAdjacentHS(player, game) >= 1);
            case "cheiran" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfStructuresOnNonHomePlanets(player, game) >= 4);
            case "cymiae" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "infantry", false) >= 10);
            case "dihmohn" -> shouldBeUnlocked = (ButtonHelper.getNumberOfUnitUpgrades(player) > 0);
            case "edyn" -> shouldBeUnlocked = (!game.getLaws().isEmpty());
            case "freesystems" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUncontrolledNonLegendaryPlanets(game) == 0);
            case "ghemina" ->
                shouldBeUnlocked = ((ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", false)
                                + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "lady", false))
                        >= 2);
            case "ghoti" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(game, player) >= 3);
            case "gledge" -> shouldBeUnlocked = (ButtonHelper.checkHighestCostSystem(player, game) >= 10);
            case "kjalengard" ->
                shouldBeUnlocked = (ButtonHelperAgents.getGloryTokenTiles(game).size() >= 2);
            case "kollecc" ->
                shouldBeUnlocked = (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() >= 4);
            case "kyro" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "infantry", false) >= 6
                        && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fighter", false) >= 6);
            case "lanefir" -> shouldBeUnlocked = (game.getNumberOfPurgedFragments() >= 7);
            case "lizho" -> shouldBeUnlocked = (player.getTrapCardsPlanets().size() >= 4);
            case "mirveda" -> shouldBeUnlocked = (ButtonHelper.getNumberOfUnitUpgrades(player) >= 2);
            case "mortheus" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfSystemsWithShipsNotAdjacentToHS(player, game) >= 3);
            case "mykomentori" -> shouldBeUnlocked = (player.getCommodities() >= 4);
            case "nivyn" -> shouldBeUnlocked = (ButtonHelper.getNumberOfNonHomeAnomaliesPlayerIsIn(game, player) >= 2);
            case "olradin" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true) >= 1
                        && ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", true) >= 1
                        && ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", true) >= 1);
            case "rohdhna" -> shouldBeUnlocked = (ButtonHelper.checkHighestProductionSystem(player, game) >= 7);
            case "tnelis" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false) >= 6);
            case "vaden" ->
                shouldBeUnlocked = (ButtonHelper.howManyDifferentDebtPlayerHas(player)
                        > (game.getRealPlayers().size() / 2) - 1);
            case "vaylerian" ->
                shouldBeUnlocked = (ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true) >= 3
                        || ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", true) >= 3
                        || ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", true) >= 3);
            case "veldyr" ->
                shouldBeUnlocked = (ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player)
                                .size()
                        >= 2);
            case "zelian" -> shouldBeUnlocked = (ButtonHelper.getNumberOfAsteroidsPlayerIsIn(game, player) >= 2);
            case "florzen", "augers", "kolume", "kortali", "khrask", "nokar", "zealots" -> shouldBeUnlocked = true;

            // BR
            case "atokera", "belkosea", "pharadn", "qhet", "toldar", "uydai", "kaltrim" -> shouldBeUnlocked = true;
        }
        if (shouldBeUnlocked) {
            UnlockLeaderService.unlockLeader(faction + "commander", game, player);
        }
    }
}
