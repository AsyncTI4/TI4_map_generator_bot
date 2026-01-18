package ti4.helpers.thundersedge;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.breakthrough.DeorbitBarrageService;
import ti4.service.breakthrough.PsychosporeService;
import ti4.service.breakthrough.ResonanceGeneratorService;
import ti4.service.breakthrough.TheIconService;
import ti4.service.breakthrough.VaultsOfTheHeirService;
import ti4.service.breakthrough.VisionariaSelectService;

@UtilityClass
public class TeHelperBreakthroughs {
    public static boolean handleBreakthroughExhaust(
            GenericInteractionCreateEvent event, Game game, Player player, String breakthroughID) {
        switch (breakthroughID) {
            case "arborecbt" -> PsychosporeService.postInitialButtons(event, game, player);
            case "crimsonbt" -> ResonanceGeneratorService.postInitialButtons(event, game, player);
            case "deepwroughtbt" -> VisionariaSelectService.postInitialButtons(event, game, player);
            case "saarbt" -> DeorbitBarrageService.postInitialButtons(game, player);
            case "mahactbt" -> VaultsOfTheHeirService.postInitialButtons(event, game, player);
            case "bastionbt" -> TheIconService.iconStepOne(event, game, player);
            case "edynbt" -> DSHelperBreakthroughs.edynBTStep1(game, player);
            case "bentorbt" -> DSHelperBreakthroughs.bentorBTStep1(game, player);
            case "kolumebt" -> DSHelperBreakthroughs.kolumeBTStep1(game, player);
            case "nokarbt" -> TeHelperActionCards.beginPirates(game, player, "resolveNokarBt", 0, false);
            case "dihmohnbt" -> DSHelperBreakthroughs.dihmohnBTExhaust(game, player);
            case "cheiranbt" -> DSHelperBreakthroughs.cheiranBTExhaust(game, player);
            default -> {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "This breakthrough either does not have an exhaust effect, or is not automated. Please resolve manually.");
                return false;
            }
        }
        return true;
    }
}
