import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Redistributes only the explicitly seeded cabinet samples; preserves each time bucket's total. */
public class RebalanceCabinetEnergy {
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static void report(Connection db,String room)throws Exception{try(PreparedStatement s=db.prepareStatement("SELECT c.name,COUNT(DISTINCT d.id) devices,COALESCE(e.kwh,0) kwh FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id LEFT JOIN sp_device d ON d.cabinet_id=c.id LEFT JOIN (SELECT cabinet_id,SUM(kwh) kwh FROM sp_energy_sample WHERE room_id=? AND category='机柜' AND source='INTEGRATION_SEED' GROUP BY cabinet_id) e ON e.cabinet_id=c.id WHERE z.room_id=? GROUP BY c.id,c.name,e.kwh ORDER BY e.kwh DESC,c.id LIMIT 5")){s.setString(1,room);s.setString(2,room);try(ResultSet r=s.executeQuery()){while(r.next())System.out.printf(Locale.ROOT,"%s | devices=%d | %.3f kWh%n",r.getString(1),r.getInt(2),r.getDouble(3));}}}
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection db=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){db.setAutoCommit(false);try{
   String room;try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT room_id FROM sp_energy_sample WHERE source='INTEGRATION_SEED' ORDER BY room_id LIMIT 1")){if(!r.next())throw new IllegalStateException("No seeded energy samples");room=r.getString(1);}
   System.out.println("Before:");report(db,room);
   Map<String,Double> weights=new LinkedHashMap<>();
   try(PreparedStatement s=db.prepareStatement("SELECT c.id FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id WHERE z.room_id=? ORDER BY c.id")){s.setString(1,room);try(ResultSet r=s.executeQuery()){while(r.next())weights.put(r.getString(1),.035);}}
   try(PreparedStatement s=db.prepareStatement("SELECT d.id,d.cabinet_id,d.device_type,(SELECT value FROM sp_reading r WHERE r.room_id=? AND r.entity_type='DEVICE' AND r.entity_id=d.id AND r.metric='power' AND r.quality='GOOD' ORDER BY r.observed_at DESC,r.id DESC LIMIT 1) power FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=?")){s.setString(1,room);s.setString(2,room);try(ResultSet r=s.executeQuery()){while(r.next()){double power=r.getDouble(4);if(r.wasNull())power="SERVER".equals(r.getString(3))?.3:"STORAGE".equals(r.getString(3))?.2:"SWITCH".equals(r.getString(3))?.06:.04;
    // Seeded load factors represent differing device workloads; never alter measured device readings.
    double load=.65+Math.floorMod(r.getString(1).hashCode(),11)*.035;String cabinet=r.getString(2);weights.put(cabinet,weights.get(cabinet)+power*load);}}}
   if(args.length>0&&"--inspect".equals(args[0])){db.rollback();return;}
   // Distinct seeded cabinet utilization avoids identical totals for cabinets with similar devices.
   for(Map.Entry<String,Double> entry:weights.entrySet())entry.setValue(entry.getValue()*(.8+Math.floorMod(entry.getKey().hashCode(),97)*.004));
   double weightTotal=weights.values().stream().mapToDouble(Double::doubleValue).sum();
   Map<Timestamp,Double> buckets=new LinkedHashMap<>();try(PreparedStatement s=db.prepareStatement("SELECT observed_at,SUM(kwh) FROM sp_energy_sample WHERE room_id=? AND category='机柜' AND source='INTEGRATION_SEED' GROUP BY observed_at")){s.setString(1,room);try(ResultSet r=s.executeQuery()){while(r.next())buckets.put(r.getTimestamp(1),r.getDouble(2));}}
   try(PreparedStatement s=db.prepareStatement("UPDATE sp_energy_sample SET kwh=? WHERE room_id=? AND category='机柜' AND cabinet_id=? AND observed_at=? AND source='INTEGRATION_SEED'")){for(Map.Entry<Timestamp,Double> bucket:buckets.entrySet())for(Map.Entry<String,Double> weight:weights.entrySet()){s.setDouble(1,bucket.getValue()*weight.getValue()/weightTotal);s.setString(2,room);s.setString(3,weight.getKey());s.setTimestamp(4,bucket.getKey());s.addBatch();}s.executeBatch();}
   try(PreparedStatement s=db.prepareStatement("SELECT observed_at,SUM(kwh) FROM sp_energy_sample WHERE room_id=? AND category='机柜' AND source='INTEGRATION_SEED' GROUP BY observed_at")){s.setString(1,room);try(ResultSet r=s.executeQuery()){while(r.next())if(Math.abs(r.getDouble(2)-buckets.get(r.getTimestamp(1)))>1e-7)throw new IllegalStateException("Bucket total changed");}}
   db.commit();System.out.println("After:");report(db,room);System.out.println("PASS: per-cabinet distribution updated; all time-bucket totals preserved");
  }catch(Exception e){db.rollback();throw e;}}
 }
}
