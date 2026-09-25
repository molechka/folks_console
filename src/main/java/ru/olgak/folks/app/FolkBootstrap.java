package ru.olgak.folks.app;

import ru.olgak.folks.api.Folk;
import ru.olgak.folks.api.search.SearchService;

/**
 * Перестраивает поисковый индекс после загрузки демонстрационных данных.
 */
public class FolkBootstrap {

    private final SearchService<Folk> searchService;

    public FolkBootstrap(SearchService<Folk> searchService) {
        this.searchService = searchService;
    }

    public void start() throws Exception {
        searchService.rebuild();
    }
}
