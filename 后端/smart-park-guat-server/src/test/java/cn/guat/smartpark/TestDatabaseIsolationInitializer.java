package cn.guat.smartpark;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs before service constructors / @PostConstruct can connect or create tables. */
public class TestDatabaseIsolationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
 public void initialize(ConfigurableApplicationContext context){
  String url=context.getEnvironment().getProperty("spring.datasource.url","");
  if(!url.matches("jdbc:mysql://[^/]+/platform_smart_park_stage1_test_20261008(?:\\?.*)?"))throw new IllegalStateException("Refusing Spring tests outside the explicitly isolated test database");
 }
}
