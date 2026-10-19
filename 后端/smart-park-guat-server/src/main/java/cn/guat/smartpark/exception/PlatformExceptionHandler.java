package cn.guat.smartpark.exception;
import cn.guat.smartpark.common.R;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
@RestControllerAdvice(basePackages="cn.guat.smartpark.controller")
public class PlatformExceptionHandler {
 private ResponseEntity<R> failure(int status,String type,String msg) {
  return ResponseEntity.status(status).body(R.error(status,msg,type));
 }
 @ExceptionHandler(PlatformException.class) public ResponseEntity<R> business(PlatformException e){return failure(e.status,e.type,e.getMessage());}
 @ExceptionHandler({MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<R> argument(Exception e){return failure(400,"INVALID_ARGUMENT","请求参数无效");}
 @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) public ResponseEntity<R> method(Exception e){return failure(405,"METHOD_NOT_ALLOWED","请求方法不支持");}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public ResponseEntity<R> duplicate(Exception e){return failure(409,"DUPLICATE_RECORD","记录已存在，请刷新后重试");}
 @ExceptionHandler(DataAccessException.class) public ResponseEntity<R> database(Exception e){return failure(503,"DATA_SOURCE_UNAVAILABLE","台账数据库暂不可用，请重试");}
 @ExceptionHandler(Exception.class) public ResponseEntity<R> unexpected(Exception e){return failure(500,"INTERNAL_ERROR","服务处理失败，请稍后重试");}
}
