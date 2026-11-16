package example;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns missing tasks into RFC 9457 problem details. */
@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler
  public ProblemDetail notFound(NoSuchElementException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    problem.setTitle("Task not found");
    return problem;
  }
}
