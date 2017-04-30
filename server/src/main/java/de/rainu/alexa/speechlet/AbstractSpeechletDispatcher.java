package de.rainu.alexa.speechlet;

import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import de.rainu.alexa.annotation.OnIntent;
import de.rainu.alexa.annotation.OnLaunch;
import de.rainu.alexa.annotation.OnSessionEnded;
import de.rainu.alexa.annotation.OnSessionStarted;
import de.rainu.alexa.util.ReflectionUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSpeechletDispatcher implements Speechlet {
  private static final Logger log = LoggerFactory.getLogger(AbstractSpeechletDispatcher.class);

  private final Map<Class<? extends Annotation>, List<Method>> handler = new HashMap<>();

  protected AbstractSpeechletDispatcher() {
    initialiseHandler();
    analyseMyself();
  }

  private void initialiseHandler(){
    handler.put(OnLaunch.class, new ArrayList<>());
    handler.put(OnIntent.class, new ArrayList<>());
    handler.put(OnSessionStarted.class, new ArrayList<>());
    handler.put(OnSessionEnded.class, new ArrayList<>());
  }

  public final void onSessionStarted(final SessionStartedRequest request, final Session session) throws SpeechletException {
    log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
    call(OnSessionStarted.class, request, session);
  }

  public final SpeechletResponse onLaunch(final LaunchRequest request, final Session session) throws SpeechletException {
    log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
    return callLaunch(request, session);
  }

  public final SpeechletResponse onIntent(final IntentRequest request, final Session session) throws SpeechletException {
    log.info("onIntent requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
    return callIntent(request, session);
  }

  public final void onSessionEnded(final SessionEndedRequest request, final Session session) throws SpeechletException {
    log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
    call(OnSessionEnded.class, request, session);
  }

  private void analyseMyself(){
    for(Method method : getClass().getDeclaredMethods()) {
      if(method.isAnnotationPresent(OnIntent.class)){
        log.info("Mapped speechlet handler: {}({}) onto {}",
            OnIntent.class.getSimpleName(),
            method.getAnnotation(OnIntent.class).value(),
            method);

        if(checkOnIntent(method)) {
          registerHandler(OnIntent.class, method);
        }
      } else if(method.isAnnotationPresent(OnLaunch.class)){
        log.info("Mapped speechlet handler: {} onto {}",
            OnLaunch.class.getSimpleName(),
            method);

        if(checkOnLaunch(method)) {
          registerHandler(OnLaunch.class, method);
        }
      } else if(method.isAnnotationPresent(OnSessionEnded.class)){
        log.info("Mapped speechlet handler: {} onto {}",
            OnSessionEnded.class.getSimpleName(),
            method);

        registerHandler(OnSessionEnded.class, method);
      } else if(method.isAnnotationPresent(OnSessionStarted.class)){
        log.info("Mapped speechlet handler: {} onto {}",
            OnSessionStarted.class.getSimpleName(),
            method);

        registerHandler(OnSessionStarted.class, method);
      }
    }
  }

  private boolean checkOnIntent(Method method) {
    if(method.getReturnType() != SpeechletResponse.class) {
      log.error("Return type of intent speechlet handler have to be {} and not {}. Skip this handler!",
          SpeechletResponse.class.getName(),
          method.getReturnType().getName());
      return false;
    }

    return true;
  }

  private boolean checkOnLaunch(Method method) {
    if(method.getReturnType() != SpeechletResponse.class) {
      log.error("Return type of launch speechlet handler have to be {} and not {}. Skip this handler!",
          SpeechletResponse.class.getName(),
          method.getReturnType().getName());
      return false;
    }

    return true;
  }

  private void registerHandler(Class<? extends Annotation> annotation, Method method) {
    handler.get(annotation).add(method);
  }

  private void call(Class<? extends Annotation> annotation, Object...args) throws SpeechletException {
    List<Method> toCall = handler.get(annotation);
    for (Method method : toCall) {
      call(method, args);
    }
  }

  private SpeechletResponse callIntent(IntentRequest request, Session session) throws SpeechletException {
    final String intent = request.getIntent().getName();

    Optional<Method> target = handler.get(OnIntent.class).stream()
        .filter(m -> m.getAnnotation(OnIntent.class).value().equals(intent))
        .findAny();

    if(target.isPresent()) {
      return (SpeechletResponse)call(target.get(), request, session);
    }

    log.error("Could not found handler for intent \"{}\"", intent);
    throw new SpeechletException("Invalid Intent");
  }

  private SpeechletResponse callLaunch(LaunchRequest request, Session session) throws SpeechletException {
    Optional<Method> target = handler.get(OnLaunch.class).stream()
        .findAny();

    if(target.isPresent()) {
      return (SpeechletResponse)call(target.get(), request, session);
    }

    log.error("Could not found handler for launch event!");
    throw new SpeechletException("Launch event is not implemented");
  }

  private Object call(Method method, Object...args) throws SpeechletException {
    try {
      return ReflectionUtils.call(method, this, args);
    } catch (UndeclaredThrowableException e) {
      if(e.getCause() instanceof SpeechletException) {
        throw (SpeechletException)e.getCause();
      }

      throw e;
    }
  }
}