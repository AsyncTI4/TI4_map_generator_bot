package ti4.commands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class DraftLimits extends GameStateSubcommand {

    private record LimitConfig(String constant, String description, int threshold) {}

    private static final List<LimitConfig> LIMITS = List.of(
            new LimitConfig(Constants.ABILITY_LIMIT, "Ability Limit", -1),
            new LimitConfig(Constants.TECH_LIMIT, "Technology Limit", -1),
            new LimitConfig(Constants.AGENT_LIMIT, "Agent Limit", -1),
            new LimitConfig(Constants.COMMANDER_LIMIT, "Commander Limit", -1),
            new LimitConfig(Constants.HERO_LIMIT, "Hero Limit", -1),
            new LimitConfig(Constants.MECH_LIMIT, "Mech Limit", -1),
            new LimitConfig(Constants.FLAGSHIP_LIMIT, "Flagship Limit", -1),
            new LimitConfig(Constants.COMMODITIES_LIMIT, "Commodities Limit", -1),
            new LimitConfig(Constants.PN_LIMIT, "PN Limit", -1),
            new LimitConfig(Constants.HOMESYSTEM_LIMIT, "Homesystem Limit", -1),
            new LimitConfig(Constants.STARTINGTECH_LIMIT, "Starting technology Limit", -1),
            new LimitConfig(Constants.STARTINGFLEET_LIMIT, "Starting fleet Limit", -1),
            new LimitConfig(Constants.BLUETILE_LIMIT, "Blue Tile Limit", -1),
            new LimitConfig(Constants.REDTILE_LIMIT, "Red tile Limit", -1),
            new LimitConfig(Constants.FIRSTPICK_LIMIT, "First Pick Limit", 0),
            new LimitConfig(Constants.LATERPICK_LIMIT, "Later Pick Limit", 0));

    DraftLimits() {
        super(Constants.DRAFT_LIMITS, "Set limits for franken draft", true, false);
        // Automatically add all options from the list
        for (LimitConfig limit : LIMITS) {
            addOptions(new OptionData(OptionType.INTEGER, limit.constant(), limit.description()));
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        for (LimitConfig config : LIMITS) {
            Integer value = event.getOption(config.constant(), null, OptionMapping::getAsInt);

            if (value != null && value > config.threshold()) {
                updateLimit(event, game, config.constant(), value);
            }
        }
    }

    private void updateLimit(SlashCommandInteractionEvent event, Game game, String limitName, int value) {
        // Shared logic for key generation and messaging
        String storageKey = "frankenLimit" + StringUtils.upperCase(limitName.replace("_limit", ""));
        game.setStoredValue(storageKey, String.valueOf(value));

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Successfully set a " + limitName + " of " + value + ".");
    }
}
