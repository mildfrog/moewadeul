package com.moewadeul.common.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moewadeul.common.error.BusinessException;
import com.moewadeul.common.error.CommonErrorCode;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageSliceTest {

    @Test
    @DisplayName("size보다 한 건 더 읽혔으면 hasNext가 true이고 size건만 남긴다")
    void extraRowMeansNextPageAndIsTrimmed() {
        // given
        List<Integer> fetched = List.of(1, 2, 3);

        // when
        PageSlice<Integer> slice = PageSlice.of(fetched, 4, 2);

        // then
        assertThat(slice.content()).containsExactly(1, 2);
        assertThat(slice.page()).isEqualTo(4);
        assertThat(slice.size()).isEqualTo(2);
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    @DisplayName("정확히 size건만 읽혔으면 hasNext가 false이고 그대로 둔다")
    void exactSizeHasNoNextPage() {
        // when
        PageSlice<Integer> slice = PageSlice.of(List.of(1, 2), 0, 2);

        // then
        assertThat(slice.content()).containsExactly(1, 2);
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    @DisplayName("읽힌 행이 없으면 빈 목록이고 hasNext가 false이다")
    void emptyFetchIsEmptyLastPage() {
        // when
        PageSlice<Integer> slice = PageSlice.of(List.of(), 3, 20);

        // then
        assertThat(slice.content()).isEmpty();
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    @DisplayName("map은 항목만 바꾸고 page, size, hasNext를 그대로 둔다")
    void mapKeepsPagingFields() {
        // given
        PageSlice<Integer> slice = new PageSlice<>(List.of(1, 2), 2, 5, true);

        // when
        PageSlice<String> mapped = slice.map(value -> "v" + value);

        // then
        assertThat(mapped).isEqualTo(new PageSlice<>(List.of("v1", "v2"), 2, 5, true));
    }

    @Test
    @DisplayName("조회 크기는 size보다 하나 크고 offset은 page×size이다")
    void fetchSizeAndOffset() {
        assertThat(PageSlice.fetchSize(20)).isEqualTo(21);
        assertThat(PageSlice.offsetOf(3, 20)).isEqualTo(60L);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 10, 20, 50})
    @DisplayName("page×size가 깊이 상한 안인 마지막 쪽은 hasNext가 false이고, 그다음 쪽은 400 INVALID_INPUT이다")
    void lastAllowedPageHasNoNextAndNextPageIsRejected(int size) {
        // given
        int lastPage = PageSlice.MAX_DEPTH / size - 1;
        List<Integer> oneExtraRow = IntStream.rangeClosed(0, size).boxed().toList();

        // when
        long lastOffset = PageSlice.offsetOf(lastPage, size);
        PageSlice<Integer> last = PageSlice.of(oneExtraRow, lastPage, size);
        PageSlice<Integer> beforeLast = PageSlice.of(oneExtraRow, lastPage - 1, size);

        // then
        assertThat(lastOffset + size).isLessThanOrEqualTo(PageSlice.MAX_DEPTH);
        assertThat(PageSlice.MAX_DEPTH - (lastOffset + size)).isLessThan(size);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.content()).hasSize(size);
        assertThat(beforeLast.hasNext()).isTrue();
        assertThatThrownBy(() -> PageSlice.offsetOf(lastPage + 1, size))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("10,000의 약수가 아닌 size 3은 page 3332까지 읽고(hasNext=false), page 3333은 400이다")
    void sizeNotDividingDepthLimit() {
        List<Integer> oneExtraRow = List.of(1, 2, 3, 4);

        assertThat(PageSlice.offsetOf(3332, 3)).isEqualTo(9_996L);
        assertThat(PageSlice.of(oneExtraRow, 3332, 3).hasNext()).isFalse();
        assertThat(PageSlice.of(oneExtraRow, 3331, 3).hasNext()).isTrue();
        assertThatThrownBy(() -> PageSlice.offsetOf(3333, 3)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("오류 문구는 상한을 쉼표 표기로 알려 준다")
    void rejectionMessageUsesGroupedNumber() {
        assertThatThrownBy(() -> PageSlice.offsetOf(500, 20)).hasMessageContaining("앞에서 10,000번째 항목 이후는 볼 수 없어요");
    }

    @Test
    @DisplayName("size가 20이면 500쪽(page 499)까지 읽을 수 있고, int 범위를 넘는 page도 넘치지 않고 400이다")
    void defaultSizeAllowsFiveHundredPages() {
        assertThat(PageSlice.offsetOf(499, 20)).isEqualTo(9_980L);
        assertThatThrownBy(() -> PageSlice.offsetOf(500, 20)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PageSlice.offsetOf(Integer.MAX_VALUE, 50)).isInstanceOf(BusinessException.class);
    }
}
