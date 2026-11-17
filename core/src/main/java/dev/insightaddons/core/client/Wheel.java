package dev.insightaddons.core.client;

/** 마우스 휠로 값을 바꿀 수 있는 우리 슬라이더. dir>0 이면 올림, shift면 더 크게 바꾼다. */
public interface Wheel {
    boolean wheel(double dir, boolean shift);
}
