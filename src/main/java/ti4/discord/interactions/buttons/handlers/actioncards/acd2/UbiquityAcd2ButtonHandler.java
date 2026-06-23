package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
class UbiquityAcd2ButtonHandler {

    @ButtonHandler(value = "ubiquity", save = false)
    public static void resolveUbiquity(Player player, Game game, ButtonInteractionEvent event) {
        List<TechnologyModel> techs = getUbiquityTechs(game, player);
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible technologies to gain with _Ubiquity_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", spend 3 resources and choose a technology to gain with _Ubiquity_.",
                ListTechService.getTechButtons(techs, player, "free"));

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use these buttons to pay the 3 resources for _Ubiquity_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static List<TechnologyModel> getUbiquityTechs(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        Set<String> seenTechs = new HashSet<>();
        for (TechnologyType techType : TechnologyType.mainFive) {
            for (TechnologyModel tech : ListTechService.getAllTechOfAType(game, techType.toString(), player)) {
                if (seenTechs.add(tech.getAlias()) && isTechOwnedByEnoughPlayers(game, tech)) {
                    techs.add(tech);
                }
            }
        }
        return techs;
    }

    private static boolean isTechOwnedByEnoughPlayers(Game game, TechnologyModel tech) {
        long techOwners = game.getRealPlayers().stream()
                .filter(player -> !player.isEliminated())
                .filter(player -> player.hasTech(tech.getAlias()))
                .count();
        return tech.getRequirements().orElse("").length() < techOwners;
    }
}
