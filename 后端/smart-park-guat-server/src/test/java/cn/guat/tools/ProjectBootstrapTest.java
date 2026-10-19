package cn.guat.tools;

import cn.guat.smartpark.service.ConsoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectBootstrapTest {
    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:bootstrap_" + UUID.randomUUID().toString().replace("-", "") + "_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1", "sa", "");
    }
    public static Timestamp utcTimestamp(int precision) { return Timestamp.from(java.time.Instant.now()); }

    @Test void adminPasswordMatchesLoginAndExistingAccountIsNotReset() throws Exception {
        DriverManagerDataSource ds=dataSource(); JdbcTemplate db=new JdbcTemplate(ds);
        ProjectBootstrap tool=new ProjectBootstrap(ds);
        assertTrue(tool.createAdmin("demo_admin","BootstrapTest123!"));
        String before=db.queryForObject("SELECT password_hash FROM console_account WHERE user_name='demo_admin'",String.class);
        ConsoleService login=new ConsoleService(db,new ObjectMapper(),null);
        Method matches=ConsoleService.class.getDeclaredMethod("matches",String.class,String.class); matches.setAccessible(true);
        assertEquals(true,matches.invoke(login,"BootstrapTest123!",before));
        assertEquals(false,matches.invoke(login,"IncorrectPassword",before));
        assertFalse(tool.createAdmin("demo_admin","AnotherPassword123!"));
        assertEquals(before,db.queryForObject("SELECT password_hash FROM console_account",String.class));
        assertEquals("ADMIN",db.queryForObject("SELECT role FROM console_account",String.class));
        db.update("UPDATE console_account SET role='MAINTAINER'");
        assertThrows(IllegalStateException.class,()->tool.createAdmin("demo_admin","AnotherPassword123!"));
    }
    @Test void rejectsInvalidSecretsAndNonDemoTargetsBeforeWriting() {
        ProjectBootstrap tool=new ProjectBootstrap(dataSource());
        assertThrows(IllegalArgumentException.class,()->tool.createAdmin("admin","short"));
        assertThrows(IllegalArgumentException.class,()->tool.seedDemo("jdbc:mysql://localhost/platform_smart_park"));
        assertThrows(IllegalArgumentException.class,()->tool.seedDemo("jdbc:mysql://localhost/a_demo/production"));
    }
    @Test void seedsSyntheticInventoryAndMonitoringOnce() {
        DriverManagerDataSource ds=dataSource(); JdbcTemplate db=new JdbcTemplate(ds);
        db.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR 'cn.guat.tools.ProjectBootstrapTest.utcTimestamp'");
        ProjectBootstrap tool=new ProjectBootstrap(ds);
        assertTrue(tool.seedDemo("jdbc:mysql://localhost/public_demo"));
        assertEquals(42,db.queryForObject("SELECT COUNT(*) FROM sp_cabinet",Integer.class));
        assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM sp_monitor_record WHERE kind='FACILITY' AND payload LIKE '%CAMERA%'",Integer.class));
        assertEquals(35,db.queryForObject("SELECT COUNT(*) FROM sp_energy_sample",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sp_reading WHERE source <> 'PUBLIC_DEMO_V1'",Integer.class));
        int count=db.queryForObject("SELECT COUNT(*) FROM sp_reading",Integer.class);
        assertFalse(tool.seedDemo("jdbc:mysql://localhost/public_demo"));
        assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM sp_reading",Integer.class));
        db.update("DELETE FROM sp_public_demo_seed");
        assertThrows(IllegalStateException.class,()->tool.seedDemo("jdbc:mysql://localhost/public_demo"));
        assertEquals(count,db.queryForObject("SELECT COUNT(*) FROM sp_reading",Integer.class));
    }
}
