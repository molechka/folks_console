package ru.olgak.folks.service;

import ru.hflabs.util.core.date.DateUtil;
import ru.hflabs.util.spring.util.ReflectionUtil;
import ru.olgak.folks.api.Device;
import ru.olgak.folks.api.Folk;
import ru.olgak.folks.api.search.SearchService;
import ru.olgak.folks.service.dao.FolkRepository;
import ru.olgak.folks.soap.SearchResponse;
import ru.olgak.folks.soap.WAttribute;
import ru.olgak.folks.soap.WField;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Создание, изменение, чтение и удаление участников с обновлением поискового индекса.
 */
public class FolkStore {

    private final FolkRepository folkRepository;
    private final EntityManagerFactory entityManagerFactory;
    private final SearchService<Folk> searchService;

    public FolkStore(FolkRepository folkRepository,
                     EntityManagerFactory entityManagerFactory,
                     SearchService<Folk> searchService) {
        this.folkRepository = folkRepository;
        this.entityManagerFactory = entityManagerFactory;
        this.searchService = searchService;
    }

    public Folk create(SearchResponse.Folk source) {
        if (source == null) {
            throw new IllegalArgumentException("folk is required");
        }
        Long id = inTransaction(em -> {
            Folk folk = new Folk();
            folk.setId(nextId(em, Folk.class));
            applyFields(folk, source.getField());
            em.persist(folk);
            persistDevices(em, folk, source.getAttribute());
            return folk.getId();
        });
        rebuildIndex();
        return getById(id);
    }

    public Folk update(SearchResponse.Folk source) {
        if (source == null || source.getId() == null) {
            throw new IllegalArgumentException("folk id is required");
        }
        inTransaction(em -> {
            Folk folk = em.find(Folk.class, source.getId());
            if (folk == null) {
                throw new IllegalArgumentException("Folk not found: " + source.getId());
            }
            applyFields(folk, source.getField());
            return folk.getId();
        });
        rebuildIndex();
        return getById(source.getId());
    }

    public Folk getById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return folkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folk not found: " + id));
    }

    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        inTransaction(em -> {
            Folk folk = em.find(Folk.class, id);
            if (folk == null) {
                throw new IllegalArgumentException("Folk not found: " + id);
            }
            em.createQuery("delete from Device d where d.folk = :folk")
                    .setParameter("folk", folk)
                    .executeUpdate();
            em.remove(folk);
            return null;
        });
        rebuildIndex();
    }

    private <T> T inTransaction(java.util.function.Function<EntityManager, T> work) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            try {
                T result = work.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException ex) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        } finally {
            em.close();
        }
    }

    private void persistDevices(EntityManager em, Folk folk, List<WAttribute> attributes) {
        if (attributes == null) {
            return;
        }
        for (WAttribute attribute : attributes) {
            if (attribute.getType() != null && !"DEVICE".equalsIgnoreCase(attribute.getType())) {
                continue;
            }
            Device device = new Device();
            device.setId(attribute.getId() != null ? attribute.getId() : nextId(em, Device.class));
            device.setFolk(folk);
            applyFields(device, attribute.getField());
            em.persist(device);
        }
    }

    private long nextId(EntityManager em, Class<?> type) {
        Number max = em.createQuery("select max(e.id) from " + type.getSimpleName() + " e", Number.class)
                .getSingleResult();
        return max == null ? 1L : max.longValue() + 1L;
    }

    private void applyFields(Object target, List<WField> fields) {
        if (fields == null) {
            return;
        }
        Map<String, Field> byName = new HashMap<String, Field>();
        for (Field field : ReflectionUtil.getAllDeclaredFields(target.getClass())) {
            if (!Collection.class.isAssignableFrom(field.getType())) {
                byName.put(field.getName(), field);
            }
        }
        for (WField wField : fields) {
            Field field = byName.get(wField.getName());
            if (field == null || "id".equals(field.getName()) || "folk".equals(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(target, convert(field.getType(), wField.getValue()));
            } catch (IllegalAccessException | ParseException ex) {
                throw new IllegalArgumentException("Cannot set " + wField.getName(), ex);
            }
        }
    }

    private Object convert(Class<?> type, String value) throws ParseException {
        if (value == null || value.isEmpty()) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
        if (type == String.class) {
            return value;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf(value);
        }
        if (type == Long.class || type == long.class) {
            return Long.valueOf(value);
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (Date.class.isAssignableFrom(type)) {
            return new SimpleDateFormat(DateUtil.DATE_PATTERN).parse(value);
        }
        if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum parsed = Enum.valueOf((Class<Enum>) type, value);
            return parsed;
        }
        throw new IllegalArgumentException("Unsupported field type " + type.getName());
    }

    private void rebuildIndex() {
        try {
            searchService.rebuild();
        } catch (Exception ex) {
            throw new IllegalStateException("Search index rebuild failed", ex);
        }
    }
}
