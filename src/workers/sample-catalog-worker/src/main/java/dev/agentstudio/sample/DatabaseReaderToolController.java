package dev.agentstudio.sample;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="sample.role",havingValue="database-tool")
public class DatabaseReaderToolController {
    private final JdbcClient jdbc;
    public DatabaseReaderToolController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping("/tools/database.describe-schema")
    Map<String,Object> schema(){return Map.of("tables",List.of(Map.of("name","products","columns",List.of("id","name","category","price","stock"))));}

    @PostMapping("/tools/database.query-readonly")
    Map<String,Object> query(@RequestBody Map<String,Object> input){
        String category=Objects.toString(input.get("category"),"").trim();
        List<Map<String,Object>> rows=category.isBlank()
          ?jdbc.sql("select id,name,category,price,stock from products order by id limit 25").query().listOfRows()
          :jdbc.sql("select id,name,category,price,stock from products where lower(category)=lower(?) order by id limit 25").param(category).query().listOfRows();
        return Map.of("queryTemplate","approved-products-by-category","rowCount",rows.size(),"rows",rows);
    }
}
