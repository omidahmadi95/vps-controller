package ch.omidahmadi.vpscontroller.overview;

/** Freshness of one data source. Only OK means "the answer is current". */
public enum SourceState {
    OK("Aktuell"),
    STALE("Veraltet"),
    UNREACHABLE("Nicht erreichbar"),
    NOT_CONFIGURED("Nicht eingerichtet");

    private final String label;

    SourceState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
