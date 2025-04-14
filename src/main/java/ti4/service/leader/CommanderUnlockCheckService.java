package ti4.service.leader;

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

@UtilityClass
public class CommanderUnlockCheckService {

    public static void checkAllPlayersInGame(Game game, String factionToCheck) {
        for (Player player : game.getRealPlayers()) {
            checkPlayer(player, factionToCheck);
        }
    }

    public static void checkPlayer(Player player, String... factionsToCheck) {
        for (String factionToCheck : factionsToCheck) {
            if (player != null &&
                player.isRealPlayer() &&
                player.hasLeader(factionToCheck + "commander") &&
                !player.hasLeaderUnlocked(factionToCheck + "commander")) {
                checkConditionsAndUnlock(player, factionToCheck);
            }
        }
    }

    private static void checkConditionsAndUnlock(Player player, String faction) {
        Game game = player.getGame();
        boolean shouldBeUnlocked = false;
        switch (faction) {
            case "axis" -> {
                if (ButtonHelperAbilities.getNumberOfDifferentAxisOrdersBought(player, game) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "rohdhna" -> {
                if (ButtonHelper.checkHighestProductionSystem(player, game) > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "freesystems" -> {
                if (ButtonHelper.getNumberOfUncontrolledNonLegendaryPlanets(game) < 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mortheus" -> {
                if (ButtonHelper.getNumberOfSystemsWithShipsNotAdjacentToHS(player, game) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "celdauri" -> {
                if (ButtonHelper.getNumberOfSpacedocksNotInOrAdjacentHS(player, game) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "cheiran" -> {
                if (ButtonHelper.getNumberOfPlanetsWithStructuresNotInHS(player, game) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaden" -> {
                if (ButtonHelper.howManyDifferentDebtPlayerHas(player) > (game.getRealPlayers().size() / 2) - 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "gledge" -> {
                if (ButtonHelper.checkHighestCostSystem(player, game) > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "olradin" -> {
                if (ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true) > 0
                    && ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", true) > 0
                    && ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", true) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "vaylerian" -> {
                if (ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", true) > 2
                    || ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", true) > 2
                    || ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", true) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghoti" -> {
                if (ButtonHelper.getNumberOfTilesPlayerIsInWithNoPlanets(game, player) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nivyn" -> {
                if (ButtonHelper.getNumberOfNonHomeAnomaliesPlayerIsIn(game, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "zelian" -> {
                if (ButtonHelper.getNumberOfAsteroidsPlayerIsIn(game, player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "yssaril" -> {
                if (player.getActionCards().size() > 7
                    || (player.getExhaustedTechs().contains("mi") && player.getActionCards().size() > 6)) {
                    shouldBeUnlocked = true;
                }
            }
            case "kjalengard" -> {
                if (ButtonHelperAgents.getGloryTokenTiles(game).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "kolume" -> shouldBeUnlocked = true;
            case "uydai" -> shouldBeUnlocked = true;
            case "pharadn" -> shouldBeUnlocked = true;
            case "veldyr" -> {
                if (ButtonHelperFactionSpecific.getPlayersWithBranchOffices(game, player).size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "mirveda" -> {
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "dihmohn" -> {
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "kollecc" -> {
                if (player.getCrf() + player.getHrf() + player.getIrf() + player.getUrf() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "bentor" -> {
                if (player.getNumberOfBluePrints() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "edyn" -> {
                if (!game.getLaws().isEmpty()) {
                    shouldBeUnlocked = true;
                }
            }
            case "lizho" -> {
                if (player.getTrapCardsPlanets().size() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "zealots", "keleres", "winnu", "muaat", "augers", "kortali", "letnev", "florzen", "yin" -> shouldBeUnlocked = true;
            case "hacan" -> {
                if (player.getTg() > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "mykomentori" -> {
                if (player.getCommodities() > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "sardakk" -> {
                if (player.getPlanets().size() > 6) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghost" -> {
                if (ButtonHelper.getAllTilesWithAlphaNBetaNUnits(player, game) > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "sol" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources += Helper.getPlanetResources(planet, game);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "xxcha" -> {
                int resources = 0;
                for (String planet : player.getPlanets()) {
                    resources += Helper.getPlanetInfluence(planet, game);
                }
                if (resources > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "mentak" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser", false) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "ghemina" -> {
                if ((ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "lady", false)) > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "tnelis" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false) > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "cymiae" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "infantry", false) > 9) {
                    shouldBeUnlocked = true;
                }
            }
            case "kyro" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "infantry", false) > 5
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fighter", false) > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "l1z1x" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false) > 3) {
                    shouldBeUnlocked = true;
                }
            }
            case "argent" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false)
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun", false);
                if (num > 5) {
                    shouldBeUnlocked = true;
                }
            }
            case "titans" -> {
                int num = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "pds")
                    + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock");
                if (num > 4) {
                    shouldBeUnlocked = true;
                }
            }
            case "cabal" -> {
                int num = ButtonHelper.getNumberOfGravRiftsPlayerIsIn(player, game);
                if (num > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nekro" -> {
                int count = 2;
                if (player.hasTech("vax")) {
                    count++;
                }
                if (player.hasTech("vay")) {
                    count++;
                }
                if (player.getTechs().size() > count) {
                    shouldBeUnlocked = true;
                }
            }
            case "jolnar" -> {
                if (player.getTechs().size() > 7) {
                    shouldBeUnlocked = true;
                }
            }
            case "saar" -> {
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "spacedock") > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "naaz" -> {
                if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).size() > 2) {
                    shouldBeUnlocked = true;
                }
            }
            case "nomad" -> {
                if (player.getSoScored() > 0) {
                    shouldBeUnlocked = true;
                }
            }
            case "mahact" -> {
                if (player.getMahactCC().size() > 1) {
                    shouldBeUnlocked = true;
                }
            }
            case "empyrean" -> {
                if (player.getNeighbourCount() > (game.getRealPlayers().size() - 2)) {
                    shouldBeUnlocked = true;
                }
            }
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
            case "arborec" -> {
                int num = ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, game, "infantry");
                num += ButtonHelper.getAmountOfSpecificUnitsOnPlanets(player, game, "mech");
                if (num > 11) {
                    shouldBeUnlocked = true;
                }
            }
            case "lanefir" -> {
                if (game.getNumberOfPurgedFragments() > 6) {
                    shouldBeUnlocked = true;
                }
            }
            // missing: yin, ghost, naalu, letnev
        }
        if (shouldBeUnlocked) {
            UnlockLeaderService.unlockLeader(faction + "commander", game, player);
        }
    }
}
