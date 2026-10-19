package cn.guat.smartpark.service;

import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rechecks submitted work orders even while no administrator is online. */
@Component
public class WorkOrderRetestScheduler {
 private static final Logger log=LoggerFactory.getLogger(WorkOrderRetestScheduler.class);
 private final ConsoleService console;
 public WorkOrderRetestScheduler(ConsoleService console){this.console=console;}
 @Scheduled(fixedDelayString="${console.orders.retest-interval-ms:15000}",initialDelayString="${console.orders.retest-interval-ms:15000}")
 public void inspect(){for(String id:console.pendingRetests()){try{console.automaticRetest(id);}catch(Exception e){log.warn("Work order {} retest failed; will retry",id,e);}}}
}
