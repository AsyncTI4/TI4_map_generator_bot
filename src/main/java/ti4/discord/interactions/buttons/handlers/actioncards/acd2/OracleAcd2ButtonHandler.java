package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.UnusedCommanderHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.BreakthroughModel;
import ti4.model.ExploreModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.model.UnitModel;
import ti4.service.RemoveCommandCounterService;
import ti4.service.combat.CombatUnitSelectionHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.planet.FlipTileService;
import ti4.service.planet.PlanetService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
class OracleAcd2ButtonHandler {

    @ButtonHandler("resolveOracle")
    public static void resolveOracle(Player player, Game game, ButtonInteractionEvent event) {
        List<MessageEmbed> embeds = new ArrayList<>();
        game.peekAtAllUnrevealedPublicObjectives(player);

        for (String objectiveId : game.getPublicObjectives1Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String objectiveId : game.getPublicObjectives2Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String secretId : game.peekAtSecrets(5)) {
            embeds.add(Mapper.getSecretObjective(secretId).getRepresentationEmbed(true));
        }

        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                "Showing all unrevealed public objectives and the top 5 secret objectives from the deck.",
                embeds);
        Collections.shuffle(game.getSecretObjectives());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Sent _Oracle_ results to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread and shuffled the secret objective deck.");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
