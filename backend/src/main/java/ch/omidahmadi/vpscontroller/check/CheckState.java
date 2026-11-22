package ch.omidahmadi.vpscontroller.check;

/** Availability state of a configured HTTP check. Only UP means "works". */
public enum CheckState {
    UP("Erreichbar"),
    DOWN("Ausgefallen"),
    PENDING("Wird geprüft"),
    PAUSED("Pausiert");

    private final String label;

    CheckState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
