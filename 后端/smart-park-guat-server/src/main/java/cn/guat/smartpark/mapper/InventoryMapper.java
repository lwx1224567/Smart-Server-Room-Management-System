package cn.guat.smartpark.mapper;
import cn.guat.smartpark.entity.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface InventoryMapper {
 @Select("SELECT * FROM sp_park ORDER BY id") List<Park> parks();
 @Select("SELECT * FROM sp_room ORDER BY id") List<Room> rooms();
 @Select("SELECT * FROM sp_zone WHERE room_id=#{roomId} ORDER BY display_order,id") List<Zone> zones(Long roomId);
 @Select("SELECT c.* FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=#{roomId} ORDER BY c.id") List<Cabinet> cabinets(Long roomId);
 @Select("SELECT * FROM sp_cabinet WHERE id=#{id}") Cabinet cabinet(Long id);
 @Select("SELECT d.* FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=#{roomId} ORDER BY d.id") List<Device> devices(Long roomId);
 @Select("SELECT * FROM sp_device WHERE cabinet_id=#{cabinetId} ORDER BY id") List<Device> cabinetDevices(Long cabinetId);
 @Select("SELECT * FROM sp_zone WHERE id=#{id}") Zone zone(Long id);
}
