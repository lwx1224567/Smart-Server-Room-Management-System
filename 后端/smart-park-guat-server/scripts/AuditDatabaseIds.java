import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Read-only inventory of identifier columns and JSON identity paths; credentials/tokens are not printed. */
public class AuditDatabaseIds {
 static final ObjectMapper JSON=new ObjectMapper();
 static String setting(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static String quote(String id){if(!id.matches("[A-Za-z0-9_]+"))throw new IllegalArgumentException(id);return "`"+id+"`";}
 static boolean sensitive(String key){return key.toLowerCase(Locale.ROOT).matches(".*(token|password|secret|hash).*");}
 static void collect(Object node,String path,Map<String,Set<String>>out){
  if(node instanceof Map)for(Object entry:((Map<?,?>)node).entrySet()){
   Map.Entry<?,?>e=(Map.Entry<?,?>)entry;String key=e.getKey().toString(),next=path+"."+key;
   if(sensitive(key))continue;
   Object value=e.getValue();if(value instanceof Map||value instanceof List)collect(value,next,out);
   else if(value!=null&&(key.equals("id")||key.endsWith("Id")||key.endsWith("Code")||key.equals("alarmEvent")||key.equals("sceneModelKey")||key.equals("key")))out.computeIfAbsent(next,k->new LinkedHashSet<>()).add(value.toString());
  }else if(node instanceof List)for(Object value:(List<?>)node)collect(value,path+"[]",out);
 }
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml")),local=Files.readString(Path.of("src/main/resources/application-local.yml"));
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(setting(cfg,"url"),System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",setting(local,"password"))));
  List<Map<String,Object>>columns=db.queryForList("SELECT TABLE_NAME tableName,COLUMN_NAME columnName,DATA_TYPE dataType,COLUMN_KEY keyType FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION");
  List<Map<String,Object>>report=new ArrayList<>();Map<String,Set<String>>paths=new TreeMap<>();
  for(Map<String,Object>column:columns){
   String table=column.get("tableName").toString(),name=column.get("columnName").toString();
   if(name.equals("id")||name.endsWith("_id")||name.endsWith("_code")||name.equals("code")||name.endsWith("_key")||name.equals("token_hash")){
    Map<String,Object>item=new LinkedHashMap<>(column);item.put("rows",db.queryForObject("SELECT COUNT(*) FROM "+quote(table),Long.class));
    item.put("samples",sensitive(name)?Collections.singletonList("[安全标识，已隐藏]"):db.queryForList("SELECT DISTINCT "+quote(name)+" FROM "+quote(table)+" WHERE "+quote(name)+" IS NOT NULL ORDER BY "+quote(name)+" LIMIT 3",String.class));report.add(item);
   }
   if(name.equals("payload"))for(Map<String,Object>row:db.queryForList("SELECT * FROM "+quote(table))){
    String group=table+(row.containsKey("kind")?"["+row.get("kind")+"]":"");
    try{collect(JSON.readValue(row.get(name).toString(),Object.class),group+".payload",paths);}catch(com.fasterxml.jackson.core.JsonProcessingException ignored){}
   }
  }
  Map<String,Object>result=new LinkedHashMap<>();result.put("columns",report);Map<String,Object>jsonPaths=new TreeMap<>();
  for(Map.Entry<String,Set<String>>entry:paths.entrySet()){Map<String,Object>item=new LinkedHashMap<>();item.put("distinct",entry.getValue().size());item.put("samples",new ArrayList<>(entry.getValue()).subList(0,Math.min(3,entry.getValue().size())));jsonPaths.put(entry.getKey(),item);}
  result.put("jsonIds",jsonPaths);JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),result);System.out.println("Audit saved: "+args[0]);
 }
}
