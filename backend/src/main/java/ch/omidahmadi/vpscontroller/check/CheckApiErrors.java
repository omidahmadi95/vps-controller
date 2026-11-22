package ch.omidahmadi.vpscontroller.check;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 answers for the checks API: unknown id → 404, bad parameters → 400. */
@RestControllerAdvice
class CheckApiErrors {

    @ExceptionHandler(CheckNotFoundException.class)
    ProblemDetail notFound(CheckNotFoundException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Nicht gefunden");
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setTitle("Ungültige Anfrage");
        return p;
    }
}
