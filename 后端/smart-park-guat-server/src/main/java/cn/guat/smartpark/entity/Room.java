package cn.guat.smartpark.entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class Room extends InventoryRecord { private Long parkId; private String location; private String managerDisplayName; private String sceneModelKey; }
