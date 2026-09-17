package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChunksTest {

    private static List<String> codes(int count) {
        return IntStream.range(0, count).mapToObj(i -> "H-" + i).toList();
    }

    @Test
    void T11_120개는_50_50_20_세_청크로_나뉜다() {
        var chunks = Chunks.split(codes(120), 50);

        assertThat(chunks).extracting(List::size).containsExactly(50, 50, 20);
        assertThat(chunks.get(0).get(0)).isEqualTo("H-0");
        assertThat(chunks.get(2).get(19)).isEqualTo("H-119");
    }

    @Test
    void T11_50개는_청크_하나() {
        assertThat(Chunks.split(codes(50), 50)).extracting(List::size).containsExactly(50);
    }

    @Test
    void T11_1개는_청크_하나() {
        assertThat(Chunks.split(codes(1), 50)).extracting(List::size).containsExactly(1);
    }
}
