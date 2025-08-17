package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameHelper;
import ti4.jda.JdaService;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class CreateGameButton extends Subcommand {

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
        String gameName = CreateGameService.getNextGameName();

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = CreateGameService.getCategoryNameForGame(gameName);
        Category categoryChannel = null;
        List<Category> categories = CreateGameService.getAllAvailablePBDCategories();
        for (Category category : categories) {
            if (category.getName().toUpperCase().startsWith(categoryChannelName)) {
                categoryChannel = category;
                break;
            }
        }
        if (categoryChannel == null) categoryChannel = CreateGameService.createNewCategory(categoryChannelName);
        if (categoryChannel == null) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Could not automatically find a category that begins with **" + categoryChannelName
                            + "** - Please create this category.\n# Warning, this may mean all servers are at capacity.");
            return;
        }
        // SET GUILD BASED ON CATEGORY SELECTED
        Guild guild = categoryChannel.getGuild();

        // PLAYERS
        List<Member> members = new ArrayList<>();
        Member gameOwner = null;
        for (int i = 1; i <= 8; i++) {
            if (Objects.nonNull(event.getOption("player" + i))) {
                Member member = event.getOption("player" + i).getAsMember();
                if (member != null) members.add(member);
                else {
                    continue;
                }

                if (!member.getUser().isBot() && !CommandHelper.hasRole(event, JdaService.developerRoles)) {
                    int ongoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, false, false, true, false, true, true, true);
                    int completedAndOngoingAmount = SearchGameHelper.searchGames(
                            member.getUser(), event, false, true, false, true, false, true, true, true);
                    int completedGames = completedAndOngoingAmount - ongoingAmount;
                    if (ongoingAmount > completedGames + 2) {
                        MessageHelper.sendMessageToChannel(
                                event.getChannel(),
                                member.getUser().getAsMention()
                                        + " is at their game limit (# of ongoing games must be equal or less than # of completed games + 3) and so cannot join more games at the moment."
                                        + " Their number of ongoing games is " + ongoingAmount
                                        + " and their number of completed games is " + completedGames + ".");
                        return;
                    }
                    // Used for specific people we are limiting the amount of games of
                    // if (member.getId().equalsIgnoreCase("400038967744921612")) {
                    //     if (ongoingAmount > 6) {
                    //         MessageHelper.sendMessageToChannel(event.getChannel(), "One of the games proposed members
                    // is currently under a limit and cannot join more games at this time");
                    //         return;
                    //     }
                    // }
                }
                if (gameOwner == null) gameOwner = member;
            } else {
                break;
            }
        }

        // CHECK IF GUILD HAS ALL PLAYERS LISTED
        CreateGameService.inviteUsersToServer(guild, members, event.getMessageChannel());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("createGameChannels", "Create Game"));
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();
        if (!members.isEmpty()) {
            StringBuilder buttonMsg =
                    new StringBuilder("Game Fun Name: " + gameFunName.replace(":", "") + "\nPlayers:\n");
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
            buttonMsg
                    .append("\n\n")
                    .append(" Please hit this button after confirming that the members are the correct ones.");
            MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(buttonMsg.toString());
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), buttonMsg.toString(), buttons);
            ActionRow actionRow = ActionRow.of(buttons);
            baseMessageObject.addComponents(actionRow);
        }
    }
}
