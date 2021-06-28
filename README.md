# JOSM-Russia-address-helper-plugin
Плагин JOSM для загрузки адресов из ЕГРН.

Плагин автоматически проиводит фильтрация выделенных объектов, выбираяя только полигоны с тегом [bulding](https://wiki.openstreetmap.org/wiki/RU:Key:building).

## Установка

1. Скопируйте файл [russia-address-helper.jar](https://github.com/De-Luxis/JOSM-Russia-address-helper-plugin/releases/latest/download/russia-address-helper.jar) в `%appdata%\JOSM\plugins`
2. Включите плагин в `Правка - Настройки - Модули`. 

## Как пользоваться

1. Выделяем здание
2. `Инструменты - Russia address helper`

## Горячая клавиша

1. Добавьте кнопку на панель инструментов в  `Правка - Настройки - Панель инструментов`. 
2. Нажмите правой кнопкой по иконке плагина на панели инструментов и выберете `Свойства горячей клавиши`.

## Ограничения

1. Не выделяйте много домов, так как с каждым домом отправляется запрос. Сервис может не выдержать или заблокировать доступ из-за большого количества запросов. В идеале работать выделяя дома вдоль улицы, приводить данные в порядок и уже после этого приступать к следующим. 
2. Запросы отправляются только для тех домов, у которых нет номера и пометки fixme.
