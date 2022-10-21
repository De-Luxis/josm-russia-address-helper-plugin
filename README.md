# JOSM-Russia-address-helper-plugin
Плагин JOSM для загрузки адресов из ЕГРН.

Плагин автоматически производит фильтрацию выделенных объектов, выбирая только полигоны с тегом [building](https://wiki.openstreetmap.org/wiki/RU:Key:building).

## Установка

1. Скопируйте файл [russia-address-helper.jar](https://github.com/De-Luxis/JOSM-Russia-address-helper-plugin/releases/latest/download/russia-address-helper.jar) в `%appdata%\JOSM\plugins` для Windows, и в `~/.local/share/JOSM/plugins` для Linux.
2. Включите плагин в `Правка - Настройки - Модули`. 

## Как пользоваться

1. Выделяем объекты, в числе которых есть здания (можно создать поисковый запрос вида "building=* AND -"addr:housenumber=* and inview" и сохранить его на панель инструментов)
2. В меню `Данные - Russia address helper`
3. После получения адресов из ЕГРН - проверьте их на вменяемость глазами: ЕГРН не является истиной в последней инстанции, в нем множество ошибок и опечаток. Если загруженные данные противоречат имеющимся в ОСМ, или здравому смыслу - не используйте их!

4. не забудьте почистить данные перед отправкой:
- сделайте поиск по "addr:RU:egrn"=* и удалите этот тэг у всех найденных объектов, в ОСМ он не нужен.
- сделайте поиск по fixme=* и убедитесь что не загружаете ненужных точек в ОСМ

## Сдвиг координат

Для повышения точности определения адресов можно (в некоторых регионах - НУЖНО) уточнить сдвиг координат слоя ПКК:

- Привязываем слои спутниковых снимков по ГПС трекам как обычно. Обрисовываем по ним здания.
- Подключаем WMS слой границ кадастровых участков ПКК (на данный момент это возможно только с применением прокси (например nginx) из-за проблем с сертификатами на сайте Росреестра.
Инструкции по настройке смотри тут 
- Сдвигаем слой ПКК так, чтобы границы участков и/или контуры зданий совпали со спутниковым снимком.
- Переходим в настройки плагина и выбираем слой ПКК в настройке "Учитывать сдвиг..." (как правило, слой зданий лучше привязан к координатам)
- При запросах к ПКК координаты здания будут модифицированы, так чтобы попасть в правильные границы.
- Если в настройках указано имя слоя, не подключенного в JOSM в момент запроса к ЕГРН, то функция сдвига работать не будет, на экране отобразится предупреждение.

## Настройки дополнительных адресных точек
В настройках плагина можно включить создание дополнительных адресных точек, если таковые будут найдены для здания.
Так же будут сгенерированы адресные точки квартир, если квартира присутствует в адресе участка или здания.

В целях отладки можно включить создание точек с нераспознанными адресами. Это поможет понять, каких улиц не хватает, или попытаться вручную расшифровать адрес.
Не загружайте такие точки в ОСМ! Удалить их можно поиском по запросу fixme="REMOVE ME!"
Не загружайте такие точки в ОСМ! Удалить их все можно поиском по запросу fixme="REMOVE ME!"

## Горячая клавиша

1. Добавьте кнопку на панель инструментов в  `Правка - Настройки - Панель инструментов`. 
2. Нажмите правой кнопкой по иконке плагина на панели инструментов и выберете `Свойства горячей клавиши`.

## Удаление дубликатов
При обработке частного сектора, промзон часто бывает, что на участке находится 2 и более строений, все они получат из ЕГРН одинаковый адрес.
Для автоматического разрешения этой проблемы можно включить в настройках плагина функцию 'Удаление дублей', которая после получения данных оставит одинаковый адрес у строения с наибольшей площадью.
Функция не трогает уже существующие в редакторе данные, т.е удаляются только загруженные из ЕГРН в данном запросе дубли.
Так же существует настройка дальности поиска дубликатов в метрах, чтобы не захватывать соседние НП или улицы-дубли в одном НП.
При нахождении дубликата будет выведено оповещение об этом.

## Ограничения

1. Не выделяйте много домов сразу, так как с каждым домом отправляется запрос. Сервис ПКК может не выдержать или заблокировать доступ из-за большого количества запросов. В идеале работать выделяя дома вдоль улицы или в небольшой области, приводить данные в порядок и уже после этого приступать к следующим. 
2. Запросы отправляются только для тех зданий, у которых нет номера дома, тега `fixme`, так же пропускаются гаражи и сараи.

## Примечания

* После того как привели значение [адресных тегов](https://wiki.openstreetmap.org/wiki/RU:Key:addr) в порядок, не забудьте удалить теги `fixme` и `addr:RU:egrn`  
* Плагин на данный момент загружает данные только для улиц, переулков, проездов, проспектов, шоссе, площадей. Не распознаются другие адресные единицы, и обьекты типа "микрорайон". 
* Для того чтобы в тэги обьектов сохранялись сырые данные ЕГРН, необходимо в настройках включить галочку "Записывать адрес из ЕГРН в `addr:RU:egrn`".
* Если в ЕГРН встречается многократное именование улицы, которое не может быть сопоставлено с данными ОСМ ("Советская Б улица"), в качестве временного решения можно добавить улице в ОСМ тэг "egrn_name" в который вписать наименование из ЕГРН. После загрузки и сопоставления адресов egrn_name надо удалить, это не валидные данные!
* Плагин умеет бороться с небольшими опечатками в именовании улиц, ошибка в 1-2 буквы может быть проигнорирована (поиск совпадений по алгоритму Jaro-Winkler distance). При таком совпадении будет выведено оповещение.
* Плагин умеет сопоставлять номерные улицы и улицы с инициалами ("улица Карла Маркса" сопоставится с "ул. К. Маркса")
* Программа пытается распознать номер дома с учетом строения, корпуса, буквы. Номера квартир в адресе приведут к генерации дополнительных точек адреса с тэгами `addr:flats`. Понимания, что делать с такими данными пока нет, лучше не загружать их в ОСМ.

## TODO

- [x] Новый инструмент - пипетка. Создаёт точку с данными из ЕГРН в месте клика.
- [x] Локализация на русский. 
- [x] Отдельная галка в настройках для работы с дублями. Проверка существующих адресов и назначение адреса на самое большое по площади здание.
- [ ] Поддержка других типов улиц и обьектов, такие как тупики, аллеи, микрорайоны, кварталы.
- [ ] Возможность добавлять свои типы улиц в настройках.
- [ ] Возможность редактировать и добавлять пользовательские регулярные выражения для парсера адресов ЕГРН.
- [ ] Загрузка справочников типов улиц и регулярных выражений парсера через единый репозиторий. Избавит от необходимости обновления плагина и даст возможность другим людям дополнять парсер.
- [ ] Отдельный парсер для номеров домов. Должен приводить к [общепринятому виду](https://wiki.openstreetmap.org/wiki/RU:Addresses#%D0%9D%D1%83%D0%BC%D0%B5%D1%80%D0%B0%D1%86%D0%B8%D1%8F_%D0%B4%D0%BE%D0%BC%D0%BE%D0%B2).
- [x] Попытка повторной отправки ошибочных запросов установленное в настройках количество раз. Вывод уведомления о не загруженных данных.
- [ ] Вывод в удобном виде информации о распознанных данных и проблемах распознавания при массовой загрузке
