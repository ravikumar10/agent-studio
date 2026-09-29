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
        assertThat(prompt).contains("Write clear, polished Markdown","Do not duplicate charts with ASCII art","Match the detail level and terminology of the user's request","Never speculate about causes, intent, demand, risk, policy, or business meaning");
    }

    @Test void explicitlyMarksMissingEvidence(){
        ModelGateway gateway=new ModelGateway(null,new ObjectMapper(),null,null);
        assertThat(gateway.prompt(Map.of("message","hello","toolResults",List.of()))).contains("No current or previous tool returned evidence");
    }

    @Test void includesDurableSessionEvidenceWithoutRepeatingTheTool(){
        ModelGateway gateway=new ModelGateway(null,new ObjectMapper(),null,null);
        Map<String,Object> evidence=Map.of("capability","document.ocr-extract","content",Map.of("response",Map.of("text","Shipment origin: Shenzhen")));
        String prompt=gateway.prompt(Map.of("message","Where did it originate?","sessionEvidence",List.of(evidence),"toolResults",List.of()));
        assertThat(prompt).contains("previousSessionEvidence","document.ocr-extract","Shipment origin: Shenzhen");
    }

    @Test void planningPromptMakesFixedObjectiveAuthoritativeAndRequiresCompleteChain(){
        ModelGateway gateway=new ModelGateway(null,new ObjectMapper(),null,null);
        String prompt=gateway.planningPromptForTest(Map.of("message","Extract and analyze the attached document.","attachedCapabilities",List.of("document.ocr.extract","cargoes.shipments.create"),"attachedSkills",List.of(Map.of("id","agent-initial-prompt","content","Parse the image and create shipment"))));
        assertThat(prompt).contains("does not override the fixed agent objective").contains("There is no later planning pass").contains("cargoes.shipments.create");
    }
}
