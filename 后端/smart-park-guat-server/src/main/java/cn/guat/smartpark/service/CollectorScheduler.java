package cn.guat.smartpark.service;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional HTTP collector adapter; disabled until configured. */
@Configuration @EnableScheduling
public class CollectorScheduler {
 private final IntegrationService service;private final Environment env;
 private final Logger log=LoggerFactory.getLogger(CollectorScheduler.class);
 public CollectorScheduler(IntegrationService service,Environment env){this.service=service;this.env=env;}
 @Scheduled(fixedDelayString="${integration.collector.interval-ms:60000}",initialDelayString="${integration.collector.interval-ms:60000}")
 public void poll(){if(env.getProperty("integration.collector.url","").trim().isEmpty())return;try{service.collect();}catch(Exception e){log.warn("自动采集本次未完成：{}",e.getMessage());}}
}
