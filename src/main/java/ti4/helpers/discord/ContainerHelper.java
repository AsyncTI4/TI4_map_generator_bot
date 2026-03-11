package ti4.helpers.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

@UtilityClass
public class ContainerHelper {

    public Container appendComponents(Container c, ContainerChildComponent... childComponents) {
        List<ContainerChildComponent> components = new ArrayList<>(c.getComponents());
      Collections.addAll(components, childComponents);
        return c.withComponents(components);
    }
}
