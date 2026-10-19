package cn.guat.smartpark.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit HTTP contracts. State, ownership and evidence checks remain in ConsoleService. */
public final class WorkOrderRequests {
    private WorkOrderRequests() {}
    public static class ExpectedState {
        public String status;
        public Long ownerAccountId;
        public String updatedAt;
        public Map<String,Object> toMap() {
            Map<String,Object> value=new LinkedHashMap<>();
            value.put("status",status);value.put("ownerAccountId",ownerAccountId);value.put("updatedAt",updatedAt);
            return value;
        }
    }
    public static class Action {
        public ExpectedState expectedState;
        public Map<String,Object> toMap() {
            Map<String,Object> value=new LinkedHashMap<>();
            if(expectedState!=null)value.put("expectedState",expectedState.toMap());
            return value;
        }
    }
    public static class Assignment extends Action {
        public Long ownerAccountId;
        @Override public Map<String,Object> toMap(){Map<String,Object> value=super.toMap();value.put("ownerAccountId",ownerAccountId);return value;}
    }
    public static class ProcessingRecord extends Action {
        public String cause;
        public String process;
        public String result;
        @Override public Map<String,Object> toMap(){Map<String,Object> value=super.toMap();value.put("cause",cause);value.put("process",process);value.put("result",result);return value;}
    }
    public static class Submission extends ProcessingRecord {}
}
