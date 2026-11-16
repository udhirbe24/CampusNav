package edu.thapar.campusnav.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Serves the HTML/CSS/JS frontend. While developing, files are read straight from the
 * {@code frontend/} folder so edits show up on refresh; the packaged jar falls back to the
 * copy bundled inside it.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final String configuredFrontendDir;

    public WebConfig(@Value("${campusnav.frontend-dir:}") String configuredFrontendDir) {
        this.configuredFrontendDir = configuredFrontendDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> locations = new ArrayList<>();
        Path dir = findFrontendDir();
        if (dir != null) {
            locations.add(dir.toUri().toString());
            log.info("Serving frontend from {}", dir);
        }
        locations.add("classpath:/static/");
        registry.addResourceHandler("/**")
                .addResourceLocations(locations.toArray(String[]::new))
                .setCacheControl(CacheControl.noCache());
    }

    private Path findFrontendDir() {
        List<String> candidates = new ArrayList<>();
        if (configuredFrontendDir != null && !configuredFrontendDir.isBlank()) {
            candidates.add(configuredFrontendDir);
        }
        candidates.add("../frontend");
        candidates.add("frontend");
        for (String c : candidates) {
            Path p = Path.of(c).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("app.html"))) {
                return p;
            }
        }
        return null;
    }
}
