package dev.emi.emi.api.widget;

/** 컴파일용 stub (EMI 실제 클래스를 쓰며 jar에 포함되지 않음). */
public interface WidgetHolder {
    int getWidth();

    int getHeight();

    <T extends Widget> T add(T widget);
}
