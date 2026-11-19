package io.github.vedmider.sestacon.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalTest {

    @Test
    void recognisesTheGroundedRefusalInEnglishAndUkrainian() {
        assertThat(Refusal.isRefusal("I do not know.")).isTrue();
        assertThat(Refusal.isRefusal("I don't know the answer based on the context.")).isTrue();
        assertThat(Refusal.isRefusal("Я не знаю.")).isTrue();
    }

    @Test
    void aRealAnswerWithARefusalClauseIsNotARefusal() {
        assertThat(Refusal.isRefusal("Paris is the capital of France. The context does not say the population, "
                + "so I do not know how many people live there.")).isFalse();
        assertThat(Refusal.isRefusal("The code is 7781-QX.")).isFalse();
        assertThat(Refusal.isRefusal(null)).isFalse();
    }
}
