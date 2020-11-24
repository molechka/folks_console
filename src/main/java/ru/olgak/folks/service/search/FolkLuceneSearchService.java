package ru.olgak.folks.service.search;

import com.google.common.collect.ImmutableSet;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;
import ru.hflabs.util.lucene.*;
import ru.olgak.folks.api.Device;
import ru.olgak.folks.api.Folk;
import ru.olgak.folks.api.search.EntityFieldFilter;
import ru.olgak.folks.api.search.EntityQuery;
import ru.olgak.folks.api.search.SearchService;
import ru.olgak.folks.service.dao.FolkRepository;

import javax.swing.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static ru.hflabs.util.lucene.LuceneQueryUtil.createTermQuery;
import static ru.hflabs.util.lucene.LuceneQueryUtil.rewriteBooleanQuery;

/**
 * Класс <class>FolkLuceneSearchService</class>
 *
 * @author nikolaig
 */
@Setter
public class FolkLuceneSearchService implements SearchService<Folk>, InitializingBean {

    /** Трансформер поисковой сущности */
    private SearchBinderTemplate<Folk> searchBinder;

    private Collection<SearchBinderFactory.SearchableField> filteredFields;

    /** Сервис доступа к поисковому индексу */
    private SearchIndexAccessor accessor;
    /** Менеджен поискового индекса */
    private SearcherManager searcherManager;
    /** Репозиторий участников */
    private FolkRepository folkRepository;

    /** Сервис разбора запросов */
    private LuceneQueryParser queryParser;

    public void setSearchBinderFactory(SearchBinderFactory<Folk> searchBinderFactory) {
        this.searchBinder = searchBinderFactory.createSearchBinder(Folk.class);
        this.filteredFields = ImmutableSet.<SearchBinderFactory.SearchableField>builder()
                .addAll(searchBinderFactory.getBinderFields(Folk.class, SearchBinderFactory.SearchableField.FILTERABLE))
                .addAll(searchBinderFactory.getBinderFields(Device.class, SearchBinderFactory.SearchableField.FILTERABLE))
                .build();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        accessor.open();
        searcherManager = accessor.createSearcherManager();
    }

    @SneakyThrows
    @Override
    public void rebuild() throws IOException {
        IndexWriter indexWriter = accessor.retrieveWriter();
        indexWriter.deleteAll();

        List<Folk> all = folkRepository.findAll();
        LuceneModifyOperation.ModifyResult result = new LuceneModifyUtil.BatchInsert().process(accessor.retrieveWriter(), searchBinder, all);
        accessor.commitWriter(indexWriter, result.getChangedEssences().size(), result.getCommitMode());
    }

    @Override
    public List<Folk> findEntities(final EntityQuery entityQuery) {
        try {
            return LuceneQueryUtil.query(searchBinder, new LuceneQueryCallback() {
                @Override
                public ReferenceManager<IndexSearcher> getSearcherManager() {
                    return refreshSearcherManager(false);
                }

                @Override
                public LuceneQueryDescriptor getQueryDescriptor() {
                    return createQuery(entityQuery);
                }

                @Override
                public DocumentStoredFieldVisitor createStoredFieldVisitor() {
                    return new DocumentStoredFieldVisitor();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Проверяет и выполняет обновление менеджера поиска
     *
     * @return Возвращает актуальный менеджера поиска
     */
    private SearcherManager refreshSearcherManager(boolean force) {
        try {
            // Проверяем актуальность менеджера
            if (force || !searcherManager.isSearcherCurrent()) {
                // Пытаемся заблокировать менеджер
                if (searcherManager.maybeRefresh()) {
                    // Выполняем обновление общего количества объектов
                } else {
                    // Выполняем принудительную блокировку, т.к. другой поток уже выполняет обновление
                    searcherManager.maybeRefreshBlocking();
                }
            }
            return searcherManager;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private LuceneQueryDescriptor createQuery(EntityQuery entityQuery) {
        final BooleanQuery.Builder query = new BooleanQuery.Builder();
        // Проверяем что фильтр задан
        if (entityQuery != null) {
            // Добавляем критерий поиска
            Query searchQuery = buildSearch(entityQuery.getSearch());
            if (searchQuery != null) {
                query.add(searchQuery, BooleanClause.Occur.MUST);
            }
            // Добавляем критерий фильтрации
            for (Query q : buildFilters(Folk.class, entityQuery.getFilters())) {
                if (q != null) {
                    query.add(q, BooleanClause.Occur.MUST);
                }
            }
        }
        // Создаем сортировку
        Set<SortField> sortFields = buildOrder(Folk.class, entityQuery != null ? entityQuery.getSortOrderKey() : null, entityQuery != null ? entityQuery.getSortOrderValue() : null);
        // Пересоздаем запрос
        Query resultQuery = rewriteBooleanQuery(query.build());
        // Формируем и возвращает созданную пару
        resultQuery = resultQuery != null ? resultQuery : new MatchAllDocsQuery();
        Sort sort = sortFields != null && !sortFields.isEmpty() ? new Sort(sortFields.toArray(new SortField[sortFields.size()])) : null;
        return new LuceneQueryDescriptor(resultQuery, sort, null, 0, 100_000);
    }

    public Set<SortField> buildOrder(Class<Folk> searchClass, String orderKey, SortOrder orderValue) {
        // Коллекция полей с сортировкой
        final Set<SortField> sortFields = new LinkedHashSet<SortField>(2);
        // Формируем сортировку
        if (StringUtils.hasText(orderKey) && orderValue != SortOrder.UNSORTED) {
            boolean revers = orderValue != null && orderValue == SortOrder.DESCENDING;
            // Получаем поле сортировки
            SearchBinderFactory.SearchableField searchableField = getSearchableField(orderKey);
            // Формируем тип сортировки
            final SortField sortField;
            if (Integer.class.isAssignableFrom(searchableField.getType())) {
                sortField = new SortedNumericSortField(searchableField.getName(), SortField.Type.INT, revers);
            } else if (Long.class.isAssignableFrom(searchableField.getType())) {
                sortField = new SortedNumericSortField(searchableField.getName(), SortField.Type.LONG, revers);
            } else if (BigDecimal.class.isAssignableFrom(searchableField.getType())) {
                sortField = new SortedNumericSortField(searchableField.getName(), SortField.Type.DOUBLE, revers);
            } else if (Date.class.isAssignableFrom(searchableField.getType())) {
                sortField = new SortedNumericSortField(searchableField.getName(), SortField.Type.LONG, revers);
            } else {
                sortField = new SortField(searchableField.getName(), SortField.Type.STRING, revers);
            }
            sortFields.add(sortField);
            // Формируем сортировку по умолчанию
            if (!SearchBinderTemplate.ID_FIELD_NAME.equals(orderKey)) {
                sortFields.add(new SortedNumericSortField(getSearchableField(SearchBinderTemplate.ID_FIELD_NAME).getName(), SortField.Type.LONG));
            }
        }
        // Возвращаем результат
        return !sortFields.isEmpty() ? sortFields : null;
    }

    private SearchBinderFactory.SearchableField getSearchableField(String key) {
        for (SearchBinderFactory.SearchableField filteredField : filteredFields) {
            if (filteredField.getDeclaringName().equals(key)) {
                return filteredField;
            }
        }
        throw new IllegalArgumentException(key);
    }

    public Collection<Query> buildFilters(Class<Folk> searchClass, Map<String, EntityFieldFilter<?>> filters) {
        final Collection<Query> criteria = new ArrayList<Query>();
        // Проверяем, что фильтры заданы
        if (filters != null) {
            // Выполняем построение коллекции критериев фильтрации
            for (Map.Entry<String, EntityFieldFilter<?>> entry : filters.entrySet()) {
                // Определяем поле фильтра
                final SearchBinderFactory.SearchableField searchableField = getSearchableField(entry.getKey());
                // Получаем значение фильтра
                final EntityFieldFilter<?> value = entry.getValue();
                // Формируем критерии
                Query filterQuery = null;
                if (value instanceof EntityFieldFilter.EmptyValue) {
                    if (Date.class.isAssignableFrom(searchableField.getType())) {
                        filterQuery = createTermQuery(searchableField.getName(), LuceneUtil.DATE_MIN_NULL_VALUE);
                    } else {
                        final BooleanQuery.Builder query = new BooleanQuery.Builder();
                        query.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                        query.add(new TermRangeQuery(searchableField.getName(), null, null, true, true), BooleanClause.Occur.MUST_NOT);
                        filterQuery = query.build();
                    }
                } else if (value instanceof EntityFieldFilter.NotEmptyValue) {
                    if (Date.class.isAssignableFrom(searchableField.getType())) {
                        final BooleanQuery.Builder query = new BooleanQuery.Builder();
                        query.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                        query.add(createTermQuery(searchableField.getName(), LuceneUtil.DATE_MIN_NULL_VALUE), BooleanClause.Occur.MUST_NOT);
                        filterQuery = query.build();
                    } else {
                        filterQuery = new TermRangeQuery(searchableField.getName(), null, null, true, true);
                    }
                } else if (value instanceof EntityFieldFilter.PatternValue) {
                    filterQuery = buildFilterByPatternValue(searchClass, searchableField.getName(), (EntityFieldFilter.PatternValue) value);
                } else {
                    throw new IllegalArgumentException();
                }
                criteria.add(filterQuery);
            }
        }
        // Возвращаем сформированные критерии
        return criteria;
    }

    public Query buildSearch(String search) {
        if (search != null && !search.isEmpty()) {
            final Matcher directQueryMatcher = EntityQuery.DIRECT_QUERY_PATTERN.matcher(search);
            if (directQueryMatcher.matches()) {
                String directQuery = directQueryMatcher.group(1).trim();
                return !directQuery.isEmpty() ? queryParser.parseQuery(directQuery, filteredFields) : null;
            } else {
                final BooleanQuery.Builder query = new BooleanQuery.Builder();
                // Разбиваем строку поиска по отдельным словам
                final Set<String> words = new LinkedHashSet<String>(Arrays.asList(search.replaceAll("\\s+", " ").split(" ")));
                // Формируем запросы для каждого слова
                for (String word : words) {
                    if (StringUtils.hasText(word)) {
                        final EntityFieldFilter.PatternValue patternValue = new EntityFieldFilter.PatternValue(word);
                        query.add(buildFilterByPatternValue(String.class, SearchBinderTemplate.DEFAULT_SEARCH_FIELD, patternValue), BooleanClause.Occur.MUST);
                    }
                }
                // Переформировываем и возвращаем запрос поиска
                return rewriteBooleanQuery(query.build());
            }
        }
        return null;
    }

    private Query buildFilterByPatternValue(Class<?> searchableType, String searchableField, EntityFieldFilter.PatternValue value) {
        String patternValue = value.getPattern();
        // Проверяем, что в выражении содержатся символы регулярного выражения
        if (patternValue.contains(EntityFieldFilter.PatternValue.MATCH_ANY_CHARACTER) || patternValue.contains(EntityFieldFilter.PatternValue.MATCH_SINGLE_CHARACTER)) {
            try {
                patternValue = EntityFieldFilter.PatternValue.convertToPatternSyntax(patternValue);
                // Компилируем регулярное выражение
                String pattern = Pattern.compile(patternValue, value.getFlags()).pattern();
                // Возвращаем запрос
                return new RegexpQuery(LuceneUtil.valueToTerm(searchableField, pattern));
            } catch (PatternSyntaxException ex) {
                throw new RuntimeException(ex);
            }
        }
        return createTermQuery(searchableField, patternValue.toLowerCase());
    }
}
