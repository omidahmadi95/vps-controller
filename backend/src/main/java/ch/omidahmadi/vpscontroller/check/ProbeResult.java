package ch.omidahmadi.vpscontroller.check;

/** Whether a probe succeeded, plus what we saw. HTTP fields are null when there was no usable response. */
public record ProbeResult(boolean ok, Integer statusCode, Long latencyMs, String error) {

    static ProbeResult of(int statusCode, long latencyMs, boolean ok, String error) {
        return new ProbeResult(ok, statusCode, latencyMs, error);
    }

    static ProbeResult failed(String error) {
        return new ProbeResult(false, null, null, error);
    }
}
