Как погасить Folks и запустить снова
=====================================

Приложение слушает http://localhost:8088
Страница участников: http://localhost:8088/ui/

Все команды выполняйте в PowerShell из каталога проекта:
C:\git\folks_console


Погасить
--------

Если окно, в котором запускали приложение, ещё открыто, нажмите Ctrl+C.

Если окна нет, завершите процесс, который занимает порт 8088:

$conn = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) { Stop-Process -Id $conn.OwningProcess -Force }

Проверка, что сервер остановлен: страница http://localhost:8088/ui/ больше не открывается.


Запустить снова
---------------

.\mvnw.cmd -q -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=ru.olgak.folks.app.FolksApplication" "-Dexec.classpathScope=runtime"

Дождитесь строк:

Folks started
UI    http://localhost:8088/ui/

Окно PowerShell не закрывайте: пока оно работает, работает и приложение.
Повторное нажатие Ctrl+C снова его погасит.

База в памяти. После перезапуска остаются только исходные четыре участника, созданные через интерфейс или Postman записи пропадают.
