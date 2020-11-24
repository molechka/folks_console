package ru.olgak.folks.api;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import ru.hflabs.util.core.PartsJoiner;
import ru.olgak.folks.api.annotation.Searchable;
import ru.olgak.folks.api.model.Gender;
import ru.olgak.folks.api.model.ThreeStateStatus;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** Класс <class>Folk</class> представляет участника SQA */
@Getter
@Setter
@Entity
public class Folk extends AbstractEntity {

    @OneToMany(mappedBy = "folk", fetch = FetchType.EAGER)
    private List<Device> devices;

    // ФИО
    @Searchable(search = true, filter = true, sort = true)
    private String surname;
    @Searchable(search = true, filter = true, sort = true)
    private String name;
    @Searchable(search = true, filter = true, sort = true)
    private String patronymic;

    // Предпочитаемое имя
    @Column(name = "preferred_name")
    @Searchable(search = true)
    private String preferredName;

    // Пол
    @Searchable(filter = true, sort = true)
    @Enumerated(value = EnumType.ORDINAL)
    private Gender gender;

    // Дата рождения
    @Searchable(search = true, filter = true, sort = true)
    @Column(name = "birth_date")
    @Temporal(TemporalType.DATE)
    private Date birthDate;

    // Место рождения
    @Column(name = "birth_place")
    private String birthPlace;

    // Дата первого посещения конференции
    @Searchable(filter = true)
    @Column(name = "first_conf_date")
    @Temporal(TemporalType.DATE)
    private Date firstConfDate;

    // Компания, в которой работает участник
    @Searchable(search = true, filter = true)
    private String company;

    // Должность
    private String position;

    // Дата начала работы
    @Temporal(TemporalType.DATE)
    @Column(name = "job_start_date")
    private Date jobStartDate;

    // Автор изменений
    @Searchable(search = true, filter = true)
    private String author;

    // Город проживания
    private String city;

    // Докладчик
    @Searchable(filter = true)
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean speaker;

    // Член орг комитета или ПК
    @Searchable(filter = true)
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean organizator;

    /** Признак: VIP-клиент */
    @Searchable(filter = true)
    @Column(name = "vip_flag")
    @Enumerated(value = EnumType.ORDINAL)
    private ThreeStateStatus vipFlag = ThreeStateStatus.UNKNOWN;

    // Какой раз на конфе
    @Searchable(filter = true)
    @Column(name = "visit_count")
    private Integer visitCount;

    // Средний рейтинг участника
    @Searchable(filter = true)
    @Column(name = "average_rating")
    private BigDecimal averageRating;

    // Язык контакта с Клиентом
    private String language;

    // Дата актуальности
    @Temporal(TemporalType.DATE)
    @Column(name = "actuality_date")
    private Date actualityDate;

    @Searchable(filter = true)
    public String getFio() {
        return new PartsJoiner(" ")
                .add(getSurname())
                .add(getName())
                .add(getPatronymic())
                .toString();
    }
}
