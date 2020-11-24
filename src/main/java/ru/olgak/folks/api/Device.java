package ru.olgak.folks.api;

import lombok.Getter;
import lombok.Setter;
import ru.olgak.folks.api.annotation.Searchable;
import ru.olgak.folks.api.model.DeviceOS;
import ru.olgak.folks.api.model.DeviceType;

import javax.persistence.*;
import java.util.Date;

/** Класс <class>Device</class> описывает устройство участника */
@Getter
@Setter
@Entity
public class Device extends AbstractEntity {

    @JoinColumn(name = "folk")
    @ManyToOne(fetch = FetchType.EAGER)
    private Folk folk;

    // Тип устройства
    @Searchable(filter = true)
    @Enumerated(value = EnumType.STRING)
    private DeviceType type = DeviceType.UNKNOWN;

    // Операционная система
    @Searchable(search = true)
    @Enumerated(value = EnumType.STRING)
    private DeviceOS os = DeviceOS.UNKNOWN;

    // Модель
    @Searchable(search = true, filter = true)
    private String model;

    // Серийный номер
    @Column(name = "serial_number")
    private String serialNumber;

    // Автор изменений
    @Searchable(search = true, filter = true)
    private String author;

    // Дата актуальности
    @Column(name = "actuality_date")
    @Temporal(TemporalType.DATE)
    private Date actualityDate;

}
