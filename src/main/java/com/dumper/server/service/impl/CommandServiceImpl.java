package com.dumper.server.service.impl;

import com.dumper.server.dao.DumpDao;
import com.dumper.server.entity.Dump;
import com.dumper.server.entity.ShortDump;
import com.dumper.server.enums.Query;
import com.dumper.server.enums.UserAccess;
import com.dumper.server.service.CommandService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dumper.server.enums.Key.*;

@Slf4j
@RequiredArgsConstructor
@Service
@PropertySource("classpath:application.properties")
public class CommandServiceImpl implements CommandService {

    @Value(value = "${spring.datasource.username:username}")
    private String username;

    @Value(value = "${spring.datasource.password:password}")
    private String password;

    @Value(value = "${server:localhost}")
    private String server;

    @Value(value = "${database.name:TestDB}")
    private String database;

    @Value(value = "${directory:directory}")
    private String directory;

    @Value(value = "${base-command:/opt/mssql-tools/bin/sqlcmd}")
    private String baseCommand;

    private final DumpDao dao;
    private final DumpServiceImpl dumpService;
    private final int TIMEOUT_IN_MINUTES = 20;


    // todo: не нравится, как здесь получилось
    /**
     * Выполняем через sqlcmd сформированный запрос, можем получить в ответ вывод из консоли и ошибки
     * @param command команда (массив ключей) для выполнения
     * @return результат выполнения запроса
     */
    @Override
    public String executeCommand(String[] command) {
        Runtime runtime = Runtime.getRuntime();
        String errors, out;
        InputStream errorStream = null, outStream = null;

        log.info("Start executing command: " + Arrays.toString(command));
        try {
            Process proc = runtime.exec(command);
            proc.waitFor(TIMEOUT_IN_MINUTES, TimeUnit.MINUTES);
            errorStream = proc.getErrorStream();
            outStream = proc.getInputStream();
            errors = IOUtils.toString(errorStream, StandardCharsets.UTF_8);
            out = IOUtils.toString(outStream, StandardCharsets.UTF_8);
            log.info("out: " + out);
            if (!errors.isEmpty()) {
                log.error("errors: " + errors);
                return errors;
            }
            return out;
        } catch (Exception e) {
            try {
                errorStream.close();
            } catch (IOException ioException) {
                log.error("Error during process: " + e.getLocalizedMessage());
            }
            try {
                outStream.close();
            } catch (IOException ioException) {
                log.error("Error during process: " + e.getLocalizedMessage());
            }

            log.error("Error during process: " + e.getLocalizedMessage());
        }
        return "Error during process..";
    }

    /**
     * Выполняем запрос с файлом дампа
     * @param filename название файла
     * @param query тип запроса
     * @return результат выполнения команды
     */
    @Override
    public String executeDumpQuery(String filename, Query query) {
        Command command = getCommandWithFileName(filename);
        setQuery(command, query);
        return String.format("Executed command %s for filename %s \n\n with output %s\n\n",
                query.getQuery(),
                filename,
                executeCommand(getCommands(command)));
    }

    /**
     * Выполняем пользовательский sql-запрос
     * @param userQuery пользовательский sql-запрос
     * @return результат выполнеия запроса
     */
    @Override
    public String executeUserQuery(String userQuery) {
        Command command = getCommandWithUserQuery(userQuery);
        setQuery(command, Query.USER_QUERY);
        return String.format("Executed query %s \n\n with output %s\n\n",
                userQuery,
                executeCommand(getCommands(command)));
    }

    private String executeQuery(Query query) {
        Command command = getBaseCommand();
        setQuery(command, query);
        return executeCommand(getCommands(command));
    }

    /**
     * "Параметризуем" команду
     * @param command базовая команда
     * @return массив ключей команды
     */
    public String[] getCommands(Command command) {
        List<String> result = new ArrayList<>();
        result.add(baseCommand);

        command.getParams().forEach((k, v) -> {
            if (k.contains("-")) {
                result.add(k);
                result.add(v);
            }
        });

        return result.toArray(new String[0]);
    }

    @Data
    static class Command {
        private HashMap<String, String> params;
    }

    private void setQuery(Command command, Query query) {
        command.getParams().put(QUERY_KEY.getKey(), getQueryWithParams(command, query));
    }

    private String getQueryWithParams(Command command, Query query) {
        String result = query.getQuery().replace(DATABASE_KEY.getKey(), command.getParams().get(DATABASE_KEY.getKey()));
        result = result.replace(DIRECTORY_KEY.getKey(), command.getParams().get(DIRECTORY_KEY.getKey()) +
                command.getParams().get(FILENAME_KEY.getKey()));

        if (command.getParams().get(USER_QUERY_KEY.getKey()) != null) {
            result = result.replace(USER_QUERY_KEY.getKey(), command.getParams().get(USER_QUERY_KEY.getKey()));
        }
        return result;
    }

    private Command getCommandWithFileName(String filename) {
        Command command = getBaseCommand();
        command.getParams().put(FILENAME_KEY.getKey(), filename);

        return command;
    }

    private Command getCommandWithUserQuery(String query) {
        Command command = getBaseCommand();
        command.getParams().put(USER_QUERY_KEY.getKey(), query);

        return command;
    }

    /**
     *
     * @return
     */
    private Command getBaseCommand() {
        Command command = new Command();

        command.setParams(new HashMap<String, String>() {{
            put(DIRECTORY_KEY.getKey(), directory);
            put(DATABASE_KEY.getKey(), database);
            put(PASSWORD_KEY.getKey(), password);
            put(USER_KEY.getKey(), username);
            put(SERVER_KEY.getKey(), server);
        }});

        log.info("Created command with params: " + command.getParams());

        return command;
    }

    /**
     * Делаем препроверку скачиваем лист дампов, выполняем дампы, переставляем в multi_user
     * @param databaseName название БД
     * @param path путь для сохранения дампов
     * @return ответ с результатом
     */
    @Override
    public String restore(String databaseName, String path) {
        String initialCheck = dumpService.initialCheck(databaseName, path);
        if (!initialCheck.isEmpty()) {
            return initialCheck;
        }

        List<Dump> dumps = dumpService.downloadDumpList(databaseName);
        String dumpsCheck = dumpService.dumpsCheck(dumps);
        if (!dumpsCheck.isEmpty()) {
            return dumpsCheck;
        }

        String result = executeRestoreDumps(dumpService.getDownloadedDumpsForRestore(dumps, path)).stream()
                .collect(Collectors.joining(""));

        setIfRequiredUserAccess(databaseName);

        return result;
    }

    /**
     * В зависимости от типа дампа, выполняем запрос с нужным query
     * @param dumps
     * @return результат выполнения команд восстановления
     */
    @Override
    public List<String> executeRestoreDumps(List<ShortDump> dumps) {
        List<String> result = new ArrayList<>();
        log.info("Start restoring.. ");

        for (ShortDump dump : dumps) {
            switch (dump.getType()) {
                case 'D':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_FULL));
                    break;
                case 'I':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_DIFFERENTIAL));
                    break;
                case 'L':
                    result.add(executeDumpQuery(dump.getFilename(), Query.RESTORE_LOG));
                    break;
                default:
                    break;
            }
        }
        result.add(executeQuery(Query.RECOVERY));

        return result;
    }

    /**
     * Переставляем в режим в multi_user
     * @param databaseName название БД
     */
    @Override
    public void setIfRequiredUserAccess(String databaseName) {
        if (UserAccess.MULTI_USER.getCode() != dao.getUserAccess(databaseName)) {
            executeQuery(Query.USE_MASTER);
            executeQuery(Query.SET_MULTI_USER);
            executeQuery(Query.USE_MSDB);
        }
    }

}
