package ch.omidahmadi.vpscontroller.container;

/** The Docker source could not be asked or did not answer usably. */
public class SourceUnreachableException extends RuntimeException {

    public SourceUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
