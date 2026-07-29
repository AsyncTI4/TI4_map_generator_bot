package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class OblivionAbilityHandler {
    private static final String IGNO_DISCO = "ignorant_discoveries";
    private static final String START_IGNO_DISCO = "useIgnorantDiscoveries";
    private static final String PURGE_TECH = "purgeTechForIgnorantDiscoveries";

    public static List<Button> getIgnorantDiscoveriesButtons(GenericInteractionCreateEvent event, Player player) {
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + START_IGNO_DISCO,
                        "Use Ignorant Discoveries",
                        FactionEmojis.oblivion),
                Buttons.red("deleteButtons", "Decline"));

        return buttons;
    }

    @ButtonHandler(START_IGNO_DISCO)
    public static void startIgnorantDiscoveries(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(IGNO_DISCO) || player.getStrategicCC() < 1) {
            return;
        }

        player.setStrategicCC(player.getStrategicCC() - 1);

        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.gray(player.factionButtonChecker() + PURGE_TECH + tech.getID(), tech.getName()))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please choose the tech you would like to purge:",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PURGE_TECH)
    public static void purgeTechAndResearchTwoMore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null || !player.hasAbility(IGNO_DISCO)) {
            return;
        }

        String techID = buttonID.replace(PURGE_TECH, "");
        if (!player.hasTech(techID)) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Unable to find owned tech.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        PlayerTechService.purgeTech(event, player, techID);
        Button researchTech = Buttons.green(
                player.factionButtonChecker() + "getAllTechOfType_allTechResearchable_noPay", "Research a Technology");
        String message = player.getRepresentationUnfogged()
                + ", research 2 technologies for _Ignorant Discoveries_:";
        MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, researchTech);
        MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, researchTech);

        ButtonHelper.deleteMessage(event);
    }
}
