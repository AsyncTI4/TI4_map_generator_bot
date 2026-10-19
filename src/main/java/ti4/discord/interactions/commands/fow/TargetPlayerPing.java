package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.actioncards.ActionCardPingButtonHandler;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.PlanetTargetService;

class TargetPlayerPing extends GameStateSubcommand {

    public TargetPlayerPing() {
        super(Constants.TARGET_PLAYER_PING, "Ping a player as the target of your action card", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Player you are targeting")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.BOOLEAN,
                        Constants.PUBLIC,
                        "True: announce in the main channel. False: tell only the targeted player")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD, "Action card you are resolving"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode()) {
            MessageHelper.replyToMessage(event, "This command is only for Fog of War games.");
            return;
        }
        Player target = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (target == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        boolean isPublic = event.getOption(Constants.PUBLIC).getAsBoolean();
        String cardTitle = event.getOption(Constants.ACTION_CARD, "", OptionMapping::getAsString);

        if (!ActionCardPingButtonHandler.pingPlayerTarget(
                game, getPlayer(), target.getFaction(), isPublic, cardTitle)) {
            MessageHelper.replyToMessage(event, PlanetTargetService.fizzleMessage());
        }
    }
}
