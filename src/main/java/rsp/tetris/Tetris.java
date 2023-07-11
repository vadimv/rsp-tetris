package rsp.tetris;

import rsp.App;
import rsp.jetty.JettyServer;
import rsp.ref.TimerRef;
import rsp.server.StaticResources;
import rsp.stateview.ComponentView;

import java.io.File;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    private static final TimerRef FALLING_ITEMS_TIMER = TimerRef.createTimerRef();

    public static void main(String[] args) {
        final ComponentView<State> component = state -> newState ->
                html(on("keydown", false, c -> {
                            final String keyCode = c.eventObject().value("keyCode").map(Object::toString).orElse("noKeyCode");
                            final State s = state;
                            switch (keyCode) {
                                case LEFT_KEY  : s.tryMoveLeft().ifPresent(ns -> newState.set(ns)); break;
                                case RIGHT_KEY : s.tryMoveRight().ifPresent(ns -> newState.set(ns)); break;
                                case DOWN_KEY  : s.tryMoveDown().ifPresent(ns -> newState.set(ns)); break;
                                case UP_KEY    : s.tryRotate().ifPresent(ns -> newState.set(ns)); break;
                            }
                        }),
                        head(title("Tetris"),
                                link(attr("rel", "stylesheet"), attr("href","/res/style.css")),
                                meta(attr("http-equiv","Content-Type"), attr("content", "text/html; charset=UTF-8"))),
                        body(div(attr("id", "header")),
                                div(attr("id", "content"),
                                        div(attr("class", "left-column")),
                                        div(attr("class", "tetris-wrapper"),
                                                div(attr("class", "stage"),
                                                        of(Arrays.stream(state.stage.cells()).flatMap(row ->
                                                                CharBuffer.wrap(row).chars().mapToObj(i -> (char)i)).map(cell ->
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
                                                                            State.initialState().start().newTetramino().ifPresent(ns -> newState.set(ns));
                                                                            c.scheduleAtFixedRate(() -> newState.applyIfPresent(
                                                                                    s -> s.tryMoveDown()
                                                                                            .or(() -> s.newTetramino())
                                                                                            .or(() -> {
                                                                                                c.cancelSchedule(FALLING_ITEMS_TIMER);
                                                                                                return Optional.of(s.stop());
                                                                                            })), FALLING_ITEMS_TIMER, 0, 1, TimeUnit.SECONDS);
                                                                        })),
                                                                p("← → ↓ move"),
                                                                p("↑ rotate")))),
                                        div(attr("class", "right-column"))
                                ),
                                div(attr("id", "footer"))));

        final var s = new JettyServer<>(SERVER_PORT,
                "",
                new App<>(State.initialState(),
                        component),
                new StaticResources(new File("src/main/java/rsp/tetris"),
                        "/res/*"));
        s.start();
        s.join();
    }
}
