package dev.agentstudio.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunTraceSanitizerTest {
    @Test void redactsDocumentPayloadsAndCredentials(){
        Map<String,Object> result=RunTraceSanitizer.map(Map.of("filename","report.pdf","base64Content","sensitive-bytes","apiKey","secret"));
        assertEquals("report.pdf",result.get("filename"));
        assertEquals("••••••••",result.get("base64Content"));
        assertEquals("••••••••",result.get("apiKey"));
    }
}
