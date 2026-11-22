package ch.omidahmadi.vpscontroller.container;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

    /** 503 instead of an empty list: an empty list would read as "nothing is broken". */
    @ExceptionHandler(SourceUnreachableException.class)
    ProblemDetail sourceUnreachable(SourceUnreachableException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle(ContainerState.UNREACHABLE.label());
        p.setProperty("state", ContainerState.UNREACHABLE);
        p.setProperty("checkedAt", Instant.now());
        return p;
    }
}
