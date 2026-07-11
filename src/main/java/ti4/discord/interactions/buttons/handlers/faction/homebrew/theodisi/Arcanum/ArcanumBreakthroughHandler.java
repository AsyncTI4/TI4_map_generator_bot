package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

@UtilityClass
public class ArcanumBreakthroughHandler {
    private static final String POWER_WORD_WISH_FRONT = "arcanumbt";
    private static final String POWER_WORD_WISH_BACK = "arcanumbtback";

    public static void handlePowerWordWishTechGain(Player player, String techId) {
        if (player == null || techId == null) {
            return;
        }

        TechnologyModel techModel = Mapper.getTech(techId);
        if (techModel == null) {
            return;
        }

        if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)
                && (techModel.isPropulsionTech() || techModel.isWarfareTech())
                && player.changeBreakthrough(POWER_WORD_WISH_FRONT, POWER_WORD_WISH_BACK)) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.toString() + " flipped _Power Word: Wish_ to its biotic/cybernetic side.",
                    Mapper.getBreakthrough(POWER_WORD_WISH_BACK).getRepresentationEmbed());
            return;
        }

        if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK)
                && (techModel.isCyberneticTech() || techModel.isBioticTech())
                && player.changeBreakthrough(POWER_WORD_WISH_BACK, POWER_WORD_WISH_FRONT)) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.toString() + " flipped _Power Word: Wish_ to its propulsion/warfare side.",
                    Mapper.getBreakthrough(POWER_WORD_WISH_FRONT).getRepresentationEmbed());
        }
    }
}
