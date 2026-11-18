package com.thinkfree.drive.common;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * JDBC 왕복 실측용 테스트 DataSource — 이것으로 만든 JdbcTemplate·NamedParameterJdbcTemplate 이 실행한
 * {@code prepareStatement}·{@code addBatch}·{@code executeBatch}·{@code executeUpdate} 횟수를 센다.
 * 커넥션은 테스트 트랜잭션에 묶인 원래 DataSource 의 커넥션을 감싸므로(close = 트랜잭션 커넥션 반환) 같은 트랜잭션의 데이터를 보고 롤백된다.
 */
public class JdbcStatementCounter extends DelegatingDataSource {

    private int prepareStatement;
    private int addBatch;
    private int executeBatch;
    private int executeUpdate;

    public JdbcStatementCounter(DataSource target) {
        super(target);
    }

    public int prepareStatementCount() {
        return prepareStatement;
    }

    public int addBatchCount() {
        return addBatch;
    }

    public int executeBatchCount() {
        return executeBatch;
    }

    public int executeUpdateCount() {
        return executeUpdate;
    }

    public void reset() {
        prepareStatement = 0;
        addBatch = 0;
        executeBatch = 0;
        executeUpdate = 0;
    }

    @Override
    public Connection getConnection() {
        DataSource target = obtainTargetDataSource();
        Connection connection = DataSourceUtils.getConnection(target);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName())) {
                DataSourceUtils.releaseConnection(connection, target);
                return null;
            }
            Object result = invoke(connection, method, args);
            if ("prepareStatement".equals(method.getName())) {
                prepareStatement++;
                return countingStatement((PreparedStatement) result);
            }
            return result;
        };
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    private PreparedStatement countingStatement(PreparedStatement statement) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "addBatch" -> addBatch++;
                case "executeBatch" -> executeBatch++;
                case "executeUpdate" -> executeUpdate++;
                default -> {
                }
            }
            return invoke(statement, method, args);
        };
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, handler);
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
