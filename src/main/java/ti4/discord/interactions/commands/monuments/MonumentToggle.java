package ti4.discord.interactions.commands.monuments;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.game.MonumentsService;

class MonumentToggle extends GameStateSubcommand {

    private interface MonumentAction {
        boolean apply(Game game, Player player, String monumentId);
    }

    private final MonumentAction action;
    private final String pastTense;
    private final String missingMessage;

    private MonumentToggle(
            String name, String description, MonumentAction action, String pastTense, String missingMessage) {
        super(name, description, true, true);
        this.action = action;
        this.pastTense = pastTense;
        this.missingMessage = missingMessage;
    }

    static MonumentToggle exhaust() {
        return new MonumentToggle(
                "exhaust",
                "Exhaust your monument",
                MonumentsService::exhaustMonument,
                "exhausted",
                "You do not have a ready monument on the board.");
    }

    static MonumentToggle ready() {
        return new MonumentToggle(
                "ready",
                "Ready your monument",
                MonumentsService::readyMonument,
                "readied",
                "You do not have an exhausted monument on the board.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        UnitModel monument = player.getUnitByBaseType("monument");
        if (monument != null && action.apply(getGame(), player, monument.getId())) {
            MessageHelper.sendMessageToEventChannel(
                    event, player.getRepresentationNoPing() + " " + pastTense + " _" + monument.getName() + "_.");
        } else {
            MessageHelper.sendMessageToEventChannel(event, missingMessage);
        }
    }
}
