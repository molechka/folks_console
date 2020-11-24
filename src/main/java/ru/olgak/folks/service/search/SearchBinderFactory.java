package ru.olgak.folks.service.search;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import lombok.ToString;
import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.springframework.util.ClassUtils;
import ru.hflabs.util.core.FormatUtil;
import ru.hflabs.util.core.Holder;
import ru.hflabs.util.core.Pair;
import ru.hflabs.util.core.date.DateUtil;
import ru.hflabs.util.lucene.LuceneUtil;
import ru.hflabs.util.spring.util.ReflectionUtil;
import ru.olgak.folks.api.Device;
import ru.olgak.folks.api.Folk;
import ru.olgak.folks.api.annotation.Searchable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;

import static ru.hflabs.util.lucene.LuceneUtil.*;

/**
 * Класс <class>SearchBinderFactory</class>
 *
 * @author nikolaig
 */
public class SearchBinderFactory<E extends Folk> {

    /** Кеш полей, по которым производится поиск */
    protected final Holder<Pair<Class<?>, String>, SearchableField> binderFieldsHolder;
    /** Кеш классов, по которым производится поиск, где ключ - класс, значение - коллекция полей */
    protected final Holder<Class<?>, Collection<SearchableField>> binderClassesHolder;

    public SearchBinderFactory() {
        this.binderClassesHolder = new BinderClassesHolder();
        this.binderFieldsHolder = new BinderFieldsHolder();
    }

    public SearchBinderTemplate<E> createSearchBinder(final Class<E> binderClass) {
        return new SearchBinderTemplate<>() {
            @Override
            public Serializable getPrimaryKeyByEssence(E e) {
                return e.getId();
            }

            @Override
            public Document reverseConvert(E e) {
                try {
                    return buildDocument(binderClass, e);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public E convert(Document source) {
                IndexableField currentField;
                if ((currentField = source.getField(OBJECT_FIELD)) != null) {
                    return LuceneUtil.byteToObject(binderClass, currentField.binaryValue());
                }
                return null;
            }

            private <T> Document doBuildDocument(Class<T> binderClass, T entity) throws IllegalAccessException {
                Document result = new Document();
                List<IndexableField> defaultSearchFields = new ArrayList<>();

                IndexableField filterField;
                IndexableField searchField;
                for (SearchableField field : binderClassesHolder.getValue(binderClass)) {
                    // Обрабатываем поля в зависимости от его типа
                    if (CharSequence.class.isAssignableFrom(field.getType())) {
                        String value = FormatUtil.format((String) getFieldValue(entity, field));
                        filterField = new SortedDocValuesField(field.getName(), new BytesRef(value));
                        searchField = new TextField(field.getName(), value, Store.NO);
                    } else if (Integer.class.isAssignableFrom(field.getType())) {
                        int value = integerToPrimitive((Integer) getFieldValue(entity, field));
                        filterField = new NumericDocValuesField(field.getName(), value);
                        searchField = new IntPoint(field.getName(), value);
                    } else if (Long.class.isAssignableFrom(field.getType())) {
                        long value = longToPrimitive((Long) getFieldValue(entity, field));
                        filterField = new NumericDocValuesField(field.getName(), value);
                        searchField = new LongPoint(field.getName(), value);
                    } else if (BigDecimal.class.isAssignableFrom(field.getType())) {
                        double value = bigDecimalToPrimitive((BigDecimal) getFieldValue(entity, field));
                        filterField = new DoubleDocValuesField(field.getName(), value);
                        searchField = new DoublePoint(field.getName(), value);
                    } else if (Boolean.class.isAssignableFrom(field.getType())) {
                        filterField = new StringField(field.getName(), FormatUtil.format((Boolean) getFieldValue(entity, field)), Store.NO);
                        searchField = filterField;
                    } else if (Enum.class.isAssignableFrom(field.getType())) {
                        String value = FormatUtil.format((Enum) getFieldValue(entity, field), false);
                        filterField = new SortedDocValuesField(field.getName(), new BytesRef(value));
                        searchField = new TextField(field.getName(), value, Store.NO);
                    } else if (Date.class.isAssignableFrom(field.getType())) {
                        long value = dateToLong((Date) getFieldValue(entity, field));
                        filterField = new NumericDocValuesField(field.getName(), value);
                        searchField = new LongPoint(field.getName(), value);
                    } else {
                        throw new IllegalArgumentException(String.format(
                                "Field type '%s' not supported by '%s'",
                                field.getType(), getClass()
                        ));
                    }
                    // Добавляем поле фильтрации
                    if (field.isStateEstablished(SearchableField.FILTERABLE)) {
                        result.add(filterField);
                        result.add(searchField);
                    }
                    // Добавляем поле поиска
                    if (field.isStateEstablished(SearchableField.SEARCHABLE)) {
                        if (Date.class.isAssignableFrom(field.getType())) {
                            defaultSearchFields.add(new TextField(field.getName(), DateUtil.formatDate((Date) getFieldValue(entity, field)), Store.NO));
                        } else {
                            defaultSearchFields.add(searchField);
                        }
                    }
                }
                result.add(createDefaultSearchField(defaultSearchFields));

                return result;
            }

            private Document buildDocument(Class<E> binderClass, E entity) throws IllegalAccessException {
                Document result = doBuildDocument(binderClass, entity);

                List<Device> devices = entity.getDevices();
                if (devices != null) {
                    for (Device device : devices) {
                        Document attributeDocument = doBuildDocument(Device.class, device);
                        for (IndexableField attributeField : attributeDocument) {
                            result.add(attributeField);
                        }
                    }
                    devices.forEach(d -> d.setFolk(null));
                }
                entity.setDevices(new ArrayList<>(entity.getDevices()));

                IndexableField currentField = new StoredField(OBJECT_FIELD, objectToByte(entity));
                result.add(currentField);
                return result;
            }
        };
    }

    private Object getFieldValue(Object entity, SearchableField field) throws IllegalAccessException {
        try {
            return field.getField() instanceof Field ? ReflectionUtil.get((java.lang.reflect.Field) field.getField(), entity) :
                    ((Method) field.getField()).invoke(entity);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<SearchableField> getBinderFields(Class<?> binderClass, final int... states) {
        return Collections2.filter(binderClassesHolder.getValue(binderClass), new Predicate<>() {
            @Override
            public boolean apply(SearchableField input) {
                for (Integer state : states) {
                    if (!input.isStateEstablished(state)) {
                        return false;
                    }
                }
                return true;
            }
        });
    }

    /**
     * Проверяет, что поле может участвовать в поиске
     *
     * @param field проверяемое поле
     * @return Возвращает <code>TRUE</code>, если поле может участвовать в поиске
     */
    protected boolean isSearchableField(Member field) {
        Searchable annotation = getSearchableAnno(field);
        return annotation != null && annotation.search();
    }

    private Searchable getSearchableAnno(Member field) {
        return field instanceof Field ? ((Field) field).getAnnotation(Searchable.class)
                : ((Method) field).getAnnotation(Searchable.class);
    }

    /**
     * Проверяет, что поле может участвовать в фильтрации
     *
     * @param field проверяемое поле
     * @return Возвращает <code>TRUE</code>, если поле может участвовать в фильтрации
     */
    protected boolean isFilterableField(Member field) {
        Searchable annotation = getSearchableAnno(field);
        if (annotation != null && (annotation.filter() || annotation.sort())) {
            return true;
        }
        return false;
    }

    /**
     * Проверяет, что поле может участвовать в сортировке
     *
     * @param field проверяемое поле
     * @return Возвращает <code>TRUE</code>, если поле может участвовать в сортировке
     */
    protected boolean isSortableField(Member field) {
        Searchable annotation = getSearchableAnno(field);
        if (annotation != null && annotation.sort()) {
            return true;
        }
        return false;
    }

    /**
     * Класс <class>BinderClassesHolder</class> реализует кеш классов, по которым производится поиск
     *
     * @author Nazin Alexander
     */
    private class BinderClassesHolder extends Holder<Class<?>, Collection<SearchableField>> {

        /**
         * Вычисляет и возвращает статус поля
         *
         * @param field проверяемое поле
         * @return Возвращает статус поля
         */
        private int createFieldState(Member field) {
            int state = 0;
            // Статус поиска
            state = state | (isSearchableField(field) ? SearchableField.SEARCHABLE : 0);
            // Статус фильтрации
            state = state | (isFilterableField(field) ? SearchableField.FILTERABLE : 0);
            // Статус сортировки
            state = state | (isSortableField(field) ? SearchableField.SORTABLE : 0);
            // Возвращаем сформированный статус
            return state;
        }

        @Override
        protected Collection<SearchableField> createValue(Class<?> key) {
            final Collection<SearchableField> fields = new LinkedHashSet<>();
            // Добавляем первичный ключ
                /*{
                    final Field field = ReflectionUtil.findField(key, primaryKeyFieldName);
                    Assert.notNull(field, format("Can't find primary field '%s' in class '%s'", primaryKeyFieldName, key.getName()));
                    fields.add(new SearchableField(field, key, ClassUtils.resolvePrimitiveIfNecessary(field.getType()), field.getName(), createFieldState(field)));
                }               */
            // Добавляем поля класса
            for (Field field : ReflectionUtil.getAnnotatedFieldsList(key, Searchable.class, true)) {
                // Получаем статус поля
                final int state = createFieldState(field);
                // Проверяем типизацию
                if (!field.getType().isInterface() &&
                        !Modifier.isTransient(field.getModifiers()) &&
                        !Modifier.isFinal(field.getModifiers()) &&
                        state != 0) {
                    fields.add(new SearchableField(field, key, ClassUtils.resolvePrimitiveIfNecessary(field.getType()), field.getName(), state));
                }
            }
            // Добавляем методы класса
            for (Method method : ReflectionUtil.getAnnotatedMethodsList(key, Searchable.class)) {
                // Получаем статус поля
                final int state = createFieldState(method);
                // Проверяем типизацию
                if (state != 0) {
                    fields.add(new SearchableField(method, key, ClassUtils.resolvePrimitiveIfNecessary(method.getReturnType()), ReflectionUtil.extractFieldName(method), state));
                }
            }
            // Возвращаем найденные поля
            return fields;
        }
    }

    /**
     * Класс <class>BinderFieldsHolder</class> реализует кеш полей, по которым производится поиск
     *
     * @author Nazin Alexander
     */
    private class BinderFieldsHolder extends Holder<Pair<Class<?>, String>, SearchableField> {

        @Override
        protected SearchableField createValue(Pair<Class<?>, String> key) {
            for (SearchableField field : binderClassesHolder.getValue(key.first)) {
                if (field.getDeclaringName().equals(key.second)) {
                    return field;
                }
            }
            throw new IllegalArgumentException(String.format("Search/filtering for field '%s' in class '%s' is prohibited", key.second, key.first));
        }
    }


    @ToString(of = "name")
    public static final class SearchableField implements Serializable, Cloneable {

        private static final long serialVersionUID = 4446973919932421179L;

        /*
         * Доступные статусы
         */
        public static final int FILTERABLE = 1;
        public static final int SEARCHABLE = 2;
        public static final int SORTABLE = 5;

        /** Поле поиска */
        private final transient Member field;
        /** Класс, в котором объявлено поле */
        private final Class<?> declaringClass;
        /** Название поля в объявленном классе */
        private final String declaringName;
        /** Тип поля */
        private final Class<?> type;

        /** Название поля */
        private final String name;
        /** Статус поля */
        private final int state;

        public SearchableField(Member field, Class<?> declaringClass, Class<?> type, String declaringName, Integer state) {
            this.field = field;
            this.declaringClass = declaringClass;
            this.declaringName = declaringName;
            this.type = type;
            this.name = String.format("%s.%s", declaringClass.getSimpleName(), declaringName);
            this.state = state;
        }

        public Member getField() {
            return field;
        }

        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        public String getDeclaringName() {
            return declaringName;
        }

        public Class<?> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Integer getState() {
            return state;
        }

        /**
         * Проверяет и возвращает <code>TRUE</code>, если указанный статус установлен
         *
         * @param stateToCheck проверяемый статус
         * @return Возвращает флаг проверки
         */
        public boolean isStateEstablished(int stateToCheck) {
            return (state & stateToCheck) == stateToCheck;
        }
    }
}
