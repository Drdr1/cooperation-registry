package dev.derops.registry.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class WebConfig {

    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.of("uz"), Locale.of("ru"), Locale.ENGLISH);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Language comes from the frontend's Accept-Language header: uz (default), ru or en. */
    @Bean
    LocaleResolver localeResolver(AppProperties props) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(Locale.forLanguageTag(props.defaultLocale()));
        return resolver;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cooperation Registry API")
                        .version("v1")
                        .description("Meetings, agreements and their implementation. "
                                + "Send Accept-Language: uz | ru | en for localized labels, messages and reports."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
