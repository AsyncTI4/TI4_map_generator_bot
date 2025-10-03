package ti4.service.draft;

import lombok.Data;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.milty.MiltyService;

@UtilityClass
public class PlayerSetupService {
    @Data
    public static class PlayerSetupState {
        private String color;
        private String faction;
        private String positionHS;
        private boolean setSpeaker;
    }

    public static void setupPlayer(
            PlayerSetupState setupState, Player player, Game game, GenericInteractionCreateEvent event) {

        // TODO: When this draft system begins to replace the whole Milty setup, move
        // this operation over to here. For now, just invoke the milty setup code;
        // this leaves it unchanged and the source of truth for how to setup.
        MiltyService.secondHalfOfPlayerSetup(
                player,
                game,
                setupState.getColor(),
                setupState.getFaction(),
                setupState.getPositionHS(),
                event,
                setupState.isSetSpeaker());
    }
}
