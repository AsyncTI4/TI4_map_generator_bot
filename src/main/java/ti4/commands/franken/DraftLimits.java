package ti4.commands.franken;

import com.amazonaws.util.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class DraftLimits extends GameStateSubcommand {

    public DraftLimits() {
        super(Constants.DRAFT_LIMITS, "Set limits for franken draft", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ABILITY_LIMIT, "Ability Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.TECH_LIMIT, "Technology Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENT_LIMIT, "Agent Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.COMMANDER_LIMIT, "Commander Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.HERO_LIMIT, "Hero Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MECH_LIMIT, "Mech Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FLAGSHIP_LIMIT, "Flagship Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_LIMIT, "Commodities Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PN_LIMIT, "PN Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.HOMESYSTEM_LIMIT, "Homesystem Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.STARTINGTECH_LIMIT, "Starting technology Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.STARTINGFLEET_LIMIT, "Starting fleet Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.BLUETILE_LIMIT, "Blue Tile Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.REDTILE_LIMIT, "Red tile Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FIRSTPICK_LIMIT, "First Pick Limit"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LATERPICK_LIMIT, "Later Pick Limit"));

    }

    public void execute(SlashCommandInteractionEvent event) {
        //ABILITY, TECH, AGENT, COMMANDER, HERO, MECH, FLAGSHIP, COMMODITIES, PN, HOMESYSTEM, STARTINGTECH, STARTINGFLEET, BLUETILE, REDTILE, DRAFTORDER
        Game game = getGame();

        String limitName = Constants.ABILITY_LIMIT;
        Integer limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.TECH_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.AGENT_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.COMMANDER_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.HERO_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.MECH_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.FLAGSHIP_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.FIRSTPICK_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.LATERPICK_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.PN_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.STARTINGFLEET_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.STARTINGTECH_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.REDTILE_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.BLUETILE_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.COMMODITIES_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

        limitName = Constants.HOMESYSTEM_LIMIT;
        limit = event.getOption(limitName, null, OptionMapping::getAsInt);
        if (limit != null && limit > 0) {
            game.setStoredValue("frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", "")), "" + limit);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully set a " + limitName + " of " + limit + ".");
        }

    }

}
