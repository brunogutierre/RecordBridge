package io.github.brunogutierre.recordbridge.reader;

import io.github.brunogutierre.recordbridge.model.ApiModel;
import io.github.brunogutierre.recordbridge.model.Controller;
import io.github.brunogutierre.recordbridge.model.Endpoint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.core.MethodIntrospector;
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
    List<Endpoint> endpoints =
        handlerMethods(type).stream().map(method -> reader.read(type, mapping, method)).toList();
    return new Controller(serviceName(type), disambiguate(endpoints));
  }

  /**
   * Handler methods found the way Spring's {@code RequestMappingHandlerMapping} finds them: any
   * visibility, declared on the class, a superclass or an interface, with overrides merged. Sorted
   * by name and then by parameter types, so overloads always come in the same order.
   */
  private static List<Method> handlerMethods(Class<?> type) {
    return annotatedMethods(type, RequestMapping.class).stream()
        .sorted(
            Comparator.comparing(Method::getName)
                .thenComparing(m -> Arrays.toString(m.getParameterTypes())))
        .toList();
  }

  /** The methods Spring would select for an annotation, as its handler mappings do. */
  static Set<Method> annotatedMethods(Class<?> type, Class<? extends Annotation> annotation) {
    return MethodIntrospector.selectMethods(
            type,
            (MethodIntrospector.MetadataLookup<Boolean>)
                m ->
                    !m.isBridge()
                            && !m.isSynthetic()
                            && AnnotatedElementUtils.hasAnnotation(m, annotation)
                        ? Boolean.TRUE
                        : null)
        .keySet();
  }

  /**
   * Gives overloaded handler methods distinct names: the one without client parameters keeps its
   * name, the others get their parameter names, such as {@code list} and {@code listByQ}.
   */
  static List<Endpoint> disambiguate(List<Endpoint> endpoints) {
    var counts = new java.util.HashMap<String, Integer>();
    endpoints.forEach(e -> counts.merge(e.name(), 1, Integer::sum));
    var result = new ArrayList<Endpoint>();
    for (Endpoint endpoint : endpoints) {
      if (counts.get(endpoint.name()) == 1 || endpoint.params().isEmpty()) {
        result.add(endpoint);
        continue;
      }
      var suffix = new StringBuilder("By");
      for (var param : endpoint.params()) {
        if (suffix.length() > 2) {
          suffix.append("And");
        }
        suffix
            .append(Character.toUpperCase(param.name().charAt(0)))
            .append(param.name().substring(1));
      }
      result.add(
          new Endpoint(
              endpoint.name() + suffix,
              endpoint.method(),
              endpoint.path(),
              endpoint.params(),
              endpoint.response()));
    }
    return result;
  }
}
