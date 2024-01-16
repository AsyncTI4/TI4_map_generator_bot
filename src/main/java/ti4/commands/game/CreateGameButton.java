package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.bothelper.CreateGameChannels;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

public class CreateGameButton extends GameSubcommandData {
    public CreateGameButton() {
        super(Constants.CREATE_GAME_BUTTON, "Create Game Creation Button");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        String gameName = CreateGameChannels.getNextGameName();
        if ("pbd2000".equals(gameName)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"No more games can be created. Please contact @Developer to resolve."); // See comments in getAllExistingPBDNumbers
            return;
        }

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = CreateGameChannels.getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = CreateGameChannels.getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null) categoryChannel = CreateGameChannels.createNewCategory(categoryChannelName);

        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        // PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
                if (gameOwner == null) gameOwner = member;
            } else {
                break;
            }
        }

        // CHECK IF GUILD HAS ALL PLAYERS LISTED
        CreateGameChannels.inviteUsersToServer(guild, members, event.getMessageChannel());

        String buttonMsg = "";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("createGameChannels", "Create Game"));
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();

        if (members.size() > 0) {
            buttonMsg = "Game Fun Name: " + gameFunName + "\nPlayers:\n";
            int counter = 1;
            for (Member member : members) {
                buttonMsg = buttonMsg + counter + ":" + member.getId() + ".(" + member.getAsMention() + ")\n";
                counter++;
            }
            Role bothelperRole = CreateGameChannels.getRole("Bothelper", event.getGuild());
            buttonMsg = buttonMsg + "\n\n" + bothelperRole.getAsMention() + " this game is ready for you to create";
            MessageHelper.sendMessageToChannel(event.getChannel(), buttonMsg, buttons);
        }
    }

    public static void decodeButtonMsg(ButtonInteractionEvent event) {
        event.getChannel().sendMessage(event.getUser().getEffectiveName() + " pressed the [Create Game] button").queue();
        Member member = event.getMember();
        boolean isAdmin = false;
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (!isAdmin) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Only authorized users can press this button successfully.");
            return;
        }
        event.editButton(null).queue();
        
        String buttonMsg = event.getMessage().getContentRaw();
        String gameSillyName = StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 3; i <= 10; i++) {
            if (StringUtils.countMatches(buttonMsg, ":") >= (i)) {
                String user = buttonMsg.split(":")[i];
                user = StringUtils.substringBefore(user, ".");
                Member member2 = event.getGuild().getMemberById(user);
                if (member2 != null) members.add(member2);
                if (gameOwner == null) gameOwner = member2;
            } else {
                break;
            }
        }

        String gameName = CreateGameChannels.getNextGameName();
        if ("pbd2000".equals(gameName)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No more games can be created. Please contact @Developer to resolve."); // See comments in getAllExistingPBDNumbers
            return;
        }
        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = CreateGameChannels.getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = CreateGameChannels.getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null) categoryChannel = CreateGameChannels.createNewCategory(categoryChannelName);
        event.getMessage().delete().queue();
        CreateGameChannels.createGameChannels(members, event, gameSillyName, gameName, gameOwner, categoryChannel);
    }
}
