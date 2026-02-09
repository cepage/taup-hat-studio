package org.tanzu.thstudio.publish;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Configures a standalone Thymeleaf TemplateEngine for static site generation.
 * This is separate from the Spring MVC template engine (which is disabled)
 * and is used only to render HTML files for the published static site.
 *
 * Uses SpringTemplateEngine (SpEL) rather than the plain TemplateEngine (OGNL)
 * to avoid needing the OGNL library as an extra dependency.
 */
@Configuration
public class SiteGeneratorConfig {

    @Bean("siteTemplateEngine")
    public SpringTemplateEngine siteTemplateEngine(ApplicationContext applicationContext) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("site-templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setEnableSpringELCompiler(true);
        return engine;
    }
}
