package com.bravoappointments.manage;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ManageWebConfig implements WebMvcConfigurer {

    private final ManagerContextArgumentResolver managerContextResolver;

    public ManageWebConfig(ManagerContextArgumentResolver managerContextResolver) {
        this.managerContextResolver = managerContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(managerContextResolver);
    }
}
