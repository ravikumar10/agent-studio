package dev.agentstudio.sample;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebReaderToolControllerTest {
    @Test void wildcardEnablesAnyPublicHostName(){
        assertThat(WebReaderToolController.hostEnabled(Set.of("*"),"news.example.org")).isTrue();
    }
    @Test void explicitConfigurationRemainsSupported(){
        assertThat(WebReaderToolController.hostEnabled(Set.of("example.com"),"example.com")).isTrue();
        assertThat(WebReaderToolController.hostEnabled(Set.of("example.com"),"other.example")).isFalse();
    }
}
