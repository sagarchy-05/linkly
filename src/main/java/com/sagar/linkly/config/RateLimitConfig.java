package com.sagar.linkly.config;

import com.sagar.linkly.filter.RateLimitFilter;
import com.sagar.linkly.service.RateLimiterService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimiterService rateLimiterService) {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();

        // Inject the service into the filter manually
        registrationBean.setFilter(new RateLimitFilter(rateLimiterService));

        // Explicitly map all exact endpoints and their sub-paths
        registrationBean.addUrlPatterns(
                "/api/shorten",
                "/api/bulk-shorten"
        );

        // Set it to run early in the filter chain
        registrationBean.setOrder(1);

        return registrationBean;
    }
}