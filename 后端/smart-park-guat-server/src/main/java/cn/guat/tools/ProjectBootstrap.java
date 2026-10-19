package cn.guat.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;

/** Explicit offline bootstrap; never runs as part of the web application. */
public final class ProjectBootstrap {
    public static final String SOURCE = "PUBLIC_DEMO_V1";
    private final JdbcTemplate db;
    private final DataSource dataSource;
    private final ObjectMapper json = new ObjectMapper();
    public ProjectBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
        this.db = new JdbcTemplate(dataSource);
    }

    public static void main(String[] args) {
        if (args.length != 1 || !Arrays.asList("admin", "demo").contains(args[0])) {
            System.err.println("Usage: ProjectBootstrap admin|demo; see docs/初始化与演示数据.md");
            System.exit(2);
        }
        try {
            String url = required(System.getenv(), "BOOTSTRAP_DB_URL");
            String user = required(System.getenv(), "DB_USERNAME");
            String password = required(System.getenv(), "DB_PASSWORD");
            // Validate before connecting. No implicit localhost/production database fallback.
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Use an explicit MySQL JDBC URL");
            if ("demo".equals(args[0])) validateDemoUrl(url);
            ProjectBootstrap tool = new ProjectBootstrap(new DriverManagerDataSource(url, user, password));
            if ("admin".equals(args[0])) {
                boolean created = tool.createAdmin(required(System.getenv(), "BOOTSTRAP_ADMIN_USERNAME"),
                        required(System.getenv(), "BOOTSTRAP_ADMIN_PASSWORD"));
                System.out.println(created ? "Administrator created. Password is not logged." : "Existing enabled administrator kept; nothing changed.");
            } else {
                System.out.println(tool.seedDemo(url) ? "Synthetic demo initialized; source=" + SOURCE : "Demo already initialized; nothing changed.");
            }
        } catch (Exception failure) {
            // JDBC exceptions may include connection strings or credentials. Do not print them.
            System.err.println("Bootstrap failed (" + failure.getClass().getSimpleName()
                    + "). Check target database, empty inventory, credentials and initialization guide. No credentials logged.");
            System.exit(1);
        }
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    public static void validateDemoUrl(String url) {
        if (!url.matches("jdbc:mysql://[^/]+/[A-Za-z0-9_]+_demo(?:\\?.*)?"))
            throw new IllegalArgumentException("Demo target must end in _demo");
    }

    public boolean createAdmin(String name, String password) {
        if (!name.matches("[A-Za-z0-9_.-]{3,100}") || password.length() < 8 || password.length() > 128)
            throw new IllegalArgumentException("Invalid account or password length");
        db.execute("CREATE TABLE IF NOT EXISTS console_account (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_name VARCHAR(100) NOT NULL UNIQUE,display_name VARCHAR(100) NOT NULL,role VARCHAR(20) NOT NULL,password_hash VARCHAR(512) NOT NULL,phone VARCHAR(100),email VARCHAR(200),enabled BOOLEAN NOT NULL DEFAULT TRUE)");
        List<Map<String,Object>> existing = db.queryForList("SELECT role,enabled FROM console_account WHERE user_name=?", name);
        if (!existing.isEmpty()) {
            Map<String,Object> row = existing.get(0);
            if (!"ADMIN".equals(row.get("role")) || !Boolean.TRUE.equals(row.get("enabled")))
                throw new IllegalStateException("Existing account is not an enabled administrator");
            return false;
        }
        db.update("INSERT INTO console_account(user_name,display_name,role,password_hash,enabled) VALUES(?,?,'ADMIN',?,TRUE)",
                name, "管理员", encodePassword(password));
        return true;
    }

    public static String encodePassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
            try {
                byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
            } finally { spec.clearPassword(); }
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("Password encoding unavailable"); }
    }

    public boolean seedDemo(String url) {
        validateDemoUrl(url);
        // Also inspect the actual connection target; caller-provided strings are not sufficient.
        String catalog;
        try (Connection connection = dataSource.getConnection()) { catalog = connection.getCatalog(); }
        catch (java.sql.SQLException e) { throw new IllegalStateException("Database unavailable"); }
        if (catalog == null || !catalog.toLowerCase(Locale.ROOT).endsWith("_demo"))
            throw new IllegalArgumentException("Actual database must end in _demo");
        db.execute("CREATE TABLE IF NOT EXISTS sp_public_demo_seed (version VARCHAR(50) PRIMARY KEY,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        if (db.queryForObject("SELECT COUNT(*) FROM sp_public_demo_seed WHERE version=?", Integer.class, SOURCE) > 0) return false;
        // Allow restart after DDL interruption only when all existing inventory tables remain empty.
        for (String table : Arrays.asList("sp_park", "sp_room", "sp_zone", "sp_cabinet", "sp_device",
                "sp_monitor_record", "sp_reading", "sp_energy_sample", "console_order")) {
            try (Connection c = dataSource.getConnection(); java.sql.ResultSet tables = c.getMetaData().getTables(catalog, null, "%", new String[]{"TABLE"})) {
                boolean exists = false;
                while (tables.next()) if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) exists = true;
                if (exists && db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class) > 0)
                    throw new IllegalStateException("Demo requires empty business tables");
            } catch (java.sql.SQLException e) { throw new IllegalStateException("Schema inspection failed"); }
        }
        // V001 intentionally has no IF NOT EXISTS: skip only for a complete empty schema.
        boolean inventoryExists;
        try { db.queryForObject("SELECT COUNT(*) FROM sp_park", Long.class); inventoryExists = true; }
        catch (org.springframework.jdbc.BadSqlGrammarException missing) { inventoryExists = false; }
        if (!inventoryExists) script("V001__inventory_schema.sql");
        script("V004__monitoring_schema.sql");
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            // SQL seed and all synthetic records share one transaction.
            org.springframework.jdbc.datasource.ConnectionHolder holder =
                    (org.springframework.jdbc.datasource.ConnectionHolder) org.springframework.transaction.support.TransactionSynchronizationManager.getResource(dataSource);
            ScriptUtils.executeSqlScript(holder.getConnection(), new ClassPathResource("bootstrap/V002__inventory_demo_seed.sql"));
            // Synthetic data carries its provenance even though existing queries require REAL.
            for (String table : Arrays.asList("sp_park", "sp_room", "sp_zone", "sp_cabinet", "sp_device"))
                db.update("UPDATE " + table + " SET source='MOCK_IMPORT',record_origin='REAL'");
            long room = db.queryForObject("SELECT MIN(id) FROM sp_room", Long.class);
            List<Map<String,Object>> cabinets = db.queryForList("SELECT c.id,c.name FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id WHERE z.room_id=? ORDER BY c.id", room);
            Instant now = Instant.now();
            String[] metrics = {"temperature","humidity","aqi","smoke","water","power","itPower","pue","todayPue","monthPue"};
            double[] values = {24.3,45.2,28,0,0,82.5,59.8,1.38,1.41,1.43};
            String[] units = {"℃","%","AQI","","","kW","kW","","",""};
            for (int i=0;i<metrics.length;i++) reading(room,"ROOM",room,metrics[i],values[i],units[i],now);
            for (int i=0;i<cabinets.size();i++) {
                long id=((Number)cabinets.get(i).get("id")).longValue();
                reading(room,"CABINET",id,"temperature",i==1?31.8:24+i%5*.2,"℃",now);
                reading(room,"CABINET",id,"humidity",45+i%5,"%",now);
                reading(room,"CABINET",id,"power",1.2+i%8*.1,"kW",now);
                reading(room,"CABINET",id,"online",1,"",now);
            }
            ZoneId zone=ZoneId.of("Asia/Shanghai");
            String[] categories={"机柜","空调","照明","监控","其他"};
            for(int day=6;day>=0;day--) for(int i=0;i<categories.length;i++) {
                Instant at=LocalDate.now(zone).minusDays(day).atStartOfDay(zone).toInstant();
                db.update("INSERT INTO sp_energy_sample(room_id,category,kwh,observed_at,quality,record_origin,source) VALUES(?,?,?,?,'GOOD','REAL',?)",
                        room,categories[i],i==0?780+day*12:100+i*15+day,Timestamp.from(at),SOURCE);
            }
            for(int i=0;i<5;i++) record(room,"FACILITY","public-camera-"+i,map("type","CAMERA","name","演示摄像头 "+(i+1),"sceneModelKey",new String[]{"zone-a","zone-b","zone-c","zone-d","camera-b"}[i],"status","在线"));
            for(int i=0;i<4;i++) record(room,"FACILITY","public-ac-"+i,map("type","AC","name","演示精密空调 "+(i+1),"sceneModelKey","ac-"+(i+1),"status","正常"));
            List<Map<String,Object>> devices=db.queryForList("SELECT d.id,d.name,d.cabinet_id FROM sp_device d JOIN sp_cabinet c ON d.cabinet_id=c.id JOIN sp_zone z ON c.zone_id=z.id WHERE z.room_id=? ORDER BY d.id",room);
            for(int i=0;i<devices.size();i++) {
                Map<String,Object> device=devices.get(i);
                record(room,"NODE","public-node-"+i,map("name","演示节点 "+(i+1),"deviceId",String.valueOf(device.get("id")),"cabinetId",String.valueOf(device.get("cabinet_id")),"layer",i==0?"core":i<3?"access":"terminal","status","正常","ports",map("total",24,"used",8,"errors",0)));
                if(i>0) record(room,"LINK","public-link-"+i,map("from","public-node-0","to","public-node-"+i,"status","normal","bandwidth",1000,"latency",.8,"loss",0));
            }
            Object cabinet=cabinets.get(1).get("id");
            record(room,"ALARM","public-temperature-alarm",map("title","【演示】机柜温度偏高","cabinetId",String.valueOf(cabinet),"entityType","CABINET","entityId",String.valueOf(cabinet),"metric","temperature","value","31.8 ℃","threshold","≤ 28 ℃","severity","warning","status","未处理","createdAt",now.toString(),"advice","演示数据：检查冷通道送风并记录处理过程。","rule",map("metric","temperature","unit","℃","min",0,"max",28,"maxAgeSeconds",300,"normalSeconds",60,"maxGapSeconds",30)));
            record(room,"EVENT","public-event",map("title","【演示】数据初始化完成","description","全部数据为合成样例，不包含真实用户或设备信息。","createdAt",now.toString()));
            db.update("INSERT INTO sp_public_demo_seed(version) VALUES(?)",SOURCE);
            return true;
        });
    }

    private void script(String filename) {
        try (Connection c=dataSource.getConnection()) { ScriptUtils.executeSqlScript(c,new ClassPathResource("bootstrap/"+filename)); }
        catch(java.sql.SQLException e) { throw new IllegalStateException("Schema initialization failed"); }
    }
    private void reading(long room,String type,long id,String metric,double value,String unit,Instant at) {
        db.update("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,?,?,?,?,?,?,'GOOD',?,'REAL')",room,type,String.valueOf(id),metric,value,unit,Timestamp.from(at),SOURCE);
    }
    private void record(long room,String kind,String id,Map<String,Object> value) {
        value.put("id",id); value.put("roomId",String.valueOf(room)); value.put("source",SOURCE); value.put("recordOrigin","REAL"); value.put("simulated",true);
        try { db.update("INSERT INTO sp_monitor_record(room_id,kind,id,payload) VALUES(?,?,?,?)",room,kind,id,json.writeValueAsString(value)); }
        catch(java.io.IOException e) { throw new IllegalStateException("Demo serialization failed"); }
    }
    private static Map<String,Object> map(Object... values) {
        Map<String,Object> out=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2) out.put(String.valueOf(values[i]),values[i+1]);
        return out;
    }
}
