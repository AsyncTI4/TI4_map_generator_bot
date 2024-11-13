package ti4.commands.franken;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public class FrankenCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new AbilityAdd(),
            new AbilityRemove(),
            new LeaderAdd(),
            new LeaderRemove(),
            new FactionTechAdd(),
            new FactionTechRemove(),
            new PNAdd(),
            new PNRemove(),
            new UnitAdd(),
            new UnitRemove(),
            new StartFrankenDraft(),
            new SetFactionIcon(),
            new SetFactionDisplayName(),
            new FrankenEdit(),
            new ShowFrankenBag(),
            new ShowFrankenHand(),
            new FrankenViewCard(),
            new BanAbility(),
            new ApplyDraftBags(),
            new SetHomeSystemPosition()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.FRANKEN;
    }

    @Override
    public String getDescription() {
        return "Franken";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getName())) {
            return false;
        }
        User user = event.getUser();
        String userID = user.getId();
        if (Objects.equals(event.getInteraction().getSubcommandName(), Constants.FRANKEN_EDIT)) {
            Member member = event.getMember();
            List<Role> roles = member.getRoles();
            for (Role role : AsyncTI4DiscordBot.bothelperRoles) {
                if (roles.contains(role)) {
                    return true;
                }
            }
        }

        String userActiveGameName = CommandHelper.getGameName(event);
        Game game = GameManager.getGame(userActiveGameName);
        if (!game.getPlayerIDs().contains(userID) && !game.isCommunityMode()) {
            MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
