package dev.agentstudio.runtime;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RunEventStream {
    private static final long TIMEOUT = 30 * 60 * 1000L;
    private final Map<String, Set<Client>> clients = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public SseEmitter subscribe(String tenant) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        Client client = new Client(emitter);
        clients.computeIfAbsent(tenant, ignored -> new CopyOnWriteArraySet<>()).add(client);
        Runnable remove = () -> remove(tenant, client);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        send(tenant, client, "connected", Map.of("kind", "connected", "at", Instant.now()));
        return emitter;
    }

    public void runChanged(RunModels.RunView run) {
        broadcast(run.tenantId(), "run", Map.of("kind", "run", "run", run));
    }

    public void semanticEvent(String tenant, String runId, String type, Map<String, Object> attributes) {
        broadcast(tenant, "run-event", Map.of(
                "kind", "event", "runId", runId, "type", type,
                "occurredAt", Instant.now(), "attributes", attributes));
    }

    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        clients.forEach((tenant, subscribers) -> subscribers.forEach(client ->
                send(tenant, client, "heartbeat", Map.of("kind", "heartbeat", "at", Instant.now()))));
    }

    private void broadcast(String tenant, String event, Object data) {
        clients.getOrDefault(tenant, Set.of()).forEach(client -> send(tenant, client, event, data));
    }

    private void send(String tenant, Client client, String event, Object data) {
        try {
            client.emitter().send(SseEmitter.event().id(Long.toString(ids.incrementAndGet())).name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
            remove(tenant, client);
        }
    }

    private void remove(String tenant, Client client) {
        Set<Client> subscribers = clients.get(tenant);
        if (subscribers == null) return;
        subscribers.remove(client);
        if (subscribers.isEmpty()) clients.remove(tenant, subscribers);
    }

    private record Client(SseEmitter emitter) {}
}
