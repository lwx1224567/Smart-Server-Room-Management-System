package cn.guat.smartpark.entity;
import lombok.Data; import lombok.EqualsAndHashCode; import java.math.BigDecimal;
@Data @EqualsAndHashCode(callSuper=true)
public class Cabinet extends InventoryRecord { private Long zoneId; private String serialNumber; private Integer capacityU; private String sceneModelKey; private Integer gridRow; private Integer gridColumn; private BigDecimal positionX; private BigDecimal positionY; private BigDecimal positionZ; private String registrationStatus; }
