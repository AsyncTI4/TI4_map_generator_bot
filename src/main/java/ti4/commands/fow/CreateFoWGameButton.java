package ti4.commands.fow;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.CreateFoWGameService;
import ti4.service.game.CreateGameService;

class CreateFoWGameButton extends Subcommand {

    public CreateFoWGameButton() {
        super(Constants.CREATE_FOW_GAME_BUTTON, "Create FoW Game Creation Button");
        addOptions(
                new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Game").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
        addOptions(new OptionData(OptionType.USER, Constants.FOWGM, "Default GM is whoever runs this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member gm = CreateFoWGameService.getGM(event);
        List<Member> members = CreateFoWGameService.getPlayers(event);
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();

        Guild guild = CreateFoWGameService.findFoWGuildWithSpace(event.getGuild(), members.size() + 1);
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(
                    event, "All FoW Server are full. Can not host a new game - please contact @Bothelper to resolve.");
            return;
        }

        // Make sure everyone is in the right server
        List<Member> allMembers = new ArrayList<>(members);
        allMembers.add(gm);
        CreateGameService.inviteUsersToServer(guild, allMembers, event.getMessageChannel());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("createFoWGameChannels", "Create FoW Game"));

        StringBuilder buttonMsg = new StringBuilder("## Game Fun Name: " + gameFunName.replace(":", ""));
        buttonMsg
                .append("\nGM: ")
                .append(gm.getId())
                .append(".(")
                .append(gm.getEffectiveName().replace(":", ""))
                .append(")\n\nPlayers:\n");
        int counter = 1;
        for (Member member : members) {
            buttonMsg
                    .append(counter)
                    .append(":")
                    .append(member.getId())
                    .append(".(")
                    .append(member.getEffectiveName().replace(":", ""))
                    .append(")\n");
            counter++;
        }
        buttonMsg.append("\nPlease hit this button after confirming that the members are the correct ones");
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(buttonMsg.toString());
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), buttonMsg.toString(), buttons);
        ActionRow actionRow = ActionRow.of(buttons);
        baseMessageObject.addComponents(actionRow);
    }
}
