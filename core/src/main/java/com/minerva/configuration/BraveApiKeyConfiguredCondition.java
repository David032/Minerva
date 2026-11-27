package com.minerva.configuration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Enables Brave search configuration only when a non-blank API key is present.
 */
final class BraveApiKeyConfiguredCondition implements Condition {
    /**
     * Checks whether a Brave API key has been configured.
     *
     * @param context the condition evaluation context
     * @param metadata the metadata of the annotated component
     * @return true when the configured API key contains text
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("brave.api.key"));
    }
}
