package dev.agentstudio.control;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class PortableWorkloadManifest {
    Map<String,Object> enrich(String placement, String agentId, String version, Map<String,Object> requested) {
        Map<String,Object> resources = new LinkedHashMap<>(requested == null ? Map.of() : requested);
        if (!"KUBERNETES".equals(placement)) return Map.copyOf(resources);
        String name = kubeName(agentId);
        String image = String.valueOf(resources.getOrDefault("imageRef", "agent-studio/runtime-worker:latest"));
        String namespace = String.valueOf(resources.getOrDefault("namespace", "agent-studio"));
        resources.put("imageRef", image);
        resources.put("namespace", namespace);
        resources.put("manifestYaml", """
                apiVersion: v1
                kind: ServiceAccount
                metadata: {name: %s, namespace: %s}
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                  namespace: %s
                  labels: {agentstudio.io/agent-id: \"%s\"}
                spec:
                  replicas: 1
                  selector: {matchLabels: {app.kubernetes.io/name: %s}}
                  template:
                    metadata: {labels: {app.kubernetes.io/name: %s}}
                    spec:
                      serviceAccountName: %s
                      securityContext: {runAsNonRoot: true}
                      containers:
                        - name: agent-runtime
                          image: %s
                          imagePullPolicy: IfNotPresent
                          env:
                            - {name: AGENT_ID, value: \"%s\"}
                            - {name: AGENT_VERSION, value: \"%s\"}
                            - {name: SERVER_PORT, value: \"8080\"}
                            - {name: DATABASE_URL, valueFrom: {secretKeyRef: {name: agent-studio-runtime, key: database-url}}}
                            - {name: DATABASE_USER, valueFrom: {secretKeyRef: {name: agent-studio-runtime, key: database-user}}}
                            - {name: DATABASE_PASSWORD, valueFrom: {secretKeyRef: {name: agent-studio-runtime, key: database-password}}}
                            - {name: REDIS_HOST, value: \"redis\"}
                            - {name: REDIS_PORT, value: \"6379\"}
                            - {name: AGENT_STUDIO_ENCRYPTION_KEY, valueFrom: {secretKeyRef: {name: agent-studio-runtime, key: encryption-key}}}
                          ports: [{name: http, containerPort: 8080}]
                          readinessProbe: {httpGet: {path: /actuator/health/readiness, port: http}}
                          livenessProbe: {httpGet: {path: /actuator/health/liveness, port: http}}
                          resources:
                            requests: {cpu: 100m, memory: 256Mi}
                            limits: {cpu: \"1\", memory: 1Gi}
                          securityContext: {allowPrivilegeEscalation: false, readOnlyRootFilesystem: true}
                ---
                apiVersion: v1
                kind: Service
                metadata: {name: %s, namespace: %s}
                spec:
                  selector: {app.kubernetes.io/name: %s}
                  ports: [{name: http, port: 8080, targetPort: http}]
                """.formatted(name, yaml(namespace), name, yaml(namespace), yaml(agentId), name, name, name, yaml(image), yaml(agentId), yaml(version), name, yaml(namespace), name));
        return Map.copyOf(resources);
    }

    private static String kubeName(String value) {
        String name = value.toLowerCase().replaceAll("[^a-z0-9.-]+", "-").replaceAll("(^-|-$)", "").replace('.', '-');
        return name.length() > 63 ? name.substring(0, 63).replaceAll("-$", "") : name;
    }
    private static String yaml(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }
}
