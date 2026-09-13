package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final CsrfInterceptor csrfInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, CsrfInterceptor csrfInterceptor) {
        this.authInterceptor = authInterceptor;
        this.csrfInterceptor = csrfInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/styles.css", "/placeholder-logo.svg");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/platform/**", "/admin/**", "/shop/**", "/cart/**", "/orders/**", "/b/**", "/files/**");
    }
}
