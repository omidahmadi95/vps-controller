package ch.omidahmadi.vpscontroller.check;

/** A history request for an id that is not configured. Turns into a 404 ProblemDetail. */
class CheckNotFoundException extends RuntimeException {

    CheckNotFoundException(long id) {
        super("Check " + id + " existiert nicht");
    }
}
