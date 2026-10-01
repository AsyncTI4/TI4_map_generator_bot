package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class TombRaidersAcd2ButtonHandler {

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        player.gainCommodities(2);
        List<String> types = new ArrayList<>(List.of("hazardous", "cultural", "industrial", "frontier"));
        StringBuilder sb = new StringBuilder();
        int fragmentsGained = 0;
        sb.append(player.getRepresentationUnfogged()).append(" gained 2 commodities and:");
        for (String type : types) {
            String cardId = game.drawExplore(type);
            ExploreModel card = Mapper.getExplore(cardId);
            String cardType = card.getResolution();
            sb.append("\n- ");
            if (Constants.FRAGMENT.equalsIgnoreCase(cardType)) {
                sb.append(ExploreEmojis.getFragEmoji(type)).append(" ");
            } else {
                sb.append("❌ ");
            }
            sb.append("Revealed _")
                    .append(card.getName())
                    .append("_ from the top of the ")
                    .append(type)
                    .append(" deck and ");
            if (Constants.FRAGMENT.equalsIgnoreCase(cardType)) {
                sb.append("gained it.");
                player.addFragment(cardId);
                fragmentsGained++;
                game.purgeExplore(cardId);
            } else {
                sb.append("discarded it.");
            }
        }
        sb.append("\n").append(getTombRaidersLoreQuip(fragmentsGained));
        CommanderUnlockCheckService.checkPlayer(player, "kollecc");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static String getTombRaidersLoreQuip(int fragmentsGained) {
        List<String> quips =
                switch (fragmentsGained) {
                    case 0 ->
                        List.of(
                                "\"They're digging in the wrong place!\" — Indiana Jones, _Raiders of the Lost Ark_",
                                "\"X never, ever marks the spot.\" — Indiana Jones, _Indiana Jones and the Last Crusade_",
                                "\"He chose... poorly.\" — Grail Knight, _Indiana Jones and the Last Crusade_");
                    case 1 ->
                        List.of(
                                "\"It belongs in a museum!\" — Indiana Jones, _Indiana Jones and the Last Crusade_",
                                "\"Throw me the idol, I'll throw you the whip!\" — Satipo, _Raiders of the Lost Ark_");
                    case 2 ->
                        List.of(
                                "\"You want to be a good archaeologist, you've got to get out of the library!\" — Indiana Jones, _Indiana Jones and the Last Crusade_",
                                "\"You and I are very much alike. Archaeology is our religion.\" — René Belloq, _Raiders of the Lost Ark_",
                                "\"This is the second time I've had to reclaim my property from you.\" — Indiana Jones, _Indiana Jones and the Last Crusade_");
                    case 3 ->
                        List.of(
                                "\"Fortune and glory, kid. Fortune and glory.\" — Indiana Jones, _Indiana Jones and the Temple of Doom_",
                                "\"Dr. Jones. Again we see there is nothing you can possess which I cannot take away.\" — René Belloq, _Raiders of the Lost Ark_");
                    default ->
                        List.of("\"You have chosen... wisely.\" — Grail Knight, _Indiana Jones and the Last Crusade_");
                };
        return quips.get(ThreadLocalRandom.current().nextInt(quips.size()));
    }
}
