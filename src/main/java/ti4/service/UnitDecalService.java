package ti4.service;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.spring.context.SpringContext;
import ti4.spring.service.tournamentwinner.TourneyWinnerService;

@UtilityClass
public class UnitDecalService {

    public static boolean userMayUseDecal(String userID, String decalID) {
        return switch (decalID) {
            case "caballed" -> Constants.eronousId.equals(userID); // caballed -> eronous
            case "cb_10" -> Constants.jazzId.equals(userID); // jazz -> jazz
            case "cb_11" -> getTournamentWinnerService().exists(userID); // tournament winner decal
            case "cb_52" -> Constants.sigmaId.equals(userID); // sigma -> void
            case "cb_93" -> Constants.bambamId.equals(userID); // bambam -> larry david
            case "cb_94" -> Constants.tspId.equals(userID); // HolyTispoon -> HolyTispoon
            case "cb_97" -> "81995487250489344".equals(userID); // gwaer bot supporter
            case "cb_12", "cb_34", "cb_35", "cb_36" -> false; // disable tech icons to prevent confusion
            case "cb_37", "cb_38", "cb_39", "cb_40" -> false; // disable trait icons to prevent confusion
            case "cb_42" -> false; // disable eye icon for use elsewhere
            case "cb_54" -> false; // disable Australia icon
            case "cb_103" -> "627421461367357441".equals(userID); // Big Al -> Stew
            default -> true;
        };
    }

    private static TourneyWinnerService getTournamentWinnerService() {
        return SpringContext.getBean(TourneyWinnerService.class);
    }
}
