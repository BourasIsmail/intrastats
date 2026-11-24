package ma.entraide.intranet.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les tentatives de connexion échouées par identifiant (en mémoire : une instance de l'API).
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String login) {
        Deque<Instant> q = failures.get(key(login));
        if (q == null) {
            return false;
        }
        synchronized (q) {
            purge(q);
            return q.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String login) {
        Deque<Instant> q = failures.computeIfAbsent(key(login), k -> new ArrayDeque<>());
        synchronized (q) {
            purge(q);
            q.addLast(Instant.now());
        }
    }

    public void reset(String login) {
        failures.remove(key(login));
    }

    private static void purge(Deque<Instant> q) {
        Instant limit = Instant.now().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(limit)) {
            q.removeFirst();
        }
    }

    private static String key(String login) {
        return login == null ? "" : login.toLowerCase();
    }
}
