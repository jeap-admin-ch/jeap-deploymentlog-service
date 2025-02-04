package ch.admin.bit.jeap.deploymentlog.persistence;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@AutoConfiguration
@EnableTransactionManagement
@EnableJpaRepositories
@ComponentScan
@EntityScan(basePackages = "ch.admin.bit.jeap.deploymentlog")
class PersistenceConfiguration {
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }

}
