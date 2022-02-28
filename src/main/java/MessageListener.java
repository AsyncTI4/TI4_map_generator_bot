import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MessageListener extends ListenerAdapter
{

	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		if (event.isFromType(ChannelType.PRIVATE))
		{
			System.out.printf("[PM] %s: %s\n", event.getAuthor().getName(),
					event.getMessage().getContentDisplay());
		}
		else
		{
			System.out.printf("[%s][%s] %s: %s\n", event.getGuild().getName(),
					event.getTextChannel().getName(), event.getMember().getEffectiveName(),
					event.getMessage().getContentDisplay());
		}
		Message msg = event.getMessage();
		if (msg.getContentRaw().equals("!test"))
		{
			MessageChannel channel = event.getChannel();
			long time = System.currentTimeMillis();
			channel.sendMessage("I'm alive!") /* => RestAction<Message> */
					.queue(response /* => Message */ -> {
						response.editMessageFormat("Here: %d ms", System.currentTimeMillis() - time).queue();
					});
		}
	}
}