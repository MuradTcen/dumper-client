package com.dumper.server.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Repository
@RequiredArgsConstructor
public class DumpDaoImpl implements DumpDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Вернуть количество подключений к БД
     * @param databaseName название БД
     * @return количество подключений к БД
     */
    @Override
    public int getCountConnectionsForDatabase(String databaseName) {
        String query = "select count(*) from sys.sysprocesses where dbid = DB_ID(:database)";

        return entityManager.createNativeQuery(query)
                .setParameter("database", databaseName)
                .getFirstResult();
    }

    /**
     * Получить код режима доступа к БД
     * @param databaseName название БД
     * @return код режима доступа к БД
     */
    @Override
    public int getUserAccess(String databaseName) {
        String query = "select user_access from master.sys.databases where name = :database";

        return entityManager.createNativeQuery(query)
                .setParameter("database", databaseName)
                .getFirstResult();
    }

    /**
     * Получить уровень совместимости
     * @param databaseName название БД
     * @return числовой уровень совместимости
     */
    @Override
    public int getCompatibilityLevel(String databaseName) {
        String query = "select compatibility_level from master.sys.databases where name = :database";

        return entityManager.createNativeQuery(query)
                .setParameter("database", databaseName)
                .getFirstResult();
    }

    /**
     * Запрос года версии
     * @return год версии
     */
    @Override
    public String getVersion() {
        return entityManager.createNativeQuery("select @@version").getSingleResult().toString();
    }
}
