package ti4.commands.bothelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;

class CreateGameChannels extends Subcommand {

    public CreateGameChannels() {
        super(Constants.CREATE_GAME_CHANNELS, "Create Role and Game Channels for a New Game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_FUN_NAME, "Fun Name for the Channel - e.g. pbd###-fun-name-goes-here").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName - this will be the game owner, who will complete /game setup").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Override default game/role name (next pbd###)"));
        addOptions(new OptionData(OptionType.STRING, Constants.CATEGORY, "Override default Category #category-name (PBD #XYZ-ZYX)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        OptionMapping gameNameOption = event.getOption(Constants.GAME_NAME);
        String gameName;
        if (gameNameOption != null) {
            gameName = gameNameOption.getAsString();
        } else {
            gameName = CreateGameService.getNextGameName();
        }
        if (CreateGameService.gameOrRoleAlreadyExists(gameName)) {
            MessageHelper.sendMessageToEventChannel(event, "Role or Game: **" + gameName
                + "** already exists accross all supported servers. Try again with a new name.");
            return;
        }

        // CHECK IF GIVEN CATEGORY IS VALID
        String categoryChannelName = event.getOption(Constants.CATEGORY, null, OptionMapping::getAsString);
        Category categoryChannel = null;
        if (categoryChannelName != null && !categoryChannelName.isEmpty()) {
            List<Category> categoriesWithName = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false);
            if (categoriesWithName.size() > 1) {
                MessageHelper.sendMessageToEventChannel(event, "Too many categories with this name!!");
                return;
            } else if (categoriesWithName.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "Category not found");
                return;
            } else {
                categoryChannel = AsyncTI4DiscordBot.jda.getCategoriesByName(categoryChannelName, false).getFirst();
            }
        } else { // CATEGORY WAS NOT PROVIDED, FIND OR CREATE ONE
            categoryChannelName = CreateGameService.getCategoryNameForGame(gameName);
            if (categoryChannelName == null) {
                MessageHelper.sendMessageToEventChannel(event, "Category could not be automatically determined. Please provide a category name for this game.");
                return;
            }
            List<Category> categories = CreateGameService.getAllAvailablePBDCategories();
            for (Category category : categories) {
                if (category.getName().startsWith(categoryChannelName)) {
                    categoryChannel = category;
                    break;
                }
            }
            if (categoryChannel == null)
                categoryChannel = CreateGameService.createNewCategory(categoryChannelName);
            if (categoryChannel == null) {
                MessageHelper.sendMessageToEventChannel(event, "Could not automatically find a category that begins with **" + categoryChannelName
                    + "** - Please create this category.");
                return;
            }
        }

        // CHECK IF CATEGORY EXISTS
        if (categoryChannel == null || categoryChannel.getType() != ChannelType.CATEGORY) {
            MessageHelper.sendMessageToEventChannel(event, "Category: **" + categoryChannelName
                + "** does not exist. Create the category or pick a different category, then try again.");
            return;
        }

        // CHECK IF SERVER CAN SUPPORT A NEW GAME
        Guild guild = categoryChannel.getGuild();
        if (!CreateGameService.serverCanHostNewGame(guild)) {
            MessageHelper.sendMessageToEventChannel(event, "Server **" + guild.getName() + "** can not host a new game - please contact @Admin to resolve.");
            return;
        }

        // CHECK IF CATEGORY HAS ROOM
        Category category = categoryChannel;
        if (category.getChannels().size() > 48) {
            MessageHelper.sendMessageToEventChannel(event, "Category: **" + category.getName() + "** is full on server **" + guild.getName()
                + "**. Create a new category then try again.");
            return;
        }

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
        String gameFunName = event.getOption(Constants.GAME_FUN_NAME).getAsString();

        Game game = CreateGameService.createGameChannels(members, event, gameFunName, gameName, gameOwner, categoryChannel);
        GameManager.save(game, "Created game channels");
    }
}
