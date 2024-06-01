package rsp.tetris;

import rsp.App;
import rsp.component.*;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static rsp.html.HtmlDsl.*;

/**
 * A Tetris game single-page application.
 */
public class Tetris {
    private static final int SERVER_PORT = 8080;

    /**
     * Arrow keys keycodes:
     */
    private static final String LEFT_KEY = "37";
    private static final String RIGHT_KEY = "39";
    private static final String DOWN_KEY = "40";
    private static final String UP_KEY = "38";

    public static void main(String[] args) {
        final StatefulComponentDefinition<State> componentDefinition = new StatefulComponentDefinition<>(Tetris.class) {

            private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
            private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();

            @Override
            protected ComponentStateSupplier<State> stateSupplier() {
                return (key, httpStateOrigin) -> CompletableFuture.completedFuture(State.initialState());
            }

            @Override
            protected ComponentView<State> componentView() {
                return state -> newState -> html(on("keydown", false, c -> {
                            final String keyCode = c.eventObject().value("keyCode").map(v -> v.asJsonString().value()).orElse("noKeyCode");
                            final State s = state;
                            switch (keyCode) {
                                case LEFT_KEY:
                                    s.tryMoveLeft().ifPresent(ns -> newState.setState(ns));
                                    break;
                                case RIGHT_KEY:
                                    s.tryMoveRight().ifPresent(ns -> newState.setState(ns));
                                    break;
                                case DOWN_KEY:
                                    s.tryMoveDown().ifPresent(ns -> newState.setState(ns));
                                    break;
                                case UP_KEY:
                                    s.tryRotate().ifPresent(ns -> newState.setState(ns));
                                    break;
                            }
                        }),
                        head(title("Tetris"),
                                link(attr("rel", "stylesheet"), attr("href", "/res/style.css")),
                                meta(attr("http-equiv", "Content-Type"), attr("content", "text/html; charset=UTF-8"))),
                        body(div(attr("id", "header")),
                                div(attr("id", "content"),
                                        div(attr("class", "left-column")),
                                        div(attr("class", "tetris-wrapper"),
                                                div(attr("class", "stage"),
                                                        of(Arrays.stream(state.stage.cells()).flatMap(row ->
                                                                CharBuffer.wrap(row).chars().mapToObj(i -> (char) i)).map(cell ->
                                                                div(attr("class", "cell t" + cell))))),
                                                div(attr("class", "sidebar"),
                                                        div(attr("id", "score"),
                                                                p("SCORE"),
                                                                span(attr("class", "score-text"),
                                                                        text(String.format("%06d", state.score())))),
                                                        div(attr("id", "start"),
                                                                button(attr("id", "start-btn"), attr("type", "button"),
                                                                        when(state.isRunning, () -> attr("disabled")),
                                                                        text("Start"),
                                                                        on("click", c -> {
                                                                            State.initialState().start().newTetramino().ifPresent(ns -> newState.setState(ns));
                                                                        })),
                                                                p("← → ↓ move"),
                                                                p("↑ rotate")))),
                                        div(attr("class", "right-column"))
                                ),
                                div(attr("id", "footer"))));
            }

            @Override
            protected ComponentUpdatedCallback<State> componentDidUpdate() {
                return (key, oldState, state, newState) -> {
                    if (!oldState.isRunning && state.isRunning) {
                        scheduleAtFixedRate(() -> {
                            System.out.println("tick");
                            newState.applyStateTransformationIfPresent(
                                    s -> s.tryMoveDown()
                                            .or(() -> s.newTetramino())
                                            .or(() -> {
                                                return Optional.of(s.stop());
                                            }));}, key,0, 1, TimeUnit.SECONDS);
                    } else if (oldState.isRunning && !state.isRunning) {
                        cancelSchedule(key);
                    }
                };
            }

            @Override
            protected ComponentMountedCallback<State> componentDidMount() {
                return (key, state, newState) -> {};
            }

            @Override
            protected ComponentUnmountedCallback<State> componentWillUnmount() {
                return (key, state) -> {
                    cancelSchedule(key);
                };
            }

            private void scheduleAtFixedRate(final Runnable command, final Object key, final long initialDelay, final long period, final TimeUnit unit) {
                final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
                schedules.put(key, timer);
            }

            private void cancelSchedule(final Object key) {
                final ScheduledFuture<?> schedule = schedules.get(key);
                if (schedule != null) {
                    schedule.cancel(true);
                    schedules.remove(key);
                }
            }
        };

        final var s = new WebServer(SERVER_PORT,
                                    new App<>(componentDefinition),
                                    new StaticResources(new File("src/main/java/rsp/examples/tetris"),
                                            "/res/*"));
        s.start();
        s.join();
    }
}
