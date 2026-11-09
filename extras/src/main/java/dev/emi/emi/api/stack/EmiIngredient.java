package dev.emi.emi.api.stack;

import java.util.List;

/** 컴파일용 stub (EMI 실제 클래스를 쓰며 jar에 포함되지 않음). */
public interface EmiIngredient {
    List<EmiStack> getEmiStacks();

    long getAmount();
}
