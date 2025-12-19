package com.example.kursach.data

import com.example.kursach.model.Trainer

object DataSource {
    fun getTrainers(): List<Trainer> {
        return listOf(
            Trainer(
                id = "1",
                name = "Алексей Петров",
                category = "Спорт",
                hobbyName = "Я тренер по самбо",
                description = "Опытный тренер по футболу с 10-летним стажем",
                price = 1500,
                rating = 4.8f,
                availableTime = "Пн-Пт: 18:00-21:00",
                availableDays = listOf(0, 1, 2, 3, 4),
                address = "Москва, ул. Спортивная, 10",
                latitude = 55.7320,
                longitude = 37.5630
            ),
            Trainer(
                id = "2",
                name = "Мария Иванова",
                category = "Музыка",
                hobbyName = "Преподаю игру на фортепиано",
                description = "Преподаватель фортепиано, консерватория",
                price = 2000,
                rating = 4.9f,
                availableTime = "Вт-Сб: 14:00-20:00",
                availableDays = listOf(1, 2, 3, 4, 5),
                address = "Санкт-Петербург, пр. Невский, 25",
                latitude = 59.9343,
                longitude = 30.3351
            ),
            Trainer(
                id = "3",
                name = "Дмитрий Сидоров",
                category = "Искусство",
                hobbyName = "Уроки живописи и рисунка",
                description = "Художник, мастер живописи и рисунка",
                price = 1800,
                rating = 4.7f,
                availableTime = "Ср-Вс: 10:00-18:00",
                availableDays = listOf(2, 3, 4, 5, 6),
                address = "Казань, ул. Баумана, 5",
                latitude = 55.7887,
                longitude = 49.1221
            ),
            Trainer(
                id = "4",
                name = "Анна Козлова",
                category = "Танцы",
                hobbyName = "Бальные танцы для начинающих",
                description = "Профессиональный хореограф, латиноамериканские танцы",
                price = 1700,
                rating = 4.6f,
                availableTime = "Пн-Пт: 19:00-22:00",
                availableDays = listOf(0, 1, 2, 3, 4),
                address = "Екатеринбург, ул. Ленина, 50",
                latitude = 56.8389,
                longitude = 60.6057
            ),
            Trainer(
                id = "5",
                name = "Игорь Волков",
                category = "Кулинария",
                hobbyName = "Итальянская кухня от шеф-повара",
                description = "Шеф-повар, итальянская кухня",
                price = 2500,
                rating = 5.0f,
                availableTime = "Сб-Вс: 11:00-17:00",
                availableDays = listOf(5, 6),
                address = "Москва, ул. Тверская, 15",
                latitude = 55.7577,
                longitude = 37.6138
            ),
            Trainer(
                id = "6",
                name = "Елена Смирнова",
                category = "Языки",
                hobbyName = "Английский язык с носителем",
                description = "Преподаватель английского языка, носитель",
                price = 2200,
                rating = 4.9f,
                availableTime = "Пн-Сб: 9:00-21:00",
                availableDays = listOf(0, 1, 2, 3, 4, 5),
                address = "Москва, ул. Арбат, 30",
                latitude = 55.7520,
                longitude = 37.5927
            ),
            Trainer(
                id = "7",
                name = "Лена Головач",
                category = "Языки",
                hobbyName = "Специальный язык с сосителем",
                description = "Преподаватель языка носитель!",
                price = 5252,
                rating = 5.0f,
                availableTime = "Пн-Сб: 9:00-21:00",
                availableDays = listOf(0, 1, 2, 3, 4, 5),
                address = "СПб, ул. Мухосранск, 52",
                latitude = 59.9500,
                longitude = 30.3200
            )
        )
    }
}


