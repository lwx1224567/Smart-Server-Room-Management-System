package cn.guat.smartpark.service;
import cn.guat.smartpark.exception.PlatformException;
import cn.guat.smartpark.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

@Component
public class ConsoleAccessFilter extends OncePerRequestFilter {
 private final ConsoleService service;private final ObjectMapper json;
 public ConsoleAccessFilter(ConsoleService service,ObjectMapper json){this.service=service;this.json=json;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String p=req.getServletPath();if(p.isEmpty())p=req.getRequestURI().substring(req.getContextPath().length());res.setHeader("Access-Control-Allow-Origin","*");res.setHeader("Access-Control-Allow-Headers","Content-Type, Authorization, X-Assistant-Page");res.setHeader("Access-Control-Allow-Methods","GET, POST, PUT, PATCH, DELETE, OPTIONS");
  if("OPTIONS".equals(req.getMethod())){res.setStatus(204);return;}
  try{
   if(p.startsWith("/api/v1/")&&!p.equals("/api/v1/auth/login")&&!p.equals("/api/v1/auth/logout")){
    java.util.Map<String,Object> actor=service.identity(req.getHeader("Authorization"));
    boolean personal=p.equals("/api/v1/auth/me")||p.equals("/api/v1/auth/password")||p.equals("/api/v1/weather")
      ||p.equals("/api/v1/work-orders")||p.startsWith("/api/v1/work-orders/")
      ||p.equals("/api/v1/notifications")||p.startsWith("/api/v1/notifications/");
    if(!personal)service.admin(actor);
   }
  }catch(PlatformException e){res.setStatus(e.status);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getWriter(),R.error(e.status,e.getMessage(),e.type));return;}
  chain.doFilter(req,res);
 }
}
