package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.actioncards.ActionCardPingButtonHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.service.fow.PlanetTargetService;

class TargetSystemPing extends GameStateSubcommand {

    public TargetSystemPing() {
        super(Constants.TARGET_SYSTEM_PING, "Ping a system as the target of your action card", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position you are targeting")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.BOOLEAN, Constants.PUBLIC, "Tell everyone who can see it").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD, "Action card you are resolving"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode()) {
            MessageHelper.replyToMessage(event, "This command is only for Fog of War games.");
            return;
        }
        String position = event.getOption(Constants.POSITION).getAsString().toLowerCase();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Tile position is not allowed");
            return;
        }
        boolean isPublic = event.getOption(Constants.PUBLIC).getAsBoolean();
        String cardTitle = event.getOption(Constants.ACTION_CARD, "", OptionMapping::getAsString);

        if (!ActionCardPingButtonHandler.pingSystemTarget(game, getPlayer(), position, isPublic, cardTitle)) {
            MessageHelper.replyToMessage(event, PlanetTargetService.fizzleMessage());
        }
    }
}
