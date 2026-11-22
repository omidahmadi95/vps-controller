package ch.omidahmadi.vpscontroller.container;

/** Unified state shown to the user. Only RUNNING means "works"; everything uncertain is never RUNNING. */
public enum ContainerState {
    RUNNING("Läuft"),
    DEGRADED("Gestört"),
    STOPPED("Gestoppt"),
    UNREACHABLE("Nicht erreichbar"),
    UNKNOWN("Unbekannt");

    private final String label;

    ContainerState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
