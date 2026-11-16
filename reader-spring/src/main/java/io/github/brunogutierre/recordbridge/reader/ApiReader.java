package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads Spring MVC controllers into an {@link ApiModel}: one {@link Controller} per class annotated
 * with {@code @RestController} (or {@code @Controller} + {@code @ResponseBody}), with the types
 * their endpoints use.
 */
public final class ApiReader {

  private final ObjectMapper mapper;
  private final UnmarkedNullness unmarked;

  /** Creates a reader that follows the rules of the given mapper. */
  public ApiReader(ObjectMapper mapper, UnmarkedNullness unmarked) {
    this.mapper = mapper;
    this.unmarked = unmarked;
  }

  /** Reads the given classes; classes that are not REST controllers are ignored. */
  public ApiModel read(Collection<Class<?>> classes) {
    var types = new TypeReader(mapper, unmarked);
    var endpoints = new EndpointReader(types);
    var controllers = new ArrayList<Controller>();
    var controllerClasses = new java.util.LinkedHashSet<Class<?>>();
    classes.stream()
        .filter(ApiReader::isRestController)
        .sorted(Comparator.comparing(Class::getName))
        .forEach(type -> {
          controllerClasses.add(type);
          controllers.add(controller(type, endpoints));
        });
    var errors = new ErrorReader(types).read(classes, controllerClasses);
    var declarations = new ArrayList<>(types.declarations());
    declarations.add(ErrorReader.PROBLEM_DETAIL_TYPE);
    return new ApiModel(declarations, controllers, errors);
  }

  /** {@code OrderController} becomes {@code OrderApi}. */
  static String serviceName(Class<?> type) {
    String name = type.getSimpleName();
    return (name.endsWith("Controller") ? name.substring(0, name.length() - 10) : name) + "Api";
  }

  private static boolean isRestController(Class<?> type) {
    var annotations = MergedAnnotations.from(type, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
    return annotations.isPresent("org.springframework.stereotype.Controller")
        && annotations.isPresent(ResponseBody.class)
        && !type.isInterface()
        && !Modifier.isAbstract(type.getModifiers());
  }

  private static Controller controller(Class<?> type, EndpointReader reader) {
    RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
    String basePath = mapping == null ? "" : EndpointReader.first(mapping.path());
    List<Endpoint> endpoints =
        handlerMethods(type).stream().map(method -> reader.read(basePath, method)).toList();
    return new Controller(serviceName(type), endpoints);
  }

  /** Public methods with a request mapping, sorted by name for a stable output. */
  private static List<Method> handlerMethods(Class<?> type) {
    return Arrays.stream(type.getMethods())
        .filter(m -> !m.isBridge() && !m.isSynthetic())
        .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
        .sorted(Comparator.comparing(Method::getName))
        .toList();
  }
}
