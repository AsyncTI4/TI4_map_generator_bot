package ti4.commands.player;

import software.amazon.awssdk.services.s3.internal.handlers.EnableChunkedEncodingInterceptor;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Player;

public class TechRemove extends TechAddRemove {
    public TechRemove() {
        super(Constants.TECH_REMOVE, "Remove Tech");
    }

    @Override
    public void doAction(Player player, String techID) {
        player.removeTech(techID);
        sendMessage(Helper.getPlayerRepresentation(getEvent(), player) + " removed tech: " + Helper.getTechRepresentation(techID));
    }
}
