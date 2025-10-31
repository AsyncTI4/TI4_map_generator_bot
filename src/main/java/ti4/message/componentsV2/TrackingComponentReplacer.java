package ti4.message.componentsV2;

import net.dv8tion.jda.api.components.replacer.ComponentReplacer;

public interface TrackingComponentReplacer extends ComponentReplacer {
    /**
     * Called before the ComponentReplacer is passed to a component tree's replace
     * method.
     * This resets the tracking being done on the whole operation.
     */
    void startingChanges();

    /**
     * Called after the component tree's replace method has finished with the
     * ComponentReplacer.
     * This returns the state being tracked for the operation
     *
     * @return The state that was produced by tracking the operation.
     */
    Boolean finishedChanges();
}
