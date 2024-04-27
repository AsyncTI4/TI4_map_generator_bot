package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.bothelper.CreateGameChannels;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CreateGameButton extends GameSubcommandData {
    public CreateGameButton() {
        super(Constants.CREATE_GAME_BUTTON, "Create Game Creation Button");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel")
            .setRequired(true));
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
        if (categoryChannel == null)
            categoryChannel = CreateGameChannels.createNewCategory(categoryChannelName);

        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        // PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null)
                    members.add(member);
                if (gameOwner == null)
                    gameOwner = member;
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
            buttonMsg = "Game Fun Name: " + gameFunName.replace(":", "") + "\nPlayers:\n";
            int counter = 1;
            for (Member member : members) {
                buttonMsg = buttonMsg + counter + ":" + member.getId() + ".("
                    + member.getEffectiveName().replace(":", "")
                    + ")\n";
                counter++;
            }
            Role bothelperRole = CreateGameChannels.getRole("Bothelper", event.getGuild());
            buttonMsg = buttonMsg + "\n\n" + " Please hit this button after confirming that the members are the correct ones.";
            MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(buttonMsg);
            // MessageHelper.sendMessageToChannel(event.getChannel(), buttonMsg, buttons);
            ActionRow actionRow = ActionRow.of(buttons);
            baseMessageObject.addComponents(actionRow);
            // event.getChannel().sendMessage(baseMessageObject.build()).queue(message_ -> {
            //     String msg = bothelperRole.getAsMention() + " this game is ready for launching "
            //         + message_.getJumpUrl();
            //     TextChannel bothelperLoungeChannel = AsyncTI4DiscordBot.guildPrimary
            //         .getTextChannelsByName("staff-lounge", true).stream().findFirst().orElse(null);
            //     if (bothelperLoungeChannel == null)
            //         return;
            //     List<ThreadChannel> threadChannels = bothelperLoungeChannel.getThreadChannels();
            //     if (threadChannels.isEmpty())
            //         return;
            //     String threadName = "game-starts-and-ends";
            //     // SEARCH FOR EXISTING OPEN THREAD
            //     for (ThreadChannel threadChannel_ : threadChannels) {
            //         if (threadChannel_.getName().equals(threadName)) {
            //             MessageHelper.sendMessageToChannel(threadChannel_, msg);
            //             break;
            //         }
            //     }
            // });
        }
    }

    public static void decodeButtonMsg(ButtonInteractionEvent event) {
        event.getChannel().sendMessage(event.getUser().getEffectiveName() + " pressed the [Create Game] button")
            .queue();
        Member member = event.getMember();
        boolean isAdmin = false;
        Game mapreference = GameManager.getInstance().getGame("finreference");

        if (mapreference.getStoredValue("allowedButtonPress").equalsIgnoreCase("false")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Admins have temporarily turned off game creation, most likely to contain a bug. Please be patient and they'll get back to you on when its fixed.");
            return;
        }
        if (member != null) {
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        if (!isAdmin && !mapreference.getStoredValue("gameCreator" + member.getIdLong()).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You created a game within the last 10 minutes and thus are being stopped from creating more until some time has passed.");
            return;
        } else {
            mapreference.setStoredValue("gameCreator" + member.getIdLong(), "created");
        }
        event.editButton(null).queue();

        String buttonMsg = event.getMessage().getContentRaw();
        String gameSillyName = StringUtils.substringBetween(buttonMsg, "Game Fun Name: ", "\n");
        String gameName = CreateGameChannels.getNextGameName();
        String lastGame = CreateGameChannels.getLastGameName();
        Game activeGame = GameManager.getInstance().getGame(lastGame);
        if (activeGame != null) {
            if (activeGame.getCustomName().equalsIgnoreCase(gameSillyName)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "The custom name of the last game is the same as the one for this game, so the bot suspects a double press occurred and is cancelling the creation of another game. ");
                return;
            }
        }
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 3; i <= 10; i++) {
            if (StringUtils.countMatches(buttonMsg, ":") >= (i)) {
                String user = buttonMsg.split(":")[i];
                user = StringUtils.substringBefore(user, ".");
                Member member2 = event.getGuild().getMemberById(user);
                if (member2 != null)
                    members.add(member2);
                if (gameOwner == null)
                    gameOwner = member2;
            } else {
                break;
            }
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
        if (categoryChannel == null)
            categoryChannel = CreateGameChannels.createNewCategory(categoryChannelName);
        event.getMessage().delete().queue();
        CreateGameChannels.createGameChannels(members, event, gameSillyName, gameName, gameOwner, categoryChannel);
    }
}
