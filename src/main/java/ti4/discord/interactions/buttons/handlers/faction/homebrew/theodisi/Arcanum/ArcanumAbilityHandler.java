package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class ArcanumAbilityHandler {
    private static final String PRIMORDIAL_SECRETS = "primordial_secrets";
    private static final String CHOOSE_PRIMORDIAL_PREFIX = "arcanumChoosePrimordial_";
    private static final List<String> PRIMORDIAL_TECHS =
            List.of("tharcanumpmy", "tharcanumpmg", "tharcanumpmr", "tharcanumpmb");

    public static void offerPrimordialSecretsButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(PRIMORDIAL_SECRETS) || hasChosenPrimordial(player)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String techId : PRIMORDIAL_TECHS) {
            if (Mapper.getTech(techId) == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_PRIMORDIAL_PREFIX + techId,
                    Mapper.getTech(techId).getName()));
        }
        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", choose 1 primordial technology to add with **Primordial Secrets**.",
                buttons);
    }

    @ButtonHandler(CHOOSE_PRIMORDIAL_PREFIX)
    public static void resolveChoosePrimordial(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasAbility(PRIMORDIAL_SECRETS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (hasChosenPrimordial(player)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "A primordial technology has already been chosen.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String techId = buttonID.substring(CHOOSE_PRIMORDIAL_PREFIX.length());
        if (!PRIMORDIAL_TECHS.contains(techId) || Mapper.getTech(techId) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That primordial technology is no longer valid.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addTech(techId);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.toString() + " added " + Mapper.getTech(techId).getNameRepresentation()
                        + " using **Primordial Secrets**.",
                Mapper.getTech(techId).getRepresentationEmbed());
        if (player.getCardsInfoThread() != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCardsInfoThread(),
                    "__Primordial Technology Added__",
                    Mapper.getTech(techId).getRepresentationEmbed());
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasChosenPrimordial(Player player) {
        for (String techId : PRIMORDIAL_TECHS) {
            if (player.hasTech(techId)) {
                return true;
            }
        }
        return false;
    }
}
