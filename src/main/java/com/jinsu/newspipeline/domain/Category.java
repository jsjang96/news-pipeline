package com.jinsu.newspipeline.domain;

/**
 * 기사 분류 카테고리. LLM이 반환한 라벨을 이 enum으로 정규화한다.
 * 확정된 4개 + catch-all(기타). 위 4개에 명확히 안 맞으면 ETC로 떨어진다.
 */
public enum Category {
    AI("AI"),
    DEV("개발"),
    CLOUD_INFRA("클라우드·인프라"),
    SECURITY("보안"),
    ETC("기타");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    /** 화면/응답에 보여줄 한국어 라벨. */
    public String getLabel() {
        return label;
    }

    /**
     * LLM 응답 문자열을 enum으로 변환. 라벨(한국어)이나 enum 이름 모두 허용하고,
     * 어느 것과도 안 맞으면 ETC로 안전하게 떨어뜨린다(분류 안정화).
     */
    public static Category fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return ETC;
        }
        String t = raw.trim();
        for (Category c : values()) {
            if (c.label.equals(t) || c.name().equalsIgnoreCase(t)) {
                return c;
            }
        }
        return ETC;
    }
}
