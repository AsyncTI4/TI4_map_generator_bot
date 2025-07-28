package ti4.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import ti4.listeners.annotations.AnnotationHandler;

/**
 * Spring service that initializes the annotation handler with the ApplicationContext
 */
@Service
public class AnnotationHandlerInitializer {

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initialize() {
        AnnotationHandler.setApplicationContext(applicationContext);
    }
}