package dev.derops.registry.common;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Resolves i18n keys in the current request's language (uz / ru / en). */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return get(LocaleContextHolder.getLocale(), key, args);
    }

    public String get(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    public String enumLabel(Enum<?> value) {
        if (value == null) {
            return "";
        }
        return get("enum." + value.getDeclaringClass().getSimpleName() + "." + value.name());
    }
}
