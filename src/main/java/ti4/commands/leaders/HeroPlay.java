package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.PlayHeroService;

class HeroPlay extends GameStateSubcommand {

    public HeroPlay() {
        super(Constants.ACTIVE_LEADER, "Play Hero", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action")
            .setAutoComplete(true));
        addOptions(
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String leader = event.getOption(Constants.LEADER, "hero", OptionMapping::getAsString);

        Leader playerLeader = player.unsafeGetLeader(leader);

        if (playerLeader == null) {
            MessageHelper.sendMessageToEventChannel(event, "Leader '" + leader + "'' could not be found. The leader might have been purged earlier.");
            return;
        }

        if (playerLeader.isLocked()) {
            MessageHelper.sendMessageToEventChannel(event, "Leader is locked, use command to unlock `/leaders unlock leader:" + leader + "`");
            MessageHelper.sendMessageToEventChannel(event, Helper.getLeaderLockedRepresentation(playerLeader));
            return;
        }

        if (!playerLeader.getType().equals(Constants.HERO)) {
            MessageHelper.sendMessageToEventChannel(event, "Leader is not a hero");
            return;
        }

        PlayHeroService.playHero(event, game, player, playerLeader);
    }
}
