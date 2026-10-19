package cn.guat.smartpark.controller;

import cn.guat.smartpark.common.R;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

/** Apply the same JSON contract to servlet errors, including removed/unknown routes. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<R> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer ? (Integer) value : 500;
        String type = status == 404 ? "NOT_FOUND" : status == 405 ? "METHOD_NOT_ALLOWED" : "HTTP_ERROR";
        String message = status == 404 ? "接口不存在" : status == 405 ? "请求方法不支持" : "请求处理失败";
        return ResponseEntity.status(status).body(R.error(status, message, type));
    }
}
