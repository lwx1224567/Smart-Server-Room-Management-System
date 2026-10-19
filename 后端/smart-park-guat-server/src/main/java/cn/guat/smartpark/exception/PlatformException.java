package cn.guat.smartpark.exception;
public class PlatformException extends RuntimeException {
 public final int status; public final String type;
 public PlatformException(int status,String type,String message){super(message);this.status=status;this.type=type;}
}
