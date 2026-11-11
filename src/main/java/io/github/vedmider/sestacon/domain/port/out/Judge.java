package io.github.vedmider.sestacon.domain.port.out;

import java.util.List;

/**
 * LLM-as-judge: scores a generated answer against its question and the retrieved contexts.
 * {@code faithfulness} = is the answer supported by the contexts (not hallucinated);
 * {@code answerRelevance} = does the answer actually address the question. Both in [0,1].
 * The default adapter is a deterministic lexical judge; a real LLM judge can replace it.
 */
public interface Judge {

    Verdict judge(String question, String answer, List<String> contexts);

    record Verdict(double faithfulness, double answerRelevance, String rationale) {
        public double overall() {
            return (faithfulness + answerRelevance) / 2.0;
        }
    }
}
