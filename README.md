Ссылка на демонстрацию: https://youtu.be/-nh1egphHKU

Порядок работы с приложением:

* Необходимые настройки. Указание параметров подключения к БД, пути sqlcmd, директории для сохранения файлов-дампов.
    Необходимые настройки в файле конфигураций src\main\resources\application.properties

    Параметры подключения к БД:
    server=<адрес сервера>
    spring.datasource.username=<username>
    spring.datasource.password=<password>

    Директория куда сохраняются дампы:
    directory=/home/user/dumps/client/

    Путь к исполняемой утилите sqlcmd
    base-command=/opt/mssql-tools/bin/sqlcmd

* Сборка jar-файла командой ```mvn clean install```

* Запуск приложения при помощи jar-файла target\dumper-client-0.0.1-SNAPSHOT.jar командой ```java -jar dumper-client-0.0.1-SNAPSHOT.jar```

* Через GET-запрос попробовать скачать актуальные дампы и восстановить ```http://localhost:8080/api/dump/start-restore?databaseName=TestDB&path=C:\WORK\DUMPS&date=2020-08-25```
    Обязательные параметр:
    databaseName - название БД, дампы которой необходимо восстановить

    Необязательные:
    path - директория куда буду скачиваться дампы
    date - дата актуализации дампов, без этого параметра сервер отдает последние актуальные дампы

* Выполнить пользовательский sql-скрипт через GET-запрос ```http://localhost:8080/api/dump/execute-query?query=use TestDB; select * from inventory

    query - пользовательский sql-запрос
