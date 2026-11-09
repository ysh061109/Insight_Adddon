package com.mrsun.insightchests.bridge;

/**
 * 컴파일용 가짜(stub) 선언입니다. 실제 클래스는 InSight Chests 안에 있고, 이 파일은 jar에 포함되지 않습니다.
 * (build.gradle의 jar { exclude 'com/mrsun/**' } 참고)
 */
public interface IItemBridge {
    boolean isBlock();

    boolean hasGlint();

    int getCount();

    boolean isEmpty();

    boolean isSameItem(IItemBridge other);

    Object getRawStack();
}
