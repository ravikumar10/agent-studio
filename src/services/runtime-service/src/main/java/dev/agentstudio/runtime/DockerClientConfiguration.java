package dev.agentstudio.runtime;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DockerClientConfiguration {
    @Bean(destroyMethod = "close")
    DockerClient dockerClient(@Value("${runtime.docker-host:tcp://docker-api-proxy:2375}") String host) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(host).build();
        var transport = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(20)).build();
        return DockerClientImpl.getInstance(config, transport);
    }
}
