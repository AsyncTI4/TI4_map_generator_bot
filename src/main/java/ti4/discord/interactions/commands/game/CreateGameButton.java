package ti4.discord.interactions.commands.game;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class CreateGameButton extends Subcommand {

    CreateGameButton() {
        super(Constants.CREATE_GAME_BUTTON, "Create Game Creation Button");
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.GAME_FUN_NAME,
                        "Fun name for the channel; a single underscore alone will generate a random name")
                .setRequired(true));
        addOptions(CommonOptions.playerOptions(true, i -> "Player" + i));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Member> members = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Member member = event.getOption("player" + i, null, OptionMapping::getAsMember);
            if (member == null) {
                continue;
            }
            members.add(member);
        }

        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();
        if ("_".equals(gameFunName)) {
            gameFunName = CreateGameService.autoGenerateGameName();
        }

        if (members.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Please provide at least one valid player.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("launchGame", "Launch Game"));
        buttons.add(Buttons.green("joinGameList", "Join Game"));
        buttons.add(Buttons.red("leaveGameList", "Leave Game"));
        buttons.add(Buttons.gray("editPlayers~MDL", "Add Players"));
        buttons.add(Buttons.gray("removePlayers~MDL", "Remove Players"));
        buttons.add(Buttons.gray("addSillyName~MDL", "Set Game Name"));

        String message = CreateGameButtonHandler.generateMemberListMessage(members, gameFunName)
                + "\n\nPlease hit this button after confirming that the members are the correct ones.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }
}
