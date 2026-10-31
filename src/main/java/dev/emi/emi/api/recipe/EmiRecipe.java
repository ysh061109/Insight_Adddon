package dev.emi.emi.api.recipe;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.List;

/** 컴파일용 stub (EMI 실제 클래스를 쓰며 jar에 포함되지 않음). */
public interface EmiRecipe {
    List<EmiIngredient> getInputs();

    List<EmiStack> getOutputs();
}
