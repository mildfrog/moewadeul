package com.moewadeul.common.testsupport;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

// 매퍼 XML의 실제 SQL을 같은 인자로 묶어 EXPLAIN한다. 테스트에 SQL을 옮겨 적으면 쿼리가 바뀌어도 테스트는 옛 SQL을 본다.
public final class MapperExplain {

    private final SqlSessionFactory sqlSessionFactory;
    private final JdbcTemplate jdbc;

    public MapperExplain(SqlSessionFactory sqlSessionFactory, JdbcTemplate jdbc) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> plan(Class<?> mapper, String method, Map<String, Object> params) {
        MappedStatement statement =
                sqlSessionFactory.getConfiguration().getMappedStatement(mapper.getName() + "." + method);
        MapperMethod.ParamMap<Object> parameter = new MapperMethod.ParamMap<>();
        parameter.putAll(params);
        BoundSql boundSql = statement.getBoundSql(parameter);
        return jdbc.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
            try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + boundSql.getSql())) {
                new DefaultParameterHandler(statement, parameter, boundSql).setParameters(explain);
                try (ResultSet rows = explain.executeQuery()) {
                    ResultSetMetaData meta = rows.getMetaData();
                    List<Map<String, Object>> plan = new ArrayList<>();
                    while (rows.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            row.put(meta.getColumnLabel(i), rows.getObject(i));
                        }
                        plan.add(row);
                    }
                    return plan;
                }
            }
        });
    }

    public Map<String, Object> rowOf(String table, Class<?> mapper, String method, Map<String, Object> params) {
        List<Map<String, Object>> plan = plan(mapper, method, params);
        return plan.stream()
                .filter(row -> table.equals(row.get("table")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("실행 계획에 " + table + " 행이 없다: " + plan));
    }
}
