package ti4.discord.interactions.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.lunarium.LunariumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.lunarium.LunariumBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

public class WarrantAgendaResolver implements AgendaResolver {
    @Override
    public String agendaId() {
        return "warrant";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        Player player2 = game.getPlayerFromColorOrFaction(winner);
        if (player2 == null) return;
        player2.flipSearchWarrant();
        game.drawSecretObjective(player2.getUserID());
        game.drawSecretObjective(player2.getUserID());
        if (player2.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player2.getUserID());
        }
        if (player2.hasAbility("multitasking")) {
            LunariumAbilityHandler.offerFactionSheetCCButtons(game, player2);
        }
        if (player2.hasUnlockedBreakthrough("lunariumbt")) {
            LunariumBreakthroughHandler.offerDarkSideExploitationButtons(game, player2);
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player2, event);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (game.isFowMode() ? "The elected player" : player2.getRepresentation())
                        + " has drawn 2 secret objectives, and their secret objective info is now public.");
    }
}
