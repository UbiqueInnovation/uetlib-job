package ch.ubique.backend.job.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class JobMvcConfig implements WebMvcConfigurer {

    private final boolean cacheResources;

    public JobMvcConfig(
            @Value("${flesk.status.resolver.cache-resources:true}") final boolean cacheResources) {
        this.cacheResources = cacheResources;
    }

    @Override
    public void addResourceHandlers(final @NotNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/status", "/status/", "/status/**")
                .addResourceLocations("classpath:/status/")
                .resourceChain(cacheResources)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(
                                    @NotNull String resourcePath, @NotNull Resource location)
                                    throws IOException {
                                final var requestedResource = location.createRelative(resourcePath);
                                if (requestedResource.exists() && requestedResource.isReadable()) {
                                    return requestedResource;
                                }
                                return new ClassPathResource("/status/index.html");
                            }
                        });
    }
}
