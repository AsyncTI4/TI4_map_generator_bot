package ti4.commands.bothelper;

import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.fow.CreateFoWGameService;

class CreateFOWGameChannels extends Subcommand {

    public CreateFOWGameChannels() {
        super(Constants.CREATE_FOW_GAME_CHANNELS, "Create Role and Game Channels for a New FOW Game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.FOWGM, "Default GM is whoever runs this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        String gameName = CreateFoWGameService.getNextFOWGameName();

        // CHECK IF GIVEN CATEGORY IS VALID
        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.sendMessageToEventChannel(event, "Guild was null");
            return;
        }

        // PLAYERS
        Member gameOwner = CreateFoWGameService.getGM(event);
        List<Member> members = CreateFoWGameService.getPlayers(event);

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        if (!CreateFoWGameService.serverCanHostNewGame(guild, members.size() + 1)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        // CHECK IF GUILD HAS ALL PLAYERS LISTED
        List<String> guildMemberIDs =
                guild.getMembers().stream().map(ISnowflake::getId).toList();
        boolean sendInviteLink = false;
        int count = 0;
        for (Member member : members) {
            if (!guildMemberIDs.contains(member.getId())) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        member.getAsMention() + " is not a member of the server **" + guild.getName()
                                + "**. Please use the invite below to join the server and then try this command again.");
                sendInviteLink = true;
                count++;
            }
        }
        if (sendInviteLink) {
            MessageHelper.sendMessageToEventChannel(event, Helper.getGuildInviteURL(guild, count + 1));
            return;
        }

        CreateFoWGameService.executeCreateFoWGame(guild, gameName, "", gameOwner, members, event.getChannel());
    }
}
