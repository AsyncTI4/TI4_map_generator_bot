package ti4.service.agenda;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class MonumentsAgendaService {
    private static final String CATHEDRAL_OF_IXTH = "cathedralofixth";
    private static final String MINISTER_OF_CULTURE = "ministerofculture";

    public static boolean playerControlsMonument(Game game, Player player) {
        if (game == null || !game.isMonumentsMode() || player == null) {
            return false;
        }

        for (String planetName : player.getPlanets()) {
            UnitHolder holder = game.getUnitHolderFromPlanet(planetName);
            if (holder != null && holder.getUnitCount(UnitType.Monument, player) > 0) {
                return true;
            }
        }
        return false;
    }

    public static void resolveCathedralOfIxthPlacement(Game game, Player player, String planetName) {
        if (game == null
                || !game.isMonumentsMode()
                || player == null
                || planetName == null
                || !ButtonHelper.isLawInPlay(game, CATHEDRAL_OF_IXTH)
                || !planetName.equals(game.getLawsInfo().get(CATHEDRAL_OF_IXTH))) {
            return;
        }

        UnitHolder holder = game.getUnitHolderFromPlanet(planetName);
        if (holder == null || holder.getUnitCount(UnitType.Monument, player) < 1) {
            return;
        }

        String objectiveName = "Cathedral Of Ixth (" + player.getFaction() + ")";
        if (!game.getCustomPublicVP().containsKey(objectiveName)) {
            game.addCustomPO(objectiveName, 1);
        }

        if (game.scorePublicObjective(
                player.getUserID(), game.getRevealedPublicObjectives().get(objectiveName))) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " gained 1 victory point from _Cathedral Of Ixth_ for placing their Monument on "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + ".");
            Helper.checkEndGame(game, player);
        }
    }

    public static void resolveCathedralOfIxthRemoval(Game game, Player player, String planetName) {
        if (game == null
                || !game.isMonumentsMode()
                || player == null
                || planetName == null
                || !ButtonHelper.isLawInPlay(game, CATHEDRAL_OF_IXTH)
                || !planetName.equals(game.getLawsInfo().get(CATHEDRAL_OF_IXTH))) {
            return;
        }

        UnitHolder holder = game.getUnitHolderFromPlanet(planetName);
        if (holder != null && holder.getUnitCount(UnitType.Monument, player) > 0) {
            return;
        }

        String objectiveName = "Cathedral Of Ixth (" + player.getFaction() + ")";
        if (game.getCustomPublicVP().containsKey(objectiveName)
                && game.unscorePublicObjective(player.getUserID(), objectiveName)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " lost 1 victory point from _Cathedral Of Ixth_ because their Monument was removed from "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + ".");
        }
    }

    public static boolean hasMinisterOfCultureBonus(Game game, Player player) {
        return game != null
                && game.isMonumentsMode()
                && player != null
                && ButtonHelper.isLawInPlay(game, MINISTER_OF_CULTURE)
                && player.getFaction().equals(game.getLawsInfo().get(MINISTER_OF_CULTURE))
                && playerControlsMonument(game, player);
    }
}
