package ti4.listeners.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.map.Game;
import ti4.map.Player;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>ButtonHandler(string) where buttonID.startsWith(string)</p>
 * <p>Supported Variables:</p>
 * <ul>
 *   <li>events<ul>
 *     <li>{@link GenericInteractionCreateEvent}</li>
 *     <li>{@link ButtonInteractionEvent}</li>
 *   </ul>
 *   <li>{@link Game} -&gt game derived from channels</li>
 *   <li>{@link Player} -&gt user who pressed button</li>
 *   <li>{@link MessageChannel} -&gt; event.getMessageChannel()</li>
 *   <li>{@link String} -&gt; buttonID</li>
 * </ul>
 * <p>To handle multiple String parameter you must use {@link NamedParam}</p>
 */
@Documented
@Retention(RUNTIME)
@Repeatable(ManyButtonHandlers.class)
@Target({ ElementType.METHOD })
public @interface ButtonHandler {
    String value();
}
