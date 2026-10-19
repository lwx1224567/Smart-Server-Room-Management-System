package cn.guat.smartpark.entity;
import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class Device extends InventoryRecord { private Long cabinetId; private String deviceType; private String sceneModelKey; private String legacyKey; private String registrationStatus; private Integer uStart; private Integer uHeight; private String networkLayer; }
