package com.example.cs.common;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体。
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /** 直接用 Spring Data 的 Page 构造（不转换元素类型）。 */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    /** 用 Spring Data 的 Page 构造，并把实体逐个转换为 VO。 */
    public static <E, V> PageResult<V> of(Page<E> page, Function<E, V> mapper) {
        List<V> list = page.getContent().stream().map(mapper).toList();
        return new PageResult<>(list, page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }
}
