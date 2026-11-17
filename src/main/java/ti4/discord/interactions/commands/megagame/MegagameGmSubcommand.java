package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.megagame.MegagameGmAccess;
import ti4.service.megagame.MegagameGroupService;

abstract class MegagameGmSubcommand extends GameStateSubcommand {

    static final String GROUP = "group";

    MegagameGmSubcommand(String name, String description, boolean saveGame) {
        super(name, description, saveGame, false);
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!super.accept(event)) {
            return false;
        }
        if (MegagameGmAccess.hasGmRole(event.getMember()) || CommandHelper.hasRole(event, JdaService.bothelperRoles)) {
            return true;
        }
        MessageHelper.replyToMessage(event, "Only a megagame GM or bothelper can use `/megagame`.");
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), run(event, getGame()));
    }

    abstract String run(SlashCommandInteractionEvent event, Game game);

    static OptionData factionOption() {
        return new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color", true)
                .setAutoComplete(true);
    }

    static OptionData groupOption() {
        return new OptionData(OptionType.STRING, GROUP, "Group letter (A = Alpha, B = Beta, ...)", true)
                .addChoice("A (Alpha)", "A")
                .addChoice("B (Beta)", "B")
                .addChoice("C (Gamma)", "C")
                .addChoice("D (Delta)", "D")
                .addChoice("E (Epsilon)", "E")
                .addChoice("F (Zeta)", "F")
                .addChoice("G (Eta)", "G")
                .addChoice("H (Theta)", "H");
    }

    static Player targetPlayer(SlashCommandInteractionEvent event, Game game) {
        String factionOrColor =
                event.getOption(Constants.FACTION_COLOR).getAsString().toLowerCase();
        return game.getPlayerFromColorOrFaction(AliasHandler.resolveColor(factionOrColor));
    }

    static String withSummary(Game game, String message) {
        return (message.isBlank() ? "" : message + "\n") + MegagameGroupService.summary(game);
    }
}
