package ch.omidahmadi.vpscontroller.container;

/** One row of the API answer. {@code label} is the German text for {@code state}. */
public record ContainerView(String name, String image, ContainerState state, String label, String detail) {

    static ContainerView of(String name, String image, ContainerState state, String detail) {
        return new ContainerView(name, image, state, state.label(), detail);
    }
}
