package com.moewadeul.common.paging;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.util.List;
import java.util.function.Function;

/**
 * 전체 개수 없이 다음 페이지가 있는지만 아는 페이지다. 조회는 {@link #offsetOf}부터 {@link #fetchSize}건을 읽고,
 * {@link #of}가 size보다 한 건 더 읽혔는지로 hasNext를 정한 뒤 남는 한 건을 자른다.
 * 읽고 버리는 행이 끝없이 늘지 않도록 page×size가 {@link #MAX_DEPTH}(10,000)를 넘는 쪽은 {@link #offsetOf}가 400으로 거절한다.
 * 기본 size 20이면 500쪽까지, size 50이면 200쪽까지 읽을 수 있다. 다음 쪽이 허용되지 않는 마지막 쪽은 행이 더 있어도 hasNext를 false로 줘서
 * "더 보기"가 400을 맞기 전에 멈춘다.
 */
public record PageSlice<T>(List<T> content, int page, int size, boolean hasNext) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    public static final int MAX_DEPTH = 10_000;
    /** 요청 DTO의 {@code @Max}용 느슨한 상한이다. size가 1이어도 이 값을 넘는 쪽은 {@link #MAX_DEPTH}를 넘으므로 터무니없는 값을 바인딩 단계에서 걸러 낸다. 실제 검사는 {@link #offsetOf}가 한다. */
    public static final int MAX_PAGE = MAX_DEPTH - 1;

    public static long offsetOf(int page, int size) {
        if (!withinDepth(page, size)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "앞에서 %,d번째 항목 이후는 볼 수 없어요. 조건을 좁혀 찾아 주세요.".formatted(MAX_DEPTH));
        }
        return (long) page * size;
    }

    private static boolean withinDepth(int page, int size) {
        return (long) page * size + size <= MAX_DEPTH;
    }

    public static int fetchSize(int size) {
        return size + 1;
    }

    public static <T> PageSlice<T> of(List<T> fetched, int page, int size) {
        boolean hasNext = withinDepth(page + 1, size) && fetched.size() > size;
        return new PageSlice<>(fetched.stream().limit(size).toList(), page, size, hasNext);
    }

    public <R> PageSlice<R> map(Function<? super T, ? extends R> mapper) {
        return new PageSlice<>(content.stream().<R>map(mapper).toList(), page, size, hasNext);
    }
}
