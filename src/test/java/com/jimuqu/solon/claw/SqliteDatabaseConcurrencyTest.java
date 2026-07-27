package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SqliteDatabaseConcurrencyTest {
    /** 现有写入路径仍由单写锁串行化。 */
    @Test
    void shouldSerializeConcurrentSqliteAccess() throws Exception {
        SqliteDatabase database = createDatabase("jimuqu-sqlite-concurrency");
        ExecutorService executorService = Executors.newFixedThreadPool(12);
        try {
            SqliteChannelStateRepository repository = new SqliteChannelStateRepository(database);
            int workers = 12;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < workers; i++) {
                final int index = i;
                futures.add(
                        executorService.submit(
                                new Callable<Boolean>() {
                                    @Override
                                    public Boolean call() throws Exception {
                                        start.await();
                                        String key = "key-" + index;
                                        String value = "value-" + index;
                                        repository.put(PlatformType.WEIXIN, "scope", key, value);
                                        return value.equals(
                                                repository.get(PlatformType.WEIXIN, "scope", key));
                                    }
                                }));
            }

            start.countDown();
            for (Future<Boolean> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            executorService.shutdownNow();
            database.shutdown();
        }
    }

    /** WAL 只读连接应在写事务持有单写锁时读取最近一次已提交快照。 */
    @Test
    void shouldReadCommittedSnapshotWhileWriteLeaseIsHeld() throws Exception {
        SqliteDatabase database = createDatabase("jimuqu-sqlite-read-write");
        Connection setup = database.openConnection();
        try {
            Statement statement = setup.createStatement();
            try {
                statement.execute(
                        "create table read_concurrency_probe (id integer primary key, value text)");
                statement.execute(
                        "insert into read_concurrency_probe (id, value) values (1, 'committed')");
            } finally {
                statement.close();
            }
        } finally {
            setup.close();
        }

        Connection writer = database.openConnection();
        writer.setAutoCommit(false);
        PreparedStatement update =
                writer.prepareStatement(
                        "update read_concurrency_probe set value = 'pending' where id = 1");
        update.executeUpdate();
        update.close();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> read =
                    executor.submit(
                            new Callable<String>() {
                                @Override
                                public String call() throws Exception {
                                    Connection connection = database.openReadConnection();
                                    try {
                                        Statement statement = connection.createStatement();
                                        try {
                                            ResultSet resultSet =
                                                    statement.executeQuery(
                                                            "select value from read_concurrency_probe where id = 1");
                                            try {
                                                return resultSet.next()
                                                        ? resultSet.getString(1)
                                                        : null;
                                            } finally {
                                                resultSet.close();
                                            }
                                        } finally {
                                            statement.close();
                                        }
                                    } finally {
                                        connection.close();
                                    }
                                }
                            });
            assertThat(read.get(2, TimeUnit.SECONDS)).isEqualTo("committed");
        } finally {
            writer.rollback();
            writer.setAutoCommit(true);
            writer.close();
            executor.shutdownNow();
            database.shutdown();
        }
    }

    /** query_only 与 mode=ro 必须共同拒绝任何误用只读连接的写语句。 */
    @Test
    void shouldRejectWritesFromReadConnection() throws Exception {
        SqliteDatabase database = createDatabase("jimuqu-sqlite-readonly");
        Connection connection = database.openReadConnection();
        Exception failure = null;
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("create table forbidden_write (id integer)");
            } catch (Exception e) {
                failure = e;
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
            database.shutdown();
        }
        assertThat(failure).isNotNull();
    }

    /**
     * 创建使用独立临时状态库的测试数据库。
     *
     * @param prefix 临时目录前缀。
     * @return 返回已完成 schema 初始化的数据库。
     */
    private SqliteDatabase createDatabase(String prefix) throws Exception {
        File workspaceHome = Files.createTempDirectory(prefix).toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(workspaceHome.getAbsolutePath());
        config.getRuntime()
                .setStateDb(
                        new File(new File(workspaceHome, "data"), "state.db").getAbsolutePath());
        return new SqliteDatabase(config);
    }
}
