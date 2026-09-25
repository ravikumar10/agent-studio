package dev.agentstudio.worker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Docker-only contract worker used to prove isolated runtime invocation without framework leakage. */
public final class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server=HttpServer.create(new InetSocketAddress(8090),0);
        server.createContext("/health",e->respond(e,200,"{\"status\":\"UP\"}"));
        server.createContext("/internal/v1/agent-invocations",Main::invoke);
        server.start();
    }
    private static void invoke(HttpExchange e)throws IOException{
        if(!"POST".equals(e.getRequestMethod())){respond(e,405,"{}");return;}
        String input=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8).replace("\\","\\\\").replace("\"","\\\"");
        String body="{\"status\":\"COMPLETED\",\"output\":{\"message\":\"Executed in isolated JVM worker\",\"invocation\":\""+input+"\"},\"artifacts\":[],\"events\":[{\"type\":\"agent.completed\",\"timestamp\":\""+Instant.now()+"\",\"attributes\":{\"worker\":\"example-java\"}}],\"usage\":{\"inputTokens\":0,\"outputTokens\":0,\"modelCalls\":0,\"costMicros\":0},\"error\":null}";
        respond(e,200,body);
    }
    private static void respond(HttpExchange e,int status,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();}
}
