package ti4.discord.interactions.commands.monuments;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

class BelkoseaToken extends GameStateSubcommand {

    enum Action {
        ADD,
        REMOVE,
        MOVE
    }

    private final Action action;

    BelkoseaToken(Action action) {
        super("belkosea_token_" + action.name().toLowerCase(), getDescription(action), true, true);
        this.action = action;
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, getOptionDescription(action))
                .setRequired(true)
                .setAutoComplete(true));
        if (action == Action.MOVE) {
            addOptions(new OptionData(OptionType.STRING, "replacement", "Superweapon to receive the token")
                    .setRequired(true)
                    .setAutoComplete(true));
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        if (!game.isMonumentsMode() || !player.hasUnit("belkosea_monument")) {
            MessageHelper.sendMessageToEventChannel(event, "You do not own the _Armageddon Project_ monument.");
            return;
        }
        String relic = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        String replacement = event.getOption("replacement", null, OptionMapping::getAsString);
        boolean validRelic = action == Action.ADD ? isSuperweaponInPlay(game, relic) : isSuperweapon(relic);
        if (!validRelic || (action == Action.MOVE && !isSuperweaponInPlay(game, replacement))) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Choose a valid Superweapon; additions and move destinations must be in a player's play area.");
            return;
        }
        Set<String> superweapons = MonumentsBRButtonHandler.getArmageddonProjectSuperweapons(game, player);
        if (action == Action.ADD) {
            if (!superweapons.add(relic)) {
                MessageHelper.sendMessageToEventChannel(event, "That Superweapon already has your control token.");
                return;
            }
            MonumentsBRButtonHandler.setArmageddonProjectSuperweapons(game, player, superweapons);
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationNoPing() + " placed a control token on _"
                            + Mapper.getRelic(relic).getName() + "_.");
            return;
        }
        if (action == Action.REMOVE) {
            if (!superweapons.remove(relic)) {
                MessageHelper.sendMessageToEventChannel(event, "That Superweapon does not have your control token.");
                return;
            }
            MonumentsBRButtonHandler.setArmageddonProjectSuperweapons(game, player, superweapons);
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationNoPing() + " removed their control token from _"
                            + Mapper.getRelic(relic).getName() + "_.");
            return;
        }
        if (!superweapons.remove(relic)) {
            MessageHelper.sendMessageToEventChannel(event, "That Superweapon does not have your control token.");
            return;
        }
        superweapons.add(replacement);
        MonumentsBRButtonHandler.setArmageddonProjectSuperweapons(game, player, superweapons);
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentationNoPing() + " moved their control token from _"
                        + Mapper.getRelic(relic).getName() + "_ to _"
                        + Mapper.getRelic(replacement).getName() + "_.");
    }

    private static boolean isSuperweaponInPlay(Game game, String relic) {
        RelicModel relicModel = Mapper.getRelic(relic);
        return isSuperweapon(relic)
                && relicModel != null
                && game.getRealPlayers().stream().anyMatch(player -> player.hasRelic(relic));
    }

    private static boolean isSuperweapon(String relic) {
        return relic != null && relic.startsWith("superweapon") && Mapper.getRelic(relic) != null;
    }

    private static String getDescription(Action action) {
        return switch (action) {
            case ADD -> "Place your Armageddon Project token on a Superweapon";
            case REMOVE -> "Remove your Armageddon Project token from a Superweapon";
            case MOVE -> "Move your Armageddon Project token between Superweapons";
        };
    }

    private static String getOptionDescription(Action action) {
        return action == Action.MOVE ? "Superweapon relic to remove the token from" : "Superweapon relic";
    }
}
