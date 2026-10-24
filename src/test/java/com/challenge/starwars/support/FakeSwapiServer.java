package com.challenge.starwars.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Un servidor HTTP real que hace de swapi.tech, para que los tests de extremo a extremo ejerciten el RestClient real,
 * los timeouts y la codificación de URLs sin tocar nunca el servicio público.
 */
public final class FakeSwapiServer implements AutoCloseable {

    /** Respuesta a una petición. */
    public record Reply(int status, String body, long delayMillis, List<String[]> headers) {

        public static Reply json(String body) {
            return new Reply(200, body, 0, List.of());
        }

        public static Reply status(int status, String body) {
            return new Reply(status, body, 0, List.of());
        }

        public static Reply slow(long delayMillis) {
            return new Reply(200, "{}", delayMillis, List.of());
        }

        public Reply withHeader(String name, String value) {
            List<String> pair = List.of(name, value);
            List<String[]> all = new ArrayList<>(headers);
            all.add(pair.toArray(String[]::new));
            return new Reply(status, body, delayMillis, all);
        }
    }

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /** @param router recibe {@code path?query} (tal como se envió por la red) y devuelve la respuesta */
    public FakeSwapiServer(Function<String, Reply> router) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            String target = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
            requests.add(target);
            respond(exchange, router.apply(target));
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, Reply reply) throws IOException {
        try {
            if (reply.delayMillis() > 0) {
                Thread.sleep(reply.delayMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        reply.headers().forEach(h -> exchange.getResponseHeaders().add(h[0], h[1]));
        try {
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
        } catch (IOException ignored) {
            // Puede que el cliente ya se haya rendido (tests de timeout).
        } finally {
            exchange.close();
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    /** Cada {@code path?query} recibido hasta ahora, en orden de llegada. */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
