package org.tanzu.thstudio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Configures the Spring Boot server to forward non-API requests to the Angular SPA's index.html.
 * This allows Angular's client-side routing to handle all frontend routes.
 * <p>
 * The Angular build output is copied into {@code classpath:/static/} by the maven-resources-plugin
 * during the prepare-package phase. Any request that does not match a real static file
 * (e.g. an Angular route like {@code /dashboard}) is forwarded to {@code index.html}
 * for client-side routing.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Resource INDEX_HTML = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        return requested.exists() && requested.isReadable()
                                ? requested
                                : INDEX_HTML;
                    }
                });
    }
}
