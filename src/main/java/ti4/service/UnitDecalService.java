package ti4.service;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;

@UtilityClass
public class UnitDecalService {

    public static boolean userMayUseDecal(String userID, String decalID) {
        return switch (decalID) {
            case "cb_10" -> userID.equals(Constants.jazzId); // jazz -> jazz
            case "cb_11" -> Constants.tourneyWinners.contains(userID); // tournament winner decal
            case "cb_52" -> userID.equals(Constants.sigmaId); // sigma -> void
            case "cb_12", "cb_34", "cb_35", "cb_36" -> false; // disable tech icons to prevent confusion
            case "cb_37", "cb_38", "cb_39", "cb_40" -> false; // disable trait icons to prevent confusion
            case "cb_42" -> false; // disable eye icon for use elsewhere
            case "cb_54" -> false; // disable Australia icon
            default -> true;
        };
    }
}
