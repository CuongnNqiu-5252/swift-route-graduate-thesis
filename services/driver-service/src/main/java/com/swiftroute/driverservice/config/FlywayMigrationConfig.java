package com.swiftroute.driverservice.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

@Configuration
public class FlywayMigrationConfig {
    @Bean
    public static BeanFactoryPostProcessor flywayBeforeJpa() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flyway");
            }
        };
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_driver_history")
                .baselineOnMigrate(true)
                .baselineVersion("3")
                .baselineDescription("Existing driver schema before managed earnings migration")
                .load();
    }
}
