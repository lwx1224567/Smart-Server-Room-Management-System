package cn.guat.smartpark.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class InventoryRecord {
 private Long id; private String code; private String name; private String recordOrigin;
 private String source; private LocalDateTime sourceUpdatedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
