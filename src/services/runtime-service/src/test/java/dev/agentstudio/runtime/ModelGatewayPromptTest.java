package dev.agentstudio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class ModelGatewayPromptTest {
    @Test void includesConversationRequestAndToolEvidence(){
        ModelGateway gateway=new ModelGateway(null,new ObjectMapper(),null,null);
        Map<String,Object> row=Map.of("name","Mechanical Keyboard","stock",8);
        Map<String,Object> step=Map.of("capability","postgres.query-readonly","providerId","local-database-mcp","output",Map.of("rows",List.of(row),"rowCount",1));
        String prompt=gateway.prompt(Map.of("conversation",List.of(Map.of("role","user","text","Show electronics")),"message","Which products are available?","toolResults",List.of(step)));
        assertThat(prompt).contains("USER REQUEST","Which products are available?","TOOL EVIDENCE","postgres.query-readonly","Mechanical Keyboard","Treat successful tool output as authoritative","every factual value in the answer must appear in TOOL EVIDENCE","Do not use outside knowledge");
    }

    @Test void explicitlyMarksMissingEvidence(){
        ModelGateway gateway=new ModelGateway(null,new ObjectMapper(),null,null);
        assertThat(gateway.prompt(Map.of("message","hello","toolResults",List.of()))).contains("No tool returned evidence");
    }
}
