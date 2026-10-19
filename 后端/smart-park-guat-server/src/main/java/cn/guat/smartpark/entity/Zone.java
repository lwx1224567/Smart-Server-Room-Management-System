package cn.guat.smartpark.entity;
import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class Zone extends InventoryRecord { private Long roomId; private Integer displayOrder; }
