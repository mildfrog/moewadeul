package com.moewadeul.memory.api;

import com.moewadeul.common.paging.PageSlice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record MemoryFeedRequest(
        @Schema(description = "직전 응답의 nextCursor. 보내지 않으면 첫 페이지를 주고, 보내면 그 id보다 오래된 기록부터 준다.")
        @Positive(message = "커서는 1 이상이어야 해요.")
        Long cursor,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 해요.")
        @Max(value = PageSlice.MAX_SIZE, message = "페이지 크기는 " + PageSlice.MAX_SIZE + " 이하여야 해요.")
        Integer size) {

    int sizeOrDefault() {
        return size == null ? PageSlice.DEFAULT_SIZE : size;
    }
}
