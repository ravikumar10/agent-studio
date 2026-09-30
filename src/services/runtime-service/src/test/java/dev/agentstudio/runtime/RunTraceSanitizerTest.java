package dev.agentstudio.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTraceSanitizerTest {
    @Test void redactsDocumentPayloadsAndCredentials(){
        Map<String,Object> result=RunTraceSanitizer.map(Map.of("filename","report.pdf","base64Content","sensitive-bytes","apiKey","secret"));
        assertEquals("report.pdf",result.get("filename"));
        assertEquals("••••••••",result.get("base64Content"));
        assertEquals("••••••••",result.get("apiKey"));
    }

    @Test void preservesNullableFieldsFromUpstreamToolResponses(){
        Map<String,Object> shipment=new LinkedHashMap<>();
        shipment.put("shipmentNumber","TS-6SEXU9");
        shipment.put("actualArrival",null);
        Map<String,Object> result=RunTraceSanitizer.map(Map.of("shipments",List.of(shipment)));
        Map<?,?> sanitized=(Map<?,?>)((List<?>)result.get("shipments")).get(0);
        assertTrue(sanitized.containsKey("actualArrival"));
        assertNull(sanitized.get("actualArrival"));
    }
}
