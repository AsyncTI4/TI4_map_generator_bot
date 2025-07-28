package ti4.service;

import lombok.Getter;
import lombok.Setter;
import ti4.buttons.handlers.relics.DynamicRelicButtonHandler;
import ti4.listeners.annotations.AnnotationHandler;

/**
 * Static service registry for dependency injection in button handlers
 */
public class ServiceRegistry {
    @Setter
    @Getter
    private static RelicService relicService;
    @Setter
    @Getter
    private static ComponentActionService componentActionService;
    @Setter
    @Getter
    private static MessageService messageService;
    @Setter
    @Getter
    private static DynamicRelicButtonHandler dynamicRelicButtonHandler;

    private static HandlerRegistry handlerRegistry;

    public static void initialize() {
        relicService = new RelicService();
        componentActionService = new ComponentActionService();
        messageService = new MessageService();
        dynamicRelicButtonHandler = new DynamicRelicButtonHandler(
            relicService, componentActionService, messageService);


        handlerRegistry = new HandlerRegistry();
        handlerRegistry.registerHandler(DynamicRelicButtonHandler.class, dynamicRelicButtonHandler);

        // Initialize the annotation system with our registry
        AnnotationHandler.setHandlerRegistry(handlerRegistry);
    }

    // For testing - reset to production defaults
    static void reset() {
        initialize();
    }
}